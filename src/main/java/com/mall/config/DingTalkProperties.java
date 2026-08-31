package com.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dingtalk")
public class DingTalkProperties {
    private boolean enabled = true;
    private String webhookUrl;
    private String secret;
}