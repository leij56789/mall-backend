package com.mall.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pay")
public class PayProperties {

    // ===== 通用配置 =====
    private Integer connectTimeout = 3000;
    private Integer readTimeout = 5000;

    // ===== 退款补偿配置 =====
    private RefundProperties refund;

    // ===== 支付宝 =====
    private AlipayProperties alipay;

    // ===== 微信 =====
    private WechatProperties wechat;

    @Data
    public static class RefundProperties {
        private int maxQueryRetry = 5;
        private int queryIntervalSeconds = 60;
        private int compensateBatchSize = 100;
    }

    @Data
    public static class AlipayProperties {
        private Boolean sandboxEnabled = false;
        private String appId;
        private String privateKey;
        private String alipayPublicKey;
        private String signType = "RSA2";
        private String gatewayUrl;
        private String sellerId;
        private String notifyUrl;
    }

    @Data
    public static class WechatProperties {
        private Boolean sandboxEnabled = false;
        private String apiBaseUrl;
        private String mchid;
        private String appid;
        private String apiV3Key;
        private String serialNo;
        private String sandboxSignKey;
        private String notifyUrl;
    }
}