package com.mall.pay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class WechatPayConfig {

    private final WechatPayProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 创建 RestClient Bean（用于微信支付 HTTP 调用）
     */
    @Bean
    public RestClient wechatRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * 创建微信支付客户端 Bean
     * 当 sandbox-enabled 为 true 时，使用沙箱配置
     */
//    @Bean
//    public PayClient wechatPayClient(RestClient wechatRestClient) {
//        // 根据沙箱标志调整基础 URL 和密钥
//        String baseUrl = properties.getApiBaseUrl();
//        if (properties.isSandboxEnabled()) {
//            baseUrl = baseUrl + "/sandboxnew";
//        }
//        String signKey = properties.isSandboxEnabled()
//                ? properties.getSandboxSignKey()
//                : properties.getApiV3Key();
//
//        return new WechatPayClient(
//                wechatRestClient,
//                properties.getAppid(),
//                properties.getMchid(),
//                signKey,
//                baseUrl,
//                properties.getSerialNo(),
//                properties.getNotifyUrl(),
//                objectMapper
//        );
//    }
}