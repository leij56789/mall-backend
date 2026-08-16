package com.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.annotation.Log;
import com.mall.common.BusinessException;
import com.mall.common.RedisKeys;
import com.mall.config.MessageProperties;
import com.mall.config.SnowflakeIdGenerator;
import com.mall.enums.AlipayExtKey;
import com.mall.mq.config.RabbitMQConfig;
import com.mall.pay.config.PayClientFactory;
import com.mall.pay.dto.*;
import com.mall.entity.Orders;
import com.mall.entity.PaymentOrder;
import com.mall.enums.OrderStatus;
import com.mall.enums.PaymentStatus;
import com.mall.enums.ResultCode;
import com.mall.interceptor.JwtInterceptor;
import com.mall.mapper.OrdersMapper;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.mapper.SeckillBookMapper;
import com.mall.mq.message.PaymentTimeoutMessage;
import com.mall.mq.producer.PaymentTimeoutProducer;
import com.mall.pay.client.PayClient;
import com.mall.pay.service.AsyncQueryService;
import com.mall.service.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author jiaolei
 * @description 针对表【payment_order】的数据库操作Service实现
 * @createDate 2026-07-18 14:37:12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderServiceImpl extends ServiceImpl<PaymentOrderMapper, PaymentOrder>
        implements PaymentOrderService {
    private final PaymentOrderMapper paymentOrderMapper;
    private final OrdersMapper ordersMapper;
    private final MessageProperties messageProperties;
    private final PaymentTimeoutProducer paymentTimeoutProducer;
    private final SeckillBookMapper seckillBookMapper;
    private final RedisRollbackService redisRollbackService;
    private final RedissonClient redissonClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final AlertService alertService;
    private final AsyncQueryService asyncQueryService;
    private final OrderPaymentOrchestrationService orderPaymentOrchestrationService;
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final PayClientFactory payClientFactory;
    private final ObjectMapper objectMapper;


    @Override
    public PaymentResponse createPayment(PaymentCreateRequest request) {
        // ========== 1. 参数校验（替你补全） ==========
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }
        Long userId = request.getUserId();
        Long orderId = request.getOrderId();
        String paymentMethod = request.getPaymentMethod();

        if (userId == null || orderId == null || paymentMethod == null) {
            log.warn("支付参数缺失: userId={}, orderId={}, paymentMethod={}", userId, orderId, paymentMethod);
            throw new BusinessException(ResultCode.PARAM_MISSING);
        }

        // 获取当前登录用户（利用你的拦截器）
        String currentUserIdStr = JwtInterceptor.getCurrentUserId();
        if (!StringUtils.hasText(currentUserIdStr)) {
            log.warn("未获取到当前登录用户信息");
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 防止横向越权（Long 比较用 equals，避免类型转换坑）
        if (!currentUserIdStr.equals(String.valueOf(userId))) {
            log.warn("用户越权操作: currentUser={}, requestUser={}", currentUserIdStr, userId);
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        
        PayClient payClient = payClientFactory.getClient(paymentMethod);
        
        String lockKey = RedisKeys.PAYMENT_CREATE_LOCK + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        PaymentOrchestrationService.InitResult initResult=null;
        try {
            // 1. 加锁（防重复点击）
            boolean locked = lock.tryLock(0, -1, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取锁失败，重复请求: orderId={}", orderId);
                throw new BusinessException(ResultCode.REPEAT_CLICK);
            }
            log.info("获取锁成功: orderId={}", orderId);
            
            if(payClient.canRecreatePaymentForm()){
                PaymentResponse paymentResponse = paymentOrchestrationService.doGetPaymentForm(orderId, userId, payClient, null, null);
                if(paymentResponse!=null){
                    return paymentResponse;
                }
            }
            initResult = paymentOrchestrationService.initPaymentWithLock(orderId, userId, paymentMethod);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        } finally {
            // 解锁（只在当前线程持有锁时释放）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("释放锁: orderId={}", orderId);
            }
        }
        // ========== 3. 校验初始化结果（替你补全） ==========
        if (initResult == null) {
            log.error("初始化支付单结果为空, orderId={}", orderId);
            throw new BusinessException(ResultCode.PAYMENT_CREATE_FAIL);
        }
        PaymentOrder paymentOrder = initResult.getPaymentOrder();
        Orders orders = initResult.getOrder();
        ThirdPartyPayResponse thirdResp = paymentOrchestrationService.getThirdRespFromThirdParty(paymentOrder, orders, orderId, userId, payClient);

        updatePaymentStatusToWaitingFromStatus(paymentOrder,paymentOrder.getStatus(), thirdResp.getPrepayId(), orders,thirdResp.getExtInfo());
        return paymentOrchestrationService.buildPaymentResponse(paymentOrder.getPaymentId(), thirdResp, paymentOrder.getExpiredAt());
    }




//    private void closePaymentOrder(Exception e, String paymentId, Long orderId, PayClient payClient, PaymentOrder paymentOrder) {
//        // ===== unifiedOrder 抛异常：二维码丢失，用户无法支付 =====
//        log.error("预下单异常: paymentId={}, orderId={}", paymentId, orderId, e);
//
//        // 1. 尝试关单（不依赖结果）
//        try {
//            payClient.closeOrder(paymentId);
//            log.info("关单成功: paymentId={}", paymentId);
//        } catch (Exception closeEx) {
//            // 关单失败：记录日志，发送告警（降级），但不影响主流程
//            log.error("关单失败: paymentId={}", paymentId, closeEx);
//            alertService.sendAlert("预下单异常且关单失败", "paymentId=" + paymentId);
//        }
//
//
//
//        // 3. 用户可重新支付（生成新 paymentId）
//    }
    @Log("更新状态为waiting")
    public void updatePaymentStatusToWaitingFromStatus(PaymentOrder paymentOrder, String paymentStatus, String prepayId, Orders orders, Map<String,Object> extInfo) {
        String extInfoJson=null;
        if(extInfo!=null){
            try {
                extInfoJson = objectMapper.writeValueAsString(extInfo);
            } catch (JsonProcessingException e) {
                throw new BusinessException(ResultCode.PAYMENT_SERIALIZE_FAIL);
            }
        }
        LambdaUpdateWrapper<PaymentOrder> wrapper = new LambdaUpdateWrapper<>();
        int updated = paymentOrderMapper.update(wrapper
                .eq(PaymentOrder::getStatus, paymentStatus)
                .eq(PaymentOrder::getId, paymentOrder.getId())
                .set(PaymentOrder::getStatus,PaymentStatus.WAITING.getCode())
                .set(prepayId!=null,PaymentOrder::getPrepayId,prepayId)
                .set(extInfo!=null,PaymentOrder::getExtInfo, extInfoJson));
        if(updated!=1){
            throw new BusinessException(ResultCode.DB_OPERATION_FAIL);
        }
        paymentTimeoutProducer.sendPaymentTimeoutMessage(paymentOrder, orders, RabbitMQConfig.PAYMENT_DELAY_EXCHANGE,RabbitMQConfig.PAYMENT_DELAY_ROUTING_KEY);
    }

    @Override
    public PaymentCallbackResponse handleCallback(PaymentCallbackRequest request) {
        String paymentId = request.getPaymentId();
        PaymentStatus tradeStatus = request.getTradeStatus();
        String transactionId = request.getThirdPartyTradeNo();

        // ===== 1. 参数校验 =====
        if (paymentId == null || tradeStatus == null) {
            log.error("回调参数缺失: paymentId={}, tradeStatus={}", paymentId, tradeStatus);
            return PaymentCallbackResponse.builder()
                                          .success(false)
                                          .message("参数缺失")
                                          .build();
        }

        // ===== 2. 分布式锁（与补偿任务共用锁键） =====
        String lockKey = RedisKeys.PAYMENT_QUERY_LOCK + paymentId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 等待3秒，持有10秒（与补偿任务一致）
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                log.warn("获取回调锁失败，可能正在被补偿任务处理: paymentId={}", paymentId);
                // 锁被占用，说明补偿任务正在处理，回调直接返回成功，避免冲突
                return PaymentCallbackResponse.builder()
                                              .success(true)
                                              .message("锁被占用，由补偿任务处理")
                                              .build();
            }

            // ===== 3. 查询支付单（双重检查） =====
            PaymentOrder payment = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .eq(PaymentOrder::getPaymentId, paymentId)
            );
            if (payment == null) {
                log.error("支付单不存在: paymentId={}", paymentId);
                return PaymentCallbackResponse.builder()
                                              .success(false)
                                              .message("支付单不存在")
                                              .build();
            }

            // ===== 4. 幂等检查 =====
            if (PaymentStatus.SUCCESS.getCode().equals(payment.getStatus())) {
                log.info("支付单已是 SUCCESS，幂等处理: paymentId={}", paymentId);
                return PaymentCallbackResponse.builder()
                                              .success(true)
                                              .message("幂等成功")
                                              .build();
            }

            // ===== 5. 状态映射 =====
//            PaymentStatus mappedStatus = mapTradeStatusToPaymentStatus(tradeStatus);

            // ===== 6. 根据映射状态处理 =====
            if (PaymentStatus.SUCCESS.equals(tradeStatus)) {
                // 支付成功（只处理从 WAITING 或 PENDING_CONFIRM 升级）
                try {
                    paymentOrchestrationService.updatePaymentStatusToSuccessFromStatusOnTransactional(
                            paymentId,
                            PaymentStatus.WAITING.getCode(),
                            transactionId
                    );
                    log.info("回调处理成功: paymentId={}, tradeNo={}", paymentId, transactionId);
                    return PaymentCallbackResponse.builder()
                                                  .success(true)
                                                  .message("OK")
                                                  .build();
                } catch (BusinessException e) {
                    // 乐观锁冲突或状态异常，检查是否已经是 SUCCESS（幂等）
                    PaymentOrder latest = paymentOrderMapper.selectOne(
                            new LambdaQueryWrapper<PaymentOrder>()
                                    .eq(PaymentOrder::getPaymentId, paymentId)
                    );
                    if (latest != null && PaymentStatus.SUCCESS.getCode().equals(latest.getStatus())) {
                        log.info("更新成功，幂等返回: paymentId={}", paymentId);
                        return PaymentCallbackResponse.builder()
                                                      .success(true)
                                                      .message("幂等成功")
                                                      .build();
                    }
                    log.error("更新 SUCCESS 失败: paymentId={}", paymentId, e);
                    // 返回失败，让支付宝重试（但重试可能也无法解决，需人工介入）
                    return PaymentCallbackResponse.builder()
                                                  .success(false)
                                                  .message("更新状态失败")
                                                  .build();
                }
            } else {
                // ===== 非 SUCCESS 状态（如 TRADE_CLOSED） =====
                // 根据官方文档，正常情况下回调只有 TRADE_SUCCESS，但为了防御性编程，处理其他状态
                log.warn("收到非成功回调: paymentId={}, tradeStatus={}", paymentId, tradeStatus);
                // 对于 TRADE_CLOSED 等，将状态置为 FAILED
                try {
                    paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(
                            payment,
                            PaymentStatus.WAITING.getCode() // 允许从 WAITING 转为 FAILED
                    );
                    return PaymentCallbackResponse.builder()
                                                  .success(true) // 告诉支付宝不要再重试
                                                  .message("已处理为失败")
                                                  .build();
                } catch (BusinessException e) {
                    // 更新失败，检查是否已经是终态
                    PaymentOrder latest = paymentOrderMapper.selectOne(
                            new LambdaQueryWrapper<PaymentOrder>()
                                    .eq(PaymentOrder::getPaymentId, paymentId)
                    );
                    if (latest != null &&
                            (PaymentStatus.SUCCESS.getCode().equals(latest.getStatus()) ||
                                    PaymentStatus.FAILED.getCode().equals(latest.getStatus()))) {
                        log.info("支付单已终态，幂等返回: paymentId={}, status={}", paymentId, latest.getStatus());
                        return PaymentCallbackResponse.builder()
                                                      .success(true)
                                                      .message("幂等成功")
                                                      .build();
                    }
                    log.error("更新 FAILED 失败: paymentId={}", paymentId, e);
                    return PaymentCallbackResponse.builder()
                                                  .success(false)
                                                  .message("更新状态失败")
                                                  .build();
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁被中断: paymentId={}", paymentId);
            return PaymentCallbackResponse.builder()
                                          .success(false)
                                          .message("锁获取中断")
                                          .build();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }



    @Transactional
    @Override
    public PaymentStatusResponse getPaymentStatus(String paymentId) {

        PaymentOrder payment = paymentOrderMapper.selectByPaymentIdForUpdate(paymentId);
        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        // 如果需要实时查询第三方（例如 PENDING_CONFIRM 状态），可以调用 payClient.queryOrder
        // 但为了减少第三方调用，通常只返回本地状态，由后台补偿任务异步查询
        // 若希望同步查询，可根据业务需要开启

        return buildStatusResponse(payment);
    }
    private PaymentStatusResponse buildStatusResponse(PaymentOrder payment) {
//        Map<String, Object> extInfo = payment.getExtInfo();
//        if(extInfo==null){
//
//        }
//        String qrCode = String.valueOf(extInfo.get(AlipayExtKey.QR_CODE.getKey()));
//        if(StrUtil.isBlank(qrCode)){
//
//        }

        return PaymentStatusResponse.builder()
                                    .paymentId(payment.getPaymentId())
                                    .orderId(payment.getOrderId())
                                    .status(payment.getStatus())
                                    .statusDesc(PaymentStatus.getDescByCode(payment.getStatus()))
                                    .amount(payment.getAmount())
                                    .thirdPartyTradeNo(payment.getThirdPartyTradeNo())
                                    .paidAt(payment.getCallbackTime())
                                    .expireAt(payment.getExpiredAt())
                                    .build();
    }

    @Transactional
    @Override
    public void cancelTimeoutPayment(PaymentTimeoutMessage message) {
        String currentUserId = JwtInterceptor.getCurrentUserId();
        if(currentUserId==null){

        }
        String paymentId = message.getPaymentId();
        if (StrUtil.isBlank(paymentId)) {

        }
        Long orderId = message.getOrderId();
        if(orderId==null){

        }
//        Long bookId = message.getBookId();
//        if(bookId==null){
//
//        }
//        Integer quantity = message.getQuantity();
//        if(quantity==null){

//        }
        int updated = paymentOrderMapper.update(new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentStatus.CLOSED.getCode())
                .eq(PaymentOrder::getStatus, PaymentStatus.WAITING.getCode())
                .eq(PaymentOrder::getPaymentId, paymentId));
        if (updated != 1) {

        }
        Orders orders = orderPaymentOrchestrationService.selectOrderStatus(orderId, OrderStatus.CANCELLED.getValue());
        orderPaymentOrchestrationService.cancelSeckillExpireOrderByOrder(orders);
    }

    @Transactional
    @Override
    public void cancelTimeoutPaymentByPaymentOrderFromPendingConfirm(PaymentOrder paymentOrder, PaymentStatus paymentStatus) {
        String currentUserId = JwtInterceptor.getCurrentUserId();
        if(currentUserId==null){

        }
        if(paymentOrder==null){

        }
        String paymentId = paymentOrder.getPaymentId();
        if (StrUtil.isBlank(paymentId)) {

        }
        Long orderId = paymentOrder.getOrderId();
        if(orderId==null){

        }
        if(paymentStatus==null||paymentStatus.getCode()==null){

        }
        int updated = paymentOrderMapper.update(new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentStatus.CLOSED.getCode())
                .eq(PaymentOrder::getStatus, paymentStatus.getCode())
                .eq(PaymentOrder::getPaymentId, paymentId));
        if (updated != 1) {

        }
        Orders orders = orderPaymentOrchestrationService.selectOrderStatus(orderId, OrderStatus.CANCELLED.getValue());
        orderPaymentOrchestrationService.cancelSeckillExpireOrderByOrder(orders);
    }
/*
* <!-- 直接使用 img 标签展示二维码 -->
<img src="/api/payment/qrcode/pay_20260803123456" alt="支付二维码" />

<!-- 或动态绑定 -->
<img :src="'/api/payment/qrcode/' + paymentId" />
* */
    @Override
    public void getQrCode(String paymentId, int width, int height, HttpServletResponse response) throws IOException {
        if(paymentId==null){

        }
        // 1. 查询支付单
        PaymentOrder payment = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentId,paymentId));
        if (payment == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Payment order not found");
            return;
        }

        // 2. 从 extInfo 中提取 qr_code
        Map<String, Object> extInfo = payment.getExtInfo();
        if (extInfo == null || !extInfo.containsKey(AlipayExtKey.QR_CODE.getKey())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("QR code not found");
            return;
        }

        String qrCode = (String) extInfo.get(AlipayExtKey.QR_CODE.getKey());
        if (qrCode == null || qrCode.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("QR code is empty");
            return;
        }

        // 3. 生成二维码图片并输出
        try {
            // 设置响应头，告诉浏览器返回的是图片
            response.setContentType(MediaType.IMAGE_PNG_VALUE);
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            // 生成 300x300 像素的二维码，直接写入输出流
            QrCodeUtil.generate(qrCode, width, height, "png", response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("生成二维码失败，paymentId={}, qrCode={}", paymentId, qrCode, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Generate QR code failed");
        }
    }
}




