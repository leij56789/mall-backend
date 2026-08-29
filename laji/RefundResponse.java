package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 退款响应参数
 */
@Data
@Builder
public class RefundResponse {
    private String tradeNo;
    private String outTradeNo;
    private String buyerLogonId;
    private String refundFee;
    private String fundChange;
    /**
     * ✅ 使用自定义 TradeFundBill，不依赖支付宝 SDK
     */
    private List<TradeFundBill> refundDetailItemList;
    private String buyerUserId;
    private String sendBackFee;

    public boolean isRefundSuccess() {
        return "Y".equals(fundChange);
    }
}