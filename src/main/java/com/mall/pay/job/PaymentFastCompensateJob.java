package com.mall.pay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.common.BusinessException;
import com.mall.common.RedisKeys;
import com.mall.pay.entity.PaymentOrder;
import com.mall.pay.enums.PaymentStatus;
import com.mall.enums.ResultCode;
import com.mall.pay.mapper.PaymentOrderMapper;
import com.mall.pay.client.PayClient;
import com.mall.pay.config.PayClientFactory;
import com.mall.pay.dto.QueryOrderRequest;
import com.mall.pay.dto.QueryOrderResponse;
import com.mall.service.AlertService;
import com.mall.service.PaymentOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mall.common.RedisLockConfig.PAYMENT_QUERY_LEASE;
import static com.mall.common.RedisLockConfig.PAYMENT_QUERY_WAIT;

/**
 * 及时性补偿任务（与 PaymentCompensateJob 分离）。
 * <p>
 * <b>职责：</b>扫"WAITING 未超时 + 2 分钟没查"的支付单，主动查支付宝。
 * <ul>
 *   <li>发现 TRADE_SUCCESS → 补 SUCCESS（回调不可达场景）</li>
 *   <li>其他情况（用户没付）→ 跳过，不修改状态</li>
 * </ul>
 * <p>
 * <b>与 PaymentCompensateJob 的区别：</b>
 * <ul>
 *   <li>PaymentCompensateJob：扫"超时"，全处理（SUCCESS/FAILED）</li>
 *   <li>PaymentFastCompensateJob：扫"未超时"，只处理 SUCCESS</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFastCompensateJob {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PayClientFactory payClientFactory;
    private final RedissonClient redissonClient;
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final AlertService alertService;

    private static final int FAST_CHECK_INTERVAL_MINUTES = 2;
    private static final int BATCH_LIMIT = 200;

    /**
     * 每 60 秒扫描一次。
     * 实际每笔 WAITING 支付单 2 分钟查一次。
     */
    @Scheduled(fixedDelay = 60000)
    public void compensateFast() {
        List<PaymentOrder> orders = paymentOrderMapper.selectWaitingForFastCheck(BATCH_LIMIT);
        if (orders.isEmpty()) {
            return;
        }
        log.info("及时性补偿：扫描到 {} 笔 WAITING 待查", orders.size());

        for (PaymentOrder order : orders) {
            try {
                fastCheck(order);
            } catch (Exception e) {
                log.error("及时性补偿处理异常: paymentId={}", order.getPaymentId(), e);
            }
        }
    }

    private void fastCheck(PaymentOrder order) {
        String paymentId = order.getPaymentId();
        String lockKey = RedisKeys.PAYMENT_QUERY_LOCK + paymentId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(
                    PAYMENT_QUERY_WAIT.toMillis(),
                    PAYMENT_QUERY_LEASE.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!locked) {
                log.debug("及时性补偿：获取锁失败，跳过: paymentId={}", paymentId);
                return;
            }

            // 双重检查
            PaymentOrder latest = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getPaymentId, paymentId));
            if (latest == null || !PaymentStatus.WAITING.getCode().equals(latest.getStatus())) {
                return;   // 状态已变
            }
            if (latest.getExpiredAt() != null && latest.getExpiredAt().isBefore(LocalDateTime.now())) {
                return;   // 已超时 → 交给 PaymentCompensateJob
            }

            // ★ 更新 last_query_time（防重复查）
            paymentOrderMapper.update(null,
                    new LambdaUpdateWrapper<PaymentOrder>()
                            .set(PaymentOrder::getLastQueryTime, LocalDateTime.now())
                            .eq(PaymentOrder::getId, latest.getId()));

            // 查支付宝
            PayClient payClient = payClientFactory.getPayClient(latest.getPaymentMethod());
            QueryOrderResponse response;
            try {
                response = payClient.queryOrder(QueryOrderRequest.builder()
                        .paymentId(paymentId).build());
            } catch (BusinessException e) {
                // ★ FATAL（用户没付/参数错）—— 跳过，不告警
                if (ResultCode.THIRD_PARTY_FATAL_ERROR.equals(e.getResultCode())) {
                    log.debug("及时性补偿：FATAL（用户未付/参数错），跳过: paymentId={}", paymentId);
                    return;
                }
                // 其他异常（网络超时等）—— 计数告警
                log.info("及时性补偿：查询失败，跳过: paymentId={}, error={}", paymentId, e.getMessage());
                incrementFastAlert(paymentId, e.getMessage());
                return;
            } catch (Exception e) {
                log.error("及时性补偿：未知异常，跳过: paymentId={}", paymentId, e);
                incrementFastAlert(paymentId, e.getMessage());
                return;
            }

            String tradeState = response.getTradeState();
            String mapTradeStatus = payClient.mapTradeStatusAfterQueryOrderOnPaymentCompensateJob(tradeState);

            // ★ 只处理 SUCCESS（回调不可达场景）
            if ("SUCCESS".equals(mapTradeStatus)) {
                log.warn("及时性补偿：发现支付成功（回调丢失），补偿: paymentId={}", paymentId);
                paymentOrchestrationService.updatePaymentStatusToSuccessFromStatusOnTransactional(
                        paymentId, latest.getStatus(), response.getTransactionId());
            } else {
                log.debug("及时性补偿：状态 {}（用户未付），跳过: paymentId={}", mapTradeStatus, paymentId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("及时性补偿：获取锁被中断: paymentId={}", paymentId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    private void incrementFastAlert(String paymentId, String errorMsg) {
        String counterKey = "compensate:fast:error:" + paymentId;
        Long count = redisTemplate.opsForValue().increment(counterKey);
        redisTemplate.expire(counterKey, 24, TimeUnit.HOURS);
        if (count != null && count == 5) {
            alertService.sendUrgentAlert("及时性补偿查询异常需人工介入",
                    String.format("paymentId=%s 已连续查询失败 5 次, lastError=%s", paymentId, errorMsg));
        }
    }
}