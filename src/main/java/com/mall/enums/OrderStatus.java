// enums/OrderStatus.java
package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatus {

    PENDING(0, "待支付"),    // 起始状态
    PAID(1, "已支付"),       // 正向流转（需支付、发货、收货等才能到 COMPLETED）
    COMPLETED(2, "已完成"),  // 正向终态
    CANCELLED(3, "已取消"),  // 逆向终态（用户主动）
    EXPIRED(4, "已过期");    // 逆向终态（系统自动超时）

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