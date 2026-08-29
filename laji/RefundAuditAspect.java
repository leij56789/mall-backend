package com.mall.aspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.annotation.RefundAudit;
import com.mall.entity.PaymentOrder;
import com.mall.entity.PaymentRefundRecord;
import com.mall.enums.AuditOperation;
import com.mall.enums.AuditResult;
import com.mall.enums.RefundStatus;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.mapper.PaymentRefundRecordMapper;
import com.mall.service.PaymentAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RefundAuditAspect {

    private final PaymentRefundRecordMapper refundRecordMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentAuditService auditService;

    @Around("@annotation(refundAudit)")
    public Object auditRefund(ProceedingJoinPoint joinPoint, RefundAudit refundAudit) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Long recordId = null;
        String failReason = null;
        PaymentRefundRecord recordBefore = null;  // 保存执行前的记录

        for (Object arg : args) {
            if (arg instanceof Long) {
                recordId = (Long) arg;
            } else if (arg instanceof String && refundAudit.value().equals("FAILED")) {
                failReason = (String) arg;
            }
        }

        // ✅ 1. 在业务方法执行前查询退款记录（获取原始状态）
        if (recordId != null) {
            recordBefore = refundRecordMapper.selectById(recordId);
        }

        // 2. 执行业务方法
        Object result = joinPoint.proceed();

        // 3. 业务成功后再记录审计日志
        try {
            if (recordBefore != null && recordBefore.getPaymentId() != null) {
                PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                        new LambdaQueryWrapper<PaymentOrder>()
                                .eq(PaymentOrder::getPaymentId, recordBefore.getPaymentId())
                );

                if (paymentOrder != null) {
                    String operation = refundAudit.value().equals("SUCCESS")
                            ? AuditOperation.REFUND_CALLBACK.getCode()
                            : AuditOperation.REFUND_FAILED.getCode();
                    String afterStatus = refundAudit.value().equals("SUCCESS")
                            ? RefundStatus.SUCCESS.getCode()
                            : RefundStatus.FAILED.getCode();
                    String desc = refundAudit.value().equals("SUCCESS")
                            ? "退款成功"
                            : "退款失败: " + failReason;

                    auditService.builder()
                                .paymentId(recordBefore.getPaymentId())
                                .orderId(paymentOrder.getOrderId())
                                .userId(paymentOrder.getUserId())
                                .operation(operation)
                                .operationDesc(desc)
                                .beforeStatus(recordBefore.getStatus())  // ✅ 方法执行前的真实状态
                                .afterStatus(afterStatus)
                                .result(refundAudit.value().equals("SUCCESS")
                                        ? AuditResult.SUCCESS.getCode()
                                        : AuditResult.FAIL.getCode())
                                .errorMsg(refundAudit.value().equals("FAILED") ? failReason : null)
                                .refundRecordId(recordId)
                                .log();
                }
            }
        } catch (Exception e) {
            log.warn("退款审计日志记录失败: recordId={}", recordId, e);
        }

        return result;
    }
}