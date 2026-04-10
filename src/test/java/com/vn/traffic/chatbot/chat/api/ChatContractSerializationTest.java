package com.vn.traffic.chatbot.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.PendingFactResponse;
import com.vn.traffic.chatbot.chat.api.dto.RememberedFactResponse;
import com.vn.traffic.chatbot.chat.api.dto.ScenarioAnalysisResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import com.vn.traffic.chatbot.chat.service.AnswerCompositionPolicy;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesChatAnswerResponseWithLockedPhase2FieldNamesForInlineAndSourceListCitations() throws Exception {
        ChatAnswerResponse response = new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
                ResponseMode.SCENARIO_ANALYSIS,
                "Kết luận [Nguồn 1]",
                "Người điều khiển xe máy có thể bị xử phạt.",
                "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.",
                null,
                List.of("Điều 6 [Nguồn 1]"),
                List.of("Phạt tiền từ 800.000 đồng đến 1.000.000 đồng [Nguồn 1]"),
                List.of("Giấy phép lái xe"),
                List.of("Chuẩn bị giấy tờ theo yêu cầu"),
                List.of("Đối chiếu tình huống với cơ quan có thẩm quyền"),
                List.of(new PendingFactResponse("vehicleType", "Bạn điều khiển loại phương tiện nào?", "Thiếu loại phương tiện")),
                List.of(new RememberedFactResponse("vehicleType", "xe máy", "ACTIVE")),
                new ScenarioAnalysisResponse(
                        List.of("Người dùng điều khiển xe máy"),
                        List.of("Áp dụng Điều 6"),
                        List.of("Có thể bị xử phạt"),
                        List.of("Đối chiếu biên bản"),
                        List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 4, "Điều 6"))
                ),
                List.of(new CitationResponse(
                        "[Nguồn 1]",
                        "source-1",
                        "version-1",
                        "Nghị định 100",
                        "https://vbpl.vn/nd100",
                        4,
                        "Điều 6",
                        "Người điều khiển xe máy..."
                )),
                List.of(new SourceReferenceResponse(
                        "[Nguồn 1]",
                        "source-1",
                        "version-1",
                        "Nghị định 100",
                        "https://vbpl.vn/nd100",
                        4,
                        "Điều 6"
                ))
        );

        assertThat(response.threadId()).isNotNull();
        assertThat(response.responseMode()).isEqualTo(ResponseMode.SCENARIO_ANALYSIS);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.has("groundingStatus")).isTrue();
        assertThat(json.has("threadId")).isTrue();
        assertThat(json.has("responseMode")).isTrue();
        assertThat(json.has("answer")).isTrue();
        assertThat(json.has("conclusion")).isTrue();
        assertThat(json.has("disclaimer")).isTrue();
        assertThat(json.has("uncertaintyNotice")).isTrue();
        assertThat(json.has("pendingFacts")).isTrue();
        assertThat(json.has("rememberedFacts")).isTrue();
        assertThat(json.has("scenarioAnalysis")).isTrue();
        assertThat(json.has("citations")).isTrue();
        assertThat(json.has("sources")).isTrue();
        assertThat(json.has("legalBasis")).isTrue();
        assertThat(json.has("penalties")).isTrue();
        assertThat(json.has("requiredDocuments")).isTrue();
        assertThat(json.has("procedureSteps")).isTrue();
        assertThat(json.has("nextSteps")).isTrue();
    }

    @Test
    void serializesRefusalResponseWithDisclaimerAndNextSteps() throws Exception {
        ChatAnswerResponse response = new ChatAnswerResponse(
                GroundingStatus.REFUSED,
                null,
                ResponseMode.STANDARD,
                AnswerCompositionPolicy.REFUSAL_MESSAGE,
                null,
                AnswerCompositionPolicy.DEFAULT_DISCLAIMER,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NARROW_SCOPE,
                        AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NAME_DOCUMENT,
                        AnswerCompositionPolicy.REFUSAL_NEXT_STEP_VERIFY_SOURCE
                ),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of()
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("disclaimer").asText()).isEqualTo(AnswerCompositionPolicy.DEFAULT_DISCLAIMER);
        assertThat(json.get("nextSteps").isArray()).isTrue();
        assertThat(json.get("nextSteps").size()).isEqualTo(3);
        assertThat(json.get("nextSteps").get(0).asText()).isEqualTo(AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NARROW_SCOPE);
    }

    @Test
    void serializesCitationResponseWithLockedProvenanceFieldsForInlineCitationRendering() throws Exception {
        CitationResponse response = new CitationResponse(
                "[Nguồn 1]",
                "source-1",
                "version-1",
                "Luật Giao thông đường bộ",
                "https://vbpl.vn/law",
                12,
                "Điều 8",
                "Nội dung trích dẫn"
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.has("inlineLabel")).isTrue();
        assertThat(json.has("sourceId")).isTrue();
        assertThat(json.has("sourceVersionId")).isTrue();
        assertThat(json.has("sourceTitle")).isTrue();
        assertThat(json.has("origin")).isTrue();
        assertThat(json.has("pageNumber")).isTrue();
        assertThat(json.has("sectionRef")).isTrue();
        assertThat(json.has("excerpt")).isTrue();
    }

    @Test
    void serializesSourceReferenceResponseWithLockedFieldsForDedicatedSourceListPerD03AndD04() throws Exception {
        SourceReferenceResponse response = new SourceReferenceResponse(
                "[Nguồn 2]",
                "source-2",
                "version-2",
                "Thông tư 32",
                "https://vbpl.vn/circular-32",
                8,
                "Khoản 2 Điều 5"
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.has("inlineLabel")).isTrue();
        assertThat(json.has("sourceId")).isTrue();
        assertThat(json.has("sourceVersionId")).isTrue();
        assertThat(json.has("sourceTitle")).isTrue();
        assertThat(json.has("origin")).isTrue();
        assertThat(json.has("pageNumber")).isTrue();
        assertThat(json.has("sectionRef")).isTrue();
    }
}
