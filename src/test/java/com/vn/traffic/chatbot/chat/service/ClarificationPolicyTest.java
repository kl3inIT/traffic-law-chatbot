package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.domain.ThreadFact;
import com.vn.traffic.chatbot.chat.domain.ThreadFactStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationPolicyTest {

    private final ClarificationPolicy clarificationPolicy = new ClarificationPolicy(2);

    @Test
    void blocksFinalScenarioConclusionWhenMaterialFactsAreMissing() {
        ClarificationPolicy.ClarificationDecision decision = clarificationPolicy.evaluate(
                "Tôi vượt đèn đỏ thì bị phạt thế nào?",
                List.of(ThreadFact.builder().factKey("violationType").factValue("vượt đèn đỏ").status(ThreadFactStatus.ACTIVE).build()),
                0
        );

        assertThat(decision.clarificationNeeded()).isTrue();
        assertThat(decision.shouldRefuse()).isFalse();
        assertThat(decision.pendingFacts()).extracting(p -> p.code()).contains("vehicleType");
    }

    @Test
    void proceedsToFinalAnalysisWhenPendingFactIsAnswered() {
        ClarificationPolicy.ClarificationDecision decision = clarificationPolicy.evaluate(
                "Tôi vượt đèn đỏ thì bị phạt thế nào?",
                List.of(
                        ThreadFact.builder().factKey("vehicleType").factValue("xe máy").status(ThreadFactStatus.ACTIVE).build(),
                        ThreadFact.builder().factKey("violationType").factValue("vượt đèn đỏ").status(ThreadFactStatus.ACTIVE).build()
                ),
                1
        );

        assertThat(decision.clarificationNeeded()).isFalse();
        assertThat(decision.shouldRefuse()).isFalse();
        assertThat(decision.pendingFacts()).isEmpty();
    }

    @Test
    void refusesAfterClarificationCapIsReached() {
        ClarificationPolicy.ClarificationDecision decision = clarificationPolicy.evaluate(
                "Tôi vượt đèn đỏ thì bị phạt thế nào?",
                List.of(),
                2
        );

        assertThat(decision.clarificationNeeded()).isFalse();
        assertThat(decision.shouldRefuse()).isTrue();
        assertThat(decision.pendingFacts()).isNotEmpty();
    }
}
