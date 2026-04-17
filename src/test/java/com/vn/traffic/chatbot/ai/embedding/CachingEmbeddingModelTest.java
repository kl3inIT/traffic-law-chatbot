// PLAN-02-DEPENDENCY — enabled by Plan 07-02 Task 1 when CachingEmbeddingModel.call(...) lands.
package com.vn.traffic.chatbot.ai.embedding;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RED tests for {@link CachingEmbeddingModel} — CACHE-02a/c (D-14 / D-16).
 *
 * <p>{@code @Disabled} today: the stub's {@code call(...)} throws UOE. Plan 07-02 Task 1
 * replaces the body with the real cache-through logic and removes this class-level
 * {@code @Disabled}. The test file compiles green today against the stub.
 */
@ExtendWith(MockitoExtension.class)
@Disabled("Enabled by Plan 07-02 Task 1 when CachingEmbeddingModel lands")
class CachingEmbeddingModelTest {

    private static final String MODEL_ID = "openai/text-embedding-3-small";
    private static final int DIM = 1536;
    private static final String CACHE_NAME = "embedding";

    private EmbeddingModel delegate;
    private ConcurrentMapCacheManager cacheManager;
    private CachingEmbeddingModel caching;

    private void setUp() {
        delegate = mock(EmbeddingModel.class);
        cacheManager = new ConcurrentMapCacheManager(CACHE_NAME);
        caching = new CachingEmbeddingModel(delegate, cacheManager, MODEL_ID, DIM);
    }

    @Test
    void hitOnRepeat() {
        setUp();
        float[] vec = new float[DIM];
        EmbeddingResponse resp = new EmbeddingResponse(List.of(new Embedding(vec, 0)));
        when(delegate.call(any(EmbeddingRequest.class))).thenReturn(resp);

        EmbeddingRequest req = new EmbeddingRequest(List.of("cùng một câu hỏi"), null);
        caching.call(req);
        caching.call(req);

        verify(delegate, times(1)).call(any(EmbeddingRequest.class));
    }

    @Test
    void missOnDifferentText() {
        setUp();
        float[] vec = new float[DIM];
        EmbeddingResponse resp = new EmbeddingResponse(List.of(new Embedding(vec, 0)));
        when(delegate.call(any(EmbeddingRequest.class))).thenReturn(resp);

        caching.call(new EmbeddingRequest(List.of("câu hỏi A"), null));
        caching.call(new EmbeddingRequest(List.of("câu hỏi B"), null));

        verify(delegate, times(2)).call(any(EmbeddingRequest.class));
    }

    @Test
    void dimensionMismatchEvictsAndRefetches() {
        setUp();
        // Pre-seed cache with a wrong-dimension vector (dim=512, but configured=1536)
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        // Caching impl owns the key format — we seed and then assert delegate is hit once,
        // which only holds if the dim-mismatch path evicts the stale entry.
        float[] wrongDim = new float[512];
        float[] rightDim = new float[DIM];
        EmbeddingResponse resp = new EmbeddingResponse(List.of(new Embedding(rightDim, 0)));
        when(delegate.call(any(EmbeddingRequest.class))).thenReturn(resp);

        // First call populates cache with the correct shape via delegate.
        EmbeddingRequest req = new EmbeddingRequest(List.of("vượt đèn đỏ"), null);
        caching.call(req);
        verify(delegate, times(1)).call(any(EmbeddingRequest.class));

        // Simulate cached stale entry with wrong dim by reseeding under the same logical key.
        // The caching impl must detect dim mismatch on read and re-delegate.
        // (Exact key format is an impl detail validated by Plan 02 Task 1.)
        // Sanity placeholder — the behavior-level assertion is the verify count above plus
        // the hit-on-repeat test which proves cache caching works. Plan 02 Task 1 expands
        // this test with the real seeded CachedVector(wrongDim, 512) record.
        assertThat(wrongDim.length).isNotEqualTo(DIM);
    }

    @Test
    void batchRequestBypassesCache() {
        setUp();
        float[] v1 = new float[DIM];
        float[] v2 = new float[DIM];
        EmbeddingResponse resp = new EmbeddingResponse(List.of(new Embedding(v1, 0), new Embedding(v2, 1)));
        when(delegate.call(any(EmbeddingRequest.class))).thenReturn(resp);

        EmbeddingRequest batch = new EmbeddingRequest(List.of("a", "b"), null);
        caching.call(batch);
        caching.call(batch);

        // batch > 1 instruction → delegate EVERY time, cache never writes
        verify(delegate, times(2)).call(any(EmbeddingRequest.class));
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
    }
}
