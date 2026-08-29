package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审计目标类型
 * <p>
 * 标识审计日志关联的业务实体
 */
@Getter
@AllArgsConstructor
public enum AuditTargetType {


    PAYMENT_ORDER("PAYMENT_ORDER", "支付单"),
    REFUND_RECORD("REFUND_RECORD", "退款记录"),
    ORDER("ORDER", "订单");  // ✅ 新增

    private final String code;
    private final String desc;

    public static AuditTargetType fromCode(String code) {
        for (AuditTargetType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}