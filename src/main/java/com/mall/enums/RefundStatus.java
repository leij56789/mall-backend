package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RefundStatus {

    PROCESSING("PROCESSING", "退款处理中"),
    SUCCESS("SUCCESS", "退款成功"),
    FAILED("FAILED", "退款失败");

    private final String code;
    private final String desc;

    public static RefundStatus fromCode(String code) {
        for (RefundStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isProcessing() {
        return this == PROCESSING;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isFailed() {
        return this == FAILED;
    }
}