package com.mall.pay.facade.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.annotation.AuditLog;
import com.mall.config.SnowflakeIdGenerator;
import com.mall.pay.dto.ThirdPartyPayResponse;
import com.mall.pay.entity.PaymentOrder;
import com.mall.pay.enums.AuditOperation;
import com.mall.pay.enums.AuditTargetType;
import com.mall.pay.facade.dto.PayOrderDTO;
import com.mall.pay.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentOrderLifecycle {

    private static final int DEFAULT_EXPIRE_MINUTES = 15;

    private final PaymentOrderMapper paymentOrderMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

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
}