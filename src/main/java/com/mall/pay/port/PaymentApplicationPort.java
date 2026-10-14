package com.mall.pay.port;

import com.mall.pay.port.model.PaymentOrderSnapshot;

import java.util.Map;

public interface PaymentApplicationPort {
    /** PENDING_CONFIRM → WAITING */
    void toWaiting(PaymentOrderSnapshot snapshot, String prepayId, Map<String,Object> extInfo);

    /** PENDING_CONFIRM → SUCCESS */
    void toSuccess(String paymentId, String expectedStatus, String transactionId);

    /** PENDING_CONFIRM → FAILED */
    void toFailed(PaymentOrderSnapshot snapshot, String expectedStatus);
}