package com.mall.pay.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.mall.pay.client.PayClient;
import com.mall.pay.client.impl.AlipayClientAdapter;
import com.mall.pay.client.impl.AlipayF2FPayClientAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AlipayConfig {

//    private final AlipayProperties properties;
    private final PayProperties payProperties;

    /**
     * 创建支付宝客户端 Bean（带超时配置）
     */
    @Bean
    public AlipayClient alipayClient() {
        PayProperties.AlipayProperties properties = payProperties.getAlipay();
        String gateway = properties.getSandboxEnabled()
                ? properties.getGatewayUrl()
                : "https://openapi.alipay.com/gateway.do";

        return DefaultAlipayClient.builder(gateway, properties.getAppId(), properties.getPrivateKey())
                                  .format("json")
                                  .charset("UTF-8")
                                  .signType(properties.getSignType())
                                  .alipayPublicKey(properties.getAlipayPublicKey())
                                  .connectTimeout(payProperties.getConnectTimeout())  // 连接超时（毫秒）
                                  .readTimeout(payProperties.getReadTimeout())        // 读取超时（毫秒）
                                  .build();
    }

    /**
     * 封装为 PayClient 适配器
     */
    @Bean
    public PayClient alipayPayClient(AlipayClient alipayClient) {
        return new AlipayClientAdapter(alipayClient, payProperties);
    }
    @Bean
    public PayClient alipayF2FPayClient(AlipayClient alipayClient) {
        return new AlipayF2FPayClientAdapter(alipayClient, payProperties);
    }
}