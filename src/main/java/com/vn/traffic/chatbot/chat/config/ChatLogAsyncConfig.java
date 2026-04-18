package com.vn.traffic.chatbot.chat.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor for async chat_log persistence (PERF-03 / D-09).
 *
 * <p>Core=2, max=8, queue=1000, {@code CallerRunsPolicy}: bounded backpressure —
 * if the queue saturates, the caller thread runs the task so no chat_log row is
 * silently dropped.
 *
 * <p>Does NOT declare {@code @EnableAsync} — already present on
 * {@link com.vn.traffic.chatbot.common.config.AsyncConfig}. Adding a second would
 * duplicate {@code AsyncAnnotationBeanPostProcessor} registration.
 */
@Configuration
public class ChatLogAsyncConfig {

    public static final String CHAT_LOG_EXECUTOR = "chatLogExecutor";

    @Bean(name = CHAT_LOG_EXECUTOR)
    public ThreadPoolTaskExecutor chatLogExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("chat-log-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}
