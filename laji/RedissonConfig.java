package com.mall.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379")
                .setDatabase(0)
                .setPassword(null)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(10)
                .setTimeout(5000);  // 5秒超时
        return Redisson.create(config);
    }
}