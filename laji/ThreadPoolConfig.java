package com.mall.config;

import com.mall.common.trace.utils.CallSeqContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        int core = Math.max(4, CPU_CORES / 2);
        int max  = CPU_CORES;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        
        // 🔥 核心：注册 MDC 传递装饰器
        executor.setTaskDecorator(new TraceTaskDecorator());
        
        executor.initialize();
        return executor;
    }
    /**
     * 用于支付查询的异步任务（新增）
     */
    @Bean(name = "payQueryExecutor")
    public Executor payQueryExecutor() {
        int core = Math.max(8, CPU_CORES * 2);      // 最少 8 个，避免单核机器太惨
        int max  = Math.max(16, CPU_CORES * 4);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("pay-query-");
//         支付查询不需要传递 TraceId，但如果你希望保留，也可以加 TaskDecorator
         executor.setTaskDecorator(new TraceTaskDecorator());
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
/*
* @Configuration
public class ThreadPoolConfig {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    @Bean("paymentBizExecutor")
    public ExecutorService paymentBizExecutor() {
        int core = Math.max(8, CPU_CORES * 2);      // 最少 8 个，避免单核机器太惨
        int max  = Math.max(16, CPU_CORES * 4);
        return new ThreadPoolExecutor(
            core, max,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean("mqSenderExecutor")
    public ExecutorService mqSenderExecutor() {
        int core = Math.max(4, CPU_CORES / 2);
        int max  = CPU_CORES;
        return new ThreadPoolExecutor(
            core, max,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
* */