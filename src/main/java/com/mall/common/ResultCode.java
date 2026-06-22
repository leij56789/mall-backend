package com.mall.common;

public enum ResultCode {
    // 成功
    SUCCESS(200, "操作成功"),

    // 客户端错误（4xx）
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),

    // 服务端错误（5xx）
    ERROR(500, "服务器内部错误"),

    // 图书模块
    BOOK_NOT_FOUND(1001, "图书不存在"),
    // 用户模块
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),

    // 订单模块
    ORDER_NOT_FOUND(2001, "订单不存在"),
    ORDER_CREATE_FAILED(2002, "订单创建失败"),
    ORDER_STATUS_INVALID(2003, "订单状态无效"),
    DUPLICATE_ORDER(4004, "订单已存在"),


    // 库存模块
    STOCK_INSUFFICIENT(3001, "库存不足"),
    STOCK_DEDUCT_FAILED(3002, "库存扣减失败");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() { return code; }
    public String getMessage() { return message; }
}