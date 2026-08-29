package com.mall.common.trace.constant;

/**
 * 链路追踪常量定义
 * 遵循 W3C TraceContext 和 B3 标准
 */
public interface TraceConstants {

    // ========== HTTP / MQ Header 传递键名（对外） ==========
    // W3C 标准格式
    String TRACE_PARENT = "trace-parent";  // 格式: 00-traceId-spanId-01
    
    // B3 标准格式（更通用，推荐）
    String TRACE_ID = "X-B3-TraceId";
    String SPAN_ID = "X-B3-SpanId";
    String PARENT_SPAN_ID = "X-B3-ParentSpanId";
    String SAMPLED = "X-B3-Sampled";

    // ========== 业务扩展字段 ==========
    String USER_ID = "X-User-Id";
    String TENANT_ID = "X-Tenant-Id";
    String GRAY_TAG = "X-Gray-Tag";

    // ========== MDC 内部键名（日志打印占位符） ==========
    String MDC_TRACE_ID = "traceId";
    String MDC_SPAN_ID = "spanId";
    String MDC_PARENT_SPAN_ID = "parentSpanId";
    String MDC_USER_ID = "userId";
    String MDC_TENANT_ID = "tenantId";
    String MDC_GRAY_TAG = "grayTag";

    // ========== 系统标识 ==========
    String SYSTEM_USER = "SYSTEM";
    String UNKNOW="unknow";
    String SOURCE_HEADER = "X-Source";

    String SYSTEM_USER_ID = "0";
    String OPERATOR_TYPE_USER = "USER";
    String OPERATOR_TYPE_SYSTEM = "SYSTEM";

    /**
     * 审计相关 Header 名
     */
    String CLIENT_IP = "X-Client-IP";
    String USER_AGENT = "X-User-Agent";
}