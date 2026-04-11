package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.parameter.repo.AiParameterSetRepository;
import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerComposerTest {

    // Use a provider backed by an empty repository so all fallback constants apply —
    // this keeps test assertions consistent with the static constant values.
    private static AnswerCompositionPolicy fallbackPolicy() {
        AiParameterSetRepository repo = mock(AiParameterSetRepository.class);
        when(repo.findByActiveTrue()).thenReturn(Optional.empty());
        ActiveParameterSetProvider provider = new ActiveParameterSetProvider(repo);
        return new AnswerCompositionPolicy(provider);
    }

    private final AnswerComposer answerComposer = new AnswerComposer(fallbackPolicy());

    @Test
    void composeGroundedResponseUsesConclusionFirstAndIncludesDisclaimer() {
        ChatAnswerResponse response = answerComposer.compose(
                GroundingStatus.GROUNDED,
                draft(
                        "Người điều khiển xe có thể bị xử phạt [Nguồn 1].",
                        List.of("Điều 6 Nghị định 100 [Nguồn 1]"),
                        List.of("Phạt tiền từ 800.000 đồng đến 1.000.000 đồng [Nguồn 1]"),
                        List.of("Giấy phép lái xe"),
                        List.of("Làm việc với cơ quan xử phạt khi được yêu cầu"),
                        List.of("Đối chiếu lại hành vi thực tế trước khi nộp phạt")
                ),
                List.of(new CitationResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 4, "Điều 6", "excerpt")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 4, "Điều 6"))
        );

        assertThat(response.answer()).startsWith("Kết luận:\nNgười điều khiển xe có thể bị xử phạt [Nguồn 1].");
        assertThat(response.answer()).contains("Căn cứ pháp lý:");
        assertThat(response.answer()).contains("Mức phạt hoặc hậu quả:");
        assertThat(response.answer()).contains("Giấy tờ hoặc thủ tục:");
        assertThat(response.answer()).contains("Các bước nên làm tiếp:");
        assertThat(response.answer()).endsWith(AnswerCompositionPolicy.DEFAULT_DISCLAIMER);
        assertThat(response.disclaimer()).isEqualTo(AnswerCompositionPolicy.DEFAULT_DISCLAIMER);
    }

    @Test
    void composeOmitsPenaltySectionWhenDraftPenaltiesAreEmpty() {
        ChatAnswerResponse response = answerComposer.compose(
                GroundingStatus.GROUNDED,
                draft(
                        "Cần mang theo giấy tờ phù hợp [Nguồn 1].",
                        List.of("Điều 58 Luật Giao thông đường bộ [Nguồn 1]"),
                        List.of(),
                        List.of("Đăng ký xe", "Giấy phép lái xe"),
                        List.of("Xuất trình giấy tờ khi kiểm tra"),
                        List.of("Kiểm tra lại hồ sơ trước khi tham gia giao thông")
                ),
                List.of(),
                List.of()
        );

        assertThat(response.penalties()).isEmpty();
        assertThat(response.answer()).doesNotContain("Mức phạt hoặc hậu quả:");
        assertThat(response.answer()).doesNotContain("Không có thông tin");
    }

    @Test
    void composeLimitedGroundingOmitsUnsupportedSectionsAndPopulatesUncertaintyNotice() {
        ChatAnswerResponse response = answerComposer.compose(
                GroundingStatus.LIMITED_GROUNDING,
                draft(
                        "Có thể áp dụng quy định về giấy tờ xe [Nguồn 1].",
                        List.of("Điều 58 [Nguồn 1]"),
                        List.of(),
                        null,
                        null,
                        List.of("Nên kiểm tra lại loại giấy tờ đang thiếu")
                ),
                List.of(new CitationResponse("[Nguồn 1]", "source-1", "version-1", "Luật Giao thông", "https://vbpl.vn/lgt", 2, "Điều 58", "excerpt")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Luật Giao thông", "https://vbpl.vn/lgt", 2, "Điều 58"))
        );

        assertThat(response.groundingStatus()).isEqualTo(GroundingStatus.LIMITED_GROUNDING);
        assertThat(response.uncertaintyNotice()).isEqualTo(AnswerCompositionPolicy.LIMITED_NOTICE);
        assertThat(response.answer()).contains("Kết luận:");
        assertThat(response.answer()).contains("Căn cứ pháp lý:");
        assertThat(response.answer()).contains("Các bước nên làm tiếp:");
        assertThat(response.answer()).doesNotContain("Mức phạt hoặc hậu quả:");
        assertThat(response.answer()).doesNotContain("Giấy tờ hoặc thủ tục:");
    }

    @Test
    void composeRefusedResponseSuppressesSubstantiveSectionsAndUsesRefusalMessage() {
        ChatAnswerResponse response = answerComposer.compose(
                GroundingStatus.REFUSED,
                draft("Không dùng", List.of("Không dùng"), List.of("Không dùng"), List.of("Không dùng"), List.of("Không dùng"), List.of("Không dùng")),
                List.of(new CitationResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 1, "Điều 1", "excerpt")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 1, "Điều 1"))
        );

        assertThat(response.groundingStatus()).isEqualTo(GroundingStatus.REFUSED);
        assertThat(response.answer()).contains("Các bước nên làm tiếp:");
        assertThat(response.answer()).contains(AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NARROW_SCOPE);
        assertThat(response.disclaimer()).isEqualTo(AnswerCompositionPolicy.DEFAULT_DISCLAIMER);
        assertThat(response.legalBasis()).isEmpty();
        assertThat(response.penalties()).isEmpty();
        assertThat(response.requiredDocuments()).isEmpty();
        assertThat(response.procedureSteps()).isEmpty();
        assertThat(response.nextSteps()).containsExactly(
                AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NARROW_SCOPE,
                AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NAME_DOCUMENT,
                AnswerCompositionPolicy.REFUSAL_NEXT_STEP_VERIFY_SOURCE
        );
    }

    @Test
    void composeUsesExactDefaultDisclaimerText() {
        ChatAnswerResponse response = answerComposer.compose(
                GroundingStatus.GROUNDED,
                draft("Kết luận mẫu [Nguồn 1].", List.of(), List.of(), List.of(), List.of(), List.of()),
                List.of(),
                List.of()
        );

        assertThat(response.disclaimer()).isEqualTo(AnswerCompositionPolicy.DEFAULT_DISCLAIMER);
    }

    @Test
    void composeStripsDuplicatedConclusionLabelFromConclusionAndAnswer() {
        ChatAnswerResponse response = answerComposer.compose(
                GroundingStatus.LIMITED_GROUNDING,
                draft(
                        "Kết luận: Người điều khiển xe mô tô vượt đèn tín hiệu màu đỏ bị xử phạt tiền từ 4.000.000 đồng đến 6.000.000 đồng. [Nguồn 1]",
                        List.of("Điều 7 Nghị định 168/2024/NĐ-CP. [Nguồn 1]"),
                        List.of("Phạt tiền từ 4.000.000 đồng đến 6.000.000 đồng. [Nguồn 1]"),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                List.of(),
                List.of()
        );

        assertThat(response.conclusion())
                .isEqualTo("Người điều khiển xe mô tô vượt đèn tín hiệu màu đỏ bị xử phạt tiền từ 4.000.000 đồng đến 6.000.000 đồng. [Nguồn 1]");
        assertThat(response.answer())
                .startsWith("Kết luận:\nNgười điều khiển xe mô tô vượt đèn tín hiệu màu đỏ bị xử phạt tiền từ 4.000.000 đồng đến 6.000.000 đồng. [Nguồn 1]")
                .doesNotContain("Kết luận:\nKết luận:");
    }

    private LegalAnswerDraft draft(
            String conclusion,
            List<String> legalBasis,
            List<String> penalties,
            List<String> requiredDocuments,
            List<String> procedureSteps,
            List<String> nextSteps
    ) {
        return new LegalAnswerDraft(
                conclusion,
                null,
                null,
                legalBasis == null ? List.of() : legalBasis,
                penalties == null ? List.of() : penalties,
                requiredDocuments == null ? List.of() : requiredDocuments,
                procedureSteps == null ? List.of() : procedureSteps,
                nextSteps == null ? List.of() : nextSteps,
                List.of(),
                null,
                null,
                List.of()
        );
    }
}
