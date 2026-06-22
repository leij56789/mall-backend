package com.mall.utils;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
/**
 * @author jiaolei
 * @date 2026/6/5 16:18
 * @description
 */
@Slf4j
@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置缓存（带过期时间，单位：秒）
     */
    public void set(String key, Object value, long seconds) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    /**
     * 设置缓存（永久）
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 获取缓存
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    /**
     * 删除单个缓存
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 批量删除缓存
     */
    public void delete(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
    }

    /**
     * 批量删除缓存（重载，支持 Collection）
     */
    public void delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
    }

    /**
     * 判断 key 是否存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long seconds) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, seconds, TimeUnit.SECONDS));
    }
    /**
     * 扫描匹配 pattern 的 keys（生产环境慎用）
     */
    public Set<String> scan(String pattern) {
        Set<String> keys = new HashSet<>();
        try {
            Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
                    connection -> connection.scan(
                            ScanOptions.scanOptions().match(pattern).count(100).build()
                    )
            );
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.error("Redis scan 异常: {}", e.getMessage());
        }
        return keys;
    }

    /**
     * 批量删除匹配 pattern 的缓存
     */
    public void deletePattern(String pattern) {
        Set<String> keys = scan(pattern);
        if (!keys.isEmpty()) {
            delete(keys);
            log.debug("批量删除缓存: pattern={}, 共{}条", pattern, keys.size());
        }
    }
}