package com.mall.controller;

import com.mall.common.Result;
import com.mall.pay.dto.AuditLogQueryRequest;
import com.mall.pay.dto.AuditLogQueryResponse;
import com.mall.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    @PostMapping("/logs")
    public Result<AuditLogQueryResponse> queryLogs(@RequestBody AuditLogQueryRequest request) {
        AuditLogQueryResponse response = auditLogQueryService.queryAuditLogs(request);
        return Result.success(response);
    }

    @GetMapping("/payment/{paymentId}")
    public Result<AuditLogQueryResponse> queryByPaymentId(
            @PathVariable String paymentId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuditLogQueryResponse response = auditLogQueryService.queryByPaymentId(paymentId, pageNum, pageSize);
        return Result.success(response);
    }

    @GetMapping("/order/{orderId}")
    public Result<AuditLogQueryResponse> queryByOrderId(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuditLogQueryResponse response = auditLogQueryService.queryByOrderId(orderId, pageNum, pageSize);
        return Result.success(response);
    }
}