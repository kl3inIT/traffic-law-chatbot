package com.vn.traffic.chatbot.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalPolicyTest {

    private final RetrievalPolicy retrievalPolicy = new RetrievalPolicy();

    @Test
    void buildRequest_containsApprovedFilter() {
        SearchRequest request = retrievalPolicy.buildRequest("query", 5);

        assertThat(request.getFilterExpression().toString())
                .contains("APPROVED");

        assertThat(RetrievalPolicy.RETRIEVAL_FILTER)
                .contains("approvalState == 'APPROVED'");
    }

    @Test
    void buildRequest_containsTrustedFilter() {
        SearchRequest request = retrievalPolicy.buildRequest("query", 5);

        assertThat(request.getFilterExpression().toString())
                .contains("trusted");

        assertThat(RetrievalPolicy.RETRIEVAL_FILTER)
                .contains("trusted == 'true'");
    }

    @Test
    void buildRequest_containsActiveFilter() {
        SearchRequest request = retrievalPolicy.buildRequest("query", 5);

        assertThat(request.getFilterExpression().toString())
                .contains("active");

        assertThat(RetrievalPolicy.RETRIEVAL_FILTER)
                .contains("active == 'true'");
    }

    @Test
    void buildRequest_setsTopKAndQuery() {
        SearchRequest request = retrievalPolicy.buildRequest("query", 5);

        assertThat(request.getQuery()).isEqualTo("query");
        assertThat(request.getTopK()).isEqualTo(5);
    }

    @Test
    void buildRequest_withNonPositiveTopK_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> retrievalPolicy.buildRequest("query", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK");
    }

    @Test
    void buildRequest_withNullOrBlankQuery_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> retrievalPolicy.buildRequest(null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");

        assertThatThrownBy(() -> retrievalPolicy.buildRequest("   ", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }
}
