package com.vn.traffic.chatbot.ai.embedding;

import org.springframework.context.ApplicationEvent;

/**
 * ApplicationEvent signaling that the embedding model has changed (D-17).
 *
 * <p>Published when an admin/operator switches the configured embedding model
 * (e.g. via {@code AiModelProperties} reload). The {@code EmbeddingCacheInvalidator}
 * {@code @EventListener} clears the embedding cache on receipt — belt-and-braces
 * alongside the {@code modelId}-prefixed cache key which already namespaces entries
 * per model.
 *
 * <p>Dormant publisher note: the reload path in {@code AiModelProperties} is not yet
 * wired in v1.1; the event type + listener are wired in P7 for future use.
 */
public class EmbeddingModelChangedEvent extends ApplicationEvent {

    private final String newModelId;

    public EmbeddingModelChangedEvent(Object source, String newModelId) {
        super(source);
        this.newModelId = newModelId;
    }

    public String newModelId() {
        return newModelId;
    }
}
