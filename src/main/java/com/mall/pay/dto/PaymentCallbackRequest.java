package com.mall.pay.dto;

import com.mall.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付回调统一请求对象
 * 支付宝和微信的回调数据统一映射为此对象
 */
@Data
@Builder
public class PaymentCallbackRequest {

    /**
     * 商户订单号（即 paymentId）
     * 微信：out_trade_no
     * 支付宝：out_trade_no
     */
    private String paymentId;

    /**
     * 第三方交易号
     * 微信：transaction_id
     * 支付宝：trade_no
     */
    private String thirdPartyTradeNo;

    /**
     * 支付金额
     * 微信：total_fee（单位：分）
     * 支付宝：total_amount（单位：元）
     */
    private BigDecimal totalAmount;

    /**
     * 签名（保留原值）
     */
    private String sign;

    /**
     * 交易状态
     * 微信：result_code（SUCCESS/FAIL）
     * 支付宝：trade_status（TRADE_SUCCESS/TRADE_CLOSED/TRADE_FINISHED）
     */
    private PaymentStatus tradeStatus;

    /**
     * 通知ID（用于幂等去重）
     * 微信：无（用 transaction_id + out_trade_no 组合去重）
     * 支付宝：notify_id
     */
    private String notifyId;

    /**
     * 支付渠道
     * WECHAT / ALIPAY
     */
    private String channel;

    /**
     * 原始数据（用于扩展/调试）
     */
    private Map<String, String> rawData;
}