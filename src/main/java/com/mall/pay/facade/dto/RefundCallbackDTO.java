package com.mall.pay.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款回调通知参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCallbackDTO {

    private Long refundId;
    private String paymentId;
    private String outRequestNo;

    private BigDecimal refundAmount;

    /** 状态：PROCESSING / SUCCESS / FAILED */
    private String status;

    /** 失败原因（status=FAILED 时） */
    private String failReason;

    private String thirdPartyRefundNo;

    private LocalDateTime callbackTime;
}