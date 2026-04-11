package com.vn.traffic.chatbot.common.aop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wave 0 — RED test for LoggingAspect.
 * Verifies that @AfterThrowing aspect captures exception and logs at ERROR level.
 */
@SpringBootTest(classes = {
        LoggingAspect.class,
        LoggingAspectTest.TestIngestionService.class
})
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration," +
                "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class LoggingAspectTest {

    @Autowired
    private TestIngestionService testIngestionService;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger testServiceLogger;

    @BeforeEach
    void setUpLogger() {
        testServiceLogger = (Logger) LoggerFactory.getLogger(TestIngestionService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        testServiceLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogger() {
        testServiceLogger.detachAppender(listAppender);
    }

    /**
     * When a @Service method throws a RuntimeException,
     * LoggingAspect @AfterThrowing must emit an ERROR-level log line
     * containing the method signature.
     */
    @Test
    void afterThrowingLogsErrorWithMethodNameWhenServiceThrows() {
        assertThatThrownBy(() -> testIngestionService.failingMethod())
                .isInstanceOf(RuntimeException.class);

        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs)
                .as("Expected at least one ERROR log from LoggingAspect @AfterThrowing")
                .isNotEmpty();

        boolean hasError = logs.stream()
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains("failingMethod"));

        assertThat(hasError)
                .as("Expected ERROR log containing 'failingMethod' from @AfterThrowing aspect")
                .isTrue();
    }

    /**
     * Minimal inner @Service to act as the join-point target.
     * Must be in the ingestion package hierarchy captured by LoggingAspect pointcut,
     * but here we rely on the @Service stereotype pointcut.
     */
    @Service
    static class TestIngestionService {
        public void failingMethod() {
            throw new RuntimeException("test-exception-from-failingMethod");
        }
    }
}
