package com.vn.traffic.chatbot.chat.config;

import com.vn.traffic.chatbot.chat.advisor.CitationPostProcessor;
import com.vn.traffic.chatbot.chat.advisor.CitationStashAdvisor;
import com.vn.traffic.chatbot.chat.advisor.GroundingGuardInputAdvisor;
import com.vn.traffic.chatbot.chat.advisor.GroundingGuardOutputAdvisor;
import com.vn.traffic.chatbot.chat.advisor.LegalQueryAugmenter;
import com.vn.traffic.chatbot.chat.advisor.PolicyAwareDocumentRetriever;
import com.vn.traffic.chatbot.chat.advisor.placeholder.NoOpPromptCacheAdvisor;
import com.vn.traffic.chatbot.chat.service.LegalAnswerDraft;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@code Map<String, ChatClient>} at startup, one entry per model in
 * {@code app.ai.models}. Each client uses a dedicated {@link OpenAiApi} instance
 * pointing to 9router at {@code app.ai.base-url} — intentionally separate from the
 * Spring AI auto-configured {@code OpenAiApi} bean used by the embedding model.
 *
 * <p>Phase 9 PR1 (Task 3): {@code NoOpRetrievalAdvisor} placeholder replaced
 * with a real {@link RetrievalAugmentationAdvisor} bean wired at
 * {@code HIGHEST_PRECEDENCE + 300}, composed of:
 * <ul>
 *   <li>{@link PolicyAwareDocumentRetriever} — per-call
 *       {@link com.vn.traffic.chatbot.retrieval.RetrievalPolicy} reads (Pitfall 5).</li>
 *   <li>{@link CitationPostProcessor} — deterministic 1..n label stamping.</li>
 *   <li>{@link LegalQueryAugmenter} — Vietnamese {@code [Nguồn n]} prompt template.</li>
 * </ul>
 * {@link CitationStashAdvisor} at +310 publishes citations/sources to
 * {@code ChatClientResponse.context()} (Q-01). {@link NoOpPromptCacheAdvisor}
 * at +500 is preserved (D-07). Chain order unchanged from Phase 8.
 *
 * <p>Phase 9 PR2 (Plan 09-02): {@code NoOpValidationAdvisor} placeholder
 * replaced with a real {@link StructuredOutputValidationAdvisor} bean at
 * {@code HIGHEST_PRECEDENCE + 1000} configured with
 * {@code outputType(LegalAnswerDraft.class)} and {@code maxRepeatAttempts(1)}
 * (D-10). Catches rare prompt-instruction-mode JSON-schema failures with a
 * single bounded retry (Pitfall 6 caps amplification at 2×; T-9-04 accept).
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
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            PolicyAwareDocumentRetriever documentRetriever,
            CitationPostProcessor citationPostProcessor,
            LegalQueryAugmenter legalQueryAugmenter) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .documentPostProcessors(citationPostProcessor)
                .queryAugmenter(legalQueryAugmenter)
                .order(Ordered.HIGHEST_PRECEDENCE + 300)
                .build();
    }

    /**
     * Plan 09-02 (D-10): real {@link StructuredOutputValidationAdvisor} replacing
     * the Phase-8 {@code NoOpValidationAdvisor} placeholder. One bounded retry on
     * JSON-schema validation failure (Pitfall 6: {@code maxRepeatAttempts=1} means
     * one ADDITIONAL attempt = 2 total LLM calls worst case). Chain slot preserved
     * at {@code HIGHEST_PRECEDENCE + 1000} (D-13).
     */
    @Bean
    public Advisor validationAdvisor() {
        return StructuredOutputValidationAdvisor.builder()
                .outputType(LegalAnswerDraft.class)
                .maxRepeatAttempts(1)
                .advisorOrder(Ordered.HIGHEST_PRECEDENCE + 1000)
                .build();
    }

    /**
     * Plan 09-05 (WR-02): shared {@link OpenAiChatModel} registry keyed by model id,
     * built once and reused by {@link #chatClientMap} and {@link #intentChatClientMap}.
     * Avoids double-building HTTP clients + observation-registered models (2N → N).
     */
    @Bean
    public Map<String, OpenAiChatModel> openAiChatModelMap(AiModelProperties modelProperties) {
        Map<String, OpenAiChatModel> map = new LinkedHashMap<>();
        for (AiModelProperties.ModelEntry entry : modelProperties.models()) {
            map.put(entry.id(), buildChatModel(entry, modelProperties));
        }
        return Collections.unmodifiableMap(map);
    }

    @Bean
    public Map<String, ChatClient> chatClientMap(
            AiModelProperties modelProperties,
            Map<String, OpenAiChatModel> openAiChatModelMap,
            ChatMemory chatMemory,
            GroundingGuardInputAdvisor guardIn,
            RetrievalAugmentationAdvisor ragAdvisor,
            CitationStashAdvisor citationStash,
            NoOpPromptCacheAdvisor noOpCache,
            Advisor validationAdvisor,
            GroundingGuardOutputAdvisor guardOut) {
        Map<String, ChatClient> map = new LinkedHashMap<>();
        for (AiModelProperties.ModelEntry entry : modelProperties.models()) {
            OpenAiChatModel chatModel = openAiChatModelMap.get(entry.id());
            ChatClient client = ChatClient.builder(chatModel)
                    .defaultAdvisors(
                            guardIn,                                              // HIGHEST_PRECEDENCE + 100
                            MessageChatMemoryAdvisor.builder(chatMemory).build(), // HIGHEST_PRECEDENCE + 200
                            ragAdvisor,                                           // HIGHEST_PRECEDENCE + 300
                            citationStash,                                        // HIGHEST_PRECEDENCE + 310
                            noOpCache,                                            // HIGHEST_PRECEDENCE + 500
                            validationAdvisor,                                    // HIGHEST_PRECEDENCE + 1000
                            guardOut                                              // LOWEST_PRECEDENCE  - 100
                    )
                    .build();
            map.put(entry.id(), client);
            log.info("Registered ChatClient model={}", entry.id());
        }
        if (map.isEmpty()) {
            log.warn("ChatClientMap is empty — check app.ai.models configuration");
        }
        log.info("Advisor chain order: {} → Memory(+200) → RAG(+300) → {}(+310) → {}(+500) → {}(+1000) → {}",
                guardIn.getName(),
                citationStash.getName(),
                noOpCache.getName(),
                validationAdvisor.getName(),
                guardOut.getName());
        List<Integer> orders = List.of(
                guardIn.getOrder(),
                Ordered.HIGHEST_PRECEDENCE + 200,
                ragAdvisor.getOrder(),
                citationStash.getOrder(),
                noOpCache.getOrder(),
                validationAdvisor.getOrder(),
                guardOut.getOrder());
        log.info("Advisor orders: {}", orders);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Plan 09-05 (G5 close-out): sibling ChatClient map used ONLY by
     * {@link com.vn.traffic.chatbot.chat.intent.IntentClassifier}. Built from the
     * same {@link OpenAiChatModel} instances as {@link #chatClientMap} but with
     * NO {@code defaultAdvisors(...)} — the main chain's
     * {@link StructuredOutputValidationAdvisor} is bound to
     * {@link LegalAnswerDraft} and would coerce {@code IntentDecision} responses
     * through the wrong schema, triggering {@code BeanOutputConverter} failure
     * and the D-02 silent fallback to {@code LEGAL}. Routing intent classification
     * through a plain client avoids that misroute.
     */
    @Bean("intentChatClientMap")
    public Map<String, ChatClient> intentChatClientMap(
            AiModelProperties modelProperties,
            Map<String, OpenAiChatModel> openAiChatModelMap) {
        Map<String, ChatClient> map = new LinkedHashMap<>();
        for (AiModelProperties.ModelEntry entry : modelProperties.models()) {
            OpenAiChatModel chatModel = openAiChatModelMap.get(entry.id());
            map.put(entry.id(), ChatClient.builder(chatModel).build());
        }
        return Collections.unmodifiableMap(map);
    }

    private OpenAiChatModel buildChatModel(
            AiModelProperties.ModelEntry entry,
            AiModelProperties modelProperties) {
        String defaultBaseUrl = modelProperties.baseUrl() != null
                ? modelProperties.baseUrl()
                : "https://openrouter.ai/api/v1";
        String modelBaseUrl = entry.baseUrl() != null ? entry.baseUrl() : defaultBaseUrl;
        String modelApiKey = entry.apiKey() != null ? entry.apiKey() : apiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMinutes(10));
        requestFactory.setReadTimeout(Duration.ofMinutes(10));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

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
        return modelBuilder.build();
    }
}
