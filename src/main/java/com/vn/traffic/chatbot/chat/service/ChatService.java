package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.advisor.context.ChatAdvisorContextKeys;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.intent.IntentClassifier;
import com.vn.traffic.chatbot.chat.intent.IntentDecision;
import com.vn.traffic.chatbot.chatlog.service.ChatLogService;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 9 ChatService — thin legal-answer orchestrator. Retrieval, citation
 * stamping, Vietnamese {@code [Nguồn n]} prompt augmentation, and refusal
 * wording are fully owned by the modular RAG advisor chain (D-04, D-08):
 *
 * <ul>
 *   <li>{@code RetrievalAugmentationAdvisor} at {@code HIGHEST_PRECEDENCE + 300}
 *       — {@code PolicyAwareDocumentRetriever} + {@code CitationPostProcessor}
 *       + {@code LegalQueryAugmenter}.</li>
 *   <li>{@code CitationStashAdvisor} at {@code HIGHEST_PRECEDENCE + 310} —
 *       publishes citations + sources to {@link ChatClientResponse#context()}.</li>
 *   <li>{@code GroundingGuardOutputAdvisor} — owns empty-context refusal (D-08,
 *       T-9-02). {@code ChatService} no longer gates refusal by hit count.</li>
 * </ul>
 *
 * <p>ARCH-03 (Plan 08-03): {@link IntentClassifier} dispatch runs BEFORE the
 * advisor chain for CHITCHAT / OFF_TOPIC short-circuit; LEGAL intent flows
 * through {@link ChatClient#prompt()} ...{@code call().chatClientResponse()}.
 *
 * <p>ARCH-02: structured output via {@link BeanOutputConverter} applied to the
 * chat response text; {@link AdvisorParams#ENABLE_NATIVE_STRUCTURED_OUTPUT}
 * activates the native JSON schema path when the model supports it (D-03a).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final BeanOutputConverter<LegalAnswerDraft> DRAFT_CONVERTER =
            new BeanOutputConverter<>(LegalAnswerDraft.class);

    // Low-confidence intent falls through to LEGAL RAG — grounding gate will refuse
    // if no citations, which is safer than mis-routing an ambiguous question to
    // CHITCHAT/OFF_TOPIC.
    static final double INTENT_CONFIDENCE_THRESHOLD = 0.6;

    private final Map<String, ChatClient> chatClientMap;
    private final AiModelProperties aiModelProperties;
    private final AnswerComposer answerComposer;
    private final ChatLogService chatLogService;
    private final ChatMemory chatMemory;
    private final IntentClassifier intentClassifier;

    public ChatAnswerResponse answer(String question, String modelId) {
        return doAnswer(question, modelId, null);
    }

    public ChatAnswerResponse answer(String question, String modelId, String conversationId) {
        return doAnswer(question, modelId, conversationId);
    }

    private ChatAnswerResponse doAnswer(String question, String modelId, String conversationId) {
        long t0 = System.currentTimeMillis();
        IntentDecision decision = intentClassifier.classify(question, modelId);
        IntentDecision.Intent effective = decision.confidence() < INTENT_CONFIDENCE_THRESHOLD
                ? IntentDecision.Intent.LEGAL
                : decision.intent();
        log.info("Intent classify: raw={} confidence={} effective={}",
                decision.intent(), decision.confidence(), effective);
        switch (effective) {
            case CHITCHAT -> { return persisted(question, answerComposer.composeChitchat(), GroundingStatus.GROUNDED, conversationId, t0, null); }
            case OFF_TOPIC -> { return persisted(question, answerComposer.composeOffTopicRefusal(), GroundingStatus.REFUSED, conversationId, t0, null); }
            case LEGAL -> { /* fall through */ }
        }
        AiModelProperties.ModelEntry entry = resolveEntry(modelId);
        String memConvId = buildMemoryConversationId(conversationId);
        ChatClient.ChatClientRequestSpec spec = resolveClient(modelId).prompt().user(question);
        if (entry.supportsStructuredOutput()) spec = spec.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT);
        spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memConvId));
        ChatClientResponse resp;
        try { resp = spec.call().chatClientResponse(); }
        catch (Exception ex) {
            log.warn("ChatClient call failed ({}); returning grounding refusal", ex.getMessage());
            return persisted(question, refusalResponse(), GroundingStatus.REFUSED, conversationId, t0, null);
        }
        ChatResponse chatResponse = resp.chatResponse();
        List<CitationResponse> citations = readList(resp, ChatAdvisorContextKeys.CITATIONS_KEY);
        List<SourceReferenceResponse> sources = readList(resp, ChatAdvisorContextKeys.SOURCES_KEY);
        GroundingStatus status = citations.isEmpty() ? GroundingStatus.REFUSED : GroundingStatus.GROUNDED;
        LegalAnswerDraft draft = convertDraft(chatResponse);
        if (draft == null || status == GroundingStatus.REFUSED) {
            return persisted(question, refusalResponse(), GroundingStatus.REFUSED, conversationId, t0, chatResponse);
        }
        return persisted(question, answerComposer.compose(status, draft, citations, sources), status, conversationId, t0, chatResponse);
    }

    static String buildMemoryConversationId(String callerConversationId) {
        return (callerConversationId != null && !callerConversationId.isBlank())
                ? callerConversationId : UUID.randomUUID().toString();
    }

    private ChatAnswerResponse persisted(String question, ChatAnswerResponse response,
                                         GroundingStatus status, String conversationId, long t0,
                                         ChatResponse chatResponse) {
        persist(question, response, status, conversationId, t0, chatResponse);
        return response;
    }

    public ChatAnswerResponse refusalResponse() {
        return answerComposer.compose(GroundingStatus.REFUSED, emptyDraft(), List.of(), List.of());
    }

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
            if (chatClientMap.isEmpty()) {
                throw new IllegalStateException(
                        "No ChatClient available — check app.ai.models configuration");
            }
            return chatClientMap.values().iterator().next();
        }
        return fallback;
    }

    private AiModelProperties.ModelEntry resolveEntry(String requestedModelId) {
        String resolved = (requestedModelId != null && !requestedModelId.isBlank())
                ? requestedModelId : aiModelProperties.chatModel();
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

    @SuppressWarnings("unchecked")
    private static <T> List<T> readList(ChatClientResponse resp, String key) {
        Object val = resp.context().get(key);
        return (val instanceof List<?> list) ? (List<T>) list : List.of();
    }

    private static LegalAnswerDraft convertDraft(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        String text = chatResponse.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return DRAFT_CONVERTER.convert(text);
        } catch (Exception ex) {
            log.warn("BeanOutputConverter failed ({}); returning grounding refusal", ex.getMessage());
            return null;
        }
    }

    private LegalAnswerDraft emptyDraft() {
        return new LegalAnswerDraft(null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private void persist(String question, ChatAnswerResponse response,
                         GroundingStatus status, String conversationId, long startTime,
                         ChatResponse chatResponse) {
        try {
            int[] tokens = extractTokens(chatResponse);
            chatLogService.save(question, response, status, conversationId,
                    tokens[0], tokens[1], (int) (System.currentTimeMillis() - startTime), "");
        } catch (Exception ex) {
            log.warn("Failed to persist chat log entry: {}", ex.getMessage());
        }
    }

    private static int[] extractTokens(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) return new int[]{0, 0};
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) return new int[]{0, 0};
        Integer p = usage.getPromptTokens();
        Integer c = usage.getCompletionTokens();
        return new int[]{p != null ? p : 0, c != null ? c : 0};
    }
}
