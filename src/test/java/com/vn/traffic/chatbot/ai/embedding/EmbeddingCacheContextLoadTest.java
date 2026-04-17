package com.vn.traffic.chatbot.ai.embedding;

import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.vn.traffic.chatbot.common.config.AiModelProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @SpringBootTest} for {@link EmbeddingCacheConfig} wiring (CACHE-02d).
 *
 * <p>Uses {@code webEnvironment=NONE} + a slim {@link TestConfiguration} supplying
 * mocked {@link EmbeddingModel} and {@link AiModelProperties} beans so the embedding
 * cache graph can be exercised without booting the full application (no Postgres /
 * Liquibase / OpenAI dependencies).
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code primaryEmbeddingModelIsCachingDecorator} — proves the {@code @Primary}
 *       bean in {@link EmbeddingCacheConfig} resolves to {@link CachingEmbeddingModel}
 *       (replaces the prior bootRun smoke for Assumption A3).</li>
 *   <li>{@code embeddingModelChangedEventClearsCache} — proves
 *       {@code EmbeddingCacheInvalidator} @EventListener clears the Caffeine cache
 *       when {@link EmbeddingModelChangedEvent} is published.</li>
 * </ul>
 */
@SpringBootTest(
        classes = {
                EmbeddingCacheConfig.class,
                EmbeddingCacheContextLoadTest.TestBeans.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.ai.vectorstore.pgvector.dimensions=1536"
})
@Import(EmbeddingCacheContextLoadTest.TestBeans.class)
class EmbeddingCacheContextLoadTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void primaryEmbeddingModelIsCachingDecorator() {
        EmbeddingModel primary = applicationContext.getBean(EmbeddingModel.class);
        assertThat(primary.getClass().getSimpleName()).isEqualTo("CachingEmbeddingModel");
    }

    @Test
    void embeddingModelChangedEventClearsCache() throws InterruptedException {
        org.springframework.cache.Cache springCache = cacheManager.getCache("embedding");
        assertThat(springCache).isNotNull();

        springCache.put("sentinel-key", new Object());
        @SuppressWarnings("unchecked")
        Cache<Object, Object> native1 = (Cache<Object, Object>) springCache.getNativeCache();
        // Caffeine estimatedSize() is eventually consistent — small poll for safety.
        long before = native1.estimatedSize();
        assertThat(before).isGreaterThanOrEqualTo(1L);

        eventPublisher.publishEvent(new EmbeddingModelChangedEvent(this, "new-model-id"));

        // Allow brief async-dispatch settle. @EventListener is synchronous by default,
        // but estimatedSize() may lag briefly.
        long deadline = System.currentTimeMillis() + 500L;
        long after;
        do {
            after = native1.estimatedSize();
            if (after == 0L) {
                break;
            }
            Thread.sleep(20L);
        } while (System.currentTimeMillis() < deadline);

        assertThat(after).isZero();
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        EmbeddingModel delegateEmbeddingModel() {
            EmbeddingModel mock = Mockito.mock(EmbeddingModel.class);
            EmbeddingResponse resp = new EmbeddingResponse(
                    List.of(new Embedding(new float[1536], 0)));
            Mockito.when(mock.call(Mockito.any(EmbeddingRequest.class))).thenReturn(resp);
            return mock;
        }

        @Bean
        AiModelProperties aiModelProperties() {
            return new AiModelProperties(
                    "https://example.com",
                    "test-chat-model",
                    "test-evaluator-model",
                    List.of(new AiModelProperties.ModelEntry(
                            "test-embed-model", "Test Embed", "https://example.com", "test-key"))
            );
        }
    }
}
