package com.mall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync  // 开启异步任务支持
public class ThreadPoolConfig {

    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：平时保持的线程数
        executor.setCorePoolSize(5);

        // 最大线程数：高峰期最多线程数
        executor.setMaxPoolSize(20);

        // 队列容量：等待执行的任务队列大小
        executor.setQueueCapacity(100);

        // 线程空闲时间：超过核心线程数的线程，空闲60秒后回收
        executor.setKeepAliveSeconds(60);

        // 线程名前缀：方便排查问题
        executor.setThreadNamePrefix("async-task-");

        // 拒绝策略：队列满了怎么办
        // CallerRunsPolicy - 让调用线程自己执行，不会丢失任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}