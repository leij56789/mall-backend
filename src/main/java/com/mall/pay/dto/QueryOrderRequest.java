package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryOrderRequest {
    private String paymentId;      // 商户订单号（即 paymentId）
    private String thirdPartyTradeNo; // 可选，第三方交易号
}