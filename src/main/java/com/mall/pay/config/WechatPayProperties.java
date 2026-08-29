package com.mall.pay.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pay.wechat")
public class WechatPayProperties {
    private boolean sandboxEnabled;
    private String apiBaseUrl;
    private String mchid;
    private String appid;
    private String apiV3Key;
    private String serialNo;
    private String notifyUrl;
    private int connectTimeout = 3000;
    private int readTimeout = 5000;
    private String sandboxSignKey;
    // --- 初始化后的最终值（由 @PostConstruct 计算）---
    private String finalApiBaseUrl;
    private String finalSignKey;

    @PostConstruct
    public void init() {
        // 1. 计算最终的 BaseUrl
        this.finalApiBaseUrl = sandboxEnabled
                ? apiBaseUrl + "/sandboxnew"
                : apiBaseUrl;

        // 2. 计算最终的签名密钥
        this.finalSignKey = sandboxEnabled
                ? sandboxSignKey
                : apiV3Key;

        // 3. 简单校验（快速失败）
        if (finalSignKey == null || finalSignKey.isEmpty()) {
            throw new IllegalStateException(
                    "微信支付签名密钥不能为空，请检查配置：沙箱模式=" + sandboxEnabled
            );
        }
    }

}