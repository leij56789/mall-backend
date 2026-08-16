package com.mall.enums.pay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 微信支付 API v3 业务错误码
 * 来源：微信支付官方文档
 */
@Getter
@AllArgsConstructor
public enum WechatPayErrorCode {

    // ========== 公共错误码 ==========
    PARAM_ERROR("PARAM_ERROR", "参数错误"),
    INVALID_REQUEST("INVALID_REQUEST", "无效请求"),
    SIGN_ERROR("SIGN_ERROR", "签名验证失败"),
    SYSTEM_ERROR("SYSTEM_ERROR", "系统错误"),
    FREQUENCY_LIMITED("FREQUENCY_LIMITED", "频率限制"),
    RATE_LIMITED("RATE_LIMITED", "频率限制"),
    RESOURCE_NOT_EXISTS("RESOURCE_NOT_EXISTS", "资源不存在"),

    // ========== 订单/支付相关 ==========
    ORDER_NOT_EXISTS("ORDER_NOT_EXISTS", "订单不存在"),
    ORDER_CLOSED("ORDER_CLOSED", "订单已关闭"),
    ORDER_PAID("ORDER_PAID", "订单已支付"),
    ORDER_CANCELED("ORDER_CANCELED", "订单已取消"),
    OUT_TRADE_NO_USED("OUT_TRADE_NO_USED", "商户订单号已被使用"),
    NO_AUTH("NO_AUTH", "商户无权限"),
    ACCOUNT_ERROR("ACCOUNT_ERROR", "账户异常"),
    NOT_ENOUGH("NOT_ENOUGH", "余额不足"),
    INVALID_TRANSACTION("INVALID_TRANSACTION", "交易无效"),

    // ========== 用户支付相关 ==========
    USERPAYING("USERPAYING", "用户支付中，请重复查询"),
    USER_CANCEL("USER_CANCEL", "用户取消支付"),
    PAY_ERROR("PAY_ERROR", "支付失败"),

    // ========== 退款相关 ==========
    REFUND_NOT_EXISTS("REFUND_NOT_EXISTS", "退款不存在"),
    REFUND_AMOUNT_INVALID("REFUND_AMOUNT_INVALID", "退款金额无效"),
    REFUND_STATUS_ABNORMAL("REFUND_STATUS_ABNORMAL", "退款状态异常"),

    // ========== 风控相关 ==========
    RISK_DECLINE("RISK_DECLINE", "风控拦截"),
    RISK_REJECT("RISK_REJECT", "风控拒绝"),
    ;

    private final String code;
    private final String message;
}