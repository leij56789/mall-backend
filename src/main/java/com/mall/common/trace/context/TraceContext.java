package com.mall.common.trace.context;

import com.mall.common.trace.constant.TraceConstants;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

/**
 * 链路追踪 + 审计上下文工具
 * <p>
 * 职责：
 * <ul>
 *   <li>链路追踪：TraceId、SpanId、ParentSpanId、UserId</li>
 *   <li>审计上下文：ClientIp、UserAgent</li>
 *   <li>支持跨线程传递（MDC + ThreadLocal）</li>
 * </ul>
 */
public final class TraceContext {

    private TraceContext() {}

    // ========== 审计专用 ThreadLocal（不在 MDC 中，避免污染） ==========
    private static final ThreadLocal<String> clientIpHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> userAgentHolder = new ThreadLocal<>();

    // ========== SpanId / TraceId 生成 ==========
    
    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ========== MDC 读写（链路追踪） ==========

    public static void putTraceId(String traceId) {
        if (StringUtils.hasText(traceId)) {
            MDC.put(TraceConstants.MDC_TRACE_ID, traceId);
        }
    }

    public static String getTraceId() {
        return MDC.get(TraceConstants.MDC_TRACE_ID);
    }

    public static void putSpanId(String spanId) {
        if (StringUtils.hasText(spanId)) {
            MDC.put(TraceConstants.MDC_SPAN_ID, spanId);
        }
    }

    public static String getSpanId() {
        return MDC.get(TraceConstants.MDC_SPAN_ID);
    }

    public static void putParentSpanId(String parentSpanId) {
        if (StringUtils.hasText(parentSpanId)) {
            MDC.put(TraceConstants.MDC_PARENT_SPAN_ID, parentSpanId);
        }
    }

    public static String getParentSpanId() {
        return MDC.get(TraceConstants.MDC_PARENT_SPAN_ID);
    }

    public static void putUserId(String userId) {
        if (StringUtils.hasText(userId)) {
            MDC.put(TraceConstants.MDC_USER_ID, userId);
        }
    }

    public static String getUserId() {
        return MDC.get(TraceConstants.MDC_USER_ID);
    }

    // ========== 审计上下文读写（ThreadLocal） ==========

    public static void setClientIp(String clientIp) {
        if (StringUtils.hasText(clientIp)) {
            clientIpHolder.set(clientIp);
        }
    }

    public static String getClientIp() {
        return clientIpHolder.get();
    }

    public static void setUserAgent(String userAgent) {
        if (StringUtils.hasText(userAgent)) {
            userAgentHolder.set(userAgent);
        }
    }

    public static String getUserAgent() {
        return userAgentHolder.get();
    }



    // ========== 批量初始化 ==========

    /**
     * 初始化 HTTP 请求上下文（入口）
     * <p>
     * 在 JwtInterceptor 或 Filter 中调用
     */
    public static void initFromRequest(
            String traceId,
            String userId,
            String tenantId,
            String grayTag,
            String clientIp,
            String userAgent) {
        
        // 1. TraceId
        if (!StringUtils.hasText(traceId)) {
            traceId = generateTraceId();
        }
        putTraceId(traceId);
        putSpanId(generateSpanId());

        // 2. 业务字段
        if (StringUtils.hasText(userId)) {
            putUserId(userId);
        }else{
            putUserId(TraceConstants.SYSTEM_USER_ID);
        }
        if (StringUtils.hasText(tenantId)) {
            MDC.put(TraceConstants.MDC_TENANT_ID, tenantId);
        }
        if (StringUtils.hasText(grayTag)) {
            MDC.put(TraceConstants.MDC_GRAY_TAG, grayTag);
        }

        // 3. 审计字段
        setClientIp(clientIp);
        setUserAgent(userAgent);
    }

    /**
     * 初始化异步任务上下文（MQ / Job / @Async）
     */
    public static void initFromParent(
            String traceId,
            String parentSpanId,
            String spanId,
            String userId,
            String tenantId,
            String clientIp,
            String userAgent) {

        putTraceId(traceId);
        putParentSpanId(parentSpanId);
        putSpanId(StringUtils.hasText(spanId) ? spanId : generateSpanId());

        if (StringUtils.hasText(userId)) {
            putUserId(userId);
        }
        if (StringUtils.hasText(tenantId)) {
            MDC.put(TraceConstants.MDC_TENANT_ID, tenantId);
        }

        setClientIp(clientIp);
        setUserAgent(userAgent);
    }

    // ========== 跨线程传递支持 ==========

    /**
     * 获取当前完整上下文快照（用于跨线程传递）
     */
    public static ContextSnapshot snapshot() {
        return new ContextSnapshot(
                MDC.getCopyOfContextMap(),
                getClientIp(),
                getUserAgent()
        );
    }

    /**
     * 恢复上下文快照
     */
    public static void restore(ContextSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.mdcContext != null) {
            MDC.setContextMap(snapshot.mdcContext);
        }
        setClientIp(snapshot.clientIp);
        setUserAgent(snapshot.userAgent);
    }

    /**
     * 从 MessageProperties 中安全提取 Header
     */
    public static String getSafeHeader(MessageProperties props, String key, String defaultValue) {
        Object value = props.getHeader(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof byte[]) {
            return new String((byte[]) value);
        }
        return value.toString();
    }

    /**
     * 获取当前 MDC 的快照（用于跨线程传递）
     */
    public static Map<String, String> getCurrentContext() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 恢复上下文（子线程使用）
     */
    public static void restoreContext(Map<String, String> contextMap) {
        if (contextMap != null && !contextMap.isEmpty()) {
            MDC.setContextMap(contextMap);
        }
    }

    /**
     * 清理当前线程所有上下文
     */
    public static void clear() {
        MDC.clear();
        clientIpHolder.remove();
        userAgentHolder.remove();
    }

    // ========== 内部类 ==========

    /**
     * 上下文快照（用于跨线程传递）
     */
    public static class ContextSnapshot {
        private final Map<String, String> mdcContext;
        private final String clientIp;
        private final String userAgent;

        public ContextSnapshot(Map<String, String> mdcContext, String clientIp, String userAgent) {
            this.mdcContext = mdcContext;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
        }

        public Map<String, String> getMdcContext() {
            return mdcContext;
        }

        public String getClientIp() {
            return clientIp;
        }

        public String getUserAgent() {
            return userAgent;
        }
    }
}