package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.trace.constant.TraceConstants;
import com.mall.common.trace.context.TraceContext;
import com.mall.pay.entity.PaymentRefundNotifyLog;
import com.mall.pay.facade.PayFacade;
import com.mall.pay.facade.dto.*;
import com.mall.pay.mapper.PaymentRefundNotifyLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/test/pay-facade")
@RequiredArgsConstructor
public class PayFacadeTestController {

    private final PayFacade payFacade;

    // ==================== 支付 ====================

    @GetMapping("/pay")
    public PayResult pay(@RequestParam String bizType,
                         @RequestParam String bizId,
                         @RequestParam Long userId,
                         @RequestParam BigDecimal amount) {
        TraceContext.putUserId(String.valueOf(userId));   // ★ 新增
        try {
            PayOrderDTO dto = PayOrderDTO.builder()
                                         .bizType(bizType)
                                         .bizId(bizId)
                                         .userId(userId)
                                         .amount(amount)
                                         .subject("测试支付")
                                         .paymentMethod("ALIPAY_WAP")
                                         .build();
            return payFacade.pay(dto);
        } finally {
            TraceContext.putUserId(TraceConstants.SYSTEM_USER_ID);  // 恢复
        }
    }

    @GetMapping("/query/{paymentId}")
    public PayQueryResult query(@PathVariable String paymentId) {
        return payFacade.query(paymentId);
    }

    @GetMapping("/close/{paymentId}")
    public Map<String, Object> close(@PathVariable String paymentId) {
        payFacade.close(paymentId);
        return Map.of("ok", true, "paymentId", paymentId);
    }

    // ==================== 退款 ====================

    @GetMapping("/refund")
    public RefundResult refund(@RequestParam String paymentId,
                               @RequestParam BigDecimal refundAmount,
                               @RequestParam(required = false, defaultValue = "测试退款") String refundReason,
                               @RequestParam(required = false) String outRequestNo) {
        if (outRequestNo == null || outRequestNo.isEmpty()) {
            outRequestNo = "REF_" + System.currentTimeMillis();
        }
            TraceContext.putUserId(String.valueOf(1L));   // operatorId=1
            try {
                RefundDTO dto = RefundDTO.builder()
                                         .paymentId(paymentId)
                                         .refundAmount(refundAmount)
                                         .refundReason(refundReason)
                                         .outRequestNo(outRequestNo)
                                         .operatorId(1L)
                                         .clientIp("127.0.0.1")
                                         .build();
                return payFacade.refund(dto);
            } finally {
                TraceContext.putUserId(TraceConstants.SYSTEM_USER_ID);
            }

    }

    @GetMapping("/refund/query/{outRequestNo}")
    public RefundResult queryRefund(@PathVariable String outRequestNo) {
        return payFacade.queryRefund(outRequestNo);
    }

    @GetMapping("/refund/list/{paymentId}")
    public List<RefundResult> listRefunds(@PathVariable String paymentId) {
        return payFacade.listRefunds(paymentId);
    }
    private final PaymentRefundNotifyLogMapper refundNotifyLogMapper;

    @GetMapping("/refund/notify-log/{outRequestNo}")
    public Map<String, Object> queryRefundNotifyLog(@PathVariable String outRequestNo) {
        List<PaymentRefundNotifyLog> logs = refundNotifyLogMapper.selectList(
                new LambdaQueryWrapper<PaymentRefundNotifyLog>()
                        .eq(PaymentRefundNotifyLog::getOutRequestNo, outRequestNo)
                        .orderByDesc(PaymentRefundNotifyLog::getId)
        );
        if (logs.isEmpty()) {
            return Map.of("found", false, "outRequestNo", outRequestNo);
        }
        PaymentRefundNotifyLog latest = logs.get(0);
        return Map.of(
                "found", true,
                "id", latest.getId(),
                "outRequestNo", latest.getOutRequestNo(),
                "notifyType", latest.getNotifyType(),
                "status", latest.getStatus(),
                "retryCount", latest.getRetryCount() != null ? latest.getRetryCount() : 0,
                "lastError", latest.getLastError() != null ? latest.getLastError() : "",
                "nextNotifyTime", latest.getNextNotifyTime() != null ? latest.getNextNotifyTime().toString() : ""
        );
    }
}