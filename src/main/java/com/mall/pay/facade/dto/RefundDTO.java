package com.mall.pay.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 发起退款入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundDTO {

    /** 原支付单号 */
    private String paymentId;

    /** 退款金额（元） */
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String refundReason;

    /**
     * 退款请求号（幂等键，业务侧必须保证唯一）。
     * <p>
     * 同一 outRequestNo 多次调用只处理一次。
     */
    private String outRequestNo;

    /** 操作人ID（可选） */
    private Long operatorId;

    /** 客户端IP（可选） */
    private String clientIp;
}