package com.vn.traffic.chatbot.ai.config;

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
     * @param id          the model ID used in API requests (e.g. "claude-sonnet-4-6")
     * @param displayName the human-readable name shown in the UI (e.g. "Claude Sonnet 4.6")
     */
    public record ModelEntry(String id, String displayName) {}
}
