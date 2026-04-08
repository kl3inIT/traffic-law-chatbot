package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerComposerTest {

    private final AnswerComposer answerComposer = new AnswerComposer();

    @Test
    void composeGroundedResponseUsesConclusionFirstAndIncludesDisclaimer() {
        ChatAnswerResponse response = answerComposer.compose(
                GroundingStatus.GROUNDED,
                new LegalAnswerDraft(
                        "Người điều khiển xe có thể bị xử phạt [Nguồn 1].",
                        null,
                        null,
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
        assertThat(response.answer()).endsWith("Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.");
        assertThat(response.disclaimer()).isEqualTo("Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.");
    }

    @Test
    void composeOmitsPenaltySectionWhenDraftPenaltiesAreEmpty() {
        ChatAnswerResponse response = answerComposer.compose(
                GroundingStatus.GROUNDED,
                new LegalAnswerDraft(
                        "Cần mang theo giấy tờ phù hợp [Nguồn 1].",
                        null,
                        null,
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
                new LegalAnswerDraft(
                        "Có thể áp dụng quy định về giấy tờ xe [Nguồn 1].",
                        null,
                        null,
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
        assertThat(response.uncertaintyNotice()).isEqualTo("Một số nội dung dưới đây chỉ được trả lời trong phạm vi nguồn đã truy xuất được; các phần chưa đủ căn cứ sẽ được lược bỏ.");
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
                new LegalAnswerDraft(
                        "Không dùng",
                        null,
                        null,
                        List.of("Không dùng"),
                        List.of("Không dùng"),
                        List.of("Không dùng"),
                        List.of("Không dùng"),
                        List.of("Không dùng")
                ),
                List.of(new CitationResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 1, "Điều 1", "excerpt")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 1, "Điều 1"))
        );

        assertThat(response.groundingStatus()).isEqualTo(GroundingStatus.REFUSED);
        assertThat(response.answer()).isEqualTo("Tôi chưa thể trả lời chắc chắn vì chưa tìm thấy đủ căn cứ đáng tin cậy trong nguồn pháp lý đã được phê duyệt. Bạn hãy nêu rõ hơn câu hỏi hoặc bổ sung bối cảnh để tôi tra cứu chính xác hơn.");
        assertThat(response.disclaimer()).isEqualTo("Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.");
        assertThat(response.legalBasis()).isEmpty();
        assertThat(response.penalties()).isEmpty();
        assertThat(response.requiredDocuments()).isEmpty();
        assertThat(response.procedureSteps()).isEmpty();
        assertThat(response.nextSteps()).isEmpty();
        assertThat(response.citations()).isEmpty();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void composeUsesExactDefaultDisclaimerText() {
        ChatAnswerResponse response = answerComposer.compose(
                GroundingStatus.GROUNDED,
                new LegalAnswerDraft(
                        "Kết luận mẫu [Nguồn 1].",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                List.of(),
                List.of()
        );

        assertThat(response.disclaimer()).isEqualTo("Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.");
    }
}
