package com.vn.traffic.chatbot.chat.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
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
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vn.traffic.chatbot.chat.domain.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final Map<String, ChatClient> chatClientMap;
    private final AiModelProperties aiModelProperties;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final RetrievalPolicy retrievalPolicy;
    private final CitationMapper citationMapper;
    private final AnswerComposer answerComposer;
    private final ChatPromptFactory chatPromptFactory;
    private final ChunkInspectionService chunkInspectionService;
    private final AnswerCompositionPolicy answerCompositionPolicy;
    private final ChatLogService chatLogService;
    private final ChatMemory chatMemory;

    @Value("${app.chat.retrieval.top-k:5}")
    private int retrievalTopK;

    /**
     * Answers a user question with conversation history for multi-turn context.
     */
    public ChatAnswerResponse answer(String question, String modelId, List<ChatMessage> conversationHistory) {
        return doAnswer(question, modelId, conversationHistory, null);
    }

    /**
     * Answers a user question using the specified model (or the default if null/unrecognized).
     * Single-turn convenience overload (no conversation history).
     *
     * @param question the user's question
     * @param modelId  optional model ID from the request body; null triggers fallback to default
     */
    public ChatAnswerResponse answer(String question, String modelId) {
        return doAnswer(question, modelId, List.of(), null);
    }

    /**
     * Answers a user question with Spring AI ChatMemory-backed conversation context.
     * The conversationId is used by {@link MessageChatMemoryAdvisor} to load/store
     * message history automatically.
     *
     * @param question       the user's question
     * @param modelId        optional model ID; null triggers fallback to default
     * @param conversationId unique conversation identifier (typically thread UUID)
     */
    public ChatAnswerResponse answer(String question, String modelId, String conversationId) {
        return doAnswer(question, modelId, List.of(), conversationId);
    }

    private ChatAnswerResponse doAnswer(String question, String modelId, List<ChatMessage> conversationHistory, String conversationId) {
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
        logger.accept(String.format("Citations mapped: %d citation(s)", citations.size()));

        List<SourceReferenceResponse> sources = citationMapper.toSources(citations);
        logger.accept(String.format("Sources mapped: %d source reference(s)", sources.size()));

        GroundingStatus groundingStatus = determineGroundingStatus(documents.size());

        // Step: grounding status
        logger.accept(String.format("Grounding: %s (%d docs)", groundingStatus.name(), documents.size()));

        boolean hasLegalCitation = containsAnyLegalCitation(citations);
        logger.accept(String.format("Legal citation check: %s", hasLegalCitation ? "passed" : "no legal signals found"));

        if (groundingStatus == GroundingStatus.REFUSED || !hasLegalCitation) {
            logger.accept("Path: refusal — grounding refused or no legal citations");
            chunkInspectionService.getRetrievalReadinessCounts();
            ChatAnswerResponse refused = refusalResponse();
            try {
                String pipelineLog = String.join("\n", logMessages);
                chatLogService.save(question, refused, GroundingStatus.REFUSED, null, 0, 0, 0, pipelineLog);
                logger.accept("Chat log: refusal entry saved");
            } catch (Exception ex) {
                log.warn("Failed to persist chat log entry for refusal: {}", ex.getMessage());
                logger.accept("Chat log: save failed — " + ex.getMessage());
            }
            return refused;
        }

        ChatClient client = resolveClient(modelId);
        long startTime = System.currentTimeMillis();
        String prompt = chatPromptFactory.buildPrompt(question, groundingStatus, citations, conversationHistory);
        logger.accept(String.format("Prompt built: %d chars", prompt.length()));

        logger.accept("LLM call: started");
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt().user(prompt);
        if (conversationId != null && !conversationId.isBlank()) {
            requestSpec = requestSpec.advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                    .conversationId(conversationId)
                    .build());
            logger.accept(String.format("ChatMemory advisor attached for conversationId=%s", conversationId));
        }
        ChatResponse chatResponse = requestSpec
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
        logger.accept(String.format("LLM response in %dms [prompt=%d, completion=%d]: %s",
                responseTime, promptTokens, completionTokens, answerPreview));

        LegalAnswerDraft draft = parseDraft(modelPayload, groundingStatus, citations, sources);
        logger.accept(String.format("Draft parsed: conclusion=%s",
                draft.conclusion() != null ? "present" : "absent (fallback)"));

        ChatAnswerResponse response = answerComposer.compose(groundingStatus, draft, citations, sources);
        logger.accept("Answer composed: done");

        try {
            String pipelineLog = String.join("\n", logMessages);
            chatLogService.save(question, response, groundingStatus, null,
                    promptTokens, completionTokens, responseTime, pipelineLog);
            logger.accept("Chat log: entry saved");
        } catch (Exception ex) {
            log.warn("Failed to persist chat log entry: {}", ex.getMessage());
            logger.accept("Chat log: save failed — " + ex.getMessage());
        }

        return response;
    }

    public ChatAnswerResponse refusalResponse() {
        return answerComposer.compose(GroundingStatus.REFUSED, emptyDraft(), List.of(), List.of());
    }

    /**
     * Resolves the ChatClient for the given modelId.
     * Fallback chain: requestedModelId → app.ai.chat-model config → first available.
     * Never throws — unrecognized modelId triggers a warning and falls back gracefully.
     */
    private ChatClient resolveClient(String requestedModelId) {
        if (requestedModelId != null && chatClientMap.containsKey(requestedModelId)) {
            return chatClientMap.get(requestedModelId);
        }
        if (requestedModelId != null && !requestedModelId.isBlank()) {
            log.warn("Unrecognized modelId '{}', falling back to default '{}'",
                    requestedModelId, aiModelProperties.chatModel());
        }
        ChatClient fallback = chatClientMap.get(aiModelProperties.chatModel());
        if (fallback == null) {
            log.warn("Default model '{}' not in chatClientMap — using first available",
                    aiModelProperties.chatModel());
            return chatClientMap.values().iterator().next();
        }
        return fallback;
    }

    private GroundingStatus determineGroundingStatus(int documentCount) {
        return documentCount <= 0 ? GroundingStatus.REFUSED : GroundingStatus.GROUNDED;
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
        String conclusion = "Chưa thể tổng hợp đầy đủ nội dung trả lời theo định dạng chuẩn; vui lòng tham khảo các căn cứ hiển thị và diễn đạt lại câu hỏi cụ thể hơn.";
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
