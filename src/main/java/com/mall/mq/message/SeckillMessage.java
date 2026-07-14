package com.mall.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 秒杀消息体
 * 用于 MQ 异步落库
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 秒杀商品ID
     */
    private Long bookId;

    /**
     * 秒杀数量（固定为1）
     */
    private Integer quantity;

    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;

    /**
     * 秒杀记录ID
     */
    private Long recordId;

    /**
     * 消息ID（幂等性）
     */
    private String messageId;

    /**
     * 时间戳
     */
    private Long timestamp;
}