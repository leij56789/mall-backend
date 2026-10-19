package com.mall.pay.enums;

/**
 * 审计操作者类型
 */
public enum OperatorType {
    USER("USER", "用户"),
    SYSTEM("SYSTEM", "系统"),
    BATCH("BATCH", "批处理"),
    COMPENSATE("COMPENSATE", "补偿");

    private final String code;
    private final String desc;

    OperatorType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static OperatorType fromCode(String code) {
        for (OperatorType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return null;
    }
}