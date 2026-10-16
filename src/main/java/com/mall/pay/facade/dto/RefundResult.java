package com.mall.pay.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 退款结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResult {

    /** 退款记录ID */
    private Long refundId;

    /** 支付单号 */
    private String paymentId;

    /** 退款请求号 */
    private String outRequestNo;

    /** 本次退款金额 */
    private BigDecimal refundAmount;

    /** 状态：PROCESSING / SUCCESS / FAILED */
    private String status;

    /** 失败原因（status=FAILED 时） */
    private String failReason;

    /** 第三方退款交易号 */
    private String thirdPartyRefundNo;
}