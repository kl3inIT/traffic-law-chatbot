package com.vn.traffic.chatbot.chat.intent;

import com.vn.traffic.chatbot.common.config.AiModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * LLM-backed intent classifier (ARCH-03, D-01, D-02, D-09).
 *
 * <p>Uses the SAME OpenRouter chat model as the main answer call (D-01). On
 * any exception (timeout, parse error, null result) returns
 * {@code IntentDecision(LEGAL, 0.0)} — safer than dropping to chitchat,
 * falls through to the grounding gate (D-02).
 */
@Slf4j
@Service
public class IntentClassifier {

    static final String INTENT_SYSTEM_VI = """
            Bạn là bộ phân loại ý định. Phân loại tin nhắn của người dùng thành một trong ba giá trị:
            - LEGAL: câu hỏi về luật giao thông Việt Nam (mức phạt, thủ tục, giấy tờ, quy định, điều khoản).
            - CHITCHAT: chào hỏi, cảm ơn, nói chuyện xã giao không liên quan đến luật.
            - OFF_TOPIC: câu hỏi về lĩnh vực khác (tin tức, thể thao, công nghệ, tài chính, y tế…).
            Trả lời theo JSON schema đã cho. Gán confidence trong [0.0, 1.0]; dùng 0.0 nếu không chắc chắn.
            """;

    private final Map<String, ChatClient> chatClientMap;
    private final AiModelProperties aiModelProperties;

    public IntentClassifier(
            @Qualifier("intentChatClientMap") Map<String, ChatClient> chatClientMap,
            AiModelProperties aiModelProperties) {
        this.chatClientMap = chatClientMap;
        this.aiModelProperties = aiModelProperties;
    }

    /**
     * Classify the user's question. Never throws: on any failure returns
     * {@code IntentDecision(LEGAL, 0.0)} per D-02.
     */
    public IntentDecision classify(String question, String modelId) {
        try {
            ChatClient client = resolveClient(modelId);
            IntentDecision decision = client.prompt()
                    .system(INTENT_SYSTEM_VI)
                    .user(question)
                    .call()
                    .entity(IntentDecision.class);
            if (decision == null || decision.intent() == null) {
                log.warn("IntentClassifier returned null; falling back to LEGAL (D-02)");
                return new IntentDecision(IntentDecision.Intent.LEGAL, 0.0);
            }
            return decision;
        } catch (Exception e) {
            log.warn("IntentClassifier failed ({}); falling back to LEGAL (D-02)", e.getMessage());
            return new IntentDecision(IntentDecision.Intent.LEGAL, 0.0);
        }
    }

    /**
     * Resolves the ChatClient for the given modelId.
     * Copied from ChatService.java:232-251 per PATTERNS.md — unknown modelId →
     * default model → first available. Never throws for unknown modelIds.
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
}
