package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 退款商品明细
 */
@Data
@Builder
public class RefundGoodsDetail {
    /**
     * 商家侧商品ID（可选）
     */
    private String outSkuId;

    /**
     * 商家侧商品ID（可选）
     */
    private String outItemId;

    /**
     * 支付宝商品ID（可选）
     */
    private String goodsId;

    /**
     * 退款金额（必填），单位：元
     */
    private String refundAmount;

    /**
     * 商品证书编号列表（可选）
     */
    private List<String> outCertificateNoList;
}