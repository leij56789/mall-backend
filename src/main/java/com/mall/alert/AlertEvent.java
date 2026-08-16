package com.mall.alert;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AlertEvent extends ApplicationEvent {
    private final String paymentId;
    private final String orderId;
    private final String errorCode;      // 或 ResultCode
    private final String message;        // 详细描述
    private final String alertType;      // 如 "PAYMENT_MANUAL_INTERVENTION"

    public AlertEvent(Object source, String paymentId, String orderId, 
                      String errorCode, String message, String alertType) {
        super(source);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.errorCode = errorCode;
        this.message = message;
        this.alertType = alertType;
    }
}