package com.mall.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.alert.AlertEvent;
import com.mall.common.BusinessException;
import com.mall.common.RedisKeys;
import com.mall.common.RedisLockConfig;
import com.mall.entity.PaymentOrder;
import com.mall.enums.PaymentStatus;
import com.mall.enums.ResultCode;
import com.mall.mapper.PaymentOrderMapper;
import com.mall.pay.client.PayClient;
import com.mall.pay.config.PayClientFactory;
import com.mall.pay.dto.QueryOrderRequest;
import com.mall.pay.dto.QueryOrderResponse;
import com.mall.pay.state.PaymentStateMachine;
import com.mall.service.AlertService;
import com.mall.service.PaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncQueryService {
    private final PayClientFactory payClientFactory;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentStateMachine paymentStateMachine;
    private final RedissonClient redissonClient;
    private final TaskScheduler taskScheduler;
    private final AlertService alertService;
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 延迟查询并推进状态（带分布式锁）
     */
//    @Async("payQueryExecutor")
    public void scheduleQuery(String paymentId, long delay, TimeUnit unit,int maxRetryCount) {
        taskScheduler.schedule(
                () -> doQueryAndTransitionWithLock(paymentId, maxRetryCount),
                Instant.now().plusMillis(unit.toMillis(delay))
        );
        log.info("调度即时补偿: paymentId={}, delay={}ms", paymentId, unit.toMillis(delay));
    }

    /**
     * 带分布式锁的查询与状态转换
     */
    private void doQueryAndTransitionWithLock(String paymentId,int maxRetryCount) {
        String lockKey = RedisKeys.PAYMENT_QUERY_LOCK + paymentId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 尝试获取锁，等待 3 秒，持有 10 秒后自动释放（防止死锁）
            boolean locked = lock.tryLock(RedisLockConfig.PAYMENT_QUERY_WAIT.toMillis(), 15, TimeUnit.MILLISECONDS);
            if (!locked) {
                log.warn("获取支付单查询锁失败，可能正在被其他任务处理: paymentId={}", paymentId);
                return;
            }

            // 双重检查：再次确认状态是否为 PENDING_CONFIRM
            PaymentOrder payment = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getPaymentId, paymentId));
            if (payment == null) {
                log.warn("支付单不存在，跳过查询: paymentId={}", paymentId);
                return;
            }
            if (!PaymentStatus.PENDING_CONFIRM.getCode().equals(payment.getStatus())) {
                log.info("支付单状态已变更，跳过查询: paymentId={}, currentStatus={}",
                        paymentId, payment.getStatus());
                return;
            }

            //检查并更新重试次数
            int currentRetry = payment.getRetryCount() == null ? 0 : payment.getRetryCount();

            if (currentRetry >= maxRetryCount) {
                // 重试次数耗尽：转为系统级补偿接管（保持 PENDING_CONFIRM，让 5 分钟定时任务去查）
                if(payment.getExpiredAt().isBefore(LocalDateTime.now())){
                    //支付的及时反馈要求高，这里不等系统几补偿任务,AI提议
                    paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(payment,PaymentStatus.PENDING_CONFIRM.getCode());
                    log.warn("支付单超时，不再重试，按失败处理，不等待系统级补偿任务：paymentId={}",paymentId);
                    return;
                }
                log.warn("即时重试耗尽，移交系统补偿任务: paymentId={}", paymentId);
                return;
            }



            // 调用第三方查询接口
            QueryOrderRequest request = QueryOrderRequest.builder()
                    .paymentId(paymentId)
                    .build();
            QueryOrderResponse response;
            PayClient payClient = payClientFactory.getPayClient(payment.getPaymentMethod());
            try {
                response = payClient.queryOrder(request);
            } catch (Exception e) {
                // 1. 解析异常类型，获取错误码和是否可重试
                ResultCode errorCode = extractResultCodeFromException(e);
                boolean isRetryable = isRetryableException(e,paymentId);
                int nextRetry = currentRetry + 1;

                // 2. 乐观锁更新重试次数（原子操作）
                int updated = paymentOrderMapper.update(
                        new LambdaUpdateWrapper<PaymentOrder>()
                                .set(PaymentOrder::getRetryCount, nextRetry)
                                .eq(PaymentOrder::getRetryCount, currentRetry)
                                .eq(PaymentOrder::getPaymentId, paymentId)
                );
                if (updated != 1) {
                    log.error("更新重试次数并发冲突，可能被其他线程抢先更新: paymentId={}, expectedRetry={}",
                            paymentId, currentRetry);
                    // 放弃本次，让系统补偿兜底（或依赖下次重试）
                    return;
                }

                // 3. 根据异常类型和重试次数决策
                if (isRetryable && nextRetry < maxRetryCount) {
                    // 释放锁（仅当当前线程持有锁时）
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                    // 可重试且未达上限：调度下次延迟查询
                    scheduleQuery(paymentId, getRetryDelay(nextRetry), TimeUnit.SECONDS, maxRetryCount);
                    log.warn("查询异常，调度第{}次重试: paymentId={}, errorCode={}, delay={}s",
                            nextRetry, paymentId, errorCode, getRetryDelay(nextRetry));
                    return;
                } else if (ResultCode.THIRD_PARTY_MANUAL_INTERVENTION.equals(errorCode)) {
                    // 需人工介入：保持 PENDING_CONFIRM，发布告警事件（不调度）
                    eventPublisher.publishEvent(new AlertEvent(
                            this,
                            paymentId,
                            String.valueOf(payment.getOrderId()),
                            errorCode.getCode().toString(),
                            "支付宝查询返回需人工介入错误",
                            "PAYMENT_MANUAL_INTERVENTION"
                    ));
                    log.warn("查询返回需人工介入错误，保持状态等待处理: paymentId={}, errorCode={}", paymentId, errorCode);
                } else if (ResultCode.THIRD_PARTY_FATAL_ERROR.equals(errorCode)) {
                    // 不可恢复错误（如参数错误）：直接转为 FAILED
                    paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(payment,PaymentStatus.PENDING_CONFIRM.getCode());
                    log.warn("查询返回不可恢复错误，支付单已关闭: paymentId={}, errorCode={}", paymentId, errorCode);
                } else {
                    // 其他情况（重试耗尽、未知错误）：保持 PENDING_CONFIRM，移交系统补偿
                    log.warn("查询异常且无法继续重试，移交系统补偿: paymentId={}, retryCount={}, errorCode={}",
                            paymentId, nextRetry, errorCode);
                    // 不调度，等待 5 分钟系统补偿任务
                }
                return;
            }
            // 驱动状态机转换
            try {
                paymentStateMachine.transitionFromPendingConfirm(payment, response,payClient);
                log.info("即时补偿完成: paymentId={}, newStatus={}", paymentId, payment.getStatus());
            } catch (Exception e) {
                log.warn("状态转换异常，等待定时任务重试: paymentId={}", paymentId, e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断: paymentId={}", paymentId);
        } finally {
            // 释放锁（仅当当前线程持有锁时）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    private int getRetryDelay(int retryCount) {
        // 第1次重试 10s，第2次 30s，第3次 60s（可配置）
        if (retryCount <= 1) return 10;
        if (retryCount == 2) return 30;
        return 60;
    }
    private ResultCode extractResultCodeFromException(Exception e) {
        if (e instanceof BusinessException) {
            ResultCode code = ((BusinessException) e).getResultCode();
            return code!=null?code:ResultCode.THIRD_PARTY_UNKNOWN_ERROR;
        }
        return ResultCode.THIRD_PARTY_UNKNOWN_ERROR;
        // 非 BusinessException（如 NPE、其他系统异常）统一按未知错误处理
    }
    private boolean isRetryableException(Exception e,String paymentId) {
        // 1. 如果是 BusinessException，根据 ResultCode 判断
        if (e instanceof BusinessException) {
            ResultCode code = ((BusinessException) e).getResultCode();
            return ResultCode.THIRD_PARTY_TIMEOUT.equals(code)
                    || ResultCode.THIRD_PARTY_RETRYABLE_ERROR.equals(code);
        }

        // 2. 网络超时/连接异常（支付宝 SDK 抛出的 AlipayApiException 的 cause）
        if (e.getCause() instanceof SocketTimeoutException
                || e.getCause() instanceof ConnectException) {
            return true;
        }

        // 3. 其他未知异常：保守处理，也视为可重试（但受次数上限控制）
        log.warn("遇到未知异常，按可重试处理: paymentId={}", paymentId, e);
        return true;
    }
}