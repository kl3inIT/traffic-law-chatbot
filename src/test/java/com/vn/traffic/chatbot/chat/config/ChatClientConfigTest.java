package com.vn.traffic.chatbot.chat.config;

import com.vn.traffic.chatbot.chat.advisor.GroundingGuardInputAdvisor;
import com.vn.traffic.chatbot.chat.advisor.GroundingGuardOutputAdvisor;
import com.vn.traffic.chatbot.chat.advisor.placeholder.NoOpPromptCacheAdvisor;
import com.vn.traffic.chatbot.chat.advisor.placeholder.NoOpRetrievalAdvisor;
import com.vn.traffic.chatbot.chat.advisor.placeholder.NoOpValidationAdvisor;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for ChatClientConfig — verifies the Map<String, ChatClient> factory.
 * ChatClientConfig builds its own OpenAiApi pointing to 9router (app.ai.base-url),
 * so tests inject apiKey via ReflectionTestUtils.
 */
class ChatClientConfigTest {

    private AiModelProperties buildModelProperties() {
        return new AiModelProperties(
                "http://localhost:20128",
                "claude-sonnet-4-6",
                "claude-haiku-4-5-20251001",
                List.of(
                        new AiModelProperties.ModelEntry("gpt-5.4", "GPT-5.4", null, null, true),
                        new AiModelProperties.ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6", null, null, true),
                        new AiModelProperties.ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5", null, null, true)
                )
        );
    }

    private ChatClientConfig newConfig() {
        ChatClientConfig config = new ChatClientConfig();
        ReflectionTestUtils.setField(config, "apiKey", "test-key");
        return config;
    }

    private Map<String, ChatClient> buildMap(ChatClientConfig config, AiModelProperties props) {
        return config.chatClientMap(
                props,
                mock(ChatMemory.class),
                new GroundingGuardInputAdvisor(),
                new NoOpRetrievalAdvisor(),
                new NoOpPromptCacheAdvisor(),
                new NoOpValidationAdvisor(),
                new GroundingGuardOutputAdvisor());
    }

    @Test
    void chatClientMapHasExactlyThreeKeys() {
        Map<String, ChatClient> map = buildMap(newConfig(), buildModelProperties());

        assertThat(map).hasSize(3);
        assertThat(map).containsKeys("gpt-5.4", "claude-sonnet-4-6", "claude-haiku-4-5-20251001");
    }

    @Test
    void chatClientMapIsUnmodifiable() {
        Map<String, ChatClient> map = buildMap(newConfig(), buildModelProperties());

        assertThatThrownBy(() -> map.put("new-model", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void chatClientMapKeysMatchModelIds() {
        AiModelProperties props = buildModelProperties();
        Map<String, ChatClient> map = buildMap(newConfig(), props);

        List<String> expectedKeys = props.models().stream()
                .map(AiModelProperties.ModelEntry::id)
                .toList();

        assertThat(map.keySet()).containsExactlyInAnyOrderElementsOf(expectedKeys);
    }

    @Test
    void chatClientMapBeanMethodCarriesSevenParameters() throws NoSuchMethodException {
        // Signature carries: AiModelProperties + ChatMemory + 5 advisors = 7 parameters.
        Method beanMethod = null;
        for (Method m : ChatClientConfig.class.getDeclaredMethods()) {
            if (m.getName().equals("chatClientMap")) {
                beanMethod = m;
                break;
            }
        }
        assertThat(beanMethod).isNotNull();
        assertThat(beanMethod.getParameterCount()).isEqualTo(7);
        List<Class<?>> paramTypes = List.of(beanMethod.getParameterTypes());
        assertThat(paramTypes).contains(
                AiModelProperties.class,
                ChatMemory.class,
                GroundingGuardInputAdvisor.class,
                NoOpRetrievalAdvisor.class,
                NoOpPromptCacheAdvisor.class,
                NoOpValidationAdvisor.class,
                GroundingGuardOutputAdvisor.class);
    }
}
