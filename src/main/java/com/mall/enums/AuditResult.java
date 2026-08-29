package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditResult {
    SUCCESS("SUCCESS", "成功"),
    FAIL("FAIL", "失败"),
    PROCESSING("PROCESSING", "处理中");

    private final String code;
    private final String desc;
}