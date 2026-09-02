package com.mall.pay.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentStatusChangedEvent {
    private String paymentId;
    private String oldStatus;
    private String newStatus;
    private Long timestamp;
    
    public PaymentStatusChangedEvent(String paymentId, String oldStatus, String newStatus) {
        this.paymentId = paymentId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.timestamp = System.currentTimeMillis();
    }
}