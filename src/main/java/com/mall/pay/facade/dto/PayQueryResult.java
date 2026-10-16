package com.mall.pay.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 查询支付单出参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayQueryResult {

    private String paymentId;
    private String bizType;
    private String bizId;
    private BigDecimal amount;
    private String paymentMethod;

    /** 状态：INIT / WAITING / SUCCESS / FAILED / PENDING_CONFIRM / CLOSED / REFUND / PARTIAL_REFUNDED */
    private String status;
    private String statusDesc;

    /** 第三方交易号 */
    private String thirdPartyTradeNo;

    /** 支付成功时间 */
    private LocalDateTime paidAt;

    /** 支付单过期时间 */
    private LocalDateTime expireAt;
}