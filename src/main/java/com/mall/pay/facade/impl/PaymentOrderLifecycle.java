package com.mall.pay.facade.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.annotation.AuditLog;
import com.mall.common.BusinessException;
import com.mall.config.SnowflakeIdGenerator;
import com.mall.enums.ResultCode;
import com.mall.pay.dto.ThirdPartyPayResponse;
import com.mall.pay.entity.PaymentOrder;
import com.mall.pay.enums.AuditOperation;
import com.mall.pay.enums.AuditTargetType;
import com.mall.pay.enums.PaymentStatus;
import com.mall.pay.event.PaymentStatusChangedEvent;
import com.mall.pay.facade.dto.PayOrderDTO;
import com.mall.pay.job.NotifyRetryScheduler;
import com.mall.pay.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
}