package com.vn.traffic.chatbot.chat.config;

import com.vn.traffic.chatbot.ai.config.AiModelProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a {@code Map<String, ChatClient>} at startup, one entry per model in
 * {@code app.ai.models}. Each client uses a dedicated {@link OpenAiApi} instance
 * pointing to 9router at {@code app.ai.base-url} — intentionally separate from the
 * Spring AI auto-configured {@code OpenAiApi} bean used by the embedding model.
 *
 * <p>Pattern follows pacphi/spring-ai-openrouter-example.
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    @Value("${spring.ai.openai.api-key:none}")
    private String apiKey;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private RetryTemplate retryTemplate;

    @Bean
    public Map<String, ChatClient> chatClientMap(AiModelProperties modelProperties) {
        String baseUrl = modelProperties.baseUrl() != null
                ? modelProperties.baseUrl()
                : "http://localhost:20128";
        OpenAiApi nineRouterApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        log.info("ChatClientConfig: using 9router base-url={}", baseUrl);

        Map<String, ChatClient> map = new LinkedHashMap<>();
        for (AiModelProperties.ModelEntry entry : modelProperties.models()) {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(entry.id())
                    .build();

            OpenAiChatModel.Builder modelBuilder = OpenAiChatModel.builder()
                    .openAiApi(nineRouterApi)
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
