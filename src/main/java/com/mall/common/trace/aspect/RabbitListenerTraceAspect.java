package com.mall.common.trace.aspect;

import com.mall.common.trace.constant.TraceConstants;
import com.mall.common.trace.context.TraceContext;
import com.mall.common.trace.util.CallSeqContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Aspect
@Component
@Slf4j
public class RabbitListenerTraceAspect {

    /**
     * 拦截所有 @RabbitListener 注解的方法
     */
//    @Log("消费者切面")
    @Around("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public Object injectTraceContext(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 提取 Message 参数
        Message message = extractMessage(joinPoint.getArgs());
        if (message == null) {
            // 如果没有 Message 参数，可能方法参数是业务对象（不推荐），直接执行
            log.warn("消费者方法无 Message 参数，无法自动注入 Trace 上下文");
            return joinPoint.proceed();
        }

        MessageProperties props = message.getMessageProperties();

        // 2. 从 Headers 中提取 Trace 上下文
        String traceId = TraceContext.getSafeHeader(props, TraceConstants.TRACE_ID, TraceContext.generateTraceId());
        String parentSpanId = TraceContext.getSafeHeader(props, TraceConstants.PARENT_SPAN_ID, "unknown");
        String userId = TraceContext.getSafeHeader(props, TraceConstants.USER_ID, TraceConstants.SYSTEM_USER);
        String tenantId = TraceContext.getSafeHeader(props, TraceConstants.TENANT_ID, "default");

// ✅ 从消息头中提取审计字段（如果有）
        String clientIp = TraceContext.getSafeHeader(props, TraceConstants.CLIENT_IP, null);
        String userAgent = TraceContext.getSafeHeader(props, TraceConstants.USER_AGENT, null);

// 3. 注入 MDC 和审计上下文（消费者生成自己的 SpanId）
        TraceContext.initFromParent(
                traceId,
                parentSpanId,
                null,          // spanId（由 initFromParent 自动生成）
                userId,
                tenantId,
                clientIp,
                userAgent
        );
        //自定义字段
        String parentCallSeq=null;
        if (CallSeqContext.isEnabled()) {
            parentCallSeq = (String) props.getHeader("X-Call-Seq");
            if (StringUtils.hasText(parentCallSeq)) {
                CallSeqContext.setParentPrefix(parentCallSeq);
                CallSeqContext.enter();
//                log.info("消费者切面：parentCallSeq={}，currentCallSeq={}",parentCallSeq,CallSeqContext.getCurrentSeq());
            }
        }
        try {
            // 4. 执行业务方法
            return joinPoint.proceed();
        } finally {
            // 5. 清理 MDC
            TraceContext.clear();
            CallSeqContext.clear();
        }
    }

    /**
     * 从参数数组中提取 Message 对象
     */
    private Message extractMessage(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Message) {
                return (Message) arg;
            }
        }
        return null;
    }
}