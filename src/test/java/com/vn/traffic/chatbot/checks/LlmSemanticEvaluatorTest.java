package com.vn.traffic.chatbot.checks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.checks.evaluator.LlmSemanticEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmSemanticEvaluatorTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmSemanticEvaluator evaluator;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        evaluator = new LlmSemanticEvaluator(chatClientBuilder, objectMapper);
    }

    @Test
    void testParsesScoreFromValidJsonResponse() {
        String json = "{\"score\":0.85,\"verdict\":\"PASS\",\"rationale\":\"good\",\"languageMatch\":true}";
        double result = evaluator.parseScore(json);
        assertThat(result).isEqualTo(0.85, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void testLanguageMismatchCapScore() {
        String json = "{\"score\":0.9,\"verdict\":\"PASS\",\"rationale\":\"ok\",\"languageMatch\":false}";
        double result = evaluator.parseScore(json);
        assertThat(result).isLessThanOrEqualTo(0.2);
    }

    @Test
    void testReturnsZeroOnLlmFailure() {
        // Mock the full ChatClient chain to throw on call
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenThrow(new RuntimeException("LLM unavailable"));

        double result = evaluator.evaluate("reference answer text", "actual answer text");
        assertThat(result).isEqualTo(0.0);
    }
}
