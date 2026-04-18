package com.vn.traffic.chatbot.chat.regression;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * RED Vietnamese 20-query regression suite (D-05).
 *
 * <p>Plan 08-04 will inject {@code org.springframework.ai.chat.evaluation.RelevancyEvaluator}
 * and {@code FactCheckingEvaluator} directly (Spring AI 2.0.0-M4 shipped these as standalone
 * {@code @Autowired} beans — the {@code BasicEvaluationTest} base class referenced in RESEARCH
 * §9.2 was removed before M4). Kept as RED stub here so the test-class skeleton exists for Plan 08-04.
 *
 * <p>CI skips via {@code excludeTags 'live'} and {@code @DisabledIfEnvironmentVariable}.
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class VietnameseRegressionIT {

    @Test
    void twentyQueryRegressionSuiteAtLeast95Percent() {
        fail("RED — implemented in Plan 08-04");
    }

    @Test
    void refusalRateWithinTenPercentOfPhase7Baseline() {
        fail("RED — implemented in Plan 08-04");
    }

    @Test
    void twoTurnConversationMemoryWorks() {
        fail("RED — implemented in Plan 08-04");
    }
}
