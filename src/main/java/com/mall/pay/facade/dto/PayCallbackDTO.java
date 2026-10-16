package com.mall.pay.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付回调通知参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayCallbackDTO {

    private String paymentId;
    private String bizType;
    private String bizId;

    private BigDecimal amount;
    private String paymentMethod;

    /** 第三方交易号 */
    private String thirdPartyTradeNo;

    /** 状态：SUCCESS / FAILED / CLOSED / PENDING_CONFIRM */
    private String status;

    /** 回调时间 */
    private LocalDateTime callbackTime;
}