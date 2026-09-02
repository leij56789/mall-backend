package com.mall.common.sse;

import com.mall.common.sse.config.SseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SseSessionManager {

    @Autowired
    private SseProperties sseProperties;

    /**
     * 所有活跃的 SSE 会话
     * Key: paymentId, Value: SseEmitter
     */
    private final ConcurrentHashMap<String, SseEmitter> sessions = new ConcurrentHashMap<>();
    
    /**
     * 心跳调度器（每个会话独立心跳）
     */
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(4);

    // ==================== 会话管理 ====================

    public SseEmitter createSession(String paymentId) {
        SseEmitter emitter = new SseEmitter(sseProperties.getSessionTimeout());
        sessions.put(paymentId, emitter);

        // 清理钩子
        emitter.onTimeout(() -> {
            log.warn("SSE session timeout: paymentId={}", paymentId);
            sessions.remove(paymentId);
        });
        emitter.onCompletion(() -> {
            log.info("SSE session completed: paymentId={}", paymentId);
            sessions.remove(paymentId);
        });
        emitter.onError(e -> {
            log.error("SSE session error: paymentId={}", paymentId, e);
            sessions.remove(paymentId);
        });

        // 发送连接成功事件
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("paymentId", paymentId, "message", "SSE connected")));
        } catch (IOException e) {
            log.error("Send connected event failed: paymentId={}", paymentId, e);
            sessions.remove(paymentId);
        }

        // 启动心跳
        startHeartbeat(paymentId, emitter);

        log.info("SSE session created: paymentId={}, activeSessions={}", 
            paymentId, sessions.size());
        return emitter;
    }

    // ==================== 推送 ====================

    @Async("sseExecutor")
    public void pushStatus(String paymentId, String status) {
        SseEmitter emitter = sessions.remove(paymentId);
        if (emitter == null) {
            log.debug("SSE session not found or already removed: paymentId={}", paymentId);
            return;
        }

        try {
            Map<String, Object> data = Map.of(
                "paymentId", paymentId,
                "status", status,
                "timestamp", System.currentTimeMillis()
            );
            emitter.send(SseEmitter.event()
                .name("payment-status")
                .data(data));
            emitter.complete();
            log.info("SSE push success: paymentId={}, status={}", paymentId, status);
        } catch (IOException e) {
            log.error("SSE push failed: paymentId={}", paymentId, e);
            // 连接已断开，已从 sessions 移除，无需额外操作
        }
    }

    // ==================== 心跳 ====================

    private void startHeartbeat(String paymentId, SseEmitter emitter) {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            // 检查会话是否还存在
            if (!sessions.containsKey(paymentId)) {
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                    .name("ping")
                    .data(Map.of("timestamp", System.currentTimeMillis())));
                log.trace("Heartbeat sent: paymentId={}", paymentId);
            } catch (IOException e) {
                log.warn("Heartbeat failed, removing session: paymentId={}", paymentId);
                sessions.remove(paymentId);
            }
        }, 10, sseProperties.getHeartbeatInterval(), TimeUnit.SECONDS);
    }

    // ==================== 清理 ====================

    /**
     * 定时清理无效会话（每 60 秒）
     * 通过发送 ping 检测连接是否真正存活
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanupInvalidSessions() {
        int before = sessions.size();
        if (before == 0) {
            return;
        }

        sessions.entrySet().removeIf(entry -> {
            String paymentId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            try {
                emitter.send(SseEmitter.event().name("ping").data("cleanup"));
                return false; // 正常，保留
            } catch (IOException e) {
                log.debug("SSE session disconnected, removing: paymentId={}", paymentId);
                return true; // 连接断开，清理
            }
        });

        int after = sessions.size();
        if (before != after) {
            log.info("SSE cleanup: removed {} sessions, remaining {}", before - after, after);
        }
    }

    // ==================== 监控 ====================

    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * 获取所有活跃 paymentId（调试用）
     */
    public List<String> getActivePaymentIds() {
        return new ArrayList<>(sessions.keySet());
    }
}