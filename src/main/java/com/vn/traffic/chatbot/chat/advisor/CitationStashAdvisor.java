package com.vn.traffic.chatbot.chat.advisor;

import com.vn.traffic.chatbot.chat.advisor.context.ChatAdvisorContextKeys;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thin CallAdvisor at HIGHEST_PRECEDENCE + 310 that reads the RAG advisor's
 * DOCUMENT_CONTEXT and publishes CitationResponse / SourceReferenceResponse
 * lists to ChatClientResponse.context() (Q-01 resolution).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class CitationStashAdvisor implements CallAdvisor {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 310;

    private final CitationMapper citationMapper;

    @Override
    public String getName() {
        return "CitationStashAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        ChatClientResponse resp = chain.nextCall(req);

        List<Document> docs = extractDocuments(resp);
        List<CitationResponse> citations = citationMapper.toCitations(docs);
        List<SourceReferenceResponse> sources = citationMapper.toSources(citations);

        resp.context().put(ChatAdvisorContextKeys.CITATIONS_KEY, citations);
        resp.context().put(ChatAdvisorContextKeys.SOURCES_KEY, sources);

        log.debug("CitationStashAdvisor published {} citations / {} sources",
                citations.size(), sources.size());
        return resp;
    }

    @SuppressWarnings("unchecked")
    private static List<Document> extractDocuments(ChatClientResponse resp) {
        Object fromContext = resp.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (fromContext instanceof List<?> list) {
            return (List<Document>) list;
        }
        if (resp.chatResponse() != null) {
            Object fromMetadata = resp.chatResponse().getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
            if (fromMetadata instanceof List<?> list) {
                return (List<Document>) list;
            }
        }
        return List.of();
    }

    @PostConstruct
    void logOrder() {
        log.info("Advisor registered: {} order={}", getName(), ORDER);
    }
}
