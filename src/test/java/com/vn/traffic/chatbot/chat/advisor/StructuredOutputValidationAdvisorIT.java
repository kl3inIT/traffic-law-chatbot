package com.vn.traffic.chatbot.chat.advisor;

import com.vn.traffic.chatbot.chat.service.LegalAnswerDraft;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plan 09-02 (Wave 4) — retry-on-bad-JSON integration test for
 * {@link StructuredOutputValidationAdvisor} configured with
 * {@code outputType(LegalAnswerDraft.class)} and {@code maxRepeatAttempts(1)}.
 *
 * <p>Resolves Q-03 (09-RESEARCH): confirms the advisor triggers exactly two
 * chain {@code nextCall(...)} invocations on bad-then-good JSON (D-10 semantics).
 *
 * <p>Mock chain pattern adapted from {@link GroundingGuardAdvisorTest}
 * (lines 66-76); the advisor under test is instantiated directly (no Spring
 * context) and the chain is fully mocked.
 *
 * <p>{@code maxRepeatAttempts=1} in Spring AI 2.0.0-M4 means "one ADDITIONAL
 * attempt" — so 2 total LLM calls on parse failure (Pitfall 6 in 09-RESEARCH).
 */
class StructuredOutputValidationAdvisorIT {

    /**
     * A well-formed {@link LegalAnswerDraft} JSON that satisfies the advisor's
     * generated JSON schema (all 8 fields present).
     */
    private static final String GOOD_JSON =
            "{"
                    + "\"conclusion\": \"Phạt tiền từ 800.000 đến 1.000.000 đồng.\","
                    + "\"answer\": \"Theo Nghị định 100/2019/NĐ-CP.\","
                    + "\"uncertaintyNotice\": \"\","
                    + "\"legalBasis\": [\"Điều 6 Nghị định 100/2019/NĐ-CP\"],"
                    + "\"penalties\": [\"800.000 - 1.000.000 VND\"],"
                    + "\"requiredDocuments\": [],"
                    + "\"procedureSteps\": [],"
                    + "\"nextSteps\": []"
                    + "}";

    /**
     * Malformed JSON that will fail schema validation (not valid JSON at all).
     */
    private static final String BAD_JSON = "{ bad json <<";

    @Test
    void retryOnBadJsonThenSucceedOnGoodJson() {
        StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
                .outputType(LegalAnswerDraft.class)
                .maxRepeatAttempts(1)
                .advisorOrder(Ordered.HIGHEST_PRECEDENCE + 1000)
                .build();

        ChatClientRequest request = newRequest("Vượt đèn đỏ xe máy bị phạt bao nhiêu?");

        ChatClientResponse badResponse = newChatClientResponse(BAD_JSON);
        ChatClientResponse goodResponse = newChatClientResponse(GOOD_JSON);

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.copy(any())).thenReturn(chain);
        when(chain.nextCall(any(ChatClientRequest.class)))
                .thenReturn(badResponse)
                .thenReturn(goodResponse);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        verify(chain, times(2)).nextCall(any(ChatClientRequest.class));
        assertThat(result).isSameAs(goodResponse);
        assertThat(result.chatResponse()).isNotNull();
        assertThat(result.chatResponse().getResult().getOutput().getText())
                .contains("Phạt tiền từ 800.000");
    }

    @Test
    void exhaustsRetriesWhenAllCallsReturnMalformedJson() {
        StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
                .outputType(LegalAnswerDraft.class)
                .maxRepeatAttempts(1)
                .advisorOrder(Ordered.HIGHEST_PRECEDENCE + 1000)
                .build();

        ChatClientRequest request = newRequest("Vượt đèn đỏ xe máy bị phạt bao nhiêu?");

        ChatClientResponse badResponse1 = newChatClientResponse(BAD_JSON);
        ChatClientResponse badResponse2 = newChatClientResponse(BAD_JSON);

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.copy(any())).thenReturn(chain);
        when(chain.nextCall(any(ChatClientRequest.class)))
                .thenReturn(badResponse1)
                .thenReturn(badResponse2);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        // maxRepeatAttempts=1 => loop body runs while counter<=1 with counter incremented first
        // (counter: 0->1 after 1st call; 1->2 after 2nd call; 2 > 1 exits loop)
        // Therefore exactly 2 total calls even when both fail validation.
        verify(chain, times(2)).nextCall(any(ChatClientRequest.class));
        // Advisor returns the last response (still invalid) without throwing — caller
        // observes the validation failure via downstream BeanOutputConverter.
        assertThat(result).isSameAs(badResponse2);
    }

    @Test
    void advisorOrderAndNameMatchContract() {
        StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
                .outputType(LegalAnswerDraft.class)
                .maxRepeatAttempts(1)
                .advisorOrder(Ordered.HIGHEST_PRECEDENCE + 1000)
                .build();

        assertThat(advisor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1000);
        assertThat(advisor.getName()).isEqualTo("Structured Output Validation Advisor");
    }

    // --- helpers ---

    private static ChatClientRequest newRequest(String userText) {
        Prompt prompt = new Prompt(List.of(new UserMessage(userText)));
        return new ChatClientRequest(prompt, new HashMap<>());
    }

    private static ChatClientResponse newChatClientResponse(String assistantText) {
        Generation generation = new Generation(new AssistantMessage(assistantText));
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        return new ChatClientResponse(chatResponse, new HashMap<>());
    }
}
