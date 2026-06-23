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
    
    // ========== 库存模块 (4000-4999) ==========
    STOCK_NOT_ENOUGH(4001, "库存不足"),
    STOCK_RECOVER_FAIL(4002, "库存恢复失败"),
    BOOK_NOT_FOUND(4003, "书籍不存在"),
    BOOK_ALREADY_EXIST(4004, "书籍已存在"),
    STOCK_DEDUCT_FAIL(4005,"库存扣减失败"),
    
    // ========== 系统模块 (5000-5999) ==========
    SYSTEM_ERROR(5000, "系统繁忙，请稍后重试"),
    DB_ERROR(5001, "数据库异常"),
    MQ_ERROR(5002, "消息队列异常"),
    REDIS_ERROR(5003, "缓存异常"),
    FILE_UPLOAD_ERROR(5004, "文件上传失败"),
    SYSTEM_BUSY(5005, "系统繁忙");
    
    private final Integer code;
    private final String message;
}