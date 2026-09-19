package com.foodrisk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Milestone M12: Asynchronous Execution & High-Concurrency Thread Pool Configuration.
 *
 * Configures dedicated thread pool executors tuned to handle peak loads up to 500 concurrent users:
 * - 50 core threads, bursting to 200 threads under peak traffic.
 * - 500 queued task capacity with CallerRunsPolicy to prevent dropped requests under surge.
 * - Graceful shutdown with 30-second drain window.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "analysisTaskExecutor")
    public Executor analysisTaskExecutor() {
        log.info("Initializing high-concurrency analysis thread pool (core=50, max=200, queue=500)");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("food-risk-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
