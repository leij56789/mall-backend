package com.mall.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.mall.config.SnowflakeProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class SnowflakeIdGenerator {
    
    private final SnowflakeProperties properties;
    private Snowflake snowflake;
    
    @PostConstruct
    public void init() {
        this.snowflake = IdUtil.createSnowflake(
            properties.getWorkerId(), 
            properties.getDatacenterId()
        );
    }
    
    public long nextId() {
        return snowflake.nextId();
    }
    
    public String nextIdStr() {
        return String.valueOf(nextId());
    }
}