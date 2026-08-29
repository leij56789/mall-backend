package com.mall.common.trace.config;

import com.mall.common.trace.constant.TraceConstants;
import com.mall.common.trace.context.TraceContext;
import com.mall.common.trace.util.CallSeqContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 链路追踪自动配置
 * 自动为 RabbitMQ 消息注入 Trace 上下文
 */
@Configuration
@Slf4j
public class TraceAutoConfiguration {

    /**
     * 消息发送前拦截器：自动注入 TraceId + SpanId + ParentSpanId
     * 所有 rabbitTemplate.convertAndSend() 都会自动带上这些头信息
     */
    @Bean
    public MessagePostProcessor traceMessagePostProcessor() {
        return (message) -> {
            // 1. 获取当前线程上下文
            String traceId = TraceContext.getTraceId();
            String currentSpanId = TraceContext.getSpanId();
            String userId = TraceContext.getUserId();

            // 2. 如果 TraceId 为空（比如定时任务发消息），自动生成
            if (!StringUtils.hasText(traceId)) {
                traceId = TraceContext.generateTraceId();
                // 放入 MDC 方便后续日志
                TraceContext.putTraceId(traceId);
            }

            // 3. 生成发送动作的 SpanId（代表“发送MQ”这个工作单元）
            String sendSpanId = TraceContext.generateSpanId();

            log.info("消息提前装入trace信息,traceId={},currentSpanId={},sendSpanId={},userId={}",traceId,currentSpanId,sendSpanId,userId);

            TraceContext.initFromParent(traceId,currentSpanId,sendSpanId,userId,null,TraceContext.getClientIp(),TraceContext.getUserAgent());
            // 4. 注入到消息头
            message.getMessageProperties().setHeader(TraceConstants.TRACE_ID, traceId);
            message.getMessageProperties().setHeader(TraceConstants.SPAN_ID, sendSpanId);
            message.getMessageProperties().setHeader(TraceConstants.CLIENT_IP, TraceContext.getClientIp());
            message.getMessageProperties().setHeader(TraceConstants.USER_AGENT, TraceContext.getUserAgent());

            // 🔥 关键：记录当前 SpanId 作为父节点（生产者的 SpanId）
            if (StringUtils.hasText(currentSpanId)) {
                message.getMessageProperties().setHeader(TraceConstants.PARENT_SPAN_ID, sendSpanId);
            }

            // 5. 业务扩展字段透传
            if (StringUtils.hasText(userId)) {
                message.getMessageProperties().setHeader(TraceConstants.USER_ID, userId);
            }

            //自设字段
            if (CallSeqContext.isEnabled()) { // 你需要在 CallSeqContext 里加一个静态标志
                String currentSeq = CallSeqContext.getCurrentSeq();
                if (StringUtils.hasText(currentSeq)) {
                    message.getMessageProperties().setHeader("X-Call-Seq", currentSeq);
                }
            }
            // 6. 记录来源（方便下游识别调用方）
            message.getMessageProperties().setHeader(TraceConstants.SOURCE_HEADER, "mall-backend");

            // 7. （可选）记录当前时间戳，方便下游计算消息延迟
            message.getMessageProperties().setHeader("X-Msg-Send-Time", System.currentTimeMillis());

            return message;
        };
    }
}