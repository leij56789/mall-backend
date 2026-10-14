package com.mall.pay.channel.alipay.config;

import lombok.Data;

@Data
public class AlipayProperties {
    private Boolean sandboxEnabled = false;
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String signType = "RSA2";
    private String gatewayUrl;
    private String sellerId;
    private String notifyUrl;
}