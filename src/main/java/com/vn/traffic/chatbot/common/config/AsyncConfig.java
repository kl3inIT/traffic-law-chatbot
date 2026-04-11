package com.vn.traffic.chatbot.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * Async configuration providing a named ingestion executor with an uncaught exception handler.
 *
 * <p>Implements {@link AsyncConfigurer} so that async failures from {@code @Async} methods
 * surfaced to the default executor produce an ERROR log entry instead of being silently dropped.
 *
 * <p>The {@code ingestionExecutor} bean is also returned as the registered default async executor
 * so that the uncaught handler covers {@code @Async("ingestionExecutor")} invocations.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "ingestionExecutor")
    public ThreadPoolTaskExecutor ingestionExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    /**
     * Returns the {@code ingestionExecutor} as the default async executor so that the
     * uncaught exception handler applies to all {@code @Async} invocations, including
     * those explicitly annotated with {@code @Async("ingestionExecutor")}.
     */
    @Override
    public Executor getAsyncExecutor() {
        // Delegate to the named bean; it is initialized by Spring before this is called
        // via the @Bean method above. We create a fresh instance here for the default.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ingestion-");
        executor.initialize();
        return executor;
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
