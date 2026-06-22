package com.mall.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器
 * 64 bit = 1bit(符号位) + 41bit(时间戳) + 10bit(机器ID) + 12bit(序列号)
 */
/**
 * @author jiaolei
 * @date 2026/6/7 13:36
 * @description 部分 位数 说明
 * 符号位 1 bit 始终为 0（正数）
 * 时间戳 41 bit 可支持 69 年
 * 数据中心ID 5 bit 支持 32 个数据中心
 * 机器ID 5 bit 每个中心支持 32 个机器
 * 序列号 12 bit 每毫秒支持 4096 个 ID
 */
@Component
public class SnowflakeIdWorker {

    // ========== 参数配置 ==========
    /** 起始时间戳 (2020-01-01) */
    private final long twepoch = 1577808000000L;

    /** 机器ID所占位数 */
    private final long workerIdBits = 5L;
    /** 数据中心ID所占位数 */
    private final long datacenterIdBits = 5L;
    /** 序列号所占位数 */
    private final long sequenceBits = 12L;

    /** 最大机器ID (31) */
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    /** 最大数据中心ID (31) */
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);

    /** 机器ID左移位数 (12) */
    private final long workerIdShift = sequenceBits;
    /** 数据中心ID左移位数 (12+5=17) */
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    /** 时间戳左移位数 (12+5+5=22) */
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;

    /** 序列号掩码 (4095) */
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    // ========== 实例变量 ==========
    @Value("${snowflake.worker-id:1}")
    private long workerId;

    @Value("${snowflake.datacenter-id:1}")
    private long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * 构造函数
     * @param workerId 机器ID (0~31)
     * @param datacenterId 数据中心ID (0~31)
     */
    public SnowflakeIdWorker(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("workerId 不能大于 %d 或小于 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenterId 不能大于 %d 或小于 0", maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 默认构造函数 (workerId=1, datacenterId=1)
     */
    public SnowflakeIdWorker() {
        this(1L, 1L);
    }

    /**
     * 获取下一个 ID
     */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 时钟回拨处理
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                // 小幅度回拨，等待
                try {
                    wait(offset << 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException(String.format("时钟回拨，拒绝生成ID: %d", lastTimestamp - timestamp));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(String.format("时钟回拨，拒绝生成ID: %d", lastTimestamp - timestamp));
            }
        }

        // 同一毫秒内，序列号递增
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                // 当前毫秒序列号用完，等待下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 生成 ID
        return ((timestamp - twepoch) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    /**
     * 等待下一毫秒
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前毫秒时间戳
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * 从 ID 中解析出时间戳
     */
    public long getTimestamp(long id) {
        return (id >> timestampLeftShift) + twepoch;
    }

    /**
     * 从 ID 中解析出机器ID
     */
    public long getWorkerId(long id) {
        return (id >> workerIdShift) & maxWorkerId;
    }

    /**
     * 从 ID 中解析出数据中心ID
     */
    public long getDatacenterId(long id) {
        return (id >> datacenterIdShift) & maxDatacenterId;
    }
}