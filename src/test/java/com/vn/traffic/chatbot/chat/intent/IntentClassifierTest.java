package com.vn.traffic.chatbot.chat.intent;

import com.vn.traffic.chatbot.common.config.AiModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IntentClassifier} — plain JUnit + Mockito, no Spring
 * context. Covers the D-02 failure policy (fail-LEGAL on any exception or
 * null result) plus the happy path.
 */
class IntentClassifierTest {

    private static final String MODEL_ID = "m";

    private static AiModelProperties buildProps(String defaultModel) {
        return new AiModelProperties(
                "http://localhost",
                defaultModel,
                defaultModel,
                List.of(new AiModelProperties.ModelEntry(defaultModel, defaultModel, null, null, true))
        );
    }

    @Test
    void classifierFailureReturnsLegalWithZeroConfidence() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt()
                .system(any(String.class))
                .user(any(String.class))
                .call()
                .entity(eq(IntentDecision.class)))
                .thenThrow(new RuntimeException("simulated upstream failure"));

        IntentClassifier classifier = new IntentClassifier(Map.of(MODEL_ID, client), buildProps(MODEL_ID));
        IntentDecision result = classifier.classify("Vượt đèn đỏ phạt bao nhiêu?", MODEL_ID);

        assertThat(result).isNotNull();
        assertThat(result.intent()).isEqualTo(IntentDecision.Intent.LEGAL);
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void classifierNullResultReturnsLegalWithZeroConfidence() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt()
                .system(any(String.class))
                .user(any(String.class))
                .call()
                .entity(eq(IntentDecision.class)))
                .thenReturn(null);

        IntentClassifier classifier = new IntentClassifier(Map.of(MODEL_ID, client), buildProps(MODEL_ID));
        IntentDecision result = classifier.classify("Vượt đèn đỏ phạt bao nhiêu?", MODEL_ID);

        assertThat(result.intent()).isEqualTo(IntentDecision.Intent.LEGAL);
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void classifierHappyPathReturnsDelegatedIntent() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        IntentDecision stub = new IntentDecision(IntentDecision.Intent.CHITCHAT, 0.9);
        when(client.prompt()
                .system(any(String.class))
                .user(any(String.class))
                .call()
                .entity(eq(IntentDecision.class)))
                .thenReturn(stub);

        IntentClassifier classifier = new IntentClassifier(Map.of(MODEL_ID, client), buildProps(MODEL_ID));
        IntentDecision result = classifier.classify("Xin chào!", MODEL_ID);

        assertThat(result).isSameAs(stub);
        assertThat(result.intent()).isEqualTo(IntentDecision.Intent.CHITCHAT);
        assertThat(result.confidence()).isEqualTo(0.9);
    }
}
