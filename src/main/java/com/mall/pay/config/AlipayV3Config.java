package com.mall.pay.config;

import com.alipay.v3.ApiClient;
import com.alipay.v3.ApiException;
import com.alipay.v3.api.AlipayDataDataserviceBillDownloadurlApi;
import com.alipay.v3.util.model.AlipayConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class AlipayV3Config {

    private final PayProperties payProperties;

    public AlipayV3Config(PayProperties payProperties) {
        this.payProperties = payProperties;
    }

    @Bean
    public ApiClient alipayV3ApiClient() {
        PayProperties.AlipayProperties alipayProps = payProperties.getAlipay();
        if (alipayProps == null) {
            throw new IllegalStateException("支付宝配置未找到");
        }

        // 1. 构建 AlipayConfig
        AlipayConfig alipayConfig = new AlipayConfig();
        // ✅ 处理 gatewayUrl，去除 /gateway.do
        String serverUrl = alipayProps.getGatewayUrl();
        if (serverUrl.contains("/gateway.do")) {
            serverUrl = serverUrl.substring(0, serverUrl.indexOf("/gateway.do"));
        }
        // 如果还有 /v2、/v3 等路径，也一并去除
        serverUrl = serverUrl.replaceAll("/v\\\\d+$", "");
        alipayConfig.setServerUrl(serverUrl);
        alipayConfig.setAppId(alipayProps.getAppId());
        alipayConfig.setPrivateKey(alipayProps.getPrivateKey());
        alipayConfig.setAlipayPublicKey(alipayProps.getAlipayPublicKey());

        // 2. 创建 ApiClient 并应用配置
        ApiClient apiClient = new ApiClient();
        try {
            apiClient.setAlipayConfig(alipayConfig);
        } catch (ApiException e) {
            log.error("支付宝 V3 ApiClient 配置失败", e);
            throw new RuntimeException("支付宝 V3 ApiClient 配置失败", e);
        }

        // 3. 设置超时时间
        OkHttpClient httpClient = apiClient.getHttpClient().newBuilder()
                .connectTimeout(payProperties.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(payProperties.getReadTimeout(), TimeUnit.MILLISECONDS)
                .build();
        apiClient.setHttpClient(httpClient);

        log.info("支付宝 V3 ApiClient 初始化完成，网关地址: {}, connectTimeout={}ms, readTimeout={}ms",
                alipayConfig.getServerUrl(),
                payProperties.getConnectTimeout(),
                payProperties.getReadTimeout());
        return apiClient;
    }

    @Bean
    public AlipayDataDataserviceBillDownloadurlApi billDownloadApi(ApiClient apiClient) {
        return new AlipayDataDataserviceBillDownloadurlApi(apiClient);
    }
}