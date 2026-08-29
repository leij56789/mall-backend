package com.mall.config;

import com.mall.common.trace.util.CallSeqContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
@Slf4j
@Component
public class ThreadPoolManager {
    private final Map<String, ThreadPoolTaskExecutor> pools = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 1. 核心池
        pools.put("payQueryExecutor", createPool(28, 56, 1000, "payment-"));
        // 2. 通知池
        pools.put("notify", createPool(10, 20, 500, "notify-"));
        // 3. 日志池
        pools.put("audit", createPool(5, 10, 200, "audit-"));
        // 4. 补偿任务taskExecutor
        pools.put("taskExecutor", createPool(10, 20, 500, "compensate-"));
    }

    public ThreadPoolTaskExecutor getPool(String group) {
        return pools.get(group);
    }

    // 定时打印所有线程池的状态（队列深度、活跃数），用于监控
    @Scheduled(fixedDelay = 60000)
    public void monitor() {
        pools.forEach((name, pool) -> {
            log.info("池[{}] 活跃: {}, 队列: {}, 活跃时间: {}",
                name, pool.getActiveCount(), pool.getQueueSize(), pool.getKeepAliveSeconds());
        });
    }
    @PreDestroy
    public void shutdown(){
        pools.values().forEach(ThreadPoolTaskExecutor::shutdown);
    }

    private ThreadPoolTaskExecutor createPool(int corePoolSize,
                                              int maxPoolSize,
                                              int queueCapacity,
                                              String threadNamePrefix,
                                              RejectedExecutionHandler handler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 1. 核心参数
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);

        // 2. 线程命名
        executor.setThreadNamePrefix(threadNamePrefix);

        // 3. 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 4. 等待任务完成后关闭（优雅停机）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        // ... 其他配置
        executor.setRejectedExecutionHandler(handler);
        executor.initialize();
        return executor;
    }
    /**
     * 创建线程池（Spring 封装方式）
     *
     * @param corePoolSize     核心线程数
     * @param maxPoolSize      最大线程数
     * @param queueCapacity    队列容量
     * @param threadNamePrefix 线程名前缀
     * @return ThreadPoolTaskExecutor 实例
     */
    private ThreadPoolTaskExecutor createPool(int corePoolSize,
                                              int maxPoolSize,
                                              int queueCapacity,
                                              String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 1. 核心参数
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);

        // 2. 线程命名
        executor.setThreadNamePrefix(threadNamePrefix);

        // 3. 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 4. 等待任务完成后关闭（优雅停机）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new TraceTaskDecorator());

        // 5. 初始化
        executor.initialize();

        return executor;
    }
    /**
     * 内部类：MDC 上下文传递装饰器
     */
    static class TraceTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
//            log.info("TaskDecorator执行了");
            Map<String, String> parentContext = MDC.getCopyOfContextMap();
            String parentCallSeq = CallSeqContext.getCurrentSeq();
            String methodName = CallSeqContext.getCurrentMethodName();
            int taskHash = System.identityHashCode(runnable);
//            log.info("搜捕到父级编号："+parentCallSeq);
//            log.info("🔥 decorate 捕获: targetMethodName={},methodName={},parentCallSeq={}, taskHash={},runnable={}",
            return () -> {
                try {
                    if (parentContext != null) {
                        MDC.setContextMap(parentContext);
                    }
                    MDC.put("taskHash",String.valueOf(taskHash));
                    if(!CallSeqContext.isEnabled()){
                        log.warn("异步线程没有开启CallSeq: enabled={}",CallSeqContext.isEnabled());
                    }
                    if(parentCallSeq!=null&&!parentCallSeq.isEmpty()){
                        CallSeqContext.setParentPrefix(parentCallSeq);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                    MDC.remove("taskHash");
                    CallSeqContext.clear();
                }
            };
        }
    }
}