package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ScenarioAnalysisResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioAnswerComposerTest {

    private final ScenarioAnswerComposer scenarioAnswerComposer = new ScenarioAnswerComposer();

    @Test
    void composesFactsRuleOutcomeStructureInsideChatResponseEnvelope() {
        ScenarioAnalysisResponse existing = new ScenarioAnalysisResponse(
                List.of("Người điều khiển dùng xe máy", "Hành vi: vượt đèn đỏ"),
                "Áp dụng Điều 7 Nghị định 168 [Nguồn 1]",
                "Có thể bị xử phạt tiền theo khung dành cho xe máy [Nguồn 1]",
                List.of("Giữ lại biên bản để đối chiếu"),
                List.of()
        );
        ChatAnswerResponse answer = new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                null,
                ResponseMode.STANDARD,
                "",
                "Người điều khiển xe máy vượt đèn đỏ có thể bị xử phạt.",
                null,
                null,
                List.of("Điều 7 Nghị định 168 [Nguồn 1]"),
                List.of("Phạt tiền [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of("Đối chiếu biên bản"),
                List.of(),
                existing,
                List.of(),
                List.of()
        );

        ScenarioAnswerComposer.ScenarioComposition composition = scenarioAnswerComposer.compose(
                answer,
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
    void groundedWithScenarioFactsProducesFinalAnalysis() {
        ScenarioAnalysisResponse existing = new ScenarioAnalysisResponse(
                List.of("Thiếu giấy tờ xe"),
                "Áp dụng Điều 58 [Nguồn 1]",
                "Có thể bị xử lý",
                List.of("Kiểm tra lại giấy tờ"),
                List.of()
        );
        ChatAnswerResponse answer = new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                null,
                ResponseMode.STANDARD,
                "",
                "Kết luận tạm thời",
                null,
                null,
                List.of("Điều 58 [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of(),
                List.of("Bổ sung thêm thông tin"),
                List.of(),
                existing,
                List.of(),
                List.of()
        );

        ScenarioAnswerComposer.ScenarioComposition composition = scenarioAnswerComposer.compose(
                answer,
                List.of()
        );

        assertThat(composition.responseMode()).isEqualTo(ResponseMode.FINAL_ANALYSIS);
        assertThat(composition.scenarioAnalysis()).isNotNull();
        assertThat(composition.scenarioAnalysis().facts()).contains("Thiếu giấy tờ xe");
        assertThat(composition.scenarioAnalysis().rule()).contains("Điều 58");
    }

    @Test
    void groundedWithoutFactsProducesScenarioAnalysisMode() {
        ChatAnswerResponse answer = new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                null,
                ResponseMode.STANDARD,
                "",
                "Kết luận tạm thời",
                null,
                null,
                List.of("Điều 58 [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of(),
                List.of("Bổ sung thêm thông tin"),
                List.of(),
                null,
                List.of(),
                List.of()
        );

        ScenarioAnswerComposer.ScenarioComposition composition = scenarioAnswerComposer.compose(
                answer,
                List.of()
        );

        assertThat(composition.responseMode()).isEqualTo(ResponseMode.SCENARIO_ANALYSIS);
        assertThat(composition.scenarioAnalysis()).isNull();
    }
}
