package com.mall.service;

import com.mall.pay.dto.AuditLogQueryRequest;
import com.mall.pay.dto.AuditLogQueryResponse;

public interface AuditLogQueryService {

    AuditLogQueryResponse queryAuditLogs(AuditLogQueryRequest request);

    AuditLogQueryResponse queryByPaymentId(String paymentId, int pageNum, int pageSize);

    AuditLogQueryResponse queryByOrderId(Long orderId, int pageNum, int pageSize);
}