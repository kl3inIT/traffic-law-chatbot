package com.vn.traffic.chatbot.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.retrieval.RetrievalPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final RetrievalPolicy retrievalPolicy;
    private final CitationMapper citationMapper;
    private final AnswerComposer answerComposer;
    private final ChatPromptFactory chatPromptFactory;
    @Value("${app.chat.retrieval.top-k:5}")
    private int retrievalTopK;
    @Value("${app.chat.grounding.limited-threshold:2}")
    private int limitedGroundingThreshold;

    public ChatAnswerResponse answer(String question) {
        SearchRequest request = retrievalPolicy.buildRequest(question, retrievalTopK);
        List<Document> documents = vectorStore.similaritySearch(request);
        GroundingStatus groundingStatus = determineGroundingStatus(documents.size());
        List<CitationResponse> citations = citationMapper.toCitations(documents);
        List<SourceReferenceResponse> sources = citationMapper.toSources(citations);

        if (groundingStatus == GroundingStatus.REFUSED) {
            return answerComposer.compose(groundingStatus, emptyDraft(), List.of(), List.of());
        }

        String prompt = chatPromptFactory.buildPrompt(question, groundingStatus, citations);
        String modelPayload = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        LegalAnswerDraft draft = parseDraft(modelPayload);
        return answerComposer.compose(groundingStatus, draft, citations, sources);
    }

    private GroundingStatus determineGroundingStatus(int documentCount) {
        if (documentCount <= 0) {
            return GroundingStatus.REFUSED;
        }
        if (documentCount <= limitedGroundingThreshold) {
            return GroundingStatus.LIMITED_GROUNDING;
        }
        return GroundingStatus.GROUNDED;
    }

    private LegalAnswerDraft parseDraft(String modelPayload) {
        try {
            return objectMapper.readValue(modelPayload, LegalAnswerDraft.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse legal answer draft", ex);
        }
    }

    private LegalAnswerDraft emptyDraft() {
        return new LegalAnswerDraft(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
