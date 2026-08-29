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
    /**
     * 生成 16 位 SpanId（短 UUID）
     */
    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成 32 位 TraceId
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ========== MDC 读写 ==========

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

    // ========== 批量操作 ==========

    /**
     * 初始化 HTTP 请求上下文（入口）
     * <p>
     * 在 JwtInterceptor 或 Filter 中调用
     */
    public static void initFromRequest(String traceId, String userId, String tenantId, String grayTag) {
        // TraceId：有则复用，无则生成
        if (!StringUtils.hasText(traceId)) {
            traceId = generateTraceId();
        }
        putTraceId(traceId);

        // SpanId：入口生成根节点
        putSpanId(generateSpanId());

        // 业务字段
        if (StringUtils.hasText(userId)) {
            putUserId(userId);
        }
        if (StringUtils.hasText(tenantId)) {
            MDC.put(TraceConstants.MDC_TENANT_ID, tenantId);
        }
        if (StringUtils.hasText(grayTag)) {
            MDC.put(TraceConstants.MDC_GRAY_TAG, grayTag);
        }
    }

    /**
     * 初始化异步任务上下文（MQ 消费者 / Job 进入时调用）
     */
    public static void initFromParent(String traceId, String parentSpanId, String spanId,String userId, String tenantId) {
        // 兜底

        putTraceId(traceId);
        putParentSpanId(parentSpanId);
        if(StringUtils.hasText(spanId)){
            putSpanId(spanId);
        }else{
            putSpanId(generateSpanId());  // 新节点生成新 SpanId
        }
        
        if (StringUtils.hasText(userId)) {
            putUserId(userId);
        }
        if (StringUtils.hasText(tenantId)) {
            MDC.put(TraceConstants.MDC_TENANT_ID, tenantId);
        }
    }
    /**
     * 从 MessageProperties 中安全提取字符串 Header
     * 自动处理 null、byte[]、String 等类型
     */
    public static String getSafeHeader(MessageProperties props, String key, String defaultValue) {
        Object value = props.getHeader(key);
        if (value == null) {
            return defaultValue;
        }
        // 如果是字节数组（某些序列化方式会这样）
        if (value instanceof byte[]) {
            return new String((byte[]) value);
        }
        // 普通对象转字符串
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
     * 清理当前线程的 MDC
     */
    public static void clear() {
        MDC.clear();
    }
}