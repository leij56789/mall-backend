package com.mall.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.annotation.AuditLog;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.common.RedisKeys;
import com.mall.config.MessageProperties;
import com.mall.config.SnowflakeIdGenerator;
import com.mall.entity.Orders;
import com.mall.entity.PaymentOrder;
import com.mall.entity.PaymentRefundRecord;
import com.mall.enums.*;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.mapper.PaymentRefundRecordMapper;
import com.mall.pay.client.PayClient;
import com.mall.pay.client.RefundQueryClient;
import com.mall.pay.config.PayClientFactory;
import com.mall.pay.config.PayProperties;
import com.mall.pay.dto.*;
import com.mall.pay.service.AsyncQueryService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 支付事务编排服务
 * <p>
 * 聚合支付相关的原子性写操作（创建、状态更新、订单联动）。
 *
 * <p><b>⚠️ 注意：</b>
 * <ul>
 *   <li>此类包含自注入（self-injection），用于解决 {@code @Transactional} 内部调用失效问题</li>
 *   <li>这不是对外 API 层，请优先使用 {@link PaymentOrderService}</li>
 *   <li>如需新增事务方法，请先评估是否需要独立 Service</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrchestrationService {

    @Lazy
    @Autowired
    private PaymentOrderService paymentOrderService;
    @Lazy
    @Autowired
    private PaymentOrchestrationService paymentOrchestrationService;
    @Lazy
    @Autowired
    private AsyncQueryService asyncQueryService;


    private final OrderPaymentOrchestrationService orderPaymentOrchestrationService;
    private final PaymentOrderMapper paymentOrderMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final MessageProperties messageProperties;
    private final AlertService alertService;
    private final PayClientFactory payClientFactory;
    private final PayProperties payProperties;
    private final PaymentRefundRecordService paymentRefundRecordService;
    private final TaskScheduler taskScheduler;
    private final RedissonClient redissonClient;
    private final PaymentRefundRecordMapper paymentRefundRecordMapper;
    private final PaymentAnnotationService paymentAnnotationService;


    @Data
    @Builder
    public static class InitResult {
        private Orders order;          // 更新后的订单
        private PaymentOrder paymentOrder; // 新建的支付单
    }

    /**
     * 加锁 + 初始化支付单（带事务）
     * 返回值：初始化成功的 PaymentOrder（状态为 INIT）
     */
    @AuditLog(
            targetTypes = {AuditTargetType.ORDER},
            orderId = "orderId",
            operation = AuditOperation.CREATE_PAYMENT,
            desc = "创建支付单"
    )
    @Log("初始化支付单initPaymentWithLock")
    @Transactional(rollbackFor = Exception.class)
    public InitResult initPaymentWithLock(Long orderId, Long userId, String paymentMethod) {
        // ========== 1. 校验订单（补全你的空 if） ==========
        Orders order = orderPaymentOrchestrationService.selectOrderStatus(orderId, OrderStatus.PENDING.getValue());
        if (order == null) {
            log.error("订单不存在或状态异常, orderId={}", orderId);
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("订单金额异常, orderId={}, amount={}", orderId, order.getTotalAmount());
            throw new BusinessException(ResultCode.ORDER_AMOUNT_ERROR);
        }
        if (StrUtil.isBlank(paymentMethod)) {
            log.warn("支付方式为空，orderId={}", orderId);
            throw new BusinessException(ResultCode.PARAM_MISSING, "支付方式不能为空");
        }

        // 2. 查询已有支付单
        PaymentOrder existing = paymentOrderMapper.selectByOrderIdForUpdate(orderId, PaymentStatus.FAILED.getCode());
        // 3. 如果存在且状态为 FAILED，删除后重建（你的业务逻辑）
        boolean deletedOld=false;
        if (existing != null) {
            paymentOrderMapper.deleteById(existing.getId());
            deletedOld = true;
            log.info("删除旧FAILED支付单: paymentId={}, orderId={}", existing.getPaymentId(), orderId);
        }
        // 4. 新建 INIT 状态支付单
        LocalDateTime expireTime = LocalDateTime.now().plus(messageProperties.getPaymentOrderDelayTimeS());
//        log.info("测试bug：1：expireTime={},messageProperties.getPaymentOrderDelayTimeS()={}",expireTime,messageProperties.getPaymentOrderDelayTimeS());

        String paymentId = String.valueOf(snowflakeIdGenerator.nextId());
        PaymentOrder paymentOrder = PaymentOrder.builder()
                                                .orderId(orderId)
                                                .userId(userId)
                                                .amount(order.getTotalAmount())
                                                .paymentId(paymentId)
                                                .expiredAt(expireTime)
                                                .paymentMethod(paymentMethod)
                                                .status(PaymentStatus.INIT.getCode())
                                                .build();
        try {
            int inserted = paymentOrderMapper.insert(paymentOrder);
            if (inserted != 1) {
                // 理论上 insert 成功一定是 1，这里保留防御性编程
                throw new BusinessException(ResultCode.PAYMENT_CREATE_FAIL);
            }
        } catch (Exception e) {
            // 唯一索引冲突 -> 说明已有 INIT/WAITING/SUCCESS 状态的支付单
            log.warn("唯一索引冲突，订单已存在支付单, orderId={}", orderId,e);
            throw new BusinessException(ResultCode.PAYMENT_ORDER_EXISTS);
        }

        log.info("创建支付单成功, paymentId={}, orderId={}", paymentId, orderId);
        // ========== 4. 记录审计日志 ==========
//        String desc = "创建支付单" + (deletedOld ? "（删除旧FAILED单后重建）" : "");
//        auditService.builder()
//                    .paymentId(paymentId)
//                    .orderId(orderId)
//                    .userId(userId)
//                    .operation(AuditOperation.CREATE_PAYMENT.getCode())
//                    .operationDesc(desc)
//                    .afterStatus(PaymentStatus.INIT.getCode())
//                    .result(AuditResult.SUCCESS.getCode())
//                    .log();

        return InitResult
                .builder()
                .order(order)
                .paymentOrder(paymentOrder)
                .build();

    }
    /**
     * 获取支付凭证（统一入口）
     * - 如果存在有效的 WAITING 支付单，复用并重新生成凭证
     * - 否则创建新支付单
     */
    @Log("获取waiting状态支付表单doGetPaymentForm")
    @Transactional(rollbackFor = Exception.class)
    public PaymentResponse doGetPaymentForm(Long orderId, Long userId, PayClient payClient,
                                            String returnUrl, String quitUrl) {
        Orders order = orderPaymentOrchestrationService.selectOrderStatus(orderId, OrderStatus.PENDING.getValue());
        if (order == null) {
            log.error("订单不存在或状态异常, orderId={}", orderId);
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("订单金额异常, orderId={}, amount={}", orderId, order.getTotalAmount());
            throw new BusinessException(ResultCode.ORDER_AMOUNT_ERROR);
        }
        // 1. 检查是否有 WAITING 状态的支付单
        PaymentOrder existing = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderId, orderId)
                        .eq(PaymentOrder::getStatus, PaymentStatus.WAITING.getCode())
        );

        if (existing != null) {
            ThirdPartyPayResponse thirdResp = paymentOrchestrationService.getThirdRespFromThirdParty(existing, order, orderId, userId, payClient);
            return paymentOrchestrationService.buildPaymentResponse(existing.getPaymentId(),thirdResp,existing.getExpiredAt());
        }
        // 2. 无 WAITING 状态，走正常创建流程
//        return createNewPayment(order, userId, paymentMethod, returnUrl, quitUrl);
        return null;
    }
    public ThirdPartyPayResponse getThirdRespFromThirdParty(PaymentOrder paymentOrder, Orders orders, Long orderId, Long userId, PayClient payClient) {
        if (paymentOrder == null || orders == null) {
            log.error("支付单或订单为空, paymentOrder={}, order={}", paymentOrder, orders);
            throw new BusinessException(ResultCode.PAYMENT_CREATE_FAIL);
        }
        // 发起 HTTP 调用（模拟或真实SDK）
        String paymentId = paymentOrder.getPaymentId();
        LocalDateTime expireTime = paymentOrder.getExpiredAt();
        ThirdPartyPayRequest thirdReq = ThirdPartyPayRequest
                .builder()
                .outTradeNo(paymentId)
                .orderId(orderId)
                .userId(userId)
                .totalAmount(paymentOrder.getAmount()
                                         .toString())
                .subject("商城订单-" + orderId)
                .notifyUrl("https://yourdomain.com/api/payment/callback")
                .timeExpire(expireTime)
                .build();

        ThirdPartyPayResponse thirdResp=null;
        try {
            thirdResp = payClient.unifiedOrder(thirdReq);
        }catch (BusinessException e) {

            if(!payClient.canRecoverFromPendingConfirm()){
                // 2. 无论关单结果如何，直接转 FAILED
                paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(
                        paymentOrder, PaymentStatus.INIT.getCode()
                );
                throw e;
            }
            if(ResultCode.PAYMENT_DESERIALIZE_FAIL.getCode().equals(e.getCode())
                    ||ResultCode.PAYMENT_TIMEOUT.getCode().equals(e.getCode())){
                int updated = paymentOrderMapper.update(new LambdaUpdateWrapper<PaymentOrder>()
                        .set(PaymentOrder::getStatus, PaymentStatus.PENDING_CONFIRM.getCode())
                        .eq(PaymentOrder::getId, paymentOrder.getId())
                        .eq(PaymentOrder::getStatus, PaymentStatus.INIT.getCode()));
                if(updated!=1){
                    throw new BusinessException(ResultCode.DB_OPERATION_FAIL);
                }
                // ?? 触发即时补偿（延迟 10 秒）
                asyncQueryService.scheduleQuery(paymentId, 10, TimeUnit.SECONDS,3);
//                return PaymentResponse.processing(paymentId);
                //当前此逻辑不处理
                return null;
            }else{
                paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(paymentOrder,PaymentStatus.INIT.getCode());
            }
            throw e;
        }catch (Exception e){
            if(!payClient.canRecoverFromPendingConfirm()){
                paymentOrchestrationService.closePaymentOrder(paymentOrder);
//                closePaymentOrder(e, paymentId, orderId, payClient, paymentOrder);
            }
            paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(paymentOrder,PaymentStatus.INIT.getCode());
            throw e;
        }
        if(thirdResp==null||!thirdResp.isSuccess()) {
            paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(paymentOrder,PaymentStatus.INIT.getCode());
            throw new BusinessException(ResultCode.PAYMENT_RESPONSE_ERROR);
        }
//        Map<String,Object> extInfo=payClient.getExtInfo(thirdResp);
        return thirdResp;
    }

    public PaymentResponse buildPaymentResponse(String paymentId, ThirdPartyPayResponse thirdResp, LocalDateTime expireTime) {
        PaymentResponse paymentResponse = PaymentResponse
                .builder()
                .paymentId(paymentId)
                .status(PaymentStatus.WAITING.getDesc())
                .payUrl(thirdResp.getPayParams())
                .expireAt(expireTime)
                .build();
        return paymentResponse;
    }
    /**
     * 支付成功后处理支付单和订单状态（必须在新事务中执行）
     * 该方法由外部调用，确保事务独立生效
     *
     * @param paymentOrder 支付单实体（已包含 ID 等信息）
     * @param extInfo    第三方返回的额外信息
     * @throws BusinessException 当订单状态异常或金额不符时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePaymentStatusToWaitingFromStatusOnTransactional(PaymentOrder paymentOrder, String paymentStatus,String prepayId, Map<String,Object> extInfo) {
        if(paymentOrder==null||paymentOrder.getOrderId()==null){
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }
        // 1. 校验订单状态是否为“待支付”（PENDING）
        Orders order = orderPaymentOrchestrationService.selectOrderStatus(
                paymentOrder.getOrderId(),
                OrderStatus.PENDING.getValue()
        );

        if (order == null) {
            log.error("订单不存在或状态异常，orderId={}", paymentOrder.getOrderId());
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        if (order.getTotalAmount() == null) {
            log.error("订单金额为空，orderId={}", paymentOrder.getOrderId());
            throw new BusinessException(ResultCode.ORDER_AMOUNT_ERROR);
        }

        // 2. 更新支付单状态为 WAITING（并填充第三方支付单号）
        //    该方法内部应包含乐观锁（status = INIT）校验，避免并发覆盖
        paymentAnnotationService.updatePaymentStatusToWaitingFromStatus(
                paymentOrder,
                paymentStatus,
                prepayId,
                order,
                extInfo
        );
        // ========== 3. 记录审计日志 ==========
//        try {
//            auditService.builder()
//                        .paymentId(paymentOrder.getPaymentId())
//                        .orderId(paymentOrder.getOrderId())
//                        .userId(paymentOrder.getUserId())
//                        .operation(AuditOperation.CREATE_PAYMENT.getCode())
//                        .operationDesc("生成支付凭证（WAITING）")
//                        .beforeStatus(paymentStatus)
//                        .afterStatus(PaymentStatus.WAITING.getCode())
//                        .result(AuditResult.SUCCESS.getCode())
//                        .log();
//        } catch (Exception e) {
//            // 审计日志记录失败不应影响主业务
//            log.warn("记录支付凭证生成审计日志失败: paymentId={}", paymentOrder.getPaymentId(), e);
//        }
    }
    @AuditLog(
            targetTypes = {AuditTargetType.PAYMENT_ORDER},
            paymentId = "paymentId",
            desc = "支付成功回调"
    )
    @Transactional
    public void updatePaymentStatusToSuccessFromStatusOnTransactional(String paymentId,String paymentStatus,String transactionId) {
        if(paymentId==null||paymentStatus==null||transactionId==null){
            throw new BusinessException(ResultCode.PARAM_INVALID);

        }
        int updated = paymentOrderMapper.update(new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentStatus.SUCCESS.getCode())
                .set(PaymentOrder::getThirdPartyTradeNo,transactionId)
                .set(PaymentOrder::getCallbackTime,LocalDateTime.now())
                .eq(PaymentOrder::getPaymentId, paymentId)
                .eq(PaymentOrder::getStatus, paymentStatus));
        if(updated!=1){
            log.error("支付单状态更新失败，可能已被其他线程修改: paymentId={}, expectedStatus={}",
                    paymentId, paymentStatus);
            throw new BusinessException(ResultCode.PAYMENT_STATUS_INVALID,
                    "支付单状态已变更，请刷新重试");
        }

        orderPaymentOrchestrationService.updateOrderToPaidFromStatus(Long.valueOf(paymentId),OrderStatus.PENDING);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        triggerPaymentSuccess(paymentId);
                    }
                }
        );
    }
    /**
     * 触发支付成功后续流程（与回调逻辑一致）
     */
    private void triggerPaymentSuccess(String paymentId) {
        // 发送 MQ 消息（发货、积分、通知等）
        // 示例：rabbitTemplate.convertAndSend(...)
        log.info("支付成功后续流程触发: paymentId={}", paymentId);
    }
    @AuditLog(
            targetTypes = {AuditTargetType.PAYMENT_ORDER},
            paymentId = "payment.paymentId",        // ⚠️ 从对象中提取
            operation = AuditOperation.PAYMENT_FAILED,
            desc = "支付失败"
    )
    @Transactional
    public void updatePaymentStatusToFailedFromStatusOnTransactional(PaymentOrder payment,String status) {
        if(status==null||payment==null){
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }

        int updatedFailed = paymentOrderMapper.update(new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentStatus.FAILED.getCode())
                .eq(PaymentOrder::getId, payment.getId())
                .eq(PaymentOrder::getStatus, status));
        if(updatedFailed!=1){
            throw new BusinessException(ResultCode.DB_OPERATION_FAIL);
        }
        // 记录审计日志
//        auditService.builder()
//                    .paymentId(payment.getPaymentId())
//                    .orderId(payment.getOrderId())
//                    .userId(payment.getUserId())
//                    .operation(AuditOperation.PAYMENT_FAILED.getCode())
//                    .operationDesc("支付失败处理")
//                    .beforeStatus(status)
//                    .afterStatus(PaymentStatus.FAILED.getCode())
//                    .result(AuditResult.SUCCESS.getCode())
//                    .log();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        paymentOrchestrationService.closePaymentOrder(payment);

                    }
                }
        );
    }
    @AuditLog(
            targetTypes = {AuditTargetType.PAYMENT_ORDER},
            paymentId = "payment.paymentId",        // ⚠️ 从对象中提取
            operation = AuditOperation.CLOSE_PAYMENT,
            desc = "关闭支付单"
    )
    @Async("taskExecutor")
    public void closePaymentOrder(PaymentOrder payment) {
        if(payment==null||payment.getPaymentMethod()==null){
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }

        PayClient payClient = payClientFactory.getPayClient(payment.getPaymentMethod());
        if(payClient==null){
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }

        // ===== unifiedOrder 抛异常：二维码丢失，用户无法支付 =====
        String paymentId = payment.getPaymentId();
        log.error("预下单异常: paymentId={}, orderId={}", paymentId, payment.getOrderId());

        // 1. 尝试关单（不依赖结果）
        try {
            payClient.closeOrder(paymentId);
            log.info("关单成功: paymentId={}", paymentId);
        } catch (Exception closeEx) {
            // 关单失败：记录日志，发送告警（降级），但不影响主流程
            log.error("关单失败: paymentId={}", paymentId, closeEx);
            alertService.sendAlert("预下单异常且关单失败", "paymentId=" + paymentId);
        }
    }
    @Data
    @AllArgsConstructor
    public static class RefundContext {
        private PaymentOrder paymentOrder;
        private PaymentRefundRecord refundRecord;
    }
    @Transactional(rollbackFor = Exception.class)
    public RefundContext prepareRefundData(PayClient.RefundRequest request) {
        // 1. 查询支付单
        PaymentOrder paymentOrder = paymentOrderMapper.selectByPaymentIdForUpdate(request.getOutTradeNo());
        if (paymentOrder == null) {
            log.error("支付单不存在: outTradeNo={}, tradeNo={}",
                    request.getOutTradeNo(), request.getTradeNo());
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 2. 校验支付单状态（SUCCESS 或 PARTIAL_REFUNDED 可退款）
        String status = paymentOrder.getStatus();
        if (!PaymentStatus.SUCCESS.getCode().equals(status)
                && !PaymentStatus.PARTIAL_REFUNDED.getCode().equals(status)) {
            log.error("支付单状态不允许退款: paymentId={}, status={}",
                    paymentOrder.getPaymentId(), status);
            throw new BusinessException(ResultCode.PAYMENT_STATUS_INVALID,
                    "支付单当前状态为 " + status + "，不允许退款");
        }

        // 3. ?? 检查是否存在进行中的退款记录（FOR UPDATE）
        List<PaymentRefundRecord> processingRecords = paymentRefundRecordMapper.selectList(new LambdaQueryWrapper<PaymentRefundRecord>()
                .eq(PaymentRefundRecord::getPaymentId,paymentOrder.getPaymentId())
                .eq(PaymentRefundRecord::getStatus,RefundStatus.PROCESSING.getCode()));
        if (!processingRecords.isEmpty()) {
            log.warn("支付单已有进行中的退款: paymentId={}, recordId={}",
                    paymentOrder.getPaymentId(), processingRecords.get(0).getId());
            throw new BusinessException(ResultCode.PAYMENT_REFUND_PROCESSING,
                    "退款正在处理中，请勿重复提交");
        }
        // 3. 校验退款金额
        BigDecimal refundAmount = new BigDecimal(request.getRefundAmount());
        BigDecimal orderAmount = paymentOrder.getAmount();
        if (refundAmount.compareTo(orderAmount) > 0) {
            log.error("退款金额超过订单金额: refundAmount={}, orderAmount={}",
                    refundAmount, orderAmount);
            throw new BusinessException(ResultCode.PAYMENT_AMOUNT_MISMATCH,
                    "退款金额不能超过订单金额");
        }
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "退款金额必须大于0");
        }

        // 4. 校验累计退款金额
        BigDecimal totalRefunded = paymentRefundRecordService.getTotalRefundAmount(
                paymentOrder.getPaymentId()
        );
        if (totalRefunded == null) {
            totalRefunded = BigDecimal.ZERO;
        }
        if (totalRefunded.add(refundAmount).compareTo(orderAmount) > 0) {
            log.error("累计退款金额超限: 已退={}, 本次={}, 订单总金额={}",
                    totalRefunded, refundAmount, orderAmount);
            throw new BusinessException(ResultCode.PAYMENT_AMOUNT_MISMATCH,
                    "累计退款金额不能超过订单金额");
        }

        // 5. 生成退款请求号
        String outRequestNo = String.valueOf(snowflakeIdGenerator.nextId());
        request.setOutRequestNo(outRequestNo);

        // 6. 创建退款记录（状态 = PROCESSING）
        PaymentRefundRecord refundRecord = paymentRefundRecordService.createRefundRecord(
                paymentOrder.getPaymentId(),
                paymentOrder.getPaymentId(),
                paymentOrder.getThirdPartyTradeNo(),
                refundAmount,
                request.getRefundReason(),
                outRequestNo
        );
        if (refundRecord == null) {
            throw new BusinessException(ResultCode.DB_OPERATION_FAIL, "创建退款记录失败");
        }

        log.info("退款准备完成: recordId={}, paymentId={}, outRequestNo={}, amount={}",
                refundRecord.getId(), paymentOrder.getPaymentId(), outRequestNo, refundAmount);

        return new RefundContext(paymentOrder, refundRecord);
    }
    /**
     * 处理退款结果
     */
    @Transactional
    public void handleRefundResult(PaymentOrder paymentOrder,
                                    PayClient.RefundResponse response,
                                    PaymentRefundRecord refundRecord) {
        String paymentId = paymentOrder.getPaymentId();

        switch (response.getResult()) {
            case SUCCESS:
                // ===== 退款成功 =====
                log.info("退款成功: paymentId={}, refundAmount={}, recordId={}",
                        paymentId, response.getRefundAmount(), refundRecord.getId());

                // 1. 更新退款记录为 SUCCESS
                // ? 直接调用，失败时内部抛异常，事务自动回滚
                paymentRefundRecordService.markSuccess(
                        refundRecord.getId(),
                        response.getTradeNo()
                );

                BigDecimal refundAmount = new BigDecimal(response.getRefundAmount());
                // 2. 更新支付单（累计退款金额 + 状态）,只有一处不同表的状态交叉
                updateRefundSuccess(paymentOrder, refundAmount);
                break;

            case PROCESSING:
                // ===== 退款不确定，触发补偿查询 =====
                log.warn("退款状态不确定，触发补偿查询: paymentId={}, recordId={}",
                        paymentId, refundRecord.getId());

                if (refundRecord.getId() == null) {
                    log.error("退款记录ID为空，无法触发补偿查询: paymentId={}, outRequestNo={}",
                            paymentOrder.getPaymentId(), refundRecord.getOutRequestNo());
                    throw new BusinessException(ResultCode.DB_OPERATION_FAIL, "退款记录ID为空，无法触发补偿查询");
                }

                // 触发补偿查询（重试次数判断由补偿任务统一处理）
                scheduleRefundQuery(refundRecord.getId(), refundRecord.getPaymentId());
                break;

            case FAILED:
                // ===== 退款明确失败 =====
                log.error("退款失败: paymentId={}, recordId={}, failReason={}",
                        paymentId, refundRecord.getId(), response.getFailReason());

                // ? 直接调用，失败时内部抛异常，事务自动回滚
                paymentRefundRecordService.markFailed(
                        refundRecord.getId(),
                        response.getFailReason()
                );
                // 2. 抛出业务异常，事务回滚（支付单状态不变）
                throw new BusinessException(ResultCode.PAYMENT_REFUND_FAIL,
                        response.getFailReason());
        }
    }

    /**
     * 更新退款成功
     */
    @AuditLog(
            targetTypes = {AuditTargetType.PAYMENT_ORDER},
            paymentId = "paymentOrder.paymentId",   // ?? 从对象中提取
            operation = AuditOperation.REFUND_SUCCESS,
            desc = "退款成功更新支付单"
    )
    @Transactional
    public void updateRefundSuccess(PaymentOrder paymentOrder, BigDecimal refundAmount) {
        // 1. 记录更新前的状态（用于审计）
        String beforeStatus = paymentOrder.getStatus();
        // 1. 计算累计退款金额
        BigDecimal currentRefunded = paymentOrder.getRefundedAmount() == null
                ? BigDecimal.ZERO
                : paymentOrder.getRefundedAmount();
        BigDecimal newRefunded = currentRefunded.add(refundAmount);

        // 2. 判断是否全额退款
        String newStatus;
        if (newRefunded.compareTo(paymentOrder.getAmount()) >= 0) {
            newStatus = PaymentStatus.REFUND.getCode();
        } else {
            newStatus = PaymentStatus.PARTIAL_REFUNDED.getCode();
        }

        // 3. 条件更新（乐观锁 + 状态限制）
        LambdaUpdateWrapper<PaymentOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PaymentOrder::getId, paymentOrder.getId())
               .eq(PaymentOrder::getVersion, paymentOrder.getVersion())
               .in(PaymentOrder::getStatus,
                       PaymentStatus.SUCCESS.getCode(),
                       PaymentStatus.PARTIAL_REFUNDED.getCode())  // ✅ 只能从这两个状态转移
               .set(PaymentOrder::getStatus, newStatus)
               .set(PaymentOrder::getRefundedAmount, newRefunded)
               .set(PaymentOrder::getRefundTime, LocalDateTime.now())
               .set(PaymentOrder::getVersion, paymentOrder.getVersion() + 1);

        int updated = paymentOrderMapper.update(null, wrapper);
        if (updated != 1) {
            // 检查是否因为状态不匹配导致更新失败
            PaymentOrder latest = paymentOrderMapper.selectById(paymentOrder.getId());
            if (latest != null) {
                String currentStatus = latest.getStatus();
                if (!PaymentStatus.SUCCESS.getCode().equals(currentStatus)
                        && !PaymentStatus.PARTIAL_REFUNDED.getCode().equals(currentStatus)) {
                    log.error("退款失败：支付单状态不允许退款, paymentId={}, currentStatus={}",
                            paymentOrder.getPaymentId(), currentStatus);
                    throw new BusinessException(ResultCode.PAYMENT_STATUS_INVALID,
                            "支付单当前状态为 " + currentStatus + "，不允许退款");
                }
            }
            log.error("退款更新支付单失败: paymentId={}, version={}, 可能已被其他操作修改",
                    paymentOrder.getPaymentId(), paymentOrder.getVersion());
            throw new BusinessException(ResultCode.DB_OPERATION_FAIL,
                    "支付单状态已变更，请刷新后重试");
        }

        log.info("退款成功: paymentId={}, 本次退款={}, 累计退款={}, 状态={}",
                paymentOrder.getPaymentId(),
                refundAmount,
                newRefunded,
                newStatus);
        // ========== 5. 记录审计日志 ==========
//        try {
//            auditService.builder()
//                        .paymentId(paymentOrder.getPaymentId())
//                        .orderId(paymentOrder.getOrderId())
//                        .userId(paymentOrder.getUserId())
//                        .operation(AuditOperation.REFUND_SUCCESS.getCode())
//                        .operationDesc(String.format("退款成功，本次退款: %s，累计退款: %s",
//                                refundAmount.toPlainString(), newRefunded.toPlainString()))
//                        .beforeStatus(beforeStatus)
//                        .afterStatus(newStatus)
//                        .result(AuditResult.SUCCESS.getCode())
//                        .log();
//        } catch (Exception e) {
//            log.warn("记录退款成功审计日志失败: paymentId={}", paymentOrder.getPaymentId(), e);
//        }

    }

    /**
     * 触发退款查询补偿（延迟查询）
     */
    public void scheduleRefundQuery(Long recordId,String paymentId) {

        // 实现方式：
        // 1. 发送延迟消息到 MQ（如 RabbitMQ 延迟队列）
        // 2. 或使用 @Async + Thread.sleep
        // 3. 或使用 @Scheduled 定时任务扫描
        log.info("触发退款查询补偿: recordId={}", recordId);
        // 延迟 5 分钟后执行
        taskScheduler.schedule(
                () -> doRefundQuery(recordId,paymentId),
                Instant.now().plusSeconds(payProperties.getRefund().getQueryIntervalSeconds())
        );
    }
    @Async("taskExecutor")  // 异步执行，避免占用 TaskScheduler 线程
    public void doRefundQuery(Long recordId, String paymentId) {
        if (paymentId == null || recordId == null) {
            log.error("退款查询参数不完整: paymentId={}, recordId={}", paymentId, recordId);
            throw new BusinessException(ResultCode.PARAM_MISSING, "退款查询参数不完整");
        }
        String lockKey = RedisKeys.PAYMENT_QUERY_LOCK + paymentId;
        RLock lock = redissonClient.getLock(lockKey);

        // ===== 阶段一：第一次抢锁（数据库准备） =====
        PaymentRefundRecord record = null;
        int oldRetryCount = 0;
        boolean needQuery = false;
        RefundQueryClient refundQueryClient=null;

        try {
            // 第一次获取锁：等待3秒，持有5秒（短锁，只做DB操作）
            boolean locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("退款补偿查询获取锁失败，跳过本次: recordId={}, paymentId={}",
                        recordId, paymentId);
                return;
            }

            // 1. 查询退款记录
            record = paymentRefundRecordService.getById(recordId);
            if (record == null) {
                log.error("退款记录不存在: recordId={}", recordId);
                return;
            }

            // 2. 检查状态是否为 PROCESSING
            if (!RefundStatus.PROCESSING.getCode().equals(record.getStatus())) {
                log.info("退款记录已终态，跳过查询: recordId={}, status={}",
                        recordId, record.getStatus());
                return;
            }

            // 3. 检查重试次数
            oldRetryCount = record.getRetryCount() == null ? 0 : record.getRetryCount();
            int maxRetry = payProperties.getRefund().getMaxQueryRetry();
            if (oldRetryCount >= maxRetry) {
                log.warn("退款查询重试次数已达上限，转失败: recordId={}, retryCount={}",
                        recordId, oldRetryCount);
                paymentRefundRecordService.markFailed(recordId, "重试次数上限，需人工核实");
                alertService.sendCriticalAlert("退款查询重试次数耗尽",
                        String.format("recordId=%s, paymentId=%s", recordId, paymentId),
                        null);
                return;
            }
            PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getPaymentId, paymentId));
            if (paymentOrder == null || paymentOrder.getPaymentMethod() == null) {
                log.error("退款参数不完整: paymentOrder={}, refundRecord={}, paymentMethod={}",
                        paymentOrder != null ? paymentOrder.getPaymentId() : "null",
                        paymentOrder != null ? paymentOrder.getPaymentMethod() : "null");
                throw new BusinessException(ResultCode.PARAM_MISSING, "退款参数不完整，请检查支付单和退款记录");
            }
            // 1. 检查是否支持退款查询
            if (!payClientFactory.supportsRefundQuery(paymentOrder.getPaymentMethod())) {
                log.warn("支付方式不支持退款查询: paymentMethod={}, recordId={}",
                        paymentOrder.getPaymentMethod(), recordId);
                // 标记退款记录为失败（或保持 PROCESSING？）
                paymentRefundRecordService.markFailed(recordId, "该支付方式不支持退款查询，请联系客服");
                alertService.sendCriticalAlert("退款查询不支持",
                        String.format("recordId=%s, paymentMethod=%s", recordId, paymentOrder.getPaymentMethod()),
                        null);
                return;
            }

            // 2. 获取退款查询客户端
            refundQueryClient = payClientFactory.getRefundQueryClient(paymentOrder.getPaymentMethod());
            // 准备查询，标记需要执行第三方查询
            needQuery = true;
            log.info("执行退款补偿查询: recordId={}, currentRetry={}", recordId, oldRetryCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("退款补偿查询第一次锁被中断: recordId={}", recordId);
            return;
        } finally {
            // 🔓 第一次释放锁（关键：在调用第三方接口前释放）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("第一次锁已释放，准备调用第三方接口: recordId={}", recordId);
            }
        }

        // ===== 阶段二：锁外调用第三方接口（无锁，网络IO） =====
        RefundQueryResponse queryResp = null;
        boolean querySuccess = false;
        try {
            // 如果在上面的阶段已经判定不需要查询，直接返回
            if (!needQuery || record == null) {
                return;
            }

            // 调用第三方退款查询接口（无锁，≤8秒）
            // ? 使用 RefundQueryRequest 对象传参
            RefundQueryRequest queryRequest = RefundQueryRequest
                    .builder()
                    .outRequestNo(record.getOutRequestNo())
                    .outTradeNo(record.getOutTradeNo())
                    // .tradeNo(record.getTradeNo())  // 如果有 tradeNo 也可以传入
                    .build();

            queryResp = refundQueryClient.query(queryRequest);
            querySuccess = true;

        } catch (Exception e) {
            log.error("退款补偿查询第三方接口异常: recordId={}", recordId, e);
            // 第三方接口异常，需要兜底处理
            // 继续执行阶段三，但 queryResp 为 null，走异常分支
        }

        // ===== 阶段三：第二次抢锁（更新数据库结果） =====
        try {
            // 第二次获取锁：等待3秒，持有5秒
            boolean locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("退款补偿查询第二次获取锁失败，跳过更新: recordId={}, paymentId={}",
                        recordId, paymentId);
                // 锁获取失败，但第三方查询可能已成功，数据待后续补偿
                // 此时状态仍是 PROCESSING，下次任务会继续处理
                return;
            }

            // 1. 重新查询退款记录（双重检查，防止锁外期间被修改）
            PaymentRefundRecord latestRecord = paymentRefundRecordService.getById(recordId);
            if (latestRecord == null) {
                log.error("退款记录不存在: recordId={}", recordId);
                return;
            }

            // 2. 检查状态是否仍然是 PROCESSING
            if (!RefundStatus.PROCESSING.getCode().equals(latestRecord.getStatus())) {
                log.info("退款记录状态已变更，跳过更新: recordId={}, status={}",
                        recordId, latestRecord.getStatus());
                return;
            }

            // 3. 检查重试次数是否与第一次一致（防止锁外期间被其他任务修改）
            int currentRetry = latestRecord.getRetryCount() == null ? 0 : latestRecord.getRetryCount();
            if (currentRetry != oldRetryCount) {
                log.warn("重试次数已变更，跳过本次更新: recordId={}, expected={}, actual={}",
                        recordId, oldRetryCount, currentRetry);
                // 说明有其他任务已处理，直接返回
                return;
            }

            int maxRetry = payProperties.getRefund().getMaxQueryRetry();

            // 4. 根据查询结果更新
            if (!querySuccess || queryResp == null) {
                // 第三方查询异常，增加重试计数
                paymentOrchestrationService.handleQueryException(latestRecord, oldRetryCount, maxRetry, recordId, paymentId);
                return;
            }

            if (queryResp.isSuccess()) {
                // 退款成功
                paymentOrchestrationService.handleQuerySuccess(latestRecord, queryResp, recordId);
            } else if (queryResp.isProcessing()) {
                // 仍在处理中和handleQueryException处理逻辑一样
                handleQueryProcessing(latestRecord, oldRetryCount, maxRetry, recordId, paymentId);
            } else {
                // 查询失败或明确失败
                handleQueryFailed(latestRecord, queryResp, recordId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("退款补偿查询第二次锁被中断: recordId={}", recordId);
        } catch (Exception e) {
            log.error("退款补偿查询更新数据异常: recordId={}", recordId, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("第二次锁已释放: recordId={}", recordId);
            }
        }
    }
    /**
     * 处理查询异常
     */
    @Transactional
    public void handleQueryException(PaymentRefundRecord record, int oldRetryCount,
                                      int maxRetry, Long recordId, String paymentId) {
        int nextRetry = oldRetryCount + 1;
        if (nextRetry >= maxRetry) {
            // 重试次数耗尽
            paymentRefundRecordService.markFailed(recordId, "查询异常，重试次数耗尽");
            alertService.sendCriticalAlert("退款查询异常",
                    String.format("recordId=%s, paymentId=%s", recordId, paymentId),
                    null);
            log.warn("退款查询异常，重试次数耗尽: recordId={}", recordId);
        } else {
            // 更新下次查询时间，继续重试
            LocalDateTime nextTime = LocalDateTime.now().plusSeconds(payProperties.getRefund().getQueryIntervalSeconds());
            paymentRefundRecordService.updateNextQueryTime(recordId, oldRetryCount, nextTime);
        }
    }

    /**
     * 处理查询成功（退款成功）
     */
    @Transactional
    public void handleQuerySuccess(PaymentRefundRecord record, RefundQueryResponse queryResp, Long recordId) {
        log.info("补偿查询发现退款成功: recordId={}", recordId);

        // ✅ 直接调用，失败时内部抛异常
        paymentRefundRecordService.markSuccess(recordId, queryResp.getTradeNo());

        // 更新支付单
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getPaymentId, record.getPaymentId())
        );
        if (paymentOrder != null) {
            updateRefundSuccess(paymentOrder, new BigDecimal(queryResp.getRefundAmount()));
        }
    }

    /**
     * 处理查询仍在处理中
     */
    private void handleQueryProcessing(PaymentRefundRecord record, int oldRetryCount,
                                       int maxRetry, Long recordId, String paymentId) {
        int nextRetry = oldRetryCount + 1;
        if (nextRetry >= maxRetry) {
            // 重试次数耗尽
            paymentRefundRecordService.markFailed(recordId, "重试次数上限，需人工核实");
            alertService.sendCriticalAlert("退款查询重试次数耗尽",
                    String.format("recordId=%s, paymentId=%s", recordId, paymentId),
                    null);
            log.warn("退款仍处理中，但重试次数已达上限: recordId={}", recordId);
        } else {
            LocalDateTime nextTime = LocalDateTime.now().plusMinutes(5);
            paymentRefundRecordService.updateNextQueryTime(recordId, oldRetryCount, nextTime);
            scheduleRefundQuery(recordId, paymentId);
        }
    }

    /**
     * 处理查询失败
     */
    private void handleQueryFailed(PaymentRefundRecord record, RefundQueryResponse queryResp, Long recordId) {
        log.error("补偿查询失败: recordId={}, failReason={}", recordId, queryResp.getFailReason());
        paymentRefundRecordService.markFailed(recordId, queryResp.getFailReason());
    }
}