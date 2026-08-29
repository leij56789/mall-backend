package com.mall.service;

import com.mall.entity.PaymentAuditLog;

public interface PaymentAuditService {

    /**
     * 记录审计日志（同步）
     */
    void auditLog(PaymentAuditLog log);

    /**
     * 记录审计日志（异步）
     */
    void logAsync(PaymentAuditLog log);

    PaymentAuditLog getLastAuditLog(String paymentId);

    /**
     * 构建审计日志 Builder
     */
    AuditLogBuilder builder();
}