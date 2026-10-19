package com.mall.pay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.annotation.Log;
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
import com.mall.pay.port.AlertPort;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mall.common.RedisLockConfig.PAYMENT_QUERY_LEASE;
import static com.mall.common.RedisLockConfig.PAYMENT_QUERY_WAIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompensateJob {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PayClientFactory payClientFactory;
    private final AlertPort alertPort;
    private final RedissonClient redissonClient;
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 每 5 分钟执行一次，扫描所有超时（expire_at < now）且非终态的支付单
     */
    @Log("支付单补偿任务")
    @Scheduled(fixedDelay = 30000)
    public void compensateTimeoutOrders() {
        log.info("系统级补偿任务开始执行");

        // 最推荐：直接硬编码
        List<String> statusList = Arrays.asList("INIT", "WAITING", "PENDING_CONFIRM");
        List<PaymentOrder> orders = paymentOrderMapper.selectTimeoutNonFinal(statusList,1000);
        if (orders.isEmpty()) {
            log.info("系统级补偿：无超时待处理支付单");
            return;
        }

        log.info("系统级补偿：扫描到 {} 笔超时支付单", orders.size());

        // ========== 新增：聚合统计容器 ==========
        List<String> failedPaymentIds = new ArrayList<>();
        List<String> alertPaymentIds = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (PaymentOrder order : orders) {
            try {
                boolean processed = processSingleOrder(order);
                if (processed) {
                    successCount++;
                } else {
                    failCount++;
                    // 记录失败的关键信息，用于告警摘要
                    failedPaymentIds.add(order.getPaymentId());
                }
            } catch (Exception e) {
                // 兜底异常，避免单笔失败影响整批
                log.error("处理支付单异常, paymentId={}", order.getPaymentId(), e);
                failCount++;
                alertPaymentIds.add(order.getPaymentId() + "(异常:" + e.getMessage() + ")");
            }
        }

        // ========== 聚合告警（替代循环内的即时告警） ==========
        if (failCount > 0 || !alertPaymentIds.isEmpty()) {
            String summary = String.format(
                    "系统补偿任务执行完毕 | 总数:%d | 成功:%d | 失败:%d | 失败ID示例:%s",
                    orders.size(), successCount, failCount,
                    failedPaymentIds.size() > 3 ? failedPaymentIds.subList(0, 3) + "..." : failedPaymentIds
            );
            alertPort.urgent("系统补偿任务异常汇总", summary);
        }

        log.info("系统级补偿任务执行完成: 成功{}笔, 失败{}笔", successCount, failCount);
    }

    /**
     * 处理单笔支付单（带分布式锁）
     */
    private boolean processSingleOrder(PaymentOrder order) {
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
                log.warn("获取支付补偿锁失败，跳过: paymentId={}", paymentId);
                return true;
            }

            // 双重检查
            PaymentOrder latest = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getPaymentId, paymentId));
            if (latest == null) {
                return true;
            }
            // 再次确认超时
            if (latest.getExpiredAt() == null || latest.getExpiredAt().isAfter(LocalDateTime.now())) {
                log.info("支付单已不超时，跳过: paymentId={}", paymentId);
                return true;
            }
            String status = latest.getStatus();
            if (!PaymentStatus.INIT.getCode().equals(status)
                    && !PaymentStatus.WAITING.getCode().equals(status)
                    && !PaymentStatus.PENDING_CONFIRM.getCode().equals(status)) {
                log.info("支付单已终态，跳过: paymentId={}", paymentId);
                return true;
            }

            // 核心处理
            return doCompensate(latest);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断: paymentId={}", paymentId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return true;
    }
    /**
     * 核心补偿逻辑：查询第三方 + 决策，更新操作单独提交事务
     */
    public boolean doCompensate(PaymentOrder payment) {
        String paymentId = payment.getPaymentId();

        // ========== 1. 查询第三方 ==========
        PayClient payClient = payClientFactory.getPayClient(payment.getPaymentMethod());
        QueryOrderResponse response;
        try {
            response = payClient.queryOrder(QueryOrderRequest.builder()
                                                             .paymentId(paymentId).build());
        } catch (BusinessException e) {
            // ★ FATAL 错误（交易不存在、参数错等）—— 直接标 FAILED，不计数
            if (ResultCode.THIRD_PARTY_FATAL_ERROR.equals(e.getResultCode())) {
                log.warn("兜底补偿确认 FATAL 错误（交易不存在/参数错），标记 FAILED: paymentId={}, msg={}",
                        paymentId, e.getMessage());
                paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(
                        payment, payment.getStatus());
                return true;
            }
            // 其他异常（网络超时等）—— 计数告警
            log.error("系统补偿查询第三方失败，保留 PENDING_CONFIRM: paymentId={}", paymentId, e);
            incrementAndAlert(paymentId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("系统补偿查询未知异常，保留 PENDING_CONFIRM: paymentId={}", paymentId, e);
            incrementAndAlert(paymentId, e.getMessage());
            return false;
        }

        // ========== 2. 根据第三方结果决定目标状态 ==========
        String tradeState = response.getTradeState();
        String transactionId = response.getTransactionId();
        String mapTradeStatus = payClient.mapTradeStatusAfterQueryOrderOnPaymentCompensateJob(tradeState);

        // 只允许非终态更新
        if (!PaymentStatus.INIT.getCode().equals(payment.getStatus())
                && !PaymentStatus.WAITING.getCode().equals(payment.getStatus())
                && !PaymentStatus.PENDING_CONFIRM.getCode().equals(payment.getStatus())) {
            log.info("支付单已终态，跳过更新: paymentId={}, currentStatus={}", paymentId, payment.getStatus());
            return true;
        }

        switch (mapTradeStatus) {
            case "SUCCESS":
                paymentOrchestrationService.updatePaymentStatusToSuccessFromStatusOnTransactional(
                        paymentId, payment.getStatus(), transactionId);
                log.info("{} → SUCCESS (via query): paymentId={}", payment.getStatus(), paymentId);
                break;
            case "FAILED":
                paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(
                        payment, payment.getStatus());
                log.info("{} → FAILED (via query): paymentId={}", payment.getStatus(), paymentId);
                break;
            default:
                paymentOrchestrationService.updatePaymentStatusToFailedFromStatusOnTransactional(
                        payment, payment.getStatus());
                log.info("{} → FAILED (via query): paymentId={}", payment.getStatus(), paymentId);
                return true;
        }
        return true;
    }

    private void incrementAndAlert(String paymentId, String errorMsg) {
        String counterKey = "compensate:query:error:" + paymentId;
        Long count = redisTemplate.opsForValue().increment(counterKey);
        redisTemplate.expire(counterKey, 24, TimeUnit.HOURS);
        if (count != null && count == 5) {
            alertPort.urgent("支付补偿查询异常需人工介入",
                    String.format("paymentId=%s 已连续查询失败 5 次, lastError=%s", paymentId, errorMsg));
        }
    }
//    /**
//     * 触发支付成功后续流程（与回调逻辑一致）
//     */
//    private void triggerPaymentSuccess(PaymentOrder payment) {
//        // 发送 MQ 消息（发货、积分、通知等）
//        // 示例：rabbitTemplate.convertAndSend(...)
//        log.info("支付成功后续流程触发: orderId={}", payment.getOrderId());
//    }
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void updatePaymentToFinal(PaymentOrder payment, String targetStatus, String transactionId) {
//        String paymentId = payment.getPaymentId();
//        String currentStatus = payment.getStatus();
//
//        if(paymentId==null||currentStatus==null){
//
//        }
//        // 1. 只允许非终态更新
//        if (!PaymentStatus.INIT.getCode().equals(currentStatus)
//                && !PaymentStatus.WAITING.getCode().equals(currentStatus)
//                && !PaymentStatus.PENDING_CONFIRM.getCode().equals(currentStatus)) {
//            log.info("支付单已终态，跳过更新: paymentId={}, currentStatus={}", paymentId, currentStatus);
//            return;
//        }
//
//        switch (targetStatus) {
//            case "SUCCESS":
//                // 用户已支付成功（回调可能丢失），直接走支付成功流程
//                // ✅ 真正成功：更新订单为 PAID
//                paymentTransactionService.updatePaymentStatusToSuccessFromStatusOnTransactional(paymentId,payment.getStatus(),transactionId);
//                log.info("{}} → SUCCESS (via query): paymentId={}",payment.getStatus(), paymentId);
//                break;
//            case "FAILED":
//                paymentTransactionService.updatePaymentStatusToFailedFromStatusOnTransactional(payment,payment.getStatus());
//                log.info("{}} → FAILED (via query): paymentId={}", payment.getStatus(),paymentId);
//                break;
//            default:
//                // UNKNOWN 或其他状态，保留 PENDING_CONFIRM，不处理（等待下次定时扫描）
//                log.info("查询结果未知，保留 {}}: paymentId={}, targetStatus={}",
//                        payment.getStatus(),paymentId, targetStatus);
//                break;
//        }
//    }



}