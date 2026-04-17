package com.vn.traffic.chatbot.ai.embedding;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Cache-through decorator over Spring AI's {@link EmbeddingModel} (CACHE-02, D-14/D-15/D-16).
 *
 * <p>Overrides only {@link #call(EmbeddingRequest)} — Spring AI's default {@code embed(...)}
 * overloads funnel through {@code call(...)}, so this decorator transparently caches every
 * single-text path (the RAG query-embed hot path). The {@code embed(Document)} abstract
 * method on the interface is delegated directly.
 *
 * <p>Single-text requests are cache-keyed as
 * {@code modelId + ":" + sha256(normalize(text))} (D-15). Multi-text (batch) requests
 * bypass the cache entirely (D-14) — batches come from ingestion, where caching would
 * only hurt. A dimension-mismatch guard (D-16) evicts stale entries whose stored dim
 * does not match the configured pgvector dim.
 */
public final class CachingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final Cache cache;
    private final String modelId;
    private final int configuredDim;

    public CachingEmbeddingModel(EmbeddingModel delegate,
                                 CacheManager cacheManager,
                                 String modelId,
                                 int configuredDim) {
        this.delegate = delegate;
        // Cache-name literal matches EmbeddingCacheConfig.EMBEDDING_CACHE (D-19).
        this.cache = cacheManager.getCache("embedding");
        if (this.cache == null) {
            throw new IllegalStateException("Cache 'embedding' not found in CacheManager");
        }
        this.modelId = modelId;
        this.configuredDim = configuredDim;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        // D-14: Batch requests bypass the cache — transparent pass-through for ingestion.
        if (request.getInstructions().size() != 1) {
            return delegate.call(request);
        }

        String text = request.getInstructions().get(0);
        String key = modelId + ":" + CacheKeyNormalizer.sha256(CacheKeyNormalizer.normalize(text));

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            Object value = wrapper.get();
            if (value instanceof CachedVector cached) {
                // D-16: Dimension-mismatch guard — evict stale entry and re-delegate.
                if (cached.dim() == configuredDim) {
                    return wrapResponse(cached.vector());
                }
                cache.evict(key);
            }
        }

        EmbeddingResponse response = delegate.call(request);
        float[] vector = response.getResults().get(0).getOutput();
        cache.put(key, new CachedVector(vector, vector.length));
        return response;
    }

    @Override
    public float[] embed(Document document) {
        return delegate.embed(document);
    }

    private static EmbeddingResponse wrapResponse(float[] vector) {
        return new EmbeddingResponse(List.of(new Embedding(vector, 0)));
    }

    /**
     * Cached vector payload. Carries the vector alongside its dimension so the
     * decorator can detect a model-dimension change (D-16) and evict stale entries
     * on lookup.
     */
    private record CachedVector(float[] vector, int dim) {
    }
}
