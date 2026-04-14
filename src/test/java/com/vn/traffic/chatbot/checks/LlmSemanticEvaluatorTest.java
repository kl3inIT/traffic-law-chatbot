package com.vn.traffic.chatbot.checks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.ai.config.AiModelProperties;
import com.vn.traffic.chatbot.checks.evaluator.LlmSemanticEvaluator;
import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import com.vn.traffic.chatbot.parameter.service.AiParameterSetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmSemanticEvaluatorTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private AiParameterSetService parameterSetService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiModelProperties aiModelProperties;
    private Map<String, ChatClient> chatClientMap;
    private LlmSemanticEvaluator evaluator;

    @BeforeEach
    void setUp() {
        aiModelProperties = new AiModelProperties(
                "http://localhost:20128",
                "claude-sonnet-4-6",
                "claude-haiku-4-5-20251001",
                List.of(
                        new AiModelProperties.ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6"),
                        new AiModelProperties.ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5")
                )
        );
        chatClientMap = Map.of(
                "claude-sonnet-4-6", chatClient,
                "claude-haiku-4-5-20251001", chatClient
        );
        evaluator = new LlmSemanticEvaluator(chatClientMap, aiModelProperties, parameterSetService, objectMapper);
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
        // Active param set returns evaluatorModel so map lookup succeeds
        when(parameterSetService.getActive()).thenReturn(Optional.empty());

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

    @Test
    void usesEvaluatorModelFromActiveParamSet() {
        AiParameterSet paramSet = mock(AiParameterSet.class);
        when(paramSet.getEvaluatorModel()).thenReturn("claude-haiku-4-5-20251001");
        when(parameterSetService.getActive()).thenReturn(Optional.of(paramSet));

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("{\"score\":0.9,\"verdict\":\"PASS\",\"rationale\":\"good\",\"languageMatch\":true}");

        double result = evaluator.evaluate("ref", "actual");
        assertThat(result).isGreaterThan(0.0);
        verify(parameterSetService).getActive();
    }

    @Test
    void fallsBackToConfigEvaluatorModelWhenActiveParamSetEmpty() {
        when(parameterSetService.getActive()).thenReturn(Optional.empty());

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("{\"score\":0.7,\"verdict\":\"PARTIAL\",\"rationale\":\"ok\",\"languageMatch\":true}");

        double result = evaluator.evaluate("ref", "actual");
        // Falls back to aiModelProperties.evaluatorModel() = "claude-haiku-4-5-20251001"
        assertThat(result).isGreaterThan(0.0);
        verify(parameterSetService).getActive();
    }
}
