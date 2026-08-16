package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "mall.message")
public class MessageProperties {
    
    // ========== 延迟时间 ==========
    //以后改成Integer类型 = 30 * 60 * 1000L
    private Long delayTime;  // 30分钟
    private Long seckillDelayTime;
    private Duration paymentOrderDelayTimeS=Duration.ofSeconds(1000);
    //系统时间误差，或LocalDateTime转换时间错精度误差
    private Duration timeToleranceMs=Duration.ofMinutes(2000);
    
    // ========== 重试配置 ==========
    private Integer maxRetry = 3;
    private Integer initialRetryDelaySeconds = 30;
    private Integer retryIntervalMinutes = 1;
    private Integer initialRetryCount=0;
    
    // ========== 补偿配置 ==========
    private Integer compensateBatchSize = 100;

    // ========== 计算属性 ==========
    public long getRetryIntervalMillis() {
        return retryIntervalMinutes == null ? 60000L : retryIntervalMinutes * 60 * 1000L;
    }
}