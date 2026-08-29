package com.mall.pay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AuditLogQueryRequest {
    private String paymentId;
    private Long orderId;
    private Long userId;
    private List<String> operations;
    private String result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}