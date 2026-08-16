package com.mall.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DynamicSnowflakeIdGenerator {
    private final SnowflakeIdWorker idWorker;

    public DynamicSnowflakeIdGenerator(StringRedisTemplate redisTemplate) {
        // 从 Redis 中原子性获取一个唯一的 workerId
        Long workerId = redisTemplate.opsForValue().increment("snowflake:worker:id");
        // dataCenterId 同理
        this.idWorker = new SnowflakeIdWorker(workerId % 32, 0);
    }

    public long nextId() {
        return idWorker.nextId();
    }
}