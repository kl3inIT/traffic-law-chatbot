package com.vn.traffic.chatbot.chat.config;

import com.vn.traffic.chatbot.chat.advisor.GroundingGuardInputAdvisor;
import com.vn.traffic.chatbot.chat.advisor.GroundingGuardOutputAdvisor;
import com.vn.traffic.chatbot.chat.advisor.placeholder.NoOpPromptCacheAdvisor;
import com.vn.traffic.chatbot.chat.advisor.placeholder.NoOpRetrievalAdvisor;
import com.vn.traffic.chatbot.chat.advisor.placeholder.NoOpValidationAdvisor;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a {@code Map<String, ChatClient>} at startup, one entry per model in
 * {@code app.ai.models}. Each client uses a dedicated {@link OpenAiApi} instance
 * pointing to 9router at {@code app.ai.base-url} — intentionally separate from the
 * Spring AI auto-configured {@code OpenAiApi} bean used by the embedding model.
 *
 * <p>Pattern follows pacphi/spring-ai-openrouter-example (feature/spring-boot-4-migration).
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
    public Map<String, ChatClient> chatClientMap(
            AiModelProperties modelProperties,
            ChatMemory chatMemory,
            GroundingGuardInputAdvisor guardIn,
            NoOpRetrievalAdvisor noOpRag,
            NoOpPromptCacheAdvisor noOpCache,
            NoOpValidationAdvisor noOpValidation,
            GroundingGuardOutputAdvisor guardOut) {
        String defaultBaseUrl = modelProperties.baseUrl() != null
                ? modelProperties.baseUrl()
                : "https://openrouter.ai/api/v1";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMinutes(10));
        requestFactory.setReadTimeout(Duration.ofMinutes(10));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        Map<String, ChatClient> map = new LinkedHashMap<>();
        for (AiModelProperties.ModelEntry entry : modelProperties.models()) {
            String modelBaseUrl = entry.baseUrl() != null ? entry.baseUrl() : defaultBaseUrl;
            String modelApiKey = entry.apiKey() != null ? entry.apiKey() : apiKey;

            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(modelBaseUrl)
                    .apiKey(modelApiKey)
                    .restClientBuilder(restClientBuilder)
                    .build();

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(entry.id())
                    .build();

            OpenAiChatModel.Builder modelBuilder = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options);

            if (retryTemplate != null) {
                modelBuilder.retryTemplate(retryTemplate);
            }
            if (observationRegistry != null) {
                modelBuilder.observationRegistry(observationRegistry);
            }

            OpenAiChatModel chatModel = modelBuilder.build();
            ChatClient client = ChatClient.builder(chatModel)
                    .defaultAdvisors(
                            guardIn,                                              // HIGHEST_PRECEDENCE + 100
                            MessageChatMemoryAdvisor.builder(chatMemory).build(), // HIGHEST_PRECEDENCE + 200
                            noOpRag,                                              // HIGHEST_PRECEDENCE + 300
                            noOpCache,                                            // HIGHEST_PRECEDENCE + 500
                            noOpValidation,                                       // HIGHEST_PRECEDENCE + 1000
                            guardOut                                              // LOWEST_PRECEDENCE  - 100
                    )
                    .build();
            map.put(entry.id(), client);
            log.info("Registered ChatClient model={} baseUrl={}", entry.id(), modelBaseUrl);
        }
        if (map.isEmpty()) {
            log.warn("ChatClientMap is empty — check app.ai.models configuration");
        }
        log.info("Advisor chain order: {} → Memory(HIGHEST_PRECEDENCE+200) → {} → {} → {} → {}",
                guardIn.getName(),
                noOpRag.getName(),
                noOpCache.getName(),
                noOpValidation.getName(),
                guardOut.getName());
        return Collections.unmodifiableMap(map);
    }
}
