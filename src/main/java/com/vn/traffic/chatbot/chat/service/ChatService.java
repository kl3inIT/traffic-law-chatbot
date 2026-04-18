package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.chat.intent.IntentClassifier;
import com.vn.traffic.chatbot.chat.intent.IntentDecision;
import com.vn.traffic.chatbot.chatlog.service.ChatLogService;
import com.vn.traffic.chatbot.chunk.service.ChunkInspectionService;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import com.vn.traffic.chatbot.retrieval.RetrievalPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Phase 8 ChatService — native structured output via {@code .entity(LegalAnswerDraft.class)}
 * (ARCH-02), IntentClassifier-driven dispatch BEFORE the advisor chain (ARCH-03), and
 * conditional {@link AdvisorParams#ENABLE_NATIVE_STRUCTURED_OUTPUT} wiring per the
 * per-model {@code supportsStructuredOutput} capability flag (D-03a).
 *
 * <p>All P7-era keyword heuristics and manual JSON-parsing stopgaps were removed in
 * Plan 08-03 (D-07). ArchUnit tests under {@code chat/archunit/} guard against
 * regressions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final Map<String, ChatClient> chatClientMap;
    private final AiModelProperties aiModelProperties;
    private final VectorStore vectorStore;
    private final RetrievalPolicy retrievalPolicy;
    private final CitationMapper citationMapper;
    private final AnswerComposer answerComposer;
    private final ChatPromptFactory chatPromptFactory;
    private final ChunkInspectionService chunkInspectionService;
    private final AnswerCompositionPolicy answerCompositionPolicy;
    private final ChatLogService chatLogService;
    private final ChatMemory chatMemory;
    private final IntentClassifier intentClassifier;

    @Value("${app.chat.retrieval.top-k:5}")
    private int retrievalTopK;

    /**
     * Stateless single-turn entry. conversationId = null triggers an ephemeral UUID
     * inside {@link #doAnswer(String, String, String)} so JDBC-backed ChatMemory stays
     * isolated (Pitfall 7 mitigation).
     */
    public ChatAnswerResponse answer(String question, String modelId) {
        return doAnswer(question, modelId, null);
    }

    /**
     * Multi-turn entry — {@code conversationId} is passed to
     * {@link MessageChatMemoryAdvisor} via {@link ChatMemory#CONVERSATION_ID} param.
     */
    public ChatAnswerResponse answer(String question, String modelId, String conversationId) {
        return doAnswer(question, modelId, conversationId);
    }

    private ChatAnswerResponse doAnswer(String question, String modelId, String conversationId) {
        List<String> logMessages = new ArrayList<>();
        Consumer<String> logger = msg -> {
            log.info(msg);
            logMessages.add(msg);
        };
        long startTime = System.currentTimeMillis();

        logger.accept("User prompt: " + question);

        // Step 1: Intent classification BEFORE advisor chain (ARCH-03, D-02).
        IntentDecision decision = intentClassifier.classify(question, modelId);
        logger.accept(String.format("Intent classified: %s (confidence=%.2f)",
                decision.intent(), decision.confidence()));

        switch (decision.intent()) {
            case CHITCHAT -> {
                ChatAnswerResponse resp = answerComposer.composeChitchat();
                saveLogAsync(question, resp, GroundingStatus.GROUNDED, conversationId,
                        0, 0, (int) (System.currentTimeMillis() - startTime),
                        snapshot(logMessages));
                return resp;
            }
            case OFF_TOPIC -> {
                ChatAnswerResponse resp = answerComposer.composeOffTopicRefusal();
                saveLogAsync(question, resp, GroundingStatus.REFUSED, conversationId,
                        0, 0, (int) (System.currentTimeMillis() - startTime),
                        snapshot(logMessages));
                return resp;
            }
            case LEGAL -> {
                // fall through to legal path below
            }
        }

        // Step 2: retrieval
        SearchRequest request = retrievalPolicy.buildRequest(question, retrievalTopK);
        double threshold = retrievalPolicy.getSimilarityThreshold();
        logger.accept(String.format(">>> Using vector_store [topK=%d, threshold=%.2f]: %s",
                retrievalTopK, threshold, question));

        List<Document> documents = safeDocuments(vectorStore.similaritySearch(request));

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
        logger.accept(String.format("Grounding: %s (%d docs)", groundingStatus.name(), documents.size()));

        // Plan 08-03: keyword-gate deletion — refusal now gated ONLY on grounding status.
        if (groundingStatus == GroundingStatus.REFUSED) {
            logger.accept("Path: refusal — grounding refused (no retrieved documents)");
            chunkInspectionService.getRetrievalReadinessCounts();
            ChatAnswerResponse refused = refusalResponse();
            saveLogAsync(question, refused, GroundingStatus.REFUSED, conversationId,
                    0, 0, (int) (System.currentTimeMillis() - startTime),
                    snapshot(logMessages));
            return refused;
        }

        // Step 3: prompt build + structured output via .entity(LegalAnswerDraft.class) (ARCH-02).
        ChatClient client = resolveClient(modelId);
        AiModelProperties.ModelEntry entry = resolveEntry(modelId);
        String prompt = chatPromptFactory.buildPrompt(question, groundingStatus, citations);
        logger.accept(String.format("Prompt built: %d chars", prompt.length()));

        // Pitfall 7 — ephemeral UUID when no conversationId so JDBC-backed memory stays isolated.
        String memConvId = (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : "ephemeral-" + UUID.randomUUID();

        ChatClient.ChatClientRequestSpec spec = client.prompt().user(prompt);
        if (entry.supportsStructuredOutput()) {
            spec = spec.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT);
            logger.accept("Structured output: native JSON schema (response_format)");
        } else {
            logger.accept("Structured output: prompt-instruction fallback (BeanOutputConverter)");
        }
        // Pitfall 2 — per-call memory via CONVERSATION_ID param ONLY; do NOT re-attach
        // MessageChatMemoryAdvisor (it is already in defaultAdvisors from ChatClientConfig).
        spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memConvId));

        logger.accept("LLM call: started");
        // P8: token counts via Micrometer observation (gen_ai.client.token.usage);
        // P9 may extract via ChatResponseMetadata if needed in the service layer.
        LegalAnswerDraft draft;
        try {
            draft = spec.call().entity(LegalAnswerDraft.class);
        } catch (Exception ex) {
            // D-06 — no runtime fallback drafts. A BeanOutputConverter parse
            // failure (malformed/non-JSON payload from the model) surfaces as a
            // grounded-answer failure → refusal. T-08-10 mitigation: exception
            // stack trace is NOT returned to the user; only the refusal template.
            log.warn(".entity(LegalAnswerDraft.class) failed ({}); returning grounding refusal", ex.getMessage());
            logger.accept("LLM entity parse failed: " + ex.getMessage());
            ChatAnswerResponse refused = refusalResponse();
            saveLogAsync(question, refused, GroundingStatus.REFUSED, conversationId,
                    0, 0, (int) (System.currentTimeMillis() - startTime),
                    snapshot(logMessages));
            return refused;
        }
        int responseTime = (int) (System.currentTimeMillis() - startTime);

        if (draft == null) {
            // D-06 — no runtime fallback. A null draft is a grounded-answer failure → refusal.
            logger.accept("LLM returned null draft; falling back to grounding refusal (D-06)");
            ChatAnswerResponse refused = refusalResponse();
            saveLogAsync(question, refused, GroundingStatus.REFUSED, conversationId,
                    0, 0, responseTime, snapshot(logMessages));
            return refused;
        }

        logger.accept(String.format("Draft parsed: conclusion=%s",
                draft.conclusion() != null ? "present" : "absent"));

        ChatAnswerResponse response = answerComposer.compose(groundingStatus, draft, citations, sources);
        logger.accept("Answer composed: done");

        saveLogAsync(question, response, groundingStatus, conversationId,
                0, 0, responseTime, snapshot(logMessages));
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
            if (chatClientMap.isEmpty()) {
                throw new IllegalStateException(
                        "No ChatClient available — check app.ai.models configuration");
            }
            return chatClientMap.values().iterator().next();
        }
        return fallback;
    }

    /**
     * Resolves the {@link AiModelProperties.ModelEntry} for the given modelId, mirroring
     * {@link #resolveClient(String)} fallback chain. Used to read the per-model
     * {@code supportsStructuredOutput} capability flag (D-03a).
     */
    private AiModelProperties.ModelEntry resolveEntry(String requestedModelId) {
        String resolved = (requestedModelId != null && !requestedModelId.isBlank())
                ? requestedModelId
                : aiModelProperties.chatModel();
        return aiModelProperties.models().stream()
                .filter(m -> m.id().equals(resolved))
                .findFirst()
                .orElseGet(() -> {
                    List<AiModelProperties.ModelEntry> all = aiModelProperties.models();
                    if (all == null || all.isEmpty()) {
                        throw new IllegalStateException(
                                "No ModelEntry available — check app.ai.models configuration");
                    }
                    return all.get(0);
                });
    }

    private GroundingStatus determineGroundingStatus(int documentCount) {
        return documentCount <= 0 ? GroundingStatus.REFUSED : GroundingStatus.GROUNDED;
    }

    private List<Document> safeDocuments(List<Document> documents) {
        return documents == null ? List.of() : documents;
    }

    private List<CitationResponse> safeCitations(List<CitationResponse> citations) {
        return citations == null ? List.of() : citations;
    }

    private LegalAnswerDraft emptyDraft() {
        return new LegalAnswerDraft(null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Async chat-log save wrapper that snapshots {@code logMessages} BEFORE
     * handoff (Pitfall 7 — prevents ConcurrentModificationException if the
     * caller mutates the list after this returns).
     */
    private void saveLogAsync(String question, ChatAnswerResponse response,
                              GroundingStatus groundingStatus, String conversationId,
                              int promptTokens, int completionTokens, int responseTime,
                              String pipelineLog) {
        try {
            chatLogService.save(question, response, groundingStatus, conversationId,
                    promptTokens, completionTokens, responseTime, pipelineLog);
        } catch (Exception ex) {
            log.warn("Failed to persist chat log entry: {}", ex.getMessage());
        }
    }

    private String snapshot(List<String> logMessages) {
        return String.join("\n", List.copyOf(logMessages));
    }
}
