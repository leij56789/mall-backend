package com.mall.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alipay.pay")
public class AlipayProperties {
    private boolean sandboxEnabled;
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String signType = "RSA2";
    private String notifyUrl;
    private String gatewayUrl;
    private String sellerId;
    private int connectTimeout = 3000;
    private int readTimeout = 5000;
}