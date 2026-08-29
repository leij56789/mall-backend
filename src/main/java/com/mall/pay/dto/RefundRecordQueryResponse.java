package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款记录查询响应
 */
@Data
@Builder
public class RefundRecordQueryResponse {
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private List<RefundRecordInfo> records;

    @Data
    @Builder
    public static class RefundRecordInfo {
        private Long id;
        private String paymentId;
        private String outTradeNo;
        private String tradeNo;
        private BigDecimal refundAmount;
        private String refundReason;
        private String outRequestNo;
        private String status;
        private String statusDesc;
        private String failReason;
        private String thirdPartyRefundNo;
        private Integer retryCount;
        private LocalDateTime nextQueryTime;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}