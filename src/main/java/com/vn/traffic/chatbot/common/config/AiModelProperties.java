package com.vn.traffic.chatbot.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Typed configuration properties for AI model catalog.
 *
 * <p>Bound from {@code app.ai.*} YAML namespace. Replaces the hardcoded
 * {@code AllowedModel} enum with a YAML-driven list that requires only a
 * restart (not a recompile) when the model catalog changes.
 *
 * <p>Registered via {@code @EnableConfigurationProperties(AiModelProperties.class)}
 * on {@link com.vn.traffic.chatbot.common.config.PropertiesConfig}.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiModelProperties(
        String baseUrl,
        String chatModel,
        String evaluatorModel,
        List<ModelEntry> models
) {

    /**
     * A single model entry in the catalog.
     *
     * @param id                       the model ID used in API requests (e.g. "claude-sonnet-4-6")
     * @param displayName              the human-readable name shown in the UI (e.g. "Claude Sonnet 4.6")
     * @param baseUrl                  per-model override of provider base URL (null = use top-level baseUrl)
     * @param apiKey                   per-model override of provider API key (null = use spring.ai.openai.api-key)
     * @param supportsStructuredOutput whether this model supports native JSON schema via OpenRouter
     *                                 response_format (D-03a). false = BeanOutputConverter prompt-instruction fallback.
     */
    public record ModelEntry(String id, String displayName, String baseUrl, String apiKey, boolean supportsStructuredOutput) {}
}
