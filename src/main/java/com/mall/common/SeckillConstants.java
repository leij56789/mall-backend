package com.mall.common;

/**
 * 秒杀常量
 */
public class SeckillConstants {

    /**
     * 每个用户限购数量
     */
    public static final int DEFAULT_USER_LIMIT = 1;

    /**
     * Redis 秒杀库存 key 前缀
     */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /**
     * Redis 用户秒杀记录 key 前缀
     */
    public static final String SECKILL_USER_KEY = "seckill:user:";

    public static final String SECKILL_SECKILLBOOK_KEY="seckill:seckillBook:";


    /**
     * 秒杀订单支付超时时间（分钟）
     */
    public static final int PAY_TIMEOUT_MINUTES = 15;

    public static final Long ESTIMATEDMS=200L;
}