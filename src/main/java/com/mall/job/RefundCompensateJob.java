package com.mall.job;

import com.mall.annotation.Log;
import com.mall.entity.PaymentRefundRecord;
import com.mall.mapper.PaymentRefundRecordMapper;
import com.mall.pay.config.PayProperties;
import com.mall.service.PaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统级退款补偿任务
 * <p>
 * 定时扫描长时间处于 PROCESSING 状态的退款记录，触发补偿查询。
 * 与即时补偿（scheduleRefundQuery）互补，兜底异常情况。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundCompensateJob {

    private final PaymentOrchestrationService paymentOrchestrationService;
    private final PaymentRefundRecordMapper refundRecordMapper;
    private final PayProperties properties;

    /**
     * 每 5 分钟执行一次，扫描超时未处理的退款记录
     */
    @Log("退款补偿任务")
    @Scheduled(fixedDelay = 300000)
    public void compensateRefund() {
        log.info("系统级退款补偿任务开始执行");

        // 查询超时未处理的退款记录（PROCESSING 且 next_query_time < NOW()）
        List<PaymentRefundRecord> records = refundRecordMapper.selectProcessingTimeout(properties.getRefund().getCompensateBatchSize());
        if (records.isEmpty()) {
            log.info("系统级退款补偿：无待处理退款记录");
            return;
        }

        log.info("系统级退款补偿：扫描到 {} 笔超时退款记录", records.size());

        for (PaymentRefundRecord record : records) {
            try {
                // 复用即时补偿的核心逻辑（包含锁、重试计数、第三方查询）
                paymentOrchestrationService.doRefundQuery(record.getId(), record.getPaymentId());
            } catch (Exception e) {
                log.error("处理退款补偿记录异常: recordId={}, paymentId={}",
                        record.getId(), record.getPaymentId(), e);
                // 继续处理下一条，不因单条失败而中断整个批次
            }
        }

        log.info("系统级退款补偿任务执行完成");
    }
}