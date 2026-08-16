package com.mall.common.trace.config;

import com.mall.common.trace.context.TraceContext;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 链路追踪 MDC 上下文传递装饰器
 * 用于 @Async 异步任务自动继承父线程的 TraceId/SpanId/UserId
 */
@Component
public class TraceTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 1. 🔥 捕获父线程的 MDC 上下文（快照）
        Map<String, String> parentContext = TraceContext.getCurrentContext();
        
        // 2. 返回包装后的任务
        return () -> {
            try {
                // 3. 🔥 子线程执行前：恢复父线程的上下文
                if (parentContext != null && !parentContext.isEmpty()) {
                    MDC.setContextMap(parentContext);
                    // 可选：子线程重新生成子 SpanId（用于更精细的追踪）
                    // 这里保持父 SpanId 不变，因为 @Async 通常视为同一个服务内部节点
                }
                
                // 4. 执行原任务
                runnable.run();
                
            } finally {
                // 5. 🔥 清理子线程 MDC，防止线程池复用污染
                MDC.clear();
            }
        };
    }
}