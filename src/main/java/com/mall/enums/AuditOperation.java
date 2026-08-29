package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditOperation {

    // ===== 支付相关 =====
    CREATE_PAYMENT("CREATE_PAYMENT", "创建支付单"),
    PAYMENT_WAITING("PAYMENT_WAITING", "生成支付凭证（WAITING）"),
    PAYMENT_CALLBACK("PAYMENT_CALLBACK", "支付回调"),
    PAYMENT_FAILED("PAYMENT_FAILED", "支付失败"),
    PAYMENT_CLOSED("PAYMENT_CLOSED", "支付关闭"),
    PAYMENT_CANCELLED("PAYMENT_CANCELLED", "支付取消"),
    PAYMENT_CREATE_FAIL("PAYMENT_CREATE_FAIL", "创建支付单失败"),

    // ===== 退款相关 =====
    CREATE_REFUND("CREATE_REFUND", "创建退款记录"),
    REFUND_CALLBACK("REFUND_CALLBACK", "退款回调"),
    REFUND_FAILED("REFUND_FAILED", "退款失败"),
    REFUND_SUCCESS("REFUND_SUCCESS", "退款成功"),

    // ===== 补偿相关 =====
    PAYMENT_COMPENSATE("PAYMENT_COMPENSATE", "支付补偿"),
    REFUND_COMPENSATE("REFUND_COMPENSATE", "退款补偿"),

    // ===== 关单 =====
    CLOSE_PAYMENT("CLOSE_PAYMENT", "关闭支付"),

    // ===== 其他 =====
    UNKNOWN("UNKNOWN", "未知操作");

    private final String code;
    private final String desc;

    public static AuditOperation fromCode(String code) {
        for (AuditOperation op : values()) {
            if (op.code.equals(code)) {
                return op;
            }
        }
        return UNKNOWN;
    }
}