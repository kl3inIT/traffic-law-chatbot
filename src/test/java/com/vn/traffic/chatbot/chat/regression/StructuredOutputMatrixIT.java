package com.vn.traffic.chatbot.chat.regression;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * RED cross-model structured-output matrix (D-05b) — iterates all 8 cataloged OpenRouter models
 * and asserts {@code .entity(LegalAnswerDraft.class)} returns a non-null record without schema 400s.
 * Implemented in Plan 08-04.
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class StructuredOutputMatrixIT {

    @Test
    void allEightCatalogedModelsReturnLegalAnswerDraft() {
        fail("RED — implemented in Plan 08-04");
    }
}
