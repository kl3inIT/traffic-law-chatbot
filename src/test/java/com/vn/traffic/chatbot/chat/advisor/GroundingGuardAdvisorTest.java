package com.vn.traffic.chatbot.chat.advisor;

import com.vn.traffic.chatbot.chat.advisor.placeholder.NoOpPromptCacheAdvisor;
import com.vn.traffic.chatbot.chat.advisor.placeholder.NoOpValidationAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Structural tests for the GroundingGuard advisor pair + NoOp placeholders
 * (Plan 08-02, Wave 1). Plain JUnit + AssertJ + Mockito — no Spring context.
 */
class GroundingGuardAdvisorTest {

    @Test
    void inputAdvisorOrderIsHighestPrecedencePlus100() {
        GroundingGuardInputAdvisor advisor = new GroundingGuardInputAdvisor();

        assertThat(advisor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 100);
        assertThat(advisor.getName()).isEqualTo("GroundingGuardInputAdvisor");
        assertThat(GroundingGuardInputAdvisor.FORCE_REFUSAL).isEqualTo("chat.guard.forceRefusal");
    }

    @Test
    void outputAdvisorOrderIsLowestPrecedenceMinus100() {
        GroundingGuardOutputAdvisor advisor = new GroundingGuardOutputAdvisor();

        assertThat(advisor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 100);
        assertThat(advisor.getName()).isEqualTo("GroundingGuardOutputAdvisor");
        assertThat(GroundingGuardOutputAdvisor.REFUSAL_TEMPLATE)
                .contains("luật giao thông Việt Nam");
    }

    @Test
    void noOpPromptCacheAdvisorDelegatesToChain() {
        NoOpPromptCacheAdvisor advisor = new NoOpPromptCacheAdvisor();
        assertThat(advisor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 500);
        assertDelegatesUnchanged(advisor::adviseCall);
    }

    @Test
    void noOpValidationAdvisorDelegatesToChain() {
        NoOpValidationAdvisor advisor = new NoOpValidationAdvisor();
        assertThat(advisor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1000);
        assertDelegatesUnchanged(advisor::adviseCall);
    }

    // --- helpers ---

    @FunctionalInterface
    private interface AdviseCallFn {
        ChatClientResponse apply(ChatClientRequest req, CallAdvisorChain chain);
    }

    private static void assertDelegatesUnchanged(AdviseCallFn fn) {
        ChatClientRequest req = mock(ChatClientRequest.class);
        ChatClientResponse resp = mock(ChatClientResponse.class);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(resp);

        ChatClientResponse actual = fn.apply(req, chain);

        assertThat(actual).isSameAs(resp);
        verify(chain, times(1)).nextCall(req);
        verifyNoMoreInteractions(chain);
    }
}
