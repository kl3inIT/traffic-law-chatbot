package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.domain.ThreadFact;
import com.vn.traffic.chatbot.chat.domain.ThreadFactStatus;
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
class ClarificationPolicyTest {

    // Use a provider backed by an empty repository — falls back to built-in fact rules and maxClarifications=2
    private static ClarificationPolicy policyWithFallbacks() {
        AiParameterSetRepository repo = mock(AiParameterSetRepository.class);
        when(repo.findByActiveTrue()).thenReturn(Optional.empty());
        ActiveParameterSetProvider provider = new ActiveParameterSetProvider(repo);
        return new ClarificationPolicy(provider);
    }

    private final ClarificationPolicy clarificationPolicy = policyWithFallbacks();

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
