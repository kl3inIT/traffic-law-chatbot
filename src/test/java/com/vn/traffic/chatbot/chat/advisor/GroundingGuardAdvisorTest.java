package com.vn.traffic.chatbot.chat.advisor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * RED structural tests for GroundingGuard advisor pair + NoOp placeholders.
 * Implemented in Plan 08-02 (advisor chain wiring).
 */
class GroundingGuardAdvisorTest {

    @Test
    void inputAdvisorOrderIsHighestPrecedencePlus100() {
        fail("RED — implemented in Plan 08-02");
    }

    @Test
    void outputAdvisorOrderIsLowestPrecedenceMinus100() {
        fail("RED — implemented in Plan 08-02");
    }

    @Test
    void noOpRetrievalAdvisorDelegatesToChain() {
        fail("RED — implemented in Plan 08-02");
    }

    @Test
    void noOpPromptCacheAdvisorDelegatesToChain() {
        fail("RED — implemented in Plan 08-02");
    }

    @Test
    void noOpValidationAdvisorDelegatesToChain() {
        fail("RED — implemented in Plan 08-02");
    }
}
