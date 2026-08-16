package com.mall.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTimeoutMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String paymentId;
    private Long orderId;
    private Long userId;
    private Integer orderType;       // 0:普通, 1:秒杀
    private Long bookId;
    private Integer quantity;
    private String messageId;


    /**
     * 支付超时截止时间戳（毫秒，UTC）
     * 用于消费者校验是否真的超时
     */
    private Long expireTimestamp;

    /**
     * 消息发送时间戳（毫秒，UTC）
     * 用于监控 MQ 延迟
     */
    private Long sendTimestamp;
}