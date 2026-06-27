package com.mall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync     // ✅ 开启异步支持（让 @Async 生效）
@EnableScheduling // ✅ 开启定时任务支持
public class AppConfig {
    
    /**
     * ✅ 注入 ObjectMapper（单例，全局复用）
     * 支持 Java 8 时间类型（LocalDateTime 等）
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 Java 8 时间模块，支持 LocalDateTime 序列化
        mapper.registerModule(new JavaTimeModule());
        // 禁用时间戳格式，使用 ISO-8601 格式
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}