package com.vn.traffic.chatbot.chat.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.chatlog.service.ChatLogService;
import com.vn.traffic.chatbot.chunk.service.ChunkInspectionService;
import com.vn.traffic.chatbot.retrieval.RetrievalPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
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
    private final AnswerCompositionPolicy answerCompositionPolicy;
    private final ChatLogService chatLogService;

    @Value("${app.chat.retrieval.top-k:5}")
    private int retrievalTopK;
    @Value("${app.chat.grounding.limited-threshold:2}")
    private int limitedGroundingThreshold;

    public ChatAnswerResponse answer(String question) {
        List<String> logMessages = new ArrayList<>();
        Consumer<String> logger = msg -> {
            log.info(msg);
            logMessages.add(msg);
        };

        // Step: user prompt
        logger.accept("User prompt: " + question);

        // Step: retrieval
        SearchRequest request = retrievalPolicy.buildRequest(question, retrievalTopK);
        double threshold = retrievalPolicy.getSimilarityThreshold();
        logger.accept(String.format(">>> Using vector_store [topK=%d, threshold=%.2f]: %s",
                retrievalTopK, threshold, question));

        List<Document> documents = safeDocuments(vectorStore.similaritySearch(request));

        // Step: found documents summary
        String docSummary = documents.stream()
                .map(doc -> {
                    Double score = doc.getScore();
                    String scoreStr = score != null ? String.format("(%.3f)", score) : "(?.???)";
                    return scoreStr + " " + doc.getId();
                })
                .collect(Collectors.joining(", "));
        logger.accept(String.format("Found %d documents: [%s]", documents.size(), docSummary));

        List<CitationResponse> citations = safeCitations(citationMapper.toCitations(documents));
        List<SourceReferenceResponse> sources = citationMapper.toSources(citations);
        GroundingStatus groundingStatus = determineGroundingStatus(documents.size());

        // Step: grounding status
        logger.accept(String.format("Grounding: %s (%d docs)", groundingStatus.name(), documents.size()));

        if (groundingStatus == GroundingStatus.REFUSED || !containsAnyLegalCitation(citations)) {
            chunkInspectionService.getRetrievalReadinessCounts();
            ChatAnswerResponse refused = refusalResponse();
            try {
                String pipelineLog = String.join("\n", logMessages);
                chatLogService.save(question, refused, GroundingStatus.REFUSED, null, 0, 0, 0, pipelineLog);
            } catch (Exception ex) {
                log.warn("Failed to persist chat log entry for refusal: {}", ex.getMessage());
            }
            return refused;
        }

        long startTime = System.currentTimeMillis();
        String prompt = chatPromptFactory.buildPrompt(question, groundingStatus, citations);
        ChatResponse chatResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();

        String modelPayload = chatResponse.getResult().getOutput().getText();
        var usage = chatResponse.getMetadata().getUsage();
        int promptTokens = usage != null ? (int) usage.getPromptTokens() : 0;
        int completionTokens = usage != null ? (int) usage.getCompletionTokens() : 0;
        int responseTime = (int) (System.currentTimeMillis() - startTime);

        // Step: response summary (preview first 120 chars of answer)
        String answerPreview = modelPayload != null && modelPayload.length() > 120
                ? modelPayload.substring(0, 120) + "…"
                : modelPayload;
        logger.accept(String.format("Response in %dms [prompt=%d, completion=%d]: %s",
                responseTime, promptTokens, completionTokens, answerPreview));

        LegalAnswerDraft draft = parseDraft(modelPayload, groundingStatus, citations, sources);
        ChatAnswerResponse response = answerComposer.compose(groundingStatus, draft, citations, sources);

        try {
            String pipelineLog = String.join("\n", logMessages);
            chatLogService.save(question, response, groundingStatus, null,
                    promptTokens, completionTokens, responseTime, pipelineLog);
        } catch (Exception ex) {
            log.warn("Failed to persist chat log entry: {}", ex.getMessage());
        }

        return response;
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
            ObjectMapper lenient = objectMapper.copy()
                    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return lenient.readValue(extractJson(modelPayload), LegalAnswerDraft.class);
        } catch (Exception ex) {
            log.warn("parseDraft failed ({}), raw payload: {}", ex.getMessage(), modelPayload);
            return fallbackDraft(groundingStatus, citations, sources);
        }
    }

    private String extractJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        String text = payload.trim();
        // Strip markdown code block wrapper (```json ... ``` or ``` ... ```)
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1).trim();
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence).trim();
            }
        }
        // Extract JSON object from potentially mixed content
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
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
        List<String> nextSteps = answerCompositionPolicy.getRefusalNextSteps();
        return new LegalAnswerDraft(
                conclusion,
                "",
                uncertaintyNotice,
                legalBasis,
                List.of(),
                List.of(),
                List.of(),
                nextSteps,
                List.of(),
                null,
                null,
                List.of()
        );
    }

    private LegalAnswerDraft emptyDraft() {
        return new LegalAnswerDraft(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, List.of());
    }
}
