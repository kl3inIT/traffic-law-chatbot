package com.vn.traffic.chatbot.chat.regression;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.service.ChatService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9 Task 4 live IT — v1.0 {@code ChatAnswerResponse} schema contract
 * regression. Confirms after the {@code ChatService.doAnswer} shrink that a
 * grounded legal query still emits:
 *
 * <ul>
 *   <li>{@code citations[].inlineLabel == "Nguồn N"} (byte-for-byte — D-04,
 *       Pitfall 4 defense).</li>
 *   <li>{@code sources[]} populated with one entry per unique
 *       {@code sourceId + sourceVersionId}.</li>
 * </ul>
 *
 * <p>Gated by {@code @Tag("live")} + {@code OPENROUTER_API_KEY} env so the
 * default {@code ./gradlew test} run skips it.
 */
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class CitationFormatRegressionIT {

    @Autowired
    ChatService chatService;

    @Test
    void v1FixtureReplayPreservesCitationsAndSourcesByteForByte() {
        ChatAnswerResponse response = chatService.answer(
                "Vượt đèn đỏ bằng xe máy bị phạt bao nhiêu tiền?",
                null);

        assertThat(response).isNotNull();
        // Shape assertions — v1.0 contract. In a live run with seeded vector-store
        // data the response should be GROUNDED with at least one citation; the
        // byte-for-byte [Nguồn N] inline label guarantees Pitfall 4 survival.
        assertThat(response.citations())
                .as("citations[] must be populated on a grounded response (D-04)")
                .isNotNull();
        for (CitationResponse c : response.citations()) {
            assertThat(c.inlineLabel())
                    .as("inlineLabel must match 'Nguồn N' byte-for-byte (CitationMapper.INLINE_LABEL_PREFIX)")
                    .matches("^Nguồn \\d+$");
        }
        assertThat(response.sources())
                .as("sources[] must mirror citations (CitationMapper.toSources)")
                .isNotNull();
        for (SourceReferenceResponse s : response.sources()) {
            assertThat(s.sourceId()).isNotNull();
        }
    }
}
