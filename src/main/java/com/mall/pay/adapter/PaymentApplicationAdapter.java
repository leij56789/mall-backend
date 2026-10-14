package com.mall.pay.adapter;

import com.mall.entity.PaymentOrder;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.pay.port.PaymentApplicationPort;
import com.mall.pay.port.model.PaymentOrderSnapshot;
import com.mall.service.PaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentApplicationAdapter implements PaymentApplicationPort {

    private final PaymentOrchestrationService orchestrationService;
    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    public void toWaiting(PaymentOrderSnapshot snapshot, String prepayId, Map<String, Object> extInfo) {
        PaymentOrder po = loadEntity(snapshot.getId());
        orchestrationService.updatePaymentStatusToWaitingFromStatusOnTransactional(
                po, po.getStatus(), prepayId, extInfo);
    }

    @Override
    public void toSuccess(String paymentId, String expectedStatus, String transactionId) {
        orchestrationService.updatePaymentStatusToSuccessFromStatusOnTransactional(
                paymentId, expectedStatus, transactionId);
    }

    @Override
    public void toFailed(PaymentOrderSnapshot snapshot, String expectedStatus) {
        PaymentOrder po = loadEntity(snapshot.getId());
        orchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(po, expectedStatus);
    }

    private PaymentOrder loadEntity(Long id) {
        PaymentOrder po = paymentOrderMapper.selectById(id);
        if (po == null) {
            throw new IllegalStateException("PaymentOrder not found: id=" + id);
        }
        return po;
    }
}