// enums/OrderStatus.java
package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatus {
    
    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    CANCELLED(2, "已取消"),
    EXPIRED(3, "已过期");
    
    private final Integer value;
    private final String desc;
    
    /**
     * 根据 value 获取枚举
     */
    public static OrderStatus fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (OrderStatus status : OrderStatus.values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
    
    /**
     * 根据 value 获取描述
     */
    public static String getDescByValue(Integer value) {
        OrderStatus status = fromValue(value);
        return status != null ? status.getDesc() : "未知状态";
    }
}