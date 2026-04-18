package com.vn.traffic.chatbot.chat.regression;

import com.vn.traffic.chatbot.chat.advisor.GroundingGuardOutputAdvisor;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.service.ChatService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9 Task 3 live IT — an off-topic / no-retrieval query must route
 * through the {@link GroundingGuardOutputAdvisor} refusal template verbatim
 * (D-08, Pitfall 1, T-9-02 mitigation). Confirms:
 *
 * <ul>
 *   <li>{@code RetrievalAugmentationAdvisor.allowEmptyContext=true} — empty
 *       retrieval does not short-circuit inside the RAG advisor.</li>
 *   <li>{@code GroundingGuardOutputAdvisor} owns the refusal wording (not the
 *       augmenter).</li>
 * </ul>
 *
 * <p>Requires a live OpenRouter-compatible LLM; gated by {@code @Tag("live")}
 * and the {@code OPENROUTER_API_KEY} env var so the default
 * {@code ./gradlew test} run skips it.
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class EmptyContextRefusalIT {

    @Autowired
    ChatService chatService;

    @Test
    void emptyRetrievalRoutesThroughGroundingGuardRefusalVerbatim() {
        // Query intentionally unrelated to Vietnamese traffic law — should yield
        // zero retrieval hits and be routed through the refusal template.
        ChatAnswerResponse resp = chatService.answer(
                "Recipe for chocolate cake with quantum physics references",
                null);

        assertThat(resp).isNotNull();
        assertThat(resp.answer())
                .as("Empty-context path must emit the GroundingGuardOutputAdvisor refusal template")
                .contains("luật giao thông Việt Nam");
    }
}
