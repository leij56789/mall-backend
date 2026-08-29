package com.mall.config;

import com.mall.common.trace.util.CallSeqContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableAsync
public class ThreadPoolManager {

    /**
     * 用于监控的线程池缓存（与 Bean 指向同一对象）
     */
    private final Map<String, ThreadPoolTaskExecutor> pools = new ConcurrentHashMap<>();

    // ==================== 线程池 Bean 定义 ====================

    @Bean(name = "payQueryExecutor")
    public ThreadPoolTaskExecutor payQueryExecutor() {
        ThreadPoolTaskExecutor executor = createPool(28, 56, 1000, "payment-");
        pools.put("payQueryExecutor", executor);
        return executor;
    }

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = createPool(10, 20, 500, "compensate-");
        pools.put("taskExecutor", executor);
        return executor;
    }

    @Bean(name = "notify")
    public ThreadPoolTaskExecutor notifyExecutor() {
        ThreadPoolTaskExecutor executor = createPool(10, 20, 500, "notify-");
        pools.put("notify", executor);
        return executor;
    }

    @Bean(name = "audit")
    public ThreadPoolTaskExecutor auditExecutor() {
        ThreadPoolTaskExecutor executor = createPool(5, 10, 200, "audit-");
        pools.put("audit", executor);
        return executor;
    }

    // ==================== 公共方法 ====================

    /**
     * 根据名称获取线程池
     */
    public ThreadPoolTaskExecutor getPool(String group) {
        return pools.get(group);
    }

    /**
     * 定时打印所有线程池的状态，用于监控
     */
    @Scheduled(fixedDelay = 60000)
    public void monitor() {
        pools.forEach((name, pool) -> {
            log.info("池[{}] 活跃: {}, 队列: {}, 核心: {}, 最大: {}, 活跃时间: {}s",
                    name,
                    pool.getActiveCount(),
                    pool.getQueueSize(),
                    pool.getCorePoolSize(),
                    pool.getMaxPoolSize(),
                    pool.getKeepAliveSeconds());
        });
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void shutdown() {
        log.info("开始优雅关闭线程池...");
        for (Map.Entry<String, ThreadPoolTaskExecutor> entry : pools.entrySet()) {
            String name = entry.getKey();
            ThreadPoolTaskExecutor pool = entry.getValue();
            try {
                log.info("关闭线程池: {}", name);
                // 1. 拒绝新任务
                pool.shutdown();
                // 2. 等待已有任务完成（最多30秒）
                if (!pool.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("线程池 {} 未能在30秒内完成，强制关闭", name);
                    // 3. 强制关闭（取消正在执行的任务，返回未执行的任务列表）
                    java.util.concurrent.ExecutorService executor = pool.getThreadPoolExecutor();
                    List<Runnable> pendingTasks = executor.shutdownNow();
                    if (!pendingTasks.isEmpty()) {
                        log.warn("线程池 {} 有 {} 个任务未执行", name, pendingTasks.size());
                    }
                } else {
                    log.info("线程池 {} 已正常关闭", name);
                }
            } catch (InterruptedException e) {
                log.warn("线程池 {} 关闭被中断", name);
                java.util.concurrent.ExecutorService executor = pool.getThreadPoolExecutor();
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("所有线程池已关闭");
    }

    // ==================== 私有方法 ====================

    /**
     * 创建线程池
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

        // 3. 拒绝策略（调用者执行，保证任务不丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 4. 优雅停机配置
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 5. MDC 上下文传递
        executor.setTaskDecorator(new TraceTaskDecorator());

        // 6. 初始化
        executor.initialize();

        return executor;
    }

    // ==================== 内部类 ====================

    /**
     * MDC 上下文传递装饰器
     */
    static class TraceTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // 在父线程中捕获上下文
            Map<String, String> parentContext = MDC.getCopyOfContextMap();
            String parentCallSeq = CallSeqContext.getCurrentSeq();
            int taskHash = System.identityHashCode(runnable);

            return () -> {
                try {
                    // 恢复 MDC 上下文
                    if (parentContext != null) {
                        MDC.setContextMap(parentContext);
                    }
                    MDC.put("taskHash", String.valueOf(taskHash));

                    // 恢复 CallSeq 上下文
                    if (parentCallSeq != null && !parentCallSeq.isEmpty()) {
                        CallSeqContext.setParentPrefix(parentCallSeq);
                    }

                    // 执行业务逻辑
                    runnable.run();

                } finally {
                    // 清理上下文，避免线程复用时的污染
                    MDC.clear();
                    MDC.remove("taskHash");
                    CallSeqContext.clear();
                }
            };
        }
    }
}
/*
* 使用方法
* // 1. @Async 可以正常使用
@Async("taskExecutor")
public void doRefundQuery(...) { ... }

// 2. 获取线程池（如需手动提交任务）
@Autowired
private ThreadPoolManager threadPoolManager;

public void submitTask() {
    ThreadPoolTaskExecutor pool = threadPoolManager.getPool("taskExecutor");
    pool.execute(() -> { ... });
}
* */