package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderType {

    NORMAL(0, "普通订单"),   // 0 = 普通订单
    SECKILL(1, "秒杀订单");  // 1 = 秒杀订单  ← 这里维护

    private final Integer code;
    private final String desc;

    public static String getDescByCode(Integer code) {
        for (OrderType type : values()) {
            if (type.getCode().equals(code)) {
                return type.getDesc();
            }
        }
        return "未知";
    }
}