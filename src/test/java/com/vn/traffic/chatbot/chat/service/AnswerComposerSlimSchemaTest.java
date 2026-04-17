package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.parameter.repo.AiParameterSetRepository;
import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * D-03 / D-04 — AnswerComposer against the slimmed 8-field LegalAnswerDraft.
 *
 * <p>Enabled by Plan 07-03 Task 2 after trimming {@link LegalAnswerDraft} from 12 → 8 fields.
 */
class AnswerComposerSlimSchemaTest {

    @Test
    void composeWithEightFieldDraftProducesResponse() {
        AnswerCompositionPolicy policy = new AnswerCompositionPolicy(
                new ActiveParameterSetProvider(mock(AiParameterSetRepository.class)));
        AnswerComposer composer = new AnswerComposer(policy);

        LegalAnswerDraft draft = new LegalAnswerDraft(
                "Kết luận mẫu [Nguồn 1].",
                null,
                null,
                List.of("Điều 6 [Nguồn 1]"),
                List.of("Phạt 800k [Nguồn 1]"),
                List.of("Giấy phép"),
                List.of("Nộp phạt"),
                List.of("Kiểm tra lại")
        );

        ChatAnswerResponse response = composer.compose(
                GroundingStatus.GROUNDED,
                draft,
                List.of(),
                List.of());

        assertThat(response).isNotNull();
        assertThat(response.scenarioFacts()).isEqualTo(List.of()); // D-04: never null
    }

    @Test
    void chatAnswerResponseHonoursNullFillRule() {
        AnswerCompositionPolicy policy = new AnswerCompositionPolicy(
                new ActiveParameterSetProvider(mock(AiParameterSetRepository.class)));
        AnswerComposer composer = new AnswerComposer(policy);

        LegalAnswerDraft draft = new LegalAnswerDraft(
                "k", null, null,
                List.of(), List.of(), List.of(), List.of(), List.of());

        ChatAnswerResponse response = composer.compose(
                GroundingStatus.GROUNDED, draft, List.of(), List.of());

        assertThat(response.scenarioFacts()).isEqualTo(List.of());
        assertThat(response.scenarioFacts()).isNotNull();
    }
}
