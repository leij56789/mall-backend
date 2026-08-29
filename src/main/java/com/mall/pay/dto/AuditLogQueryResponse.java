package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AuditLogQueryResponse {
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private List<AuditLogInfo> records;

    @Data
    @Builder
    public static class AuditLogInfo {
        private Long id;
        private String traceId;
        private Long userId;
        private String username;
        private String clientIp;
        private String paymentId;
        private Long orderId;
        private Long refundRecordId;
        private String operation;
        private String operationDesc;
        private String beforeStatus;
        private String afterStatus;
        private String result;
        private String errorCode;
        private String errorMsg;
        private Long costMs;
        private LocalDateTime createdAt;
    }
}