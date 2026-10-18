package com.mall.pay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.pay.entity.PaymentRefundNotifyLog;
import com.mall.pay.entity.PaymentRefundRecord;
import com.mall.pay.mapper.PaymentRefundNotifyLogMapper;
import com.mall.pay.mapper.PaymentRefundRecordMapper;
import com.mall.pay.facade.dto.RefundCallbackDTO;
import com.mall.pay.port.AlertPort;
import com.mall.pay.port.PaymentApplicationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款结果通知业务侧的调度器。
 * <p>
 * 重试策略：最多 3 次，每次间隔 30 秒；3 次后告警 + 标记 FAILED。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundNotifyScheduler {

    private final PaymentRefundNotifyLogMapper notifyLogMapper;
    private final PaymentRefundRecordMapper refundRecordMapper;
    private final PaymentApplicationPort paymentApplicationPort;
    private final AlertPort alertPort;

    private static final int MAX_RETRY = 3;
    private static final int RETRY_INTERVAL_SECONDS = 30;

    /** 立即通知（异步） */
    @Async("notify")
    public void notifyImmediately(Long notifyLogId) {
        notifyOne(notifyLogId);
    }

    /** 定时扫描（每 30 秒） */
    @Scheduled(fixedDelay = 30000)
    public void scanAndRetry() {
        List<PaymentRefundNotifyLog> pending = notifyLogMapper.selectList(
                new LambdaQueryWrapper<PaymentRefundNotifyLog>()
                        .eq(PaymentRefundNotifyLog::getStatus, "PENDING")
                        .le(PaymentRefundNotifyLog::getNextNotifyTime, LocalDateTime.now())
                        .orderByAsc(PaymentRefundNotifyLog::getNextNotifyTime)
                        .last("LIMIT 100")
        );
        if (pending.isEmpty()) return;

        log.info("RefundNotifyScheduler: 扫描到 {} 条待通知", pending.size());
        for (PaymentRefundNotifyLog log : pending) {
            notifyOne(log.getId());
        }
    }

    /** 通知单条 */
    private void notifyOne(Long notifyLogId) {
        // CAS 抢占
        int locked = notifyLogMapper.update(null,
                new LambdaUpdateWrapper<PaymentRefundNotifyLog>()
                        .set(PaymentRefundNotifyLog::getStatus, "PROCESSING")
                        .eq(PaymentRefundNotifyLog::getId, notifyLogId)
                        .eq(PaymentRefundNotifyLog::getStatus, "PENDING"));
        if (locked != 1) return;

        // 查通知日志 + 退款记录
        PaymentRefundNotifyLog notifyLog = notifyLogMapper.selectById(notifyLogId);
        if (notifyLog == null) return;
        PaymentRefundRecord refundRecord = refundRecordMapper.selectById(notifyLog.getRefundId());
        if (refundRecord == null) {
            log.error("退款记录不存在: refundId={}", notifyLog.getRefundId());
            notifyLogMapper.update(null,
                    new LambdaUpdateWrapper<PaymentRefundNotifyLog>()
                            .set(PaymentRefundNotifyLog::getStatus, "FAILED")
                            .set(PaymentRefundNotifyLog::getLastError, "退款记录不存在")
                            .eq(PaymentRefundNotifyLog::getId, notifyLogId));
            return;
        }

        // 构建 DTO
        RefundCallbackDTO dto = RefundCallbackDTO.builder()
                .refundId(refundRecord.getId())
                .paymentId(refundRecord.getPaymentId())
                .outRequestNo(refundRecord.getOutRequestNo())
                .refundAmount(refundRecord.getRefundAmount())
                .status(refundRecord.getStatus())
                .failReason(refundRecord.getFailReason())
                .thirdPartyRefundNo(refundRecord.getThirdPartyRefundNo())
                .callbackTime(LocalDateTime.now())
                .build();

        try {
            if ("SUCCESS".equals(notifyLog.getNotifyType())) {
                paymentApplicationPort.onRefundSuccess(dto);
            } else {
                paymentApplicationPort.onRefundFailed(dto);
            }

            notifyLogMapper.update(null,
                    new LambdaUpdateWrapper<PaymentRefundNotifyLog>()
                            .set(PaymentRefundNotifyLog::getStatus, "SUCCESS")
                            .set(PaymentRefundNotifyLog::getNextNotifyTime, null)   // ★ 新增
                            .eq(PaymentRefundNotifyLog::getId, notifyLogId));
            log.info("退款通知业务侧成功: outRequestNo={}, type={}",
                    notifyLog.getOutRequestNo(), notifyLog.getNotifyType());

        } catch (Exception e) {
            int retryCount = notifyLog.getRetryCount() == null ? 0 : notifyLog.getRetryCount();
            retryCount++;

            if (retryCount >= MAX_RETRY) {
                log.error("退款通知失败 {} 次，放弃并告警: outRequestNo={}",
                        retryCount, notifyLog.getOutRequestNo(), e);
                notifyLogMapper.update(null,
                        new LambdaUpdateWrapper<PaymentRefundNotifyLog>()
                                .set(PaymentRefundNotifyLog::getStatus, "FAILED")
                                .set(PaymentRefundNotifyLog::getRetryCount, retryCount)
                                .set(PaymentRefundNotifyLog::getLastError, truncate(e.getMessage(), 500))
                                .eq(PaymentRefundNotifyLog::getId, notifyLogId));
                alertPort.critical(
                        "退款成功但通知业务侧失败，需人工介入",
                        String.format("outRequestNo=%s, paymentId=%s, 重试次数=%d",
                                notifyLog.getOutRequestNo(), notifyLog.getPaymentId(), retryCount),
                        e);
                return;
            }

            LocalDateTime nextTime = LocalDateTime.now().plusSeconds(RETRY_INTERVAL_SECONDS);
            notifyLogMapper.update(null,
                    new LambdaUpdateWrapper<PaymentRefundNotifyLog>()
                            .set(PaymentRefundNotifyLog::getStatus, "PENDING")
                            .set(PaymentRefundNotifyLog::getRetryCount, retryCount)
                            .set(PaymentRefundNotifyLog::getNextNotifyTime, nextTime)
                            .set(PaymentRefundNotifyLog::getLastError, truncate(e.getMessage(), 500))
                            .eq(PaymentRefundNotifyLog::getId, notifyLogId));

            log.warn("退款通知失败，第 {} 次重试将于 {}: outRequestNo={}",
                    retryCount, nextTime, notifyLog.getOutRequestNo(), e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}