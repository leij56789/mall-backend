package com.mall.pay.facade.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.annotation.AuditLog;
import com.mall.common.BusinessException;
import com.mall.config.SnowflakeIdGenerator;
import com.mall.enums.ResultCode;
import com.mall.pay.client.PayClient;
import com.mall.pay.config.PayClientFactory;
import com.mall.pay.dto.ThirdPartyPayResponse;
import com.mall.pay.entity.PaymentOrder;
import com.mall.pay.enums.AuditOperation;
import com.mall.pay.enums.AuditTargetType;
import com.mall.pay.enums.PaymentStatus;
import com.mall.pay.event.PaymentStatusChangedEvent;
import com.mall.pay.facade.dto.PayOrderDTO;
import com.mall.pay.job.NotifyRetryScheduler;
import com.mall.pay.mapper.PaymentOrderMapper;
import com.mall.pay.port.AlertPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderLifecycle {

    private static final int DEFAULT_EXPIRE_MINUTES = 15;

    private final PaymentOrderMapper paymentOrderMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotifyRetryScheduler notifyScheduler;
    private final PayClientFactory payClientFactory;
    private final AlertPort alertPort;

    @AuditLog(
        targetTypes = {AuditTargetType.PAYMENT_ORDER},
        paymentIdFromResult = "paymentId",
        operation = AuditOperation.CREATE_PAYMENT,
        desc = "创建支付单（初始化 INIT）"
    )
    public PaymentOrder createNew(PayOrderDTO dto) {
        String paymentId = String.valueOf(snowflakeIdGenerator.nextId());
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(DEFAULT_EXPIRE_MINUTES);

        PaymentOrder po = PaymentOrder.builder()
                                      .paymentId(paymentId)
                                      .bizType(dto.getBizType())
                                      .bizId(dto.getBizId())
                                      .userId(dto.getUserId())
                                      .amount(dto.getAmount())
                                      .paymentMethod(dto.getPaymentMethod())
                                      .subject(dto.getSubject())
                                      .description(dto.getDescription())
                                      .status("INIT")
                                      .expiredAt(expireAt)
                                      .retryCount(0)
                                      .build();

        paymentOrderMapper.insert(po);
        return po;
    }

    @AuditLog(
        targetTypes = {AuditTargetType.PAYMENT_ORDER},
        paymentId = "po.paymentId",
        operation = AuditOperation.PAYMENT_WAITING,
        desc = "渠道下单成功（INIT→WAITING）"
    )
    public void markWaiting(PaymentOrder po, ThirdPartyPayResponse thirdResp) {
        paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .set(PaymentOrder::getStatus, "WAITING")
                        .set(PaymentOrder::getPrepayId, thirdResp.getPrepayId())
                        .set(PaymentOrder::getExtInfo, thirdResp.getExtInfo())
                        .eq(PaymentOrder::getId, po.getId())
                        .eq(PaymentOrder::getStatus, "INIT"));
    }

    /**
     * 复用已有支付单时，刷新 prepayId / extInfo（无状态变更，不写审计）
     */
    public void refreshPayInfo(PaymentOrder po, ThirdPartyPayResponse thirdResp) {
        paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .set(PaymentOrder::getPrepayId, thirdResp.getPrepayId())
                        .set(PaymentOrder::getExtInfo, thirdResp.getExtInfo())
                        .eq(PaymentOrder::getId, po.getId())
                        .eq(PaymentOrder::getStatus, "WAITING"));
    }
    @AuditLog(
            targetTypes = {AuditTargetType.PAYMENT_ORDER},
            paymentId = "paymentId",
            operation = AuditOperation.PAYMENT_CALLBACK,
            desc = "支付成功回调"
    )
    @Transactional(rollbackFor = Exception.class)
    public void markSuccessOnTransactional(String paymentId, String expectedStatus, String transactionId) {
        if (paymentId == null || expectedStatus == null || transactionId == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }

        int updated = paymentOrderMapper.update(new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentStatus.SUCCESS.getCode())
                .set(PaymentOrder::getThirdPartyTradeNo, transactionId)
                .set(PaymentOrder::getCallbackTime, LocalDateTime.now())
                .set(PaymentOrder::getNotifyStatus, "PENDING")
                .set(PaymentOrder::getNotifyRetryCount, 0)
                .set(PaymentOrder::getNotifyNextTime, LocalDateTime.now())
                .eq(PaymentOrder::getPaymentId, paymentId)
                .eq(PaymentOrder::getStatus, expectedStatus));

        if (updated != 1) {
            throw new BusinessException(ResultCode.PAYMENT_STATUS_INVALID, "支付单状态已变更");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                applicationEventPublisher.publishEvent(new PaymentStatusChangedEvent(
                        paymentId, expectedStatus, PaymentStatus.SUCCESS.getCode()));
                try {
                    notifyScheduler.notifyImmediately(paymentId);
                } catch (Exception e) {
                    log.error("立即通知业务侧失败: paymentId={}", paymentId, e);
                }
            }
        });
    }

    @AuditLog(
            targetTypes = {AuditTargetType.PAYMENT_ORDER},
            paymentId = "payment.paymentId",
            operation = AuditOperation.PAYMENT_FAILED,
            desc = "支付失败"
    )
    @Transactional(rollbackFor = Exception.class)
    public void markFailedOnTransactional(PaymentOrder payment, String expectedStatus) {
        if (payment == null || expectedStatus == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }

        int updated = paymentOrderMapper.update(new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentStatus.FAILED.getCode())
                .set(PaymentOrder::getNotifyStatus, "PENDING")
                .set(PaymentOrder::getNotifyRetryCount, 0)
                .set(PaymentOrder::getNotifyNextTime, LocalDateTime.now())
                .eq(PaymentOrder::getId, payment.getId())
                .eq(PaymentOrder::getStatus, expectedStatus));

        if (updated != 1) {
            throw new BusinessException(ResultCode.DB_OPERATION_FAIL);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                closePaymentOrder(payment);
                applicationEventPublisher.publishEvent(new PaymentStatusChangedEvent(
                        payment.getPaymentId(), expectedStatus, PaymentStatus.FAILED.getCode()));
            }
        });
    }

    @AuditLog(
            targetTypes = {AuditTargetType.PAYMENT_ORDER},
            paymentId = "payment.paymentId",
            operation = AuditOperation.CLOSE_PAYMENT,
            desc = "关闭支付单"
    )
    @Async("taskExecutor")
    public void closePaymentOrder(PaymentOrder payment) {
        if (payment == null || payment.getPaymentMethod() == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }

        PayClient payClient = payClientFactory.getPayClient(payment.getPaymentMethod());
        if (payClient == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID);
        }

        String paymentId = payment.getPaymentId();
        log.error("预下单异常: paymentId={}, orderId={}", paymentId, payment.getOrderId());

        try {
            payClient.closeOrder(paymentId);
            log.info("关单成功: paymentId={}", paymentId);
        } catch (Exception closeEx) {
            log.error("关单失败: paymentId={}", paymentId, closeEx);
            alertPort.alert("预下单异常且关单失败", "paymentId=" + paymentId);
        }
    }
    @AuditLog(
            targetTypes = {AuditTargetType.PAYMENT_ORDER},
            paymentId = "paymentOrder.paymentId",
            operation = AuditOperation.REFUND_SUCCESS,
            desc = "支付单退款状态变更"
    )
    public void updateRefundSuccess(PaymentOrder paymentOrder, BigDecimal refundAmount) {
        BigDecimal currentRefunded = paymentOrder.getRefundedAmount() == null
                ? BigDecimal.ZERO
                : paymentOrder.getRefundedAmount();
        BigDecimal newRefunded = currentRefunded.add(refundAmount);

        String newStatus;
        if (newRefunded.compareTo(paymentOrder.getAmount()) >= 0) {
            newStatus = PaymentStatus.REFUND.getCode();
        } else {
            newStatus = PaymentStatus.PARTIAL_REFUNDED.getCode();
        }

        LambdaUpdateWrapper<PaymentOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PaymentOrder::getId, paymentOrder.getId())
               .eq(PaymentOrder::getVersion, paymentOrder.getVersion())
               .in(PaymentOrder::getStatus,
                       PaymentStatus.SUCCESS.getCode(),
                       PaymentStatus.PARTIAL_REFUNDED.getCode())
               .set(PaymentOrder::getStatus, newStatus)
               .set(PaymentOrder::getRefundedAmount, newRefunded)
               .set(PaymentOrder::getRefundTime, LocalDateTime.now())
               .set(PaymentOrder::getVersion, paymentOrder.getVersion() + 1);

        int updated = paymentOrderMapper.update(null, wrapper);
        if (updated != 1) {
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
            throw new BusinessException(ResultCode.DB_OPERATION_FAIL, "支付单状态已变更，请刷新后重试");
        }

        log.info("退款成功: paymentId={}, 本次退款={}, 累计退款={}, 状态={}",
                paymentOrder.getPaymentId(), refundAmount, newRefunded, newStatus);
    }
}