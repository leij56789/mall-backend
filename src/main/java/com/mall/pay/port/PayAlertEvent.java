package com.mall.pay.port;

import lombok.Getter;

/**
 * 支付模块自身的告警事件。
 * 业务系统通过 @EventListener 监听后转发到自己的告警通道。
 */
@Getter
public class PayAlertEvent {
    private final String paymentId;
    private final String orderId;
    private final String errorCode;
    private final String message;
    private final String alertType;

    public PayAlertEvent(String paymentId, String orderId,
                         String errorCode, String message, String alertType) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.errorCode = errorCode;
        this.message = message;
        this.alertType = alertType;
    }
}