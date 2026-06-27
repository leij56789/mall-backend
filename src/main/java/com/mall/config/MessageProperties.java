package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mall.message")
public class MessageProperties {
    
    // ========== 延迟时间 ==========
    //以后改成Integer类型
    private Long delayTime = 30 * 60 * 1000L;  // 30分钟
    
    // ========== 重试配置 ==========
    private Integer maxRetry = 3;
    private Integer initialRetryDelaySeconds = 30;
    private Integer retryIntervalMinutes = 5;
    
    // ========== 补偿配置 ==========
    private Integer compensateBatchSize = 100;

    // ========== 计算属性 ==========
    public long getRetryIntervalMillis() {
        return retryIntervalMinutes * 60 * 1000L;
    }
}