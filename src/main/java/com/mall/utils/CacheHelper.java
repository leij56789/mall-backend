package com.mall.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Component
public class CacheHelper {

    private static RedisUtil redisUtilStatic;

    @Autowired
    public CacheHelper(RedisUtil redisUtil) {
        redisUtilStatic = redisUtil;
    }

    private static final long NULL_TTL = 300;
    private static final long NORMAL_TTL = 3600;
    private static final long LIST_TTL = 300;

    /**
     * 根据 cacheKey 自动生成 nullKey
     */
    private static String getNullKey(String cacheKey) {
        // user:john → user:null:john
        // article:how-to-train → article:null:how-to-train
        int colonIndex = cacheKey.indexOf(":");
        if (colonIndex == -1) {
            return "null:" + cacheKey;
        }
        String prefix = cacheKey.substring(0, colonIndex);
        String value = cacheKey.substring(colonIndex + 1);
        return prefix + ":null:" + value;
    }

    // ========== 查询 ==========

    public static <T> T getOrLoad(String cacheKey, Supplier<T> dbSupplier, Class<T> clazz) {
        String nullKey = getNullKey(cacheKey);
        try {
            if (redisUtilStatic.get(nullKey) != null) {
                return null;
            }

            Object cached = redisUtilStatic.get(cacheKey);
            if (cached != null) {
                return clazz.cast(cached);
            }

            T result = dbSupplier.get();
            if (result == null) {
                redisUtilStatic.set(nullKey, "NULL", NULL_TTL);
                return null;
            }

            redisUtilStatic.set(cacheKey, result, NORMAL_TTL);
            return result;

        } catch (Exception e) {
            log.warn("Redis 异常，降级到数据库: {}", e.getMessage());
            return dbSupplier.get();
        }
    }

    public static <T> List<T> getOrLoadList(String cacheKey, Supplier<List<T>> dbSupplier, Class<T> itemClazz) {
        String nullKey = getNullKey(cacheKey);
        try {
            if (redisUtilStatic.get(nullKey) != null) {
                return null;
            }

            Object cached = redisUtilStatic.get(cacheKey);
            if (cached != null) {
                // 这里可以安全转型为 List<T>
                return (List<T>) cached;
            }

            List<T> result = dbSupplier.get();
            if (result == null) {
                redisUtilStatic.set(nullKey, "NULL", NULL_TTL);
                return null;
            }

            redisUtilStatic.set(cacheKey, result, LIST_TTL);
            return result;

        } catch (Exception e) {
            log.warn("Redis 异常，降级到数据库: {}", e.getMessage());
            return dbSupplier.get();
        }
    }

    // ========== 更新 ==========

    public static void put(String cacheKey, Object value) {
        put(cacheKey, value, NORMAL_TTL);
    }

    public static void put(String cacheKey, Object value, long ttl) {
        String nullKey = getNullKey(cacheKey);
        if (value == null) {
            redisUtilStatic.set(nullKey, "NULL", NULL_TTL);
            log.debug("空值缓存写入: {}", nullKey);
        } else {
            redisUtilStatic.set(cacheKey, value, ttl);
            redisUtilStatic.delete(nullKey);
            log.debug("正常缓存写入: {}, 空值缓存已删除: {}", cacheKey, nullKey);
        }
    }

    // ========== 删除 ==========

    public static void evict(String cacheKey) {
        String nullKey = getNullKey(cacheKey);
        redisUtilStatic.delete(cacheKey);
        redisUtilStatic.delete(nullKey);
        log.debug("缓存清除: {}, {}", cacheKey, nullKey);
    }

    public static void delete(String key) {
        redisUtilStatic.delete(key);
    }

    public static boolean exists(String key) {
        return redisUtilStatic.get(key) != null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Class<T> clazz) {
        Object value = redisUtilStatic.get(key);
        if (value == null) {
            return null;
        }
        return clazz.cast(value);
    }
    /**
     * 批量删除匹配 pattern 的缓存
     */

    public static void deletePattern(String pattern) {
        redisUtilStatic.deletePattern(pattern);
    }
    public static String buildArticleListCacheKey(String tag, String author, String favorited, int limit, int offset) {
        String key = "articles:list:" + limit + ":" + offset;
        key += ":" + (tag != null ? tag : "null");
        key += ":" + (author != null ? author : "null");
        key += ":" + (favorited != null ? favorited : "null");
        return key;
    }
}
/*
* // 查询：只传 cacheKey
User user = CacheUtil.getOrLoad("user:" + username,
    () -> userService.getByUsername(username), User.class);

Article article = CacheUtil.getOrLoad("article:" + slug,
    () -> articleService.getBySlug(slug), Article.class);

Page<Article> page = CacheUtil.getOrLoadList("articles:list:" + page + ":" + size,
    () -> articleService.list(page, size), Page.class);

// 更新：只传 cacheKey 和 value
CacheUtil.put("user:" + username, user);
CacheUtil.put("article:" + slug, article);

// 删除：只传 cacheKey
CacheUtil.evict("user:" + username);
CacheUtil.evict("article:" + slug);
* */