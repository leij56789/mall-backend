package com.mall.pay.adapter;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.pay.entity.PaymentOrder;
import com.mall.pay.mapper.PaymentOrderMapper;
import com.mall.pay.port.PaymentOrderRepository;
import com.mall.pay.port.model.PaymentOrderSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 把业务系统的 PaymentOrderMapper 桥接到支付模块的 PaymentOrderRepository。
 */
@Component
@RequiredArgsConstructor
public class PaymentOrderRepositoryAdapter implements PaymentOrderRepository {

    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    public PaymentOrderSnapshot findByPaymentIdForUpdate(String paymentId) {
        return toSnapshot(paymentOrderMapper.selectByPaymentIdForUpdate(paymentId));
    }

    @Override
    public PaymentOrderSnapshot findByPaymentId(String paymentId) {
        // 目前用 FOR UPDATE 版本兜底；如 mapper 后续补无锁查询，可替换
        return toSnapshot(paymentOrderMapper.selectByPaymentIdForUpdate(paymentId));
    }

    private PaymentOrderSnapshot toSnapshot(PaymentOrder po) {
        if (po == null) return null;
        return PaymentOrderSnapshot.builder()
                                   .id(po.getId())
                                   .paymentId(po.getPaymentId())
                                   .orderId(po.getOrderId())
                                   .userId(po.getUserId())
                                   .amount(po.getAmount())
                                   .paymentMethod(po.getPaymentMethod())
                                   .status(po.getStatus())
                                   .thirdPartyTradeNo(po.getThirdPartyTradeNo())
                                   .callbackTime(po.getCallbackTime())
                                   .expiredAt(po.getExpiredAt())
                                   .retryCount(po.getRetryCount())
                                   .prepayId(po.getPrepayId())
                                   .extInfo(po.getExtInfo())
                                   .bizType(po.getBizType())
                                   .bizId(po.getBizId())
                                   .subject(po.getSubject())
                                   .description(po.getDescription())
                                   .notifyStatus(po.getNotifyStatus())
                                   .notifyRetryCount(po.getNotifyRetryCount())
                                   .notifyNextTime(po.getNotifyNextTime())
                                   .build();
    }

    // PaymentOrderRepositoryAdapter
    @Override
    public int updateRetryCount(String paymentId, int expectedRetry, int nextRetry) {
        return paymentOrderMapper.update(
                new LambdaUpdateWrapper<PaymentOrder>()
                        .set(PaymentOrder::getRetryCount, nextRetry)
                        .eq(PaymentOrder::getRetryCount, expectedRetry)
                        .eq(PaymentOrder::getPaymentId, paymentId)
        );
    }
}