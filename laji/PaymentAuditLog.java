package com.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@TableName("payment_audit_log")
public class PaymentAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;
    private Long userId;
    private String username;
    private String clientIp;
    private String userAgent;

    private String operatorType;

    private String paymentId;
    private Long orderId;
    private Long refundRecordId;

    private String operation;
    private String operationDesc;
    private String requestParams;
    private String requestBody;
    private String responseBody;

    private String beforeStatus;
    private String afterStatus;

    private String result;
    private String errorCode;
    private String errorMsg;

    private Long costMs;
    // ===== ✅ 哈希链字段（新增） =====
    private String prevHash;
    private String selfHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}