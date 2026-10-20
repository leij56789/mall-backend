package com.mall.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.annotation.AuditLog;
import com.mall.common.BusinessException;
import com.mall.common.RedisKeys;
import com.mall.enums.ResultCode;
import com.mall.pay.client.RefundQueryClient;
import com.mall.pay.config.PayClientFactory;
import com.mall.pay.config.PayProperties;
import com.mall.pay.dto.RefundQueryRequest;
import com.mall.pay.dto.RefundQueryResponse;
import com.mall.pay.entity.PaymentOrder;
import com.mall.pay.entity.PaymentRefundRecord;
import com.mall.pay.enums.AuditTargetType;
import com.mall.pay.enums.PaymentStatus;
import com.mall.pay.enums.RefundStatus;
import com.mall.pay.facade.impl.PaymentOrderLifecycle;
import com.mall.pay.mapper.PaymentOrderMapper;
import com.mall.pay.mapper.PaymentRefundRecordMapper;
import com.mall.pay.port.AlertPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 退款查询补偿处理器。
 * <p>
 * 从 PaymentOrchestrationService 搬出，可插拔：不依赖 mall 业务服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundQueryProcessor {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentRefundRecordMapper paymentRefundRecordMapper;
    private final PayClientFactory payClientFactory;
    private final PayProperties payProperties;
    private final RedissonClient redissonClient;
    private final AlertPort alertPort;
    private final TaskScheduler taskScheduler;
    private final PaymentOrderLifecycle paymentOrderLifecycle;

    @Lazy
    @Autowired
    private RefundQueryProcessor self;

    @Async("taskExecutor")
    public void doRefundQuery(Long recordId, String paymentId) {
        if (paymentId == null || recordId == null) {
            log.error("退款查询参数不完整: paymentId={}, recordId={}", paymentId, recordId);
            throw new BusinessException(ResultCode.PARAM_MISSING, "退款查询参数不完整");
        }
        String lockKey = RedisKeys.PAYMENT_QUERY_LOCK + paymentId;
        RLock lock = redissonClient.getLock(lockKey);

        // ===== 阶段一：第一次抢锁（数据库准备） =====
        PaymentRefundRecord record = null;
        int oldRetryCount = 0;
        boolean needQuery = false;
        RefundQueryClient refundQueryClient = null;

        try {
            boolean locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("退款补偿查询获取锁失败，跳过本次: recordId={}, paymentId={}", recordId, paymentId);
                return;
            }

            record = paymentRefundRecordMapper.selectById(recordId);
            if (record == null) {
                log.error("退款记录不存在: recordId={}", recordId);
                return;
            }

            if (!RefundStatus.PROCESSING.getCode().equals(record.getStatus())) {
                log.info("退款记录已终态，跳过查询: recordId={}, status={}", recordId, record.getStatus());
                return;
            }

            oldRetryCount = record.getRetryCount() == null ? 0 : record.getRetryCount();
            int maxRetry = payProperties.getRefund().getMaxQueryRetry();
            if (oldRetryCount >= maxRetry) {
                log.warn("退款查询重试次数已达上限，转失败: recordId={}, retryCount={}", recordId, oldRetryCount);
                self.markFailed(recordId, "重试次数上限，需人工核实");
                alertPort.critical("退款查询重试次数耗尽",
                        String.format("recordId=%s, paymentId=%s", recordId, paymentId), null);
                return;
            }
            PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getPaymentId, paymentId));
            if (paymentOrder == null || paymentOrder.getPaymentMethod() == null) {
                log.error("退款参数不完整: paymentOrder={}, refundRecord={}, paymentMethod={}",
                        paymentOrder != null ? paymentOrder.getPaymentId() : "null",
                        paymentOrder != null ? paymentOrder.getPaymentMethod() : "null");
                throw new BusinessException(ResultCode.PARAM_MISSING, "退款参数不完整，请检查支付单和退款记录");
            }
            if (!payClientFactory.supportsRefundQuery(paymentOrder.getPaymentMethod())) {
                log.warn("支付方式不支持退款查询: paymentMethod={}, recordId={}",
                        paymentOrder.getPaymentMethod(), recordId);
                self.markFailed(recordId, "该支付方式不支持退款查询，请联系客服");
                alertPort.critical("退款查询不支持",
                        String.format("recordId=%s, paymentMethod=%s", recordId, paymentOrder.getPaymentMethod()), null);
                return;
            }

            refundQueryClient = payClientFactory.getRefundQueryClient(paymentOrder.getPaymentMethod());
            needQuery = true;
            log.info("执行退款补偿查询: recordId={}, currentRetry={}", recordId, oldRetryCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("退款补偿查询第一次锁被中断: recordId={}", recordId);
            return;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("第一次锁已释放，准备调用第三方接口: recordId={}", recordId);
            }
        }

        // ===== 阶段二：锁外调用第三方接口（无锁，网络IO） =====
        RefundQueryResponse queryResp = null;
        boolean querySuccess = false;
        try {
            if (!needQuery || record == null) {
                return;
            }
            RefundQueryRequest queryRequest = RefundQueryRequest.builder()
                    .outRequestNo(record.getOutRequestNo())
                    .outTradeNo(record.getOutTradeNo())
                    .build();
            queryResp = refundQueryClient.query(queryRequest);
            querySuccess = true;
        } catch (Exception e) {
            log.error("退款补偿查询第三方接口异常: recordId={}", recordId, e);
        }

        // ===== 阶段三：第二次抢锁（更新数据库结果） =====
        try {
            boolean locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("退款补偿查询第二次获取锁失败，跳过更新: recordId={}, paymentId={}", recordId, paymentId);
                return;
            }

            PaymentRefundRecord latestRecord = paymentRefundRecordMapper.selectById(recordId);
            if (latestRecord == null) {
                log.error("退款记录不存在: recordId={}", recordId);
                return;
            }
            if (!RefundStatus.PROCESSING.getCode().equals(latestRecord.getStatus())) {
                log.info("退款记录状态已变更，跳过更新: recordId={}, status={}", recordId, latestRecord.getStatus());
                return;
            }
            int currentRetry = latestRecord.getRetryCount() == null ? 0 : latestRecord.getRetryCount();
            if (currentRetry != oldRetryCount) {
                log.warn("重试次数已变更，跳过本次更新: recordId={}, expected={}, actual={}",
                        recordId, oldRetryCount, currentRetry);
                return;
            }

            int maxRetry = payProperties.getRefund().getMaxQueryRetry();

            if (!querySuccess || queryResp == null) {
                self.handleQueryException(latestRecord, oldRetryCount, maxRetry, recordId, paymentId);
                return;
            }

            if (queryResp.isSuccess()) {
                self.handleQuerySuccess(latestRecord, queryResp, recordId);
            } else if (queryResp.isProcessing()) {
                handleQueryProcessing(latestRecord, oldRetryCount, maxRetry, recordId, paymentId);
            } else {
                handleQueryFailed(latestRecord, queryResp, recordId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("退款补偿查询第二次锁被中断: recordId={}", recordId);
        } catch (Exception e) {
            log.error("退款补偿查询更新数据异常: recordId={}", recordId, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("第二次锁已释放: recordId={}", recordId);
            }
        }
    }

    @Transactional
    public void handleQueryException(PaymentRefundRecord record, int oldRetryCount,
                                      int maxRetry, Long recordId, String paymentId) {
        int nextRetry = oldRetryCount + 1;
        if (nextRetry >= maxRetry) {
            self.markFailed(recordId, "查询异常，重试次数耗尽");
            alertPort.critical("退款查询异常",
                    String.format("recordId=%s, paymentId=%s", recordId, paymentId), null);
            log.warn("退款查询异常，重试次数耗尽: recordId={}", recordId);
        } else {
            LocalDateTime nextTime = LocalDateTime.now().plusSeconds(payProperties.getRefund().getQueryIntervalSeconds());
            updateNextQueryTime(recordId, oldRetryCount, nextTime);
        }
    }

    @Transactional
    public void handleQuerySuccess(PaymentRefundRecord record, RefundQueryResponse queryResp, Long recordId) {
        log.info("补偿查询发现退款成功: recordId={}", recordId);
        self.markSuccess(recordId, queryResp.getTradeNo());

        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getPaymentId, record.getPaymentId()));
        if (paymentOrder != null) {
            paymentOrderLifecycle.updateRefundSuccess(paymentOrder, new BigDecimal(queryResp.getRefundAmount()));
        }
    }

    private void handleQueryProcessing(PaymentRefundRecord record, int oldRetryCount,
                                       int maxRetry, Long recordId, String paymentId) {
        int nextRetry = oldRetryCount + 1;
        if (nextRetry >= maxRetry) {
            // ★ 不覆盖 failReason，只改 status
            self.markFailed(recordId, null);   // ← 传 null，updateFailed 保留原 failReason
            alertPort.critical("退款查询重试次数耗尽",
                    String.format("recordId=%s, paymentId=%s", recordId, paymentId), null);
            log.warn("退款仍处理中，但重试次数已达上限: recordId={}", recordId);
        } else {
            LocalDateTime nextTime = LocalDateTime.now().plusMinutes(5);
            updateNextQueryTime(recordId, oldRetryCount, nextTime);
            self.scheduleRefundQuery(recordId, paymentId);
        }
    }

    private void handleQueryFailed(PaymentRefundRecord record, RefundQueryResponse queryResp, Long recordId) {
        log.error("补偿查询失败: recordId={}, failReason={}", recordId, queryResp.getFailReason());
        self.markFailed(recordId, queryResp.getFailReason());
    }



    public void scheduleRefundQuery(Long recordId, String paymentId) {
        log.info("触发退款查询补偿: recordId={}", recordId);
        taskScheduler.schedule(
                () -> doRefundQuery(recordId, paymentId),
                Instant.now().plusSeconds(payProperties.getRefund().getQueryIntervalSeconds())
        );
    }

    @AuditLog(
            targetTypes = {AuditTargetType.REFUND_RECORD},
            refundRecordId = "recordId",
            desc = "退款成功（补偿查询）"
    )
    public void markSuccess(Long recordId, String thirdPartyRefundNo) {
        int updated = paymentRefundRecordMapper.updateSuccess(recordId, thirdPartyRefundNo);
        if (updated != 1) {
            log.error("退款记录标记成功失败: recordId={}, 可能状态已变更", recordId);
            throw new BusinessException(ResultCode.OPTIMISTIC_LOCK_CONFLICT, "退款记录更新失败，请稍后重试");
        }
    }

    @AuditLog(
            targetTypes = {AuditTargetType.REFUND_RECORD},
            refundRecordId = "recordId",
            desc = "退款失败（补偿查询）"
    )
    public void markFailed(Long recordId, String failReason) {
        int updated = paymentRefundRecordMapper.updateFailed(recordId, failReason);
        if (updated != 1) {
            log.error("退款记录标记失败: recordId={}, 可能状态已变更", recordId);
            throw new BusinessException(ResultCode.OPTIMISTIC_LOCK_CONFLICT, "退款记录更新失败，请稍后重试");
        }
    }

    private void updateNextQueryTime(Long recordId, Integer oldRetryCount, LocalDateTime nextQueryTime) {
        int updated = paymentRefundRecordMapper.updateNextQueryTime(recordId, oldRetryCount, nextQueryTime);
        if (updated != 1) {
            log.error("更新退款记录下次查询时间失败: recordId={}, oldRetryCount={}", recordId, oldRetryCount);
            throw new BusinessException(ResultCode.OPTIMISTIC_LOCK_CONFLICT, "退款记录更新失败，请稍后重试");
        }
    }


}