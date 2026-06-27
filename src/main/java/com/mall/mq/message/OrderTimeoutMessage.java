package com.mall.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 订单超时消息体
 * 用于 RabbitMQ 延迟消息
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage {
    
    // ========== 业务数据（快照） ==========
    private Long orderId;
    private Long bookId;
    private Integer quantity;
    
    // ========== 元数据（幂等性） ==========
    private Long expireTimestamp;
    private Long createTimestamp;
    private String messageId;
}