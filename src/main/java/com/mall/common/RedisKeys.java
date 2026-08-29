package com.mall.common;

import java.time.Duration;

/**
 * Redis Key 统一管理
 * <p>
 * 命名规范：{业务前缀}:{模块}:{标识}
 * <br>示例：payment:order:881553621924319232
 * <p>
 * 环境隔离建议：在配置文件中通过 spring.redis.key-prefix 统一添加环境前缀
 */
public final class RedisKeys {

    // ========== 业务前缀（环境隔离） ==========
    private static final String SECKILL_PREFIX = "seckill";
    private static final String PAYMENT_PREFIX = "payment";
    private static final String MESSAGE_PREFIX = "message";
    private static final String COMPENSATE_PREFIX = "compensate";
    private static final String AUDIT_PREFIX = "audit";

    // ============================================================
    //  1. 秒杀相关 (seckill:*)
    // ============================================================
    /** seckill:stock:{bookId} - 秒杀库存 */
    public static final String SECKILL_STOCK = SECKILL_PREFIX + ":stock:";
    /** seckill:user:{bookId}:{userId} - 秒杀用户标记 */
    public static final String SECKILL_USER = SECKILL_PREFIX + ":user:";
    /** seckill:users:{bookId} - 秒杀用户集合 */
    public static final String SECKILL_USERS = SECKILL_PREFIX + ":users:";
    /** seckill:seckillBook:{bookId} - 秒杀商品信息 */
    public static final String SECKILL_BOOK = SECKILL_PREFIX + ":seckillBook:";
    /** seckill:queue:{bookId} - 秒杀排队队列 */
    public static final String SECKILL_QUEUE = SECKILL_PREFIX + ":queue:";
    /** seckill:lock:{bookId} - 秒杀分布式锁 */
    public static final String SECKILL_LOCK = SECKILL_PREFIX + ":lock:";

    // ============================================================
    //  2. 支付相关 (payment:*)
    // ============================================================
    /** payment:createPayment:lock:{orderId} - 创建支付单锁 */
    public static final String PAYMENT_CREATE_LOCK = PAYMENT_PREFIX + ":createPayment:lock:";
    /** payment:query:lock:{paymentId} - 支付查询锁（PENDING_CONFIRM 补偿查询） */
    public static final String PAYMENT_QUERY_LOCK = PAYMENT_PREFIX + ":query:lock:";

    // ============================================================
    //  3. 消息重试相关 (message:*)
    // ============================================================
    /** message:retry:{messageId} - 消息重试计数 */
    public static final String MESSAGE_RETRY = MESSAGE_PREFIX + ":retry:";

    // ============================================================
    //  4. 补偿相关 (compensate:*)
    // ============================================================
    /** compensate:lock - 补偿任务全局锁 */
    public static final String COMPENSATE_LOCK = COMPENSATE_PREFIX + ":lock";

    // ============================================================
    //  5. 审计相关 (audit:*)  ✅ 新增
    // ============================================================
    /** audit:lock:{paymentId} - 审计日志写入锁（防止哈希链并发分叉） */
    public static final String AUDIT_LOCK = AUDIT_PREFIX + ":lock:";

    // ============================================================
    //  TTL 配置（统一管理）
    // ============================================================
    public static final Duration TTL_SECKILL_BOOK = Duration.ofHours(1);
    public static final Duration TTL_SECKILL_STOCK = Duration.ofHours(1);
    public static final Duration TTL_SECKILL_USER = Duration.ofHours(1);
    public static final Duration TTL_SECKILL_USERS = Duration.ofHours(1);
    public static final Duration TTL_SECKILL_QUEUE = Duration.ofHours(2);
    public static final Duration TTL_MESSAGE_RETRY = Duration.ofMinutes(5);
    public static final Duration TTL_COMPENSATE_LOCK = Duration.ofMinutes(1);
    /** 审计锁 TTL：30 秒（足够完成一次审计写入） */
    public static final Duration TTL_AUDIT_LOCK = Duration.ofSeconds(30);

    // ============================================================
    //  工具方法
    // ============================================================

    /**
     * 生成秒杀库存 key
     */
    public static String seckillStock(Long bookId) {
        return SECKILL_STOCK + bookId;
    }

    /**
     * 生成秒杀用户标记 key
     */
    public static String seckillUser(Long bookId, Long userId) {
        return SECKILL_USER + bookId + ":" + userId;
    }

    /**
     * 生成支付创建锁 key
     */
    public static String paymentCreateLock(Long orderId) {
        return PAYMENT_CREATE_LOCK + orderId;
    }

    /**
     * 生成支付查询锁 key
     */
    public static String paymentQueryLock(String paymentId) {
        return PAYMENT_QUERY_LOCK + paymentId;
    }

    /**
     * 生成审计锁 key
     */
    public static String auditLock(String paymentId) {
        return AUDIT_LOCK + paymentId;
    }

    private RedisKeys() {
        // 工具类不允许实例化
    }
}