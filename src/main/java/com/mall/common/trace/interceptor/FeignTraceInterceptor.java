package com.mall.common.trace.interceptor;

import com.mall.common.trace.constant.TraceConstants;
import com.mall.common.trace.context.TraceContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Feign 请求拦截器
 * 自动将当前线程 MDC 中的 Trace 上下文透传到下游服务
 */
@Slf4j
@Component
public class FeignTraceInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // ========== 1. 获取当前线程的 MDC 上下文 ==========
        String traceId = TraceContext.getTraceId();
        String spanId = TraceContext.getSpanId();
        String userId = TraceContext.getUserId();
        String tenantId = MDC.get(TraceConstants.MDC_TENANT_ID);
        String grayTag = MDC.get(TraceConstants.MDC_GRAY_TAG);

        // ========== 2. 兜底：如果 TraceId 为空（比如定时任务调 Feign），自动生成 ==========
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceContext.generateTraceId();
            // 注意：这里不放入 MDC，避免污染当前线程（定时任务可能不需要全链路）
            // 但建议在 Feign 调用前由调用方显式初始化上下文
            log.warn("[Feign] 当前线程无 TraceId，自动生成临时 ID: {}", traceId);
        }

        // ========== 3. 生成当前 Feign 调用的 SpanId（代表“一次 RPC 调用”） ==========
        String rpcSpanId = TraceContext.generateSpanId();

        // ========== 4. 注入到请求头（透传下游） ==========
        // 链路追踪核心
        template.header(TraceConstants.TRACE_ID, traceId);
        template.header(TraceConstants.SPAN_ID, rpcSpanId);
        
        // 🔥 关键：把当前 SpanId 作为父 ID 传下去（下游知道是谁调了自己）
        if (StringUtils.hasText(spanId)) {
            template.header(TraceConstants.PARENT_SPAN_ID, spanId);
        }

        // 业务扩展字段
        if (StringUtils.hasText(userId)) {
            template.header(TraceConstants.USER_ID, userId);
        }
        if (StringUtils.hasText(tenantId)) {
            template.header(TraceConstants.TENANT_ID, tenantId);
        }
        if (StringUtils.hasText(grayTag)) {
            template.header(TraceConstants.GRAY_TAG, grayTag);
        }

        // 服务来源标识
        template.header(TraceConstants.SOURCE_HEADER, "mall-backend");

        // ========== 5. （可选）记录调用开始时间，下游可计算网络耗时 ==========
        template.header("X-Rpc-Start-Time", String.valueOf(System.currentTimeMillis()));

        // ========== 6. Debug 级别日志（排查问题时开启） ==========
        if (log.isDebugEnabled()) {
            log.debug("[Feign] 透传上下文: traceId={}, spanId={}, parentSpanId={}, userId={}",
                    traceId, rpcSpanId, spanId, userId);
        }
    }
}