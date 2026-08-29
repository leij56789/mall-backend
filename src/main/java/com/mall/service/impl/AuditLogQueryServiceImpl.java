package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.entity.PaymentAuditLog;
import com.mall.mapper.PaymentAuditLogMapper;
import com.mall.pay.dto.AuditLogQueryRequest;
import com.mall.pay.dto.AuditLogQueryResponse;
import com.mall.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogQueryServiceImpl implements AuditLogQueryService {

    private final PaymentAuditLogMapper auditLogMapper;

    @Override
    public AuditLogQueryResponse queryAuditLogs(AuditLogQueryRequest request) {
        LambdaQueryWrapper<PaymentAuditLog> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getPaymentId())) {
            wrapper.eq(PaymentAuditLog::getPaymentId, request.getPaymentId());
        }
        if (request.getOrderId() != null) {
            wrapper.eq(PaymentAuditLog::getOrderId, request.getOrderId());
        }
        if (request.getUserId() != null) {
            wrapper.eq(PaymentAuditLog::getUserId, request.getUserId());
        }
        if (request.getOperations() != null && !request.getOperations().isEmpty()) {
            wrapper.in(PaymentAuditLog::getOperation, request.getOperations());
        }
        if (StringUtils.hasText(request.getResult())) {
            wrapper.eq(PaymentAuditLog::getResult, request.getResult());
        }
        if (request.getStartTime() != null) {
            wrapper.ge(PaymentAuditLog::getCreatedAt, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(PaymentAuditLog::getCreatedAt, request.getEndTime());
        }

        wrapper.orderByDesc(PaymentAuditLog::getCreatedAt);

        Page<PaymentAuditLog> page = new Page<>(request.getPageNum(), request.getPageSize());
        IPage<PaymentAuditLog> pageResult = auditLogMapper.selectPage(page, wrapper);

        List<AuditLogQueryResponse.AuditLogInfo> records = new ArrayList<>();
        for (PaymentAuditLog log : pageResult.getRecords()) {
            records.add(convertToInfo(log));
        }

        return AuditLogQueryResponse.builder()
                .total(pageResult.getTotal())
                .pageNum(request.getPageNum())
                .pageSize(request.getPageSize())
                .records(records)
                .build();
    }

    @Override
    public AuditLogQueryResponse queryByPaymentId(String paymentId, int pageNum, int pageSize) {
        AuditLogQueryRequest request = AuditLogQueryRequest.builder()
                .paymentId(paymentId)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
        return queryAuditLogs(request);
    }

    @Override
    public AuditLogQueryResponse queryByOrderId(Long orderId, int pageNum, int pageSize) {
        AuditLogQueryRequest request = AuditLogQueryRequest.builder()
                .orderId(orderId)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
        return queryAuditLogs(request);
    }

    private AuditLogQueryResponse.AuditLogInfo convertToInfo(PaymentAuditLog log) {
        return AuditLogQueryResponse.AuditLogInfo.builder()
                .id(log.getId())
                .traceId(log.getTraceId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .clientIp(log.getClientIp())
                .paymentId(log.getPaymentId())
                .orderId(log.getOrderId())
                .refundRecordId(log.getRefundRecordId())
                .operation(log.getOperation())
                .operationDesc(log.getOperationDesc())
                .beforeStatus(log.getBeforeStatus())
                .afterStatus(log.getAfterStatus())
                .result(log.getResult())
                .errorCode(log.getErrorCode())
                .errorMsg(log.getErrorMsg())
                .costMs(log.getCostMs())
                .createdAt(log.getCreatedAt())
                .build();
    }
}