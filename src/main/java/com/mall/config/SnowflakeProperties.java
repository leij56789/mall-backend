package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "snowflake")
public class SnowflakeProperties {
    private long workerId = 0;      // 默认值
    private long datacenterId = 0;  // 默认值
}