package com.vn.traffic.chatbot.chat.config;

import com.vn.traffic.chatbot.ai.config.AiModelProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ChatClientConfig — verifies the Map<String, ChatClient> factory.
 */
@ExtendWith(MockitoExtension.class)
class ChatClientConfigTest {

    @Mock
    private OpenAiApi openAiApi;

    private AiModelProperties buildModelProperties() {
        return new AiModelProperties(
                "claude-sonnet-4-6",
                "claude-haiku-4-5-20251001",
                List.of(
                        new AiModelProperties.ModelEntry("gpt-5.4", "GPT-5.4"),
                        new AiModelProperties.ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6"),
                        new AiModelProperties.ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5")
                )
        );
    }

    @Test
    void chatClientMapHasExactlyThreeKeys() {
        ChatClientConfig config = new ChatClientConfig();
        Map<String, ChatClient> map = config.chatClientMap(openAiApi, buildModelProperties());

        assertThat(map).hasSize(3);
        assertThat(map).containsKeys("gpt-5.4", "claude-sonnet-4-6", "claude-haiku-4-5-20251001");
    }

    @Test
    void chatClientMapIsUnmodifiable() {
        ChatClientConfig config = new ChatClientConfig();
        Map<String, ChatClient> map = config.chatClientMap(openAiApi, buildModelProperties());

        assertThatThrownBy(() -> map.put("new-model", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void chatClientMapKeysMatchModelIds() {
        ChatClientConfig config = new ChatClientConfig();
        AiModelProperties props = buildModelProperties();
        Map<String, ChatClient> map = config.chatClientMap(openAiApi, props);

        List<String> expectedKeys = props.models().stream()
                .map(AiModelProperties.ModelEntry::id)
                .toList();

        assertThat(map.keySet()).containsExactlyInAnyOrderElementsOf(expectedKeys);
    }
}
