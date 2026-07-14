package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    
    // ========== 通用模块 (1000-1999) ==========
    SUCCESS(1000, "操作成功"),
    FAIL(1001, "操作失败"),
    PARAM_ERROR(1002, "参数错误"),
    PARAM_MISSING(1003, "缺少必要参数"),
    PARAM_INVALID(1004, "参数格式错误"),
    // ResultCode.java 只定义少数几个通用错误码
    TRANSACTION_ROLLBACK_FAIL(1005, "事务回滚回调执行失败"),
    REDIS_ROLLBACK_FAIL(1005, "事务回滚回调执行失败"),
    REDIS_OPERATION_FAIL(1006, "Redis操作失败"),
    DB_OPERATION_FAIL(1007, "数据库操作失败"),
    
    // ========== 认证模块 (2000-2999) ==========
    UNAUTHORIZED(2001, "未登录或Token已过期"),
    FORBIDDEN(2002, "无权限访问"),
    TOKEN_EXPIRED(2003, "Token已过期"),
    TOKEN_INVALID(2004, "Token无效"),
    LOGIN_FAIL(2005, "用户名或密码错误"),
    USER_NOT_EXIST(2006, "用户不存在"),
    USER_ALREADY_EXIST(2007, "用户已存在"),
    
    // ========== 订单模块 (3000-3999) ==========
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_INVALID(3002, "订单状态异常"),
    ORDER_CANCEL_FAIL(3003, "订单取消失败"),
    ORDER_NOT_EXPIRE(3004, "订单尚未超时"),
    ORDER_EXPIRED(3005, "订单已超时"),
    ORDER_ALREADY_PAID(3006, "订单已支付"),
    ORDER_ALREADY_CANCELLED(3007, "订单已取消"),
    ORDER_CREATE_FAIL(3008,"订单创建失败"),
    ORDER_UPDATE_FAIL(3009,"订单更新失败"),
    ORDER_SELECT_FAIL(3009,"订单查询失败"),
    ORDER_TYPE_ERROR(3009,"订单查询失败"),

    
    // ========== 库存模块 (4000-4999) ==========
    STOCK_NOT_ENOUGH(4001, "库存不足"),
    STOCK_RECOVER_FAIL(4002, "库存恢复失败"),
    BOOK_NOT_FOUND(4003, "书籍不存在"),
    BOOK_ALREADY_EXIST(4004, "书籍已存在"),
    STOCK_DEDUCT_FAIL(4005,"库存扣减失败"),
    
    // ========== 系统模块 (5000-5999) ==========
    SYSTEM_ERROR(5000, "系统错误"),
    DB_ERROR(5001, "数据库异常"),
    MQ_ERROR(5002, "消息队列异常"),
    REDIS_ERROR(5003, "缓存异常"),
    FILE_UPLOAD_ERROR(5004, "文件上传失败"),
    SYSTEM_BUSY(5005, "系统繁忙"),

    // ========== MQ 模块 (5000-5099) ==========
    MESSAGE_SEND_FAIL(5001, "消息发送失败"),
    MESSAGE_SERIALIZE_FAIL(5002, "消息序列化失败"),
    MESSAGE_DESERIALIZE_FAIL(5003, "消息反序列化失败"),
    MESSAGE_CONSUME_FAIL(5004, "消息消费失败"),
    MESSAGE_CONSUME_SUCCESS(5007, "消息消费成功"),
    MESSAGE_RETRY_EXHAUSTED(5005, "消息重试次数耗尽"),
    MESSAGE_INSERT_FAIL(5006,"消息存入数据库失败"),
    BROKER_MESSAGE_LOG_UPDATE_FAIL(5008,"订单超时消息数据库更新失败" ),
    OPTIMISTIC_LOCK_CONFLICT(5010, "乐观锁冲突"),
    PREMATURE_DELIVERY(5009, "消息提前送达"),
    // 秒杀模块
    REPEAT_ORDER(5001, "您已参与该秒杀活动，不能重复抢购"),
    SECKILL_NOT_START(5002, "秒杀尚未开始"),
    SECKILL_ENDED(5003, "秒杀已结束"),
    SECKILL_BUSY(5004, "系统繁忙，请稍后重试"),
    USER_LIMIT(5005, "每个用户限购1件"),
    SECKILL_NOT_EXIST(5006,"秒杀活动不存在"),
    SECKILL_SUCCESS(5007, "抢购成功"),
    STOCK_EMPTY(5008, "秒杀库存不足"),
    SECKILL_REPEAT_ORDER(5009, "您已参与该秒杀活动，不能重复抢购"),
    NOT_START(5010, "秒杀尚未开始"),
    ENDED(5011, "秒杀已结束"),
    SECKILL_RECORD_INSERT_FAIL(5012,"秒杀记录插入失败"),
    SECKILL_RECORD_UPDATE_FAIL(5013,"秒杀记录更新失败"),
    SECKILL_ORDER_ALREADY_PAID(5014,"秒杀订单已支付");
    private final Integer code;
    private final String message;
}