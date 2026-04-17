package com.vn.traffic.chatbot.ai.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.cache.CacheManager;

/**
 * Cache-through decorator over Spring AI's {@link EmbeddingModel} (CACHE-02, D-14/D-15/D-16).
 *
 * <p>Wave-0 stub. Constructor stores fields; {@link #call(EmbeddingRequest)} throws
 * {@link UnsupportedOperationException}. All other {@link EmbeddingModel} methods
 * delegate directly to the wrapped instance so the stub compiles green without
 * needing to re-implement defaults.
 *
 * <p>Plan 07-02 Task 1 replaces {@link #call(EmbeddingRequest)} with:
 * <ul>
 *   <li>Single-instruction requests → cache lookup by
 *       {@code modelId + ":" + sha256(normalize(text))}</li>
 *   <li>Multi-instruction requests → bypass cache, delegate directly (batch ingestion)</li>
 *   <li>Dimension mismatch (cached.dim != configuredDim) → evict + delegate + repopulate</li>
 * </ul>
 */
public class CachingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final CacheManager cacheManager;
    private final String modelId;
    private final int configuredDim;

    public CachingEmbeddingModel(EmbeddingModel delegate,
                                 CacheManager cacheManager,
                                 String modelId,
                                 int configuredDim) {
        this.delegate = delegate;
        this.cacheManager = cacheManager;
        this.modelId = modelId;
        this.configuredDim = configuredDim;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        throw new UnsupportedOperationException("Wave 1 — not implemented");
    }

    @Override
    public float[] embed(Document document) {
        return delegate.embed(document);
    }

    /**
     * Cached vector payload. Carries the vector alongside its dimension so the
     * decorator can detect a model-dimension change (D-16) and evict stale entries
     * on lookup.
     */
    @SuppressWarnings("unused") // referenced by Wave 1 Plan 02 implementation
    private record CachedVector(float[] vector, int dim) {
    }
}
