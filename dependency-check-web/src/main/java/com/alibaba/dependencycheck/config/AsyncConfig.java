package com.alibaba.dependencycheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * 异步任务线程池配置
 * <p>
 * 用于执行耗时的扫描任务，避免阻塞 HTTP 请求线程。
 * 扫描任务会在独立的线程池中异步执行。
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 扫描任务线程池
     * <p>
     * 配置说明：
     * - corePoolSize=2：核心线程数，即使空闲也保持存活
     * - maxPoolSize=4：最大线程数，超过核心线程且队列满时创建新线程
     * - queueCapacity=10：队列容量，超过核心线程数时先入队列
     * - 支持同时处理最多 2+10=12 个等待任务，超过后创建新线程到 max=4
     * </p>
     */
    @Bean("scanTaskExecutor")
    public Executor scanTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("scan-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // 拒绝策略：由调用者线程执行（避免任务丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }
}
