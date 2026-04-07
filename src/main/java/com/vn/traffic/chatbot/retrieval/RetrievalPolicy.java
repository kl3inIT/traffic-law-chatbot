package com.vn.traffic.chatbot.retrieval;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;

@Component
public class RetrievalPolicy {

    public static final String RETRIEVAL_FILTER = "approvalState == 'APPROVED' && trusted == 'true' && active == 'true'";
    static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;

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
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .filterExpression(RETRIEVAL_FILTER)
                .build();
    }
}
