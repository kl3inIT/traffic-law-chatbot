package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.RememberedFactResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioAnswerComposerTest {

    private final ScenarioAnswerComposer scenarioAnswerComposer = new ScenarioAnswerComposer();

    @Test
    void composesFactsRuleOutcomeStructureInsideChatResponseEnvelope() {
        LegalAnswerDraft draft = new LegalAnswerDraft(
                "Người điều khiển xe máy vượt đèn đỏ có thể bị xử phạt.",
                "",
                null,
                List.of("Điều 7 Nghị định 168 [Nguồn 1]"),
                List.of("Phạt tiền [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of("Đối chiếu biên bản"),
                List.of("Người điều khiển dùng xe máy", "Hành vi: vượt đèn đỏ"),
                "Áp dụng Điều 7 Nghị định 168 [Nguồn 1]",
                "Có thể bị xử phạt tiền theo khung dành cho xe máy [Nguồn 1]",
                List.of("Giữ lại biên bản để đối chiếu")
        );

        ScenarioAnswerComposer.ScenarioComposition composition = scenarioAnswerComposer.compose(
                GroundingStatus.GROUNDED,
                draft,
                List.of(new RememberedFactResponse("vehicleType", "xe máy", "ACTIVE")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
        );

        assertThat(composition.responseMode()).isEqualTo(ResponseMode.FINAL_ANALYSIS);
        assertThat(composition.scenarioAnalysis()).isNotNull();
        assertThat(composition.scenarioAnalysis().facts()).isNotEmpty();
        assertThat(composition.scenarioAnalysis().rule()).contains("Điều 7");
        assertThat(composition.scenarioAnalysis().outcome()).contains("xử phạt");
        assertThat(composition.scenarioAnalysis().actions()).contains("Giữ lại biên bản để đối chiếu", "Đối chiếu biên bản");
        assertThat(composition.scenarioAnalysis().sources()).hasSize(1);
    }

    @Test
    void doesNotFinalizeWhenGroundingIsLimited() {
        LegalAnswerDraft draft = new LegalAnswerDraft(
                "Kết luận tạm thời",
                "",
                null,
                List.of("Điều 58 [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of(),
                List.of("Bổ sung thêm thông tin"),
                List.of("Thiếu giấy tờ xe"),
                "Áp dụng Điều 58 [Nguồn 1]",
                "Có thể bị xử lý",
                List.of("Kiểm tra lại giấy tờ")
        );

        ScenarioAnswerComposer.ScenarioComposition composition = scenarioAnswerComposer.compose(
                GroundingStatus.LIMITED_GROUNDING,
                draft,
                List.of(new RememberedFactResponse("documentStatus", "không mang đăng ký xe", "ACTIVE")),
                List.of()
        );

        assertThat(composition.responseMode()).isEqualTo(ResponseMode.SCENARIO_ANALYSIS);
        assertThat(composition.scenarioAnalysis()).isNull();
    }
}
