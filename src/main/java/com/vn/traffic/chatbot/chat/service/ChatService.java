package com.vn.traffic.chatbot.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.chunk.service.ChunkInspectionService;
import com.vn.traffic.chatbot.retrieval.RetrievalPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final ChunkInspectionService chunkInspectionService;
    @Value("${app.chat.retrieval.top-k:5}")
    private int retrievalTopK;
    @Value("${app.chat.grounding.limited-threshold:2}")
    private int limitedGroundingThreshold;

    public ChatAnswerResponse answer(String question) {
        SearchRequest request = retrievalPolicy.buildRequest(question, retrievalTopK);
        List<Document> documents = safeDocuments(vectorStore.similaritySearch(request));
        List<CitationResponse> citations = safeCitations(citationMapper.toCitations(documents));
        List<SourceReferenceResponse> sources = citationMapper.toSources(citations);
        GroundingStatus groundingStatus = determineGroundingStatus(documents.size());

        if (groundingStatus == GroundingStatus.REFUSED || !containsAnyLegalCitation(citations)) {
            chunkInspectionService.getRetrievalReadinessCounts();
            return refusalResponse();
        }

        String prompt = chatPromptFactory.buildPrompt(question, groundingStatus, citations);
        String modelPayload = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        LegalAnswerDraft draft = parseDraft(modelPayload, groundingStatus, citations, sources);
        return answerComposer.compose(groundingStatus, draft, citations, sources);
    }

    public ChatAnswerResponse refusalResponse() {
        return answerComposer.compose(GroundingStatus.REFUSED, emptyDraft(), List.of(), List.of());
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

    private List<Document> safeDocuments(List<Document> documents) {
        return documents == null ? List.of() : documents;
    }

    private boolean containsAnyLegalCitation(List<CitationResponse> citations) {
        return safeCitations(citations).stream().anyMatch(this::looksLikeLegalCitation);
    }

    private List<CitationResponse> safeCitations(List<CitationResponse> citations) {
        return citations == null ? List.of() : citations;
    }

    private boolean looksLikeLegalCitation(CitationResponse citation) {
        if (citation == null) {
            return false;
        }
        return containsLegalSignal(citation.sourceTitle())
                || containsLegalSignal(citation.origin())
                || containsLegalSignal(citation.sectionRef())
                || containsLegalSignal(citation.excerpt());
    }

    private boolean containsLegalSignal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("nghị định")
                || normalized.contains("luật")
                || normalized.contains("thông tư")
                || normalized.contains("nghị quyết")
                || normalized.contains("quy chuẩn")
                || normalized.contains("quy định")
                || normalized.contains("điều ")
                || normalized.contains("khoản ")
                || normalized.contains("điểm ")
                || normalized.contains("xử phạt")
                || normalized.contains("giao thông")
                || normalized.contains("đường bộ")
                || normalized.contains("biển số")
                || normalized.contains("giấy phép lái xe")
                || normalized.contains("đăng ký xe");
    }

    private LegalAnswerDraft parseDraft(
            String modelPayload,
            GroundingStatus groundingStatus,
            List<CitationResponse> citations,
            List<SourceReferenceResponse> sources
    ) {
        try {
            return objectMapper.readValue(modelPayload, LegalAnswerDraft.class);
        } catch (Exception ex) {
            return fallbackDraft(groundingStatus, citations, sources);
        }
    }

    private LegalAnswerDraft fallbackDraft(
            GroundingStatus groundingStatus,
            List<CitationResponse> citations,
            List<SourceReferenceResponse> sources
    ) {
        String conclusion = groundingStatus == GroundingStatus.LIMITED_GROUNDING
                ? "Chưa thể tổng hợp đầy đủ nội dung trả lời theo định dạng chuẩn từ các nguồn đã truy xuất; chỉ nên tham khảo các căn cứ hiển thị bên dưới."
                : "Chưa thể tổng hợp đầy đủ nội dung trả lời theo định dạng chuẩn; vui lòng tham khảo các căn cứ hiển thị và diễn đạt lại câu hỏi cụ thể hơn.";
        String uncertaintyNotice = "Phản hồi từ mô hình không đúng định dạng mong đợi, nên hệ thống chỉ trả về phần thông tin đã truy xuất được một cách an toàn.";
        List<String> legalBasis = citations.isEmpty() && sources.isEmpty()
                ? List.of()
                : List.of("Đối chiếu các nguồn trích dẫn bên dưới để xác minh căn cứ pháp lý phù hợp với tình huống cụ thể.");
        List<String> nextSteps = List.of(
                AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NARROW_SCOPE,
                AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NAME_DOCUMENT,
                AnswerCompositionPolicy.REFUSAL_NEXT_STEP_VERIFY_SOURCE
        );
        return new LegalAnswerDraft(conclusion, "", uncertaintyNotice, legalBasis, List.of(), List.of(), List.of(), nextSteps);
    }

    private LegalAnswerDraft emptyDraft() {
        return new LegalAnswerDraft(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
