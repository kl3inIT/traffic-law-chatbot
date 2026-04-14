package com.vn.traffic.chatbot.chat.config;

import com.vn.traffic.chatbot.ai.config.AiModelProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a {@code Map<String, ChatClient>} at startup, one entry per model in
 * {@code app.ai.models}. All clients share the same {@link OpenAiApi} bean, which
 * points to 9router at {@code spring.ai.openai.base-url}.
 *
 * <p>Pattern follows pacphi/spring-ai-openrouter-example.
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private RetryTemplate retryTemplate;

    @Bean
    public Map<String, ChatClient> chatClientMap(OpenAiApi openAiApi,
                                                  AiModelProperties modelProperties) {
        Map<String, ChatClient> map = new LinkedHashMap<>();
        for (AiModelProperties.ModelEntry entry : modelProperties.models()) {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(entry.id())
                    .build();

            OpenAiChatModel.Builder modelBuilder = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options);

            if (retryTemplate != null) {
                modelBuilder.retryTemplate(retryTemplate);
            }
            if (observationRegistry != null) {
                modelBuilder.observationRegistry(observationRegistry);
            }

            OpenAiChatModel chatModel = modelBuilder.build();
            map.put(entry.id(), ChatClient.builder(chatModel).build());
            log.info("Registered ChatClient for model: {}", entry.id());
        }
        if (map.isEmpty()) {
            log.warn("ChatClientMap is empty — check app.ai.models configuration");
        }
        return Collections.unmodifiableMap(map);
    }
}
