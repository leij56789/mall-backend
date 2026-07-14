package com.mall.common;

import java.time.Duration;

/**
 * Redis Key 统一管理
 * 格式：[业务前缀]:[模块]:[标识]
 * 示例：seckill:stock:1
 */
public final class RedisKeys {

    // ========== 环境前缀（本地/测试/生产自动切换） ==========
    private static final String ENV = "seckill";  // 可改成 "dev:seckill" / "prod:seckill"

    // ========== 秒杀相关 ==========
    /** 秒杀库存 key: seckill:stock:{bookId} */
    public static final String SECKILL_STOCK = ENV + ":stock:";
    /** 秒杀用户标记 key: seckill:user:{bookId}:{userId} */
    public static final String SECKILL_USER = ENV + ":user:";
    public static final String SECKILL_USERS = ENV + ":users:";
    /** 秒杀商品信息 key: seckill:seckillBook:{bookId} */
    public static final String SECKILL_BOOK = ENV + ":seckillBook:";
    /** 秒杀排队队列 key: seckill:queue:{bookId} */
    public static final String SECKILL_QUEUE = ENV + ":queue:";
    /** 秒杀分布式锁 key: seckill:lock:{bookId} */
    public static final String SECKILL_LOCK = ENV + ":lock:";

    // ========== 消息重试相关 ==========
    /** 消息重试计数 key: message:retry:{messageId} */
    public static final String MESSAGE_RETRY = "message:retry:";

    // ========== 补偿相关 ==========
    /** 补偿任务锁 key: compensate:lock */
    public static final String COMPENSATE_LOCK = "compensate:lock";

    // ========== TTL 配置（统一管理） ==========
    public static final Duration TTL_SECKILL_BOOK = Duration.ofHours(1);
    public static final Duration TTL_SECKILL_STOCK = Duration.ofHours(1);
    public static final Duration TTL_SECKILL_USER = Duration.ofHours(1);
    public static final Duration TTL_SECKILL_USERS = Duration.ofHours(1);
    public static final Duration TTL_SECKILL_QUEUE = Duration.ofHours(2);
    public static final Duration TTL_MESSAGE_RETRY = Duration.ofMinutes(5);
    public static final Duration TTL_COMPENSATE_LOCK = Duration.ofMinutes(1);

    private RedisKeys() {
        // 工具类不允许实例化
    }
}