package com.vn.traffic.chatbot.chat.intent;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * RED live integration tests for IntentClassifier (D-01, D-02, D-09).
 * Implemented in Plan 08-02 (LLM intent classifier).
 *
 * <p>CI skips this class via the default {@code test} task's {@code excludeTags 'live'} filter
 * and {@code @DisabledIfEnvironmentVariable} when {@code OPENROUTER_API_KEY} is blank.
 * Run locally with: {@code ./gradlew liveTest}.
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class IntentClassifierIT {

    @Test
    void entityIntentDecisionReturnsNonNull() {
        fail("RED — implemented in Plan 08-02");
    }

    @Test
    void classifierFailureFallsBackToLegal() {
        fail("RED — implemented in Plan 08-02");
    }
}
