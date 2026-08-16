package com.mall.enums.pay;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付宝 业务子错误码
 * 来源：支付宝官方文档
 */
@Getter
@AllArgsConstructor
public enum AlipaySubErrorCode {

    // ========== 订单相关 ==========
    ORDER_PAY_SUCCESS("ACQ.ORDER_PAY_SUCCESS","订单已支付"),
    ORDER_CLOSED("ACQ.ORDER_CLOSED", "订单已关闭"),
    ORDER_NOT_EXIST("ACQ.ORDER_NOT_EXIST", "订单不存在"),
    OUT_TRADE_NO_DUPLICATE("ACQ.OUT_TRADE_NO_DUPLICATE", "商户订单号重复"),
    TRADE_NOT_EXIST("ACQ.TRADE_NOT_EXIST", "交易不存在"),
    TRADE_STATUS_ERROR("ACQ.TRADE_STATUS_ERROR", "交易状态异常"),

    // ========== 支付相关 ==========
    SYSTEM_ERROR("ACQ.SYSTEM_ERROR", "系统异常"),
    INVALID_PARAMETER("ACQ.INVALID_PARAMETER", "参数无效"),
    ACCESS_DENIED("ACQ.ACCESS_DENIED", "访问被拒绝"),
    PRODUCT_NOT_SUPPORT("ACQ.PRODUCT_NOT_SUPPORT", "产品不支持"),
    BUYER_NOT_EXIST("ACQ.BUYER_NOT_EXIST", "买家不存在"),
    BUYER_ENABLE_STATUS_FORBID("ACQ.BUYER_ENABLE_STATUS_FORBID", "买家状态异常"),
    BUYER_PAY_AMOUNT_DAY_LIMIT("ACQ.BUYER_PAY_AMOUNT_DAY_LIMIT", "买家日限额"),
    BUYER_PAY_AMOUNT_MONTH_LIMIT("ACQ.BUYER_PAY_AMOUNT_MONTH_LIMIT", "买家月限额"),
    TOTAL_FEE_EXCEED("ACQ.TOTAL_FEE_EXCEED", "金额超限"),
    PAYER_UNMATCHED("ACQ.PAYER_UNMATCHED", "付款人不匹配"),

    // ========== 风控相关 ==========
    RISK_DECLINE("ACQ.RISK_DECLINE", "风控拦截"),
    RISK_REJECT("ACQ.RISK_REJECT", "风控拒绝"),

    // ========== 退款相关 ==========
    REFUND_AMT_NOT_EQUAL_TOTAL("ACQ.REFUND_AMT_NOT_EQUAL_TOTAL", "退款金额超限"),
    REFUND_FEE_ERROR("ACQ.REFUND_FEE_ERROR", "退款金额错误"),
    REFUND_NOT_EXIST("ACQ.REFUND_NOT_EXIST", "退款不存在"),
    REFUND_ROLLBACK_ERROR("ACQ.REFUND_ROLLBACK_ERROR", "退款回滚失败"),

    // ========== 用户支付状态 ==========
    WAIT_BUYER_PAY("ACQ.WAIT_BUYER_PAY", "交易创建，等待买家付款"),
    TRADE_FINISHED("ACQ.TRADE_FINISHED", "交易已完结"),
    TRADE_SUCCESS("ACQ.TRADE_SUCCESS", "交易成功"),
    TRADE_CLOSED("ACQ.TRADE_CLOSED", "交易已关闭");

    private final String code;
    private final String message;
}