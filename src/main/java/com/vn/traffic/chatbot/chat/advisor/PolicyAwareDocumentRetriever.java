package com.vn.traffic.chatbot.chat.advisor;

import com.vn.traffic.chatbot.retrieval.RetrievalPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Custom {@link DocumentRetriever} that defers {@code topK} /
 * {@code similarityThreshold} / filter expression to {@link RetrievalPolicy}
 * <b>per call</b>, rebuilding a fresh {@link SearchRequest} on every
 * {@link #retrieve(Query)} invocation.
 *
 * <p><b>Pitfall 5 (RESEARCH §8):</b> Spring AI 2.0.0-M4
 * {@code VectorStoreDocumentRetriever.Builder} binds {@code topK} +
 * {@code similarityThreshold} at construction time — policy changes at
 * runtime would not take effect. This decorator resolves that by delegating
 * directly to {@link VectorStore#similaritySearch(SearchRequest)} with a
 * policy-built request each call, preserving the safety-critical
 * {@link RetrievalPolicy#RETRIEVAL_FILTER} expression (D-13).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class PolicyAwareDocumentRetriever implements DocumentRetriever {

    private final VectorStore vectorStore;
    private final RetrievalPolicy retrievalPolicy;

    @Override
    public List<Document> retrieve(Query query) {
        String text = query == null ? null : query.text();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int topK = retrievalPolicy.getTopK();
        SearchRequest request = retrievalPolicy.buildRequest(text, topK);
        List<Document> result = vectorStore.similaritySearch(request);
        if (log.isDebugEnabled()) {
            log.debug("PolicyAwareDocumentRetriever: topK={} threshold={} found={}",
                    topK, retrievalPolicy.getSimilarityThreshold(),
                    result == null ? 0 : result.size());
        }
        return result == null ? List.of() : result;
    }
}
