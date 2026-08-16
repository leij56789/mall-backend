package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentStatusResponse {
    private String paymentId;
    private Long orderId;
    private String status;              // WAITING / SUCCESS / FAILED / CLOSED / REFUND
    private String statusDesc;          // 状态描述
    private BigDecimal amount;
    private String thirdPartyTradeNo;   // 第三方交易号
    private LocalDateTime paidAt;       // 支付成功时间
    private LocalDateTime expireAt;     // 超时时间
}