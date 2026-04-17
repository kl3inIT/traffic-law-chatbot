package com.vn.traffic.chatbot.chat.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.fail;

/**
 * D-03 / D-04 RED tests — AnswerComposer against the slimmed 8-field LegalAnswerDraft.
 *
 * <p>{@code @Disabled} today. Plan 07-03 Task 2 will:
 * <ol>
 *   <li>Trim {@link LegalAnswerDraft} from 12 → 8 fields.</li>
 *   <li>Uncomment the real test bodies in this file (see {@code /* ...real assertions... *}{@code /}
 *       block below).</li>
 *   <li>Remove the class-level {@code @Disabled}.</li>
 * </ol>
 *
 * <p>The file compiles green today because the real assertion bodies (which reference
 * the not-yet-existing 8-arg constructor of {@code LegalAnswerDraft}) are wrapped in a
 * Java block comment. Placeholder {@code Assertions.fail(...)} keeps each {@code @Test}
 * method signature intact.
 */
@Disabled("Enabled by Plan 07-03 Task 2 when LegalAnswerDraft is slimmed")
class AnswerComposerSlimSchemaTest {

    @Test
    void composeWithEightFieldDraftProducesResponse() {
        /* --- Plan 07-03 Task 2 will enable this block and delete the placeholder below. ---
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
        --- end block --- */
        fail("Plan 07-03 Task 2 will enable this test body");
    }

    @Test
    void chatAnswerResponseHonoursNullFillRule() {
        /* --- Plan 07-03 Task 2 will enable this block and delete the placeholder below. ---
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
        --- end block --- */
        fail("Plan 07-03 Task 2 will enable this test body");
    }
}
