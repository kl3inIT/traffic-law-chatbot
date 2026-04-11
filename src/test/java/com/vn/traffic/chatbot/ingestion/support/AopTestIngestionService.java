package com.vn.traffic.chatbot.ingestion.support;

import org.springframework.stereotype.Service;

/**
 * Minimal @Service stub used exclusively by LoggingAspectTest.
 *
 * <p>Lives in com.vn.traffic.chatbot.ingestion.support so it falls within
 * LoggingAspect's applicationPackagePointcut (within(com.vn.traffic.chatbot.ingestion..))
 * AND within springBeanPointcut (within(@Service *)).
 */
@Service("aopLoggingTestIngestionService")
public class AopTestIngestionService {

    /**
     * Throws to trigger LoggingAspect @AfterThrowing in tests.
     */
    public void failingMethod() {
        throw new RuntimeException("test-exception-from-failingMethod");
    }
}
