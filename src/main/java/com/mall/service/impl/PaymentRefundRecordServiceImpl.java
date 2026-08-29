package com.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.annotation.AuditLog;
import com.mall.common.BusinessException;
import com.mall.entity.PaymentRefundRecord;
import com.mall.enums.AuditTargetType;
import com.mall.enums.RefundStatus;
import com.mall.enums.ResultCode;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.mapper.PaymentRefundRecordMapper;
import com.mall.service.PaymentAuditService;
import com.mall.service.PaymentRefundRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundRecordServiceImpl
        extends ServiceImpl<PaymentRefundRecordMapper, PaymentRefundRecord>
        implements PaymentRefundRecordService {

    private final PaymentRefundRecordMapper refundRecordMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentAuditService auditService;

    @Override
    public PaymentRefundRecord createRefundRecord(String paymentId, String outTradeNo,
                                                   String tradeNo, BigDecimal refundAmount,
                                                   String refundReason, String outRequestNo) {
        PaymentRefundRecord record = PaymentRefundRecord.builder()
                .paymentId(paymentId)
                .outTradeNo(outTradeNo)
                .tradeNo(tradeNo)
                .refundAmount(refundAmount)
                .refundReason(refundReason)
                .outRequestNo(outRequestNo)
                .status(RefundStatus.PROCESSING.getCode())
                .retryCount(0)
                .nextQueryTime(LocalDateTime.now().plusMinutes(5)) // 5分钟后首次查询
                .build();

        save(record);
        log.info("创建退款记录: id={}, paymentId={}, outRequestNo={}, amount={}",
                record.getId(), paymentId, outRequestNo, refundAmount);
        return record;
    }

    @Override
    public PaymentRefundRecord getByOutRequestNo(String outRequestNo) {
        return refundRecordMapper.selectByOutRequestNo(outRequestNo);
    }

    @Override
    public List<PaymentRefundRecord> getProcessingTimeout(int limit) {
        return refundRecordMapper.selectProcessingTimeout(limit);
    }

    @Override
    public List<PaymentRefundRecord> getByPaymentId(String paymentId) {
        return refundRecordMapper.selectByPaymentId(paymentId);
    }

    @AuditLog(
            targetTypes = {AuditTargetType.REFUND_RECORD},
            refundRecordId = "recordId",
            desc = "退款成功"
    )
    @Override
    public void markSuccess(Long recordId, String thirdPartyRefundNo) {
//        // 1. 先查询退款记录（获取原始状态）
//        PaymentRefundRecord record = refundRecordMapper.selectById(recordId);
//        if (record == null) {
//            log.error("退款记录不存在: recordId={}", recordId);
//            throw new BusinessException(ResultCode.PAYMENT_REFUND_NOT_FOUND);
//        }
//
//        // ? 保存原始状态（更新前）
//        String beforeStatus = record.getStatus();
//        //以上为审计需求

        int updated = refundRecordMapper.updateSuccess(recordId, thirdPartyRefundNo);
        if (updated != 1) {
            log.error("退款记录标记成功失败: recordId={}, 可能状态已变更", recordId);
            throw new BusinessException(ResultCode.OPTIMISTIC_LOCK_CONFLICT,
                    "退款记录更新失败，请稍后重试");
        }
        log.info("退款记录标记成功: recordId={}, thirdPartyRefundNo={}",
                recordId, thirdPartyRefundNo);
        // ========== 3. 记录审计日志 ==========
//        try {
//            PaymentRefundRecord refundRecord = refundRecordMapper.selectById(recordId);
//            PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
//                    new LambdaQueryWrapper<PaymentOrder>()
//                            .eq(PaymentOrder::getPaymentId, record.getPaymentId())
//            );
//            if (paymentOrder != null) {
//                auditService.builder()
//                            .paymentId(record.getPaymentId())
//                            .orderId(paymentOrder.getOrderId())
//                            .userId(paymentOrder.getUserId())
//                            .operation(AuditOperation.REFUND_CALLBACK.getCode())
//                            .operationDesc("退款成功")
//                            .beforeStatus(refundRecord.getStatus())  // 原状态 PROCESSING
//                            .afterStatus(RefundStatus.SUCCESS.getCode())
//                            .result(AuditResult.SUCCESS.getCode())
//                            .refundRecordId(recordId)
//                            .log();
//            }
//        } catch (Exception e) {
//            log.warn("记录退款成功审计日志失败: recordId={}", recordId, e);
//        }
    }

    @AuditLog(
            targetTypes = {AuditTargetType.REFUND_RECORD},
            refundRecordId = "recordId",
            desc = "退款失败"
    )
    @Override
    public void markFailed(Long recordId, String failReason) {

        // 1. 先查询退款记录（获取原始状态）
//        PaymentRefundRecord record = refundRecordMapper.selectById(recordId);
//        if (record == null) {
//            log.error("退款记录不存在: recordId={}", recordId);
//            throw new BusinessException(ResultCode.PAYMENT_REFUND_NOT_FOUND);
//        }
//
//        // ✅ 保存原始状态（更新前）
//        String beforeStatus = record.getStatus();

        int updated = refundRecordMapper.updateFailed(recordId, failReason);
        if (updated != 1) {
            log.error("退款记录标记失败: recordId={}, 可能状态已变更", recordId);
            throw new BusinessException(ResultCode.OPTIMISTIC_LOCK_CONFLICT,
                    "退款记录更新失败，请稍后重试");
        }
        log.info("退款记录标记失败: recordId={}, failReason={}", recordId, failReason);
        // ========== 3. 记录审计日志 ==========
        // 记录审计日志
//        try {
//            PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
//                    new LambdaQueryWrapper<PaymentOrder>()
//                            .eq(PaymentOrder::getPaymentId, record.getPaymentId())
//            );
//            if (paymentOrder != null) {
//                auditService.builder()
//                            .paymentId(record.getPaymentId())
//                            .orderId(paymentOrder.getOrderId())
//                            .userId(paymentOrder.getUserId())
//                            .operation(AuditOperation.REFUND_FAILED.getCode())
//                            .operationDesc("退款失败: " + failReason)
//                            .beforeStatus(beforeStatus)
//                            .afterStatus(RefundStatus.FAILED.getCode())
//                            .result(AuditResult.FAIL.getCode())
//                            .errorMsg(failReason)
//                            .refundRecordId(recordId)
//                            .log();
//            }
//        } catch (Exception e) {
//            log.warn("记录退款失败审计日志失败: recordId={}", recordId, e);
//        }
    }

    @Override
    public void updateNextQueryTime(Long recordId, Integer oldRetryCount, LocalDateTime nextQueryTime) {
        int updated = refundRecordMapper.updateNextQueryTime(recordId, oldRetryCount, nextQueryTime);
        if (updated != 1) {
            log.error("更新退款记录下次查询时间失败: recordId={}, oldRetryCount={}",
                    recordId, oldRetryCount);
            throw new BusinessException(ResultCode.OPTIMISTIC_LOCK_CONFLICT,
                    "退款记录更新失败，请稍后重试");
        }
        log.info("更新退款记录下次查询时间成功: recordId={}, oldRetryCount={}->{}, nextQueryTime={}",
                recordId, oldRetryCount, oldRetryCount + 1, nextQueryTime);
    }

    @Override
    public BigDecimal getTotalRefundAmount(String paymentId) {
        return refundRecordMapper.sumRefundAmountByPaymentId(paymentId);
    }

    @Override
    public boolean canRefund(String paymentId, BigDecimal refundAmount, BigDecimal orderTotalAmount) {
        BigDecimal totalRefunded = getTotalRefundAmount(paymentId);
        // 已退金额 + 本次退款金额 <= 订单总金额
        return totalRefunded.add(refundAmount).compareTo(orderTotalAmount) <= 0;
    }
}