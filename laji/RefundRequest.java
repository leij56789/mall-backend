package com.mall.pay.dto;

import com.alipay.api.domain.OpenApiRoyaltyDetailInfoPojo;
import com.alipay.api.domain.RefundGoodsDetail;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 退款请求参数
 */
@Data
@Builder
public class RefundRequest {
    /**
     * 商户订单号（与 tradeNo 二选一，至少传一个）
     */
    private String outTradeNo;

    /**
     * 支付宝交易号（与 outTradeNo 二选一，同时存在时优先使用 tradeNo）
     */
    private String tradeNo;

    /**
     * 退款金额（必填），单位：元，精确到小数点后两位
     */
    private String refundAmount;

    /**
     * 退款原因说明（可选），长度 256
     */
    private String refundReason;

    /**
     * 退款请求号（可选，部分退款时必传）
     */
    private String outRequestNo;

    /**
     * 退款包含的商品列表信息（可选）
     * 直接使用支付宝官方 SDK 的 RefundGoodsDetail
     */
    private List<RefundGoodsDetail> refundGoodsDetail;

    /**
     * 退分账明细信息（可选）
     * 直接使用支付宝官方 SDK 的 OpenApiRoyaltyDetailInfoPojo
     */
    private List<OpenApiRoyaltyDetailInfoPojo> refundRoyaltyParameters;

    /**
     * 查询选项（可选）
     */
    private List<String> queryOptions;

    /**
     * 账期交易确认结算单号（可选）
     */
    private String relatedSettleConfirmNo;
}