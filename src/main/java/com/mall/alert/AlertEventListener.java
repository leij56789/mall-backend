package com.mall.alert;

import com.mall.service.AlertService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventListener {

    private final RedissonClient redissonClient;
    private final AlertService alertService; // 实际发送告警的服务（钉钉/邮件）

    // 聚合缓存：key -> (时间窗口起始, 计数)
    private final ConcurrentHashMap<String, WindowCounter> aggregateCache = new ConcurrentHashMap<>();

    // ====== 1. 异步监听事件 ======
    @Async("alertExecutor")  // 配置一个线程池
    @EventListener
    public void handleAlertEvent(AlertEvent event) {
        String key = buildLimitKey(event);
        // 限流检查：1小时内同一 paymentId 只允许告警一次
        if (!checkRateLimit(key, 1,
                RateIntervalUnit.HOURS)) {
            log.debug("告警被限流: paymentId={}", event.getPaymentId());
            return;
        }

        // 聚合检查：10分钟窗口内相同类型的告警合并
        String aggregateKey = buildAggregateKey(event);
        aggregateAndSend(aggregateKey, event);
    }

    // ====== 2. 限流实现（基于 Redisson 分布式限流器） ======
    private boolean checkRateLimit(String key, int rate,
                                   RateIntervalUnit timeUnit) {
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        // 如果未初始化，设置限流参数
        if (!limiter.isExists()) {
            limiter.trySetRate(RateType.OVERALL, rate, 1, timeUnit);
        }
        // 尝试获取一个许可
        return limiter.tryAcquire(1);
    }

    // ====== 3. 聚合实现（本地缓存 + 定时刷新） ======
    private void aggregateAndSend(String key, AlertEvent event) {
        WindowCounter counter = aggregateCache.computeIfAbsent(key, k -> new WindowCounter());
        synchronized (counter) {
            counter.increment();
            if (counter.getFirstTime() == null) {
                counter.setFirstTime(LocalDateTime.now());
            }
            // 如果窗口已超时（如10分钟），立即发送摘要并重置
            if (Duration.between(counter.getFirstTime(), LocalDateTime.now()).toMinutes() >= 10) {
                sendAggregatedAlert(key, counter);
                aggregateCache.remove(key);
            }
        }
    }

    private void sendAggregatedAlert(String key, WindowCounter counter) {
        String title = "告警聚合摘要（10分钟窗口）";
        String message = String.format("告警类型: %s, 触发次数: %d", key, counter.getCount());
        alertService.sendUrgentAlert(title, message);
        log.info("发送聚合告警: {}", message);
    }

    // ====== 辅助方法：构建 key ======
    private String buildLimitKey(AlertEvent event) {
        return "alert:limit:" + event.getAlertType() + ":" + event.getPaymentId();
    }

    private String buildAggregateKey(AlertEvent event) {
        return "alert:aggregate:" + event.getAlertType() + ":" + event.getErrorCode();
    }

    // ====== 内部类：窗口计数器 ======
    @Data
    private static class WindowCounter {
        private LocalDateTime firstTime;
        private AtomicInteger count = new AtomicInteger(0);

        public void increment() {
            count.incrementAndGet();
        }
        public int getCount() {
            return count.get();
        }
    }
}
//使用方法
/*
* // 在 AsyncQueryService 中注入
private final ApplicationEventPublisher eventPublisher;

// 在需要告警的地方（例如 MANUAL_INTERVENTION 分支）
eventPublisher.publishEvent(new AlertEvent(
    this,
    paymentId,
    String.valueOf(payment.getOrderId()),
    errorCode.getCode().toString(),
    "支付宝查询返回需人工介入错误",
    "PAYMENT_MANUAL_INTERVENTION"
));
* */