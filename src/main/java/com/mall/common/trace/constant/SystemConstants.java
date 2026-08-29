package com.mall.common.trace.constant;

/**
 * 系统常量
 */
public final class SystemConstants {

    private SystemConstants() {}

    /**
     * 系统操作者 ID（0 表示系统/定时任务/补偿任务）
     */
    public static final Long SYSTEM_USER_ID = 0L;

    /**
     * 系统操作者名称
     */
    public static final String SYSTEM_USER_NAME = "SYSTEM";

    /**
     * 操作者类型：用户
     */
    public static final String OPERATOR_TYPE_USER = "USER";

    /**
     * 操作者类型：系统（定时任务、补偿任务）
     */
    public static final String OPERATOR_TYPE_SYSTEM = "SYSTEM";

    /**
     * 操作者类型：批量任务
     */
    public static final String OPERATOR_TYPE_BATCH = "BATCH";

    /**
     * 操作者类型：消息队列消费者
     */
    public static final String OPERATOR_TYPE_MQ = "MQ";
}