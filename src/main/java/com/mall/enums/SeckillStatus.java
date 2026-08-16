package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 秒杀状态（响应给前端）
 */
@Getter
@AllArgsConstructor
public enum SeckillStatus {

    SUCCESS("SUCCESS", "抢购成功"),
    PENDING("PENDING", "处理中"),
    FAILED("FAILED", "抢购失败");

    private final String code;
    private final String desc;
}