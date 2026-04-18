package com.vn.traffic.chatbot.chat.regression;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Phase 8 Plan 4 (Wave 3) — cross-model structured-output matrix (D-05b).
 *
 * <p>Iterates every row of {@code app.ai.models} (all 8 cataloged OpenRouter models
 * per {@code application.yaml}) and asserts that {@link ChatService#answer(String, String, String)}
 * — which calls {@code .entity(LegalAnswerDraft.class)} under the hood — returns a
 * non-null, non-blank {@link ChatAnswerResponse} without throwing schema 400s.
 *
 * <p>If a row with {@code supportsStructuredOutput=true} 400s on
 * {@code response_format: json_schema} through OpenRouter (assumptions A1/A2 in
 * RESEARCH §3), the remediation is to flip that YAML flag to {@code false} and
 * re-run the matrix — not to loosen this assertion. Any flip must be recorded in
 * {@code 08-04-SUMMARY.md}.
 *
 * <p>Note: {@code ChatService} routes structured-output parse failures to a
 * grounding-refusal response (Plan 08-03 Rule 2 fix) rather than propagating 500s.
 * The assertion therefore verifies a non-blank answer body — a REFUSED response
 * still has a non-blank refusal template.
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class StructuredOutputMatrixIT {

    @Autowired
    ChatService chatService;

    @Autowired
    AiModelProperties properties;

    @Test
    void allEightCatalogedModelsReturnLegalAnswerDraft() {
        assertThat(properties.models())
                .as("8 cataloged OpenRouter models")
                .hasSize(8);

        for (AiModelProperties.ModelEntry entry : properties.models()) {
            assertThatCode(() -> {
                ChatAnswerResponse r = chatService.answer(
                        "Vượt đèn đỏ phạt bao nhiêu với xe máy?",
                        entry.id(),
                        null);
                assertThat(r)
                        .as("model=%s returned null ChatAnswerResponse", entry.id())
                        .isNotNull();
                assertThat(r.answer())
                        .as("model=%s (supportsStructuredOutput=%s) returned blank answer",
                                entry.id(), entry.supportsStructuredOutput())
                        .isNotBlank();
            })
                    .as("model=%s (supportsStructuredOutput=%s)",
                            entry.id(), entry.supportsStructuredOutput())
                    .doesNotThrowAnyException();
        }
    }
}
