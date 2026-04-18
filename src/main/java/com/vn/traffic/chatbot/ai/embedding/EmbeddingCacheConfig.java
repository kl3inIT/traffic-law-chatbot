package com.vn.traffic.chatbot.ai.embedding;

import java.time.Duration;
import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.vn.traffic.chatbot.common.config.AiModelProperties;

/**
 * Caffeine-backed embedding cache (CACHE-02, D-14/D-17/D-18/D-19).
 *
 * <p>JHipster-inspired pattern: single {@code @EnableCaching} class, cache-name
 * constant co-located with the consumer, per-cache Caffeine builder with
 * {@code recordStats()} (Pitfall C — required for Micrometer binding).
 *
 * <p>TTL = 30 min is a SAFETY CEILING ONLY. Correctness comes from key
 * namespacing ({@code modelId} prefix) + {@link EmbeddingModelChangedEvent}
 * write-path invalidation.
 */
@Configuration
@EnableCaching
public class EmbeddingCacheConfig {

    public static final String EMBEDDING_CACHE = "embedding";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setCacheNames(List.of(EMBEDDING_CACHE));
        mgr.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats());
        return mgr;
    }

    @Bean
    @Primary
    public EmbeddingModel cachingEmbeddingModel(EmbeddingModel delegate,
                                                CacheManager cm,
                                                AiModelProperties props,
                                                @Value("${spring.ai.vectorstore.pgvector.dimensions:1536}") int dim) {
        String modelId = (props.models() == null || props.models().isEmpty())
                ? "unknown"
                : props.models().get(0).id();
        return new CachingEmbeddingModel(delegate, cm, modelId, dim);
    }

    @Component
    @RequiredArgsConstructor
    static class EmbeddingCacheInvalidator {
        private final CacheManager cacheManager;

        @EventListener
        public void on(EmbeddingModelChangedEvent ev) {
            Cache c = cacheManager.getCache(EMBEDDING_CACHE);
            if (c != null) {
                c.clear();
            }
        }
    }
}
