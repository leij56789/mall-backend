package com.mall.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.config.MessageProperties;
import com.mall.config.SnowflakeIdGenerator;
import com.mall.entity.Orders;
import com.mall.entity.PaymentOrder;
import com.mall.enums.OrderStatus;
import com.mall.enums.PaymentStatus;
import com.mall.enums.ResultCode;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.pay.client.PayClient;
import com.mall.pay.config.AlipayProperties;
import com.mall.pay.config.PayClientFactory;
import com.mall.pay.dto.PaymentResponse;
import com.mall.pay.dto.ThirdPartyPayRequest;
import com.mall.pay.dto.ThirdPartyPayResponse;
import com.mall.pay.service.AsyncQueryService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final AlipayProperties alipayProperties;


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
        if (existing != null) {
            paymentOrderMapper.deleteById(existing.getId());
        }
        // 4. 新建 INIT 状态支付单
        LocalDateTime expireTime = LocalDateTime.now().plus(messageProperties.getPaymentOrderDelayTimeS());
        log.info("测试bug：1：expireTime={},messageProperties.getPaymentOrderDelayTimeS()={}",expireTime,messageProperties.getPaymentOrderDelayTimeS());

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
        paymentOrderService.updatePaymentStatusToWaitingFromStatus(
                paymentOrder,
                paymentStatus,
                prepayId,
                order,
                extInfo
        );
    }
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
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        paymentOrchestrationService.closePaymentOrder(payment);

                    }
                }
        );
    }
    @Async
    public void closePaymentOrder(PaymentOrder payment) {
        if(payment==null||payment.getPaymentMethod()==null){
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }

        PayClient payClient = payClientFactory.getClient(payment.getPaymentMethod());
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
}