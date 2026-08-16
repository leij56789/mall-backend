package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    // ========== 通用模块 (1000-1099) ==========
    SUCCESS(1000, "操作成功"),
    FAIL(1001, "操作失败"),
    PARAM_ERROR(1002, "参数错误"),
    PARAM_MISSING(1003, "缺少必要参数"),
    PARAM_INVALID(1004, "参数格式错误"),
    REPEAT_CLICK(1005, "操作过于频繁，请稍后再试"),
    TRANSACTION_ROLLBACK_FAIL(1006, "事务回滚回调执行失败"), // 原来1005→改为1006
    REDIS_ROLLBACK_FAIL(1007, "Redis事务回滚失败"),         // 原来1005→改为1007
    REDIS_OPERATION_FAIL(1008, "Redis操作失败"),            // 原来1006→改为1008
    DB_OPERATION_FAIL(1009, "数据库操作失败"),              // 原来1007→改为1009

    // ========== 认证模块 (2000-2099) ==========
    UNAUTHORIZED(2001, "未登录或Token已过期"),
    FORBIDDEN(2002, "无权限访问"),
    TOKEN_EXPIRED(2003, "Token已过期"),
    TOKEN_INVALID(2004, "Token无效"),
    LOGIN_FAIL(2005, "用户名或密码错误"),
    USER_NOT_EXIST(2006, "用户不存在"),
    USER_ALREADY_EXIST(2007, "用户已存在"),

    // ========== 订单模块 (3000-3099) ==========
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_INVALID(3002, "订单状态异常"),
    ORDER_CANCEL_FAIL(3003, "订单取消失败"),
    ORDER_NOT_EXPIRE(3004, "订单尚未超时"),
    ORDER_EXPIRED(3005, "订单已超时"),
    ORDER_ALREADY_PAID(3006, "订单已支付"),
    ORDER_ALREADY_CANCELLED(3007, "订单已取消"),
    ORDER_CREATE_FAIL(3008, "订单创建失败"),
    ORDER_UPDATE_FAIL(3009, "订单更新失败"),          // 保留3009
    ORDER_SELECT_FAIL(3010, "订单查询失败"),          // 原3009→3010
    ORDER_TYPE_ERROR(3011, "订单类型错误"),            // 原3009→3011
    ORDER_HAS_ACTIVE_PAYMENT(3012, "订单存在进行中的支付，无法取消"), // 原3010→3012
    ORDER_AMOUNT_ERROR(3013, "订单金额为空"),          // 原3011→3013

    // ========== 库存模块 (4000-4099) ==========
    STOCK_NOT_ENOUGH(4001, "库存不足"),
    STOCK_RECOVER_FAIL(4002, "库存恢复失败"),
    BOOK_NOT_FOUND(4003, "书籍不存在"),
    BOOK_ALREADY_EXIST(4004, "书籍已存在"),
    STOCK_DEDUCT_FAIL(4005, "库存扣减失败"),

    // ========== 系统 & MQ 模块 (5000-5099) 注意合并去重 ==========
    SYSTEM_ERROR(5000, "系统错误"),
    DB_ERROR(5001, "数据库异常"),                    // 保留5001
    MQ_ERROR(5002, "消息队列异常"),                  // 保留5002
    REDIS_ERROR(5003, "缓存异常"),                   // 保留5003
    FILE_UPLOAD_ERROR(5004, "文件上传失败"),         // 保留5004
    SYSTEM_BUSY(5005, "系统繁忙"),                   // 保留5005
    
    // MQ 专属 (5010-5019)
    MESSAGE_SEND_FAIL(5010, "消息发送失败"),          // 原5001→5010
    MESSAGE_SERIALIZE_FAIL(5011, "消息序列化失败"),    // 原5002→5011
    MESSAGE_DESERIALIZE_FAIL(5012, "消息反序列化失败"),// 原5003→5012
    MESSAGE_CONSUME_FAIL(5013, "消息消费失败"),        // 原5004→5013
    MESSAGE_CONSUME_SUCCESS(5014, "消息消费成功"),     // 原5007→5014
    MESSAGE_RETRY_EXHAUSTED(5015, "消息重试次数耗尽"), // 原5005→5015
    MESSAGE_INSERT_FAIL(5016, "消息存入数据库失败"),   // 原5006→5016
    BROKER_MESSAGE_LOG_UPDATE_FAIL(5017, "订单超时消息数据库更新失败"), // 原5008→5017
    OPTIMISTIC_LOCK_CONFLICT(5018, "乐观锁冲突"),      // 原5010→5018
    PREMATURE_DELIVERY(5019, "消息提前送达"),          // 原5009→5019

    // ========== 秒杀模块 (5200-5299) ==========
    REPEAT_ORDER(5201, "您已参与该秒杀活动，不能重复抢购"), // 原5001→5201
    SECKILL_NOT_START(5202, "秒杀尚未开始"),              // 原5002→5202
    SECKILL_ENDED(5203, "秒杀已结束"),                    // 原5003→5203
    SECKILL_BUSY(5204, "系统繁忙，请稍后重试"),           // 原5004→5204
    USER_LIMIT(5205, "每个用户限购1件"),                  // 原5005→5205
    SECKILL_NOT_EXIST(5206, "秒杀活动不存在"),            // 原5006→5206
    SECKILL_SUCCESS(5207, "抢购成功"),                    // 原5007→5207
    STOCK_EMPTY(5208, "秒杀库存不足"),                    // 原5008→5208
    SECKILL_REPEAT_ORDER(5209, "您已参与该秒杀活动，不能重复抢购"), // 原5009→5209
    NOT_START(5210, "秒杀尚未开始"),                      // 原5010→5210
    ENDED(5211, "秒杀已结束"),                           // 原5011→5211
    SECKILL_RECORD_INSERT_FAIL(5212, "秒杀记录插入失败"), // 原5012→5212
    SECKILL_RECORD_UPDATE_FAIL(5213, "秒杀记录更新失败"), // 原5013→5213
    SECKILL_ORDER_ALREADY_PAID(5214, "秒杀订单已支付"),   // 原5014→5214

    // ========== 支付模块 (6000-6099) 完全保留，未发现重复 ==========
    PAYMENT_NOT_FOUND(6001, "支付单不存在"),
    PAYMENT_STATUS_INVALID(6002, "支付单状态异常"),
    PAYMENT_FAILED(6003, "支付失败"),
    PAYMENT_TIMEOUT(6004, "支付超时"),
    PAYMENT_AMOUNT_MISMATCH(6005, "支付金额不匹配"),
    PAYMENT_SIGN_INVALID(6006, "支付回调签名验证失败"),
    PAYMENT_REPEAT(6007, "支付正在进行中，请勿重复操作"),
    PAYMENT_CREATE_FAIL(6008, "支付单创建失败"),
    THIRD_PARTY_ERROR(6009, "第三方支付接口异常"),
    PAYMENT_CALLBACK_PROCESS_FAIL(6010, "支付回调处理失败"),
    PAYMENT_CLOSED(6011, "支付单已关闭"),
    PAYMENT_ALREADY_SUCCESS(6012, "支付单已成功"),
    PAYMENT_EXPIRED(6013, "支付单已过期"),
    PAYMENT_METHOD_NOT_SUPPORT(6014, "不支持的支付方式"),
    PAYMENT_REFUND_FAIL(6015, "退款失败"),
    PAYMENT_REFUND_NOT_FOUND(6016, "退款记录不存在"),      // 原6016，调整顺序
    THIRD_PARTY_TIMEOUT(6017, "第三方接口超时"),       // 原6017
    PAYMENT_SERIALIZE_FAIL(6018, "支付序列化失败"),
    PAYMENT_DESERIALIZE_FAIL(6019, "支付反序列化失败"),
    ORDER_STATUS_NOT_PAYABLE(6020, "订单状态异常，无法支付"),
    PAYMENT_RESPONSE_EMPTY(6021, "第三方接口返回结果为空"),
    PAYMENT_ORDER_EXISTS(6023, "该订单已存在进行中的支付单，请勿重复发起"),
    PAYMENT_RESPONSE_ERROR(6022, "第三方接口返回结果错误"),
    // ========== 第三方支付错误码 (6100-6199) ==========
    /**
     * 第三方返回可重试错误（如：ACQ.TRADE_NOT_EXIST、ACQ.SYSTEM_ERROR）
     * 对应策略：即时补偿任务进行重试
     */
    THIRD_PARTY_RETRYABLE_ERROR(6101, "第三方返回可重试错误"),

    /**
     * 第三方返回不可恢复错误（如：ACQ.INVALID_PARAMETER）
     * 对应策略：直接更新支付单为 FAILED，不再重试
     */
    THIRD_PARTY_FATAL_ERROR(6102, "第三方返回不可恢复错误"),

    /**
     * 第三方返回需人工介入错误（如：ACQ.ENTERPRISE_PAY_BIZ_ERROR）
     * 对应策略：保持 PENDING_CONFIRM 状态，触发告警，等待人工处理
     */
    THIRD_PARTY_MANUAL_INTERVENTION(6103, "第三方返回需人工介入错误"),

    /**
     * 第三方返回未知错误码（未在映射表中定义）
     * 对应策略：保守处理，保持中间态并告警，避免误判
     */
    THIRD_PARTY_UNKNOWN_ERROR(6104, "第三方返回未知错误");
    
    private final Integer code;
    private final String message;
}