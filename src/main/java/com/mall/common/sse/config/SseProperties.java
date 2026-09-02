// com.mall.common.sse.config.SseProperties.java（新建独立类）
package com.mall.common.sse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sse")
public class SseProperties {
    /**
     * 会话超时时间（毫秒），默认 5 分钟
     */
    private long sessionTimeout = 300_000L;
    
    /**
     * 心跳间隔（秒），默认 30 秒
     */
    private long heartbeatInterval = 30L;
}