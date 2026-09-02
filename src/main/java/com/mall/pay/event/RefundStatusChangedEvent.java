package com.mall.pay.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RefundStatusChangedEvent {
    private Long refundId;
    private Long paymentId;
    private String oldStatus;
    private String newStatus;
    private Long timestamp;
}