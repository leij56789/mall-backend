package com.mall.common;

import java.time.Duration;

/**
 * Redis 分布式锁统一配置
 * 所有锁的超时参数集中管理，便于调整和监控
 */
public final class RedisLockConfig {

    // ========== 锁默认超时配置 ==========

    /**
     * 获取锁的最大等待时间（默认 3 秒）
     * 超过此时间仍未获取到锁，视为获取失败
     */
    public static final Duration LOCK_WAIT_TIME = Duration.ofSeconds(3);

    /**
     * 锁的持有时间（默认 10 秒）
     * 超过此时间锁自动释放，防止死锁
     * 注意：此值应大于业务逻辑的最大执行时间
     */
    public static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(10);

    // ========== 各业务场景专用配置（可独立调优） ==========

    /**
     * 支付创建锁：等待 3 秒，持有 10 秒
     * 业务逻辑：查 DB + 调第三方预下单（readTimeout=5s）
     */
    public static final Duration PAYMENT_CREATE_WAIT = Duration.ofSeconds(3);
    public static final Duration PAYMENT_CREATE_LEASE = Duration.ofSeconds(10);

    /**
     * 支付查询补偿锁：等待 3 秒，持有 10 秒
     * 业务逻辑：查 DB + 调第三方查询（readTimeout=3s）
     */
    public static final Duration PAYMENT_QUERY_WAIT = Duration.ofSeconds(3);
    public static final Duration PAYMENT_QUERY_LEASE = Duration.ofSeconds(15);

    /**
     * 订单超时取消锁：等待 3 秒，持有 8 秒
     * 业务逻辑：查 DB + 更新状态（无第三方调用）
     */
    public static final Duration ORDER_TIMEOUT_WAIT = Duration.ofSeconds(3);
    public static final Duration ORDER_TIMEOUT_LEASE = Duration.ofSeconds(8);

    /**
     * 秒杀锁：等待 0 秒（快速失败），持有 5 秒
     * 高并发场景，抢不到直接返回，不排队
     */
    public static final Duration SECKILL_WAIT = Duration.ZERO;
    public static final Duration SECKILL_LEASE = Duration.ofSeconds(5);

    private RedisLockConfig() {}
}