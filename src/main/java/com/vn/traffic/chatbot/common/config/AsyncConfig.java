package com.vn.traffic.chatbot.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * Async configuration providing a named ingestion executor with an uncaught exception handler.
 *
 * <p>Implements {@link AsyncConfigurer} so that async failures from {@code @Async} methods
 * surfaced to the default executor produce an ERROR log entry instead of being silently dropped.
 *
 * <p>The {@code ingestionExecutor} bean is also returned as the registered default async executor
 * so that the uncaught handler covers both {@code @Async("ingestionExecutor")} and plain
 * {@code @Async} invocations — a single pool instance is used for both.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncConfig.class);

    @Lazy
    @Autowired
    private Executor ingestionExecutor;

    /**
     * Virtual-thread-per-task executor for ingestion jobs.
     * Each blocking I/O operation (HTTP fetch, OpenAI embed) suspends its virtual thread
     * instead of occupying a platform thread, allowing far higher concurrency than a
     * fixed ThreadPoolTaskExecutor at no extra cost.
     * Concurrency is bounded to 20 to avoid hammering the OpenAI embedding API.
     */
    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("ingestion-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(20);
        return executor;
    }

    /**
     * Returns the same {@code ingestionExecutor} bean as the default async executor so that
     * the uncaught exception handler applies to all {@code @Async} invocations and a single
     * thread pool is used throughout.
     */
    @Override
    public Executor getAsyncExecutor() {
        return ingestionExecutor;
    }

    /**
     * Returns a handler that logs uncaught exceptions from {@code @Async} methods at ERROR level.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> LOG.error(
                "Uncaught async exception in {}() params={}: {}",
                method.getName(), Arrays.toString(params), ex.getMessage(), ex);
    }
}
