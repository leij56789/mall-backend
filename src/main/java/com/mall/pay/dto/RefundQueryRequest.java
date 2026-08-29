package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 退款查询请求（渠道无关）
 */
@Data
@Builder
public class RefundQueryRequest {

    /**
     * 退款请求号（必填）
     * <p>
     * 发起退款时传入的 outRequestNo，用于关联退款请求。
     * 如果在退款时未传入，则值为商户订单号。
     */
    private String outRequestNo;

    /**
     * 商户订单号（与 tradeNo 二选一，至少传一个）
     * <p>
     * 订单支付时传入的商户订单号。
     */
    private String outTradeNo;

    /**
     * 支付宝交易号（与 outTradeNo 二选一，至少传一个）
     * <p>
     * 支付宝系统生成的交易流水号，优先级高于 outTradeNo。
     */
    private String tradeNo;
}