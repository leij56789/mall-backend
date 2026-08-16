package com.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 秒杀记录状态（数据库 seckill_record.status 字段）
 */
@Getter
@AllArgsConstructor
public enum SeckillRecordStatus {

    PENDING(0, "抢购中"),
    SUCCESS(1, "抢购成功"),
    FAILED(2, "抢购失败"),
    PAID(3, "已支付");

    private final Integer code;
    private final String desc;

    public static String getDescByCode(Integer code) {
        for (SeckillRecordStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status.getDesc();
            }
        }
        return "未知";
    }
}