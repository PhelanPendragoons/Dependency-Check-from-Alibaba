package com.alibaba.dependencycheck.config;

import org.springframework.beans.factory.annotation.Value;
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
 *
 * <b>B2-08 修复：</b>线程池参数从 application.yml 读取，不再硬编码。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** B2-08 修复：从配置文件读取线程池参数，替换硬编码值 */
    @Value("${task.pool.core-pool-size:2}")
    private int corePoolSize;

    @Value("${task.pool.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${task.pool.queue-capacity:10}")
    private int queueCapacity;

    @Value("${task.pool.thread-name-prefix:scan-task-}")
    private String threadNamePrefix;

    /**
     * 扫描任务线程池
     * <p>
     * 配置说明：
     * - corePoolSize：核心线程数，即使空闲也保持存活（默认 2，可通过 task.pool.core-pool-size 配置）
     * - maxPoolSize：最大线程数，超过核心线程且队列满时创建新线程（默认 4，可通过 task.pool.max-pool-size 配置）
     * - queueCapacity：队列容量，超过核心线程数时先入队列（默认 10，可通过 task.pool.queue-capacity 配置）
     * - 支持同时处理最多 corePoolSize+queueCapacity 个等待任务，超过后创建新线程到 maxPoolSize
     * </p>
     */
    @Bean("scanTaskExecutor")
    public Executor scanTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // 拒绝策略：由调用者线程执行（避免任务丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }
}
