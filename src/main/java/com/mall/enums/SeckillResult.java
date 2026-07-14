package com.mall.enums;

import com.mall.common.SeckillConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 秒杀结果（内部业务逻辑使用）
 */
@Getter
@AllArgsConstructor
public enum SeckillResult {

    SUCCESS(1000, "抢购成功"),
    STOCK_EMPTY(4001, "库存不足"),
    REPEAT_ORDER(5001, "您已参与该秒杀活动，不能重复抢购"),
    NOT_START(5002, "秒杀尚未开始"),
    ENDED(5003, "秒杀已结束"),
    SYSTEM_BUSY(5004, "系统繁忙，请稍后重试"),
    USER_LIMIT(5005, "每个用户限购" + SeckillConstants.DEFAULT_USER_LIMIT + "件");

    private final Integer code;
    private final String message;
}