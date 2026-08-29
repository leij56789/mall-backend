package com.mall.service;

import com.mall.entity.PaymentAuditLog;

import java.time.LocalDateTime;

/**
 * 审计日志构建器
 */
public class AuditLogBuilder {
    private final PaymentAuditLog.PaymentAuditLogBuilder builder = PaymentAuditLog.builder();
    private final PaymentAuditService auditService;

    public AuditLogBuilder(PaymentAuditService auditService) {
        this.auditService = auditService;
    }

    public AuditLogBuilder traceId(String traceId) {
        builder.traceId(traceId);
        return this;
    }

    public AuditLogBuilder userId(Long userId) {
        builder.userId(userId);
        return this;
    }

    public AuditLogBuilder username(String username) {
        builder.username(username);
        return this;
    }

    public AuditLogBuilder clientIp(String clientIp) {
        builder.clientIp(clientIp);
        return this;
    }

    public AuditLogBuilder userAgent(String userAgent) {
        builder.userAgent(userAgent);
        return this;
    }

    public AuditLogBuilder paymentId(String paymentId) {
        builder.paymentId(paymentId);
        return this;
    }

    public AuditLogBuilder orderId(Long orderId) {
        builder.orderId(orderId);
        return this;
    }

    public AuditLogBuilder refundRecordId(Long refundRecordId) {
        builder.refundRecordId(refundRecordId);
        return this;
    }

    public AuditLogBuilder operation(String operation) {
        builder.operation(operation);
        return this;
    }

    public AuditLogBuilder operationDesc(String operationDesc) {
        builder.operationDesc(operationDesc);
        return this;
    }

    public AuditLogBuilder requestParams(String requestParams) {
        builder.requestParams(requestParams);
        return this;
    }

    public AuditLogBuilder requestBody(String requestBody) {
        builder.requestBody(requestBody);
        return this;
    }

    public AuditLogBuilder responseBody(String responseBody) {
        builder.responseBody(responseBody);
        return this;
    }

    public AuditLogBuilder beforeStatus(String beforeStatus) {
        builder.beforeStatus(beforeStatus);
        return this;
    }

    public AuditLogBuilder afterStatus(String afterStatus) {
        builder.afterStatus(afterStatus);
        return this;
    }

    public AuditLogBuilder result(String result) {
        builder.result(result);
        return this;
    }

    public AuditLogBuilder errorCode(String errorCode) {
        builder.errorCode(errorCode);
        return this;
    }

    public AuditLogBuilder errorMsg(String errorMsg) {
        builder.errorMsg(errorMsg);
        return this;
    }

    public AuditLogBuilder costMs(Long costMs) {
        builder.costMs(costMs);
        return this;
    }
    // ===== ✅ 哈希链字段（新增） =====
    public AuditLogBuilder prevHash(String prevHash) {
        builder.prevHash(prevHash);
        return this;
    }

    public AuditLogBuilder selfHash(String selfHash) {
        builder.selfHash(selfHash);
        return this;
    }

    public void log() {
        // 直接设置 created_at 为当前时间
        builder.createdAt(LocalDateTime.now());
        auditService.logAsync(builder.build());
    }
    public AuditLogBuilder operatorType(String operatorType) {
        builder.operatorType(operatorType);
        return this;
    }
}