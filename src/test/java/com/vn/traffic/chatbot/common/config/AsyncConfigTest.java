package com.vn.traffic.chatbot.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 0 — RED test for AsyncConfig AsyncConfigurer upgrade.
 * Verifies that AsyncConfig implements AsyncConfigurer and provides a non-null uncaught handler.
 */
class AsyncConfigTest {

    @Test
    void asyncConfigImplementsAsyncConfigurer() {
        AsyncConfig config = new AsyncConfig();
        assertThat(config)
                .as("AsyncConfig must implement AsyncConfigurer")
                .isInstanceOf(AsyncConfigurer.class);
    }

    @Test
    void getAsyncUncaughtExceptionHandlerReturnsNonNull() {
        AsyncConfigurer config = new AsyncConfig();
        assertThat(config.getAsyncUncaughtExceptionHandler())
                .as("getAsyncUncaughtExceptionHandler() must return a non-null handler")
                .isNotNull();
    }
}
