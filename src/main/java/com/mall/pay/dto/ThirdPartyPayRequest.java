package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ThirdPartyPayRequest {
    private String outTradeNo;      // 商户订单号（即 paymentId）
    private Long orderId;
    private Long userId;
    private String totalAmount;     // 单位：元，字符串类型，如 "0.01"
    private String subject;         // 商品描述
    private String body;            // 商品详情（可选）
    private String notifyUrl;       // 回调地址
    private String returnUrl;       // 同步跳转地址（可选）
    private LocalDateTime timeExpire; // 订单超时时间
    private String quitUrl;         // 用户中途退出返回地址（WAP支付）
}