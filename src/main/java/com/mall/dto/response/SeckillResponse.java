package com.mall.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀响应实体
 * 
 * 包含两种场景：
 * 1. 秒杀成功 → 返回订单信息
 * 2. 秒杀处理中 → 返回排队信息
 * 3. 秒杀失败 → 返回错误信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillResponse {

    /**
     * 抢购状态：SUCCESS / PENDING / FAILED
     */
    private String status;

    /**
     * 订单ID（抢购成功时返回）
     */
    private Long orderId;

    /**
     * 订单号（抢购成功时返回）
     */
    private String orderNo;

    /**
     * 商品ID
     */
    private Long bookId;

    /**
     * 商品名称
     */
    private String bookName;

    /**
     * 商品封面图
     */
    private String bookCover;

    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 订单状态（抢购成功时返回）
     */
    private Integer statusCode;

    /**
     * 订单状态描述
     */
    private String statusDesc;

    /**
     * 订单过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 排队位置（秒杀处理中时返回）
     */
    private Integer queuePosition;

    /**
     * 预计等待时间（秒）
     */
    private Integer estimatedWaitSeconds;

    /**
     * 提示信息
     */
    private String message;
}