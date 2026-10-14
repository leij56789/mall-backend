package com.mall.pay.config;

import com.mall.pay.channel.alipay.config.AlipayProperties;   // ★ 加
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pay")
public class PayProperties {

    private Integer connectTimeout = 3000;
    private Integer readTimeout = 5000;

    private RefundProperties refund;

    private AlipayProperties alipay;    // ★ 类型改成独立类

    @Data
    public static class RefundProperties {
        private int maxQueryRetry = 5;
        private int queryIntervalSeconds = 60;
        private int compensateBatchSize = 100;
    }
}