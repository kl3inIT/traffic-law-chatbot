package com.vn.traffic.chatbot.chat.regression;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * RED scaffold for Phase 9 Task 3 — empty retrieval routes through
 * {@code GroundingGuardOutputAdvisor} refusal verbatim (D-08, Pitfall 1,
 * T-9-02 mitigation).
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class EmptyContextRefusalIT {

    @Test
    void emptyRetrievalRoutesThroughGroundingGuardRefusalVerbatim() {
        fail("RED — implement in Task 3 after RAG advisor is wired");
    }
}
