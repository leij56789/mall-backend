package com.mall.pay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.entity.PaymentOrder;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.pay.facade.dto.PayCallbackDTO;
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
 * 支付结果通知业务侧的调度器。
 * <p>
 * 重试策略：最多 3 次，每次间隔 30 秒；3 次后告警 + 标记 FAILED（人工介入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyRetryScheduler {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentApplicationPort paymentApplicationPort;
    private final AlertPort alertPort;

    private static final int MAX_RETRY = 3;
    private static final int RETRY_INTERVAL_SECONDS = 30;

    /** 立即通知（异步，支付成功后调用） */
    @Async("notify")
    public void notifyImmediately(String paymentId) {
        notifyOne(paymentId);
    }

    /** 定时扫描（每 30 秒） */
    @Scheduled(fixedDelay = 30000)
    public void scanAndRetry() {
        List<PaymentOrder> pending = paymentOrderMapper.selectList(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getNotifyStatus, "PENDING")
                        .le(PaymentOrder::getNotifyNextTime, LocalDateTime.now())
                        .orderByAsc(PaymentOrder::getNotifyNextTime)
                        .last("LIMIT 100")
        );

        if (pending.isEmpty()) return;

        log.info("NotifyRetryScheduler: 扫描到 {} 条待通知", pending.size());
        for (PaymentOrder po : pending) {
            notifyOne(po.getPaymentId());
        }
    }

    /** 通知单条 */
    private void notifyOne(String paymentId) {
        // 1. CAS 抢占（避免并发重复通知）
        int locked = paymentOrderMapper.update(null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .set(PaymentOrder::getNotifyStatus, "PROCESSING")
                        .eq(PaymentOrder::getPaymentId, paymentId)
                        .eq(PaymentOrder::getNotifyStatus, "PENDING"));

        if (locked != 1) return;   // 已被其他线程处理

        // 2. 查支付单
        PaymentOrder po = paymentOrderMapper.selectByPaymentIdForUpdate(paymentId);
        if (po == null) {
            log.error("通知失败：支付单不存在，paymentId={}", paymentId);
            return;
        }

        // 3. 构建 DTO
        String bizId = po.getBizId() != null ? po.getBizId() : String.valueOf(po.getOrderId());
        String bizType = po.getBizType() != null ? po.getBizType() : "ORDER";

        PayCallbackDTO dto = PayCallbackDTO.builder()
                .paymentId(paymentId)
                .bizType(bizType)
                .bizId(bizId)
                .amount(po.getAmount())
                .paymentMethod(po.getPaymentMethod())
                .thirdPartyTradeNo(po.getThirdPartyTradeNo())
                .status(po.getStatus())
                .callbackTime(LocalDateTime.now())
                .build();

        // 4. 通知业务侧
        try {
            if ("SUCCESS".equals(po.getStatus())) {
                paymentApplicationPort.onSuccess(dto);
            } else if ("FAILED".equals(po.getStatus())) {
                paymentApplicationPort.onFailed(dto);
            } else if ("CLOSED".equals(po.getStatus())) {
                paymentApplicationPort.onExpired(dto);
            }

            // 5. 标记成功
            paymentOrderMapper.update(null,
                    new LambdaUpdateWrapper<PaymentOrder>()
                            .set(PaymentOrder::getNotifyStatus, "SUCCESS")
                            .eq(PaymentOrder::getPaymentId, paymentId));

            log.info("通知业务侧成功: paymentId={}", paymentId);

        } catch (Exception e) {
            // 6. 失败：递增重试次数
            int retryCount = po.getNotifyRetryCount() == null ? 0 : po.getNotifyRetryCount();
            retryCount++;

            if (retryCount >= MAX_RETRY) {
                // 达到最大重试次数：告警 + 标记 FAILED
                log.error("通知业务侧失败 {} 次，放弃并告警: paymentId={}", retryCount, paymentId, e);

                paymentOrderMapper.update(null,
                        new LambdaUpdateWrapper<PaymentOrder>()
                                .set(PaymentOrder::getNotifyStatus, "FAILED")
                                .set(PaymentOrder::getNotifyRetryCount, retryCount)
                                .eq(PaymentOrder::getPaymentId, paymentId));

                alertPort.critical(
                        "支付成功但通知业务侧失败，需人工介入",
                        String.format("paymentId=%s, 重试次数=%d, 最后异常=%s",
                                paymentId, retryCount, e.getMessage()),
                        e
                );
                return;
            }

            // 固定 30 秒后重试
            LocalDateTime nextTime = LocalDateTime.now().plusSeconds(RETRY_INTERVAL_SECONDS);
            paymentOrderMapper.update(null,
                    new LambdaUpdateWrapper<PaymentOrder>()
                            .set(PaymentOrder::getNotifyStatus, "PENDING")
                            .set(PaymentOrder::getNotifyRetryCount, retryCount)
                            .set(PaymentOrder::getNotifyNextTime, nextTime)
                            .eq(PaymentOrder::getPaymentId, paymentId));

            log.warn("通知业务侧失败，第 {} 次重试将于 {} 执行: paymentId={}",
                    retryCount, nextTime, paymentId, e);
        }
    }
}