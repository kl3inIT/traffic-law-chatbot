package com.vn.traffic.chatbot.retrieval;

import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetrievalPolicy {

    // Safety-critical: filter expression stays hardcoded per D-13.
    // This ensures only approved, trusted, and active chunks can ever be retrieved.
    public static final String RETRIEVAL_FILTER = "approvalState == 'APPROVED' && trusted == 'true' && active == 'true'";

    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;
    private static final int DEFAULT_TOP_K = 5;

    private final ActiveParameterSetProvider paramProvider;

    public int getTopK() {
        return paramProvider.getInt("retrieval.topK", DEFAULT_TOP_K);
    }

    public double getSimilarityThreshold() {
        return paramProvider.getDouble("retrieval.similarityThreshold", DEFAULT_SIMILARITY_THRESHOLD);
    }

    public SearchRequest buildRequest(String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be null or blank");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }

        return SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(getSimilarityThreshold())
                .filterExpression(RETRIEVAL_FILTER)
                .build();
    }
}
