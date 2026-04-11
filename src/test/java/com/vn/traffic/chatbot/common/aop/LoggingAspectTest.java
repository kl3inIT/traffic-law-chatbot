package com.vn.traffic.chatbot.common.aop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vn.traffic.chatbot.ingestion.support.AopTestIngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that LoggingAspect @AfterThrowing emits an ERROR log containing
 * the method name when a @Service bean in the application packages throws.
 *
 * Uses the full Spring Boot context (DB/Liquibase excluded) so that
 * @EnableAspectJAutoProxy auto-configuration is active and AOP proxies
 * are created for beans in the application package pointcut.
 *
 * AopTestIngestionService lives in com.vn.traffic.chatbot.ingestion.support,
 * satisfying applicationPackagePointcut (within(com.vn.traffic.chatbot.ingestion..)).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
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
    private AopTestIngestionService testIngestionService;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger rootLogger;

    @BeforeEach
    void setUpLogger() {
        // Attach to root logger to capture ERROR logs from any logger name the aspect uses
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogger() {
        rootLogger.detachAppender(listAppender);
    }

    /**
     * When a @Service method in the ingestion package throws a RuntimeException,
     * LoggingAspect @AfterThrowing must emit an ERROR-level log line
     * containing the method signature.
     */
    @Test
    void afterThrowingLogsErrorWithMethodNameWhenServiceThrows() {
        assertThat(AopUtils.isAopProxy(testIngestionService))
                .as("AopTestIngestionService must be AOP-proxied by LoggingAspect")
                .isTrue();

        assertThatThrownBy(() -> testIngestionService.failingMethod())
                .isInstanceOf(RuntimeException.class);

        List<ILoggingEvent> logs = listAppender.list;

        boolean hasError = logs.stream()
                .anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains("failingMethod"));

        assertThat(hasError)
                .as("Expected ERROR log containing 'failingMethod' from @AfterThrowing aspect; captured: %s",
                        logs.stream().map(e -> e.getLevel() + " " + e.getFormattedMessage()).toList())
                .isTrue();
    }
}
