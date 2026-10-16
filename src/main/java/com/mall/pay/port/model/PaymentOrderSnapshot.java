package com.mall.pay.port.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付模块内部使用的"支付单快照"。
 * 业务系统从自己的 PaymentOrder 实体转换而来，支付模块只依赖此 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderSnapshot {
    private Long id;
    private String paymentId;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private String thirdPartyTradeNo;
    private LocalDateTime callbackTime;
    private LocalDateTime expiredAt;
    private Integer retryCount;
    private String prepayId;
    private Map<String, Object> extInfo;
    private String bizType;
    private String bizId;
    private String subject;
    private String description;
}