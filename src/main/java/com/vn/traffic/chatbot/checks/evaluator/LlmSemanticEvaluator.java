package com.vn.traffic.chatbot.checks.evaluator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import com.vn.traffic.chatbot.parameter.service.AiParameterSetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-judge semantic evaluator for answer quality assessment.
 * Uses a Vietnamese-first scoring prompt to evaluate answer similarity.
 * Returns 0.0 on any failure — never propagates exceptions.
 *
 * <p>Evaluator model fallback chain (D-09, D-13):
 * {@code activeParamSet.evaluatorModel} → {@code app.ai.evaluator-model} config.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LlmSemanticEvaluator implements SemanticEvaluator {

    private static final String SYSTEM_PROMPT = """
            Bạn đánh giá độ tương đồng ngữ nghĩa giữa câu trả lời tham chiếu và câu trả lời thực tế.
            Cho điểm từ 0 đến 1 theo tiêu chí:
            - Độ chính xác ngữ nghĩa so với tham chiếu: 60%
            - Đầy đủ các điểm chính: 30%
            - Trừ điểm nếu có mâu thuẫn, thông tin sai, hoặc nội dung không liên quan: 10%
            - Nếu ngôn ngữ không khớp (ví dụ: trả lời tiếng Anh thay vì tiếng Việt), áp dụng hệ số phạt mạnh.

            Trả về JSON hợp lệ (không có markdown fences):
            {"score": <số 0..1>, "verdict": "PASS"|"PARTIAL"|"FAIL", "rationale": "giải thích ngắn", "languageMatch": true|false}
            """;

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final double LANGUAGE_MISMATCH_CAP = 0.2;

    private final Map<String, ChatClient> chatClientMap;
    private final AiModelProperties aiModelProperties;
    private final AiParameterSetService parameterSetService;
    private final ObjectMapper objectMapper;

    @Override
    public double evaluate(String referenceAnswer, String actualAnswer) {
        return evaluate(referenceAnswer, actualAnswer, null);
    }

    @Override
    public double evaluate(String referenceAnswer, String actualAnswer, String evaluatorModelId) {
        try {
            ChatClient client = evaluatorModelId != null && !evaluatorModelId.isBlank()
                    ? resolveClient(evaluatorModelId)
                    : resolveEvaluatorClient();
            String content = client.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("Câu trả lời tham chiếu:\n" + referenceAnswer
                            + "\n\nCâu trả lời thực tế:\n" + actualAnswer)
                    .call()
                    .content();
            return parseScore(content);
        } catch (Exception ex) {
            log.error("SemanticEvaluator failed: {}", ex.getMessage(), ex);
            return 0.0;
        }
    }

    private ChatClient resolveClient(String modelId) {
        ChatClient client = chatClientMap.get(modelId);
        if (client == null) {
            log.warn("Requested evaluator model '{}' not in chatClientMap, falling back to default", modelId);
            return resolveEvaluatorClient();
        }
        return client;
    }

    public double parseScore(String content) {
        try {
            Matcher m = JSON_PATTERN.matcher(content);
            if (!m.find()) {
                log.warn("SemanticEvaluator: no JSON found in response");
                return 0.0;
            }
            JsonNode node = objectMapper.readTree(m.group());
            double score = node.path("score").asDouble(0.0);
            boolean languageMatch = node.path("languageMatch").asBoolean(true);
            if (!languageMatch) {
                score = Math.min(score, LANGUAGE_MISMATCH_CAP);
            }
            return Math.max(0.0, Math.min(1.0, score));
        } catch (Exception ex) {
            log.warn("SemanticEvaluator: score parse failed: {}", ex.getMessage());
            return 0.0;
        }
    }

    /**
     * Resolves the ChatClient for evaluation.
     * Fallback chain: activeParamSet.evaluatorModel → app.ai.evaluator-model → first available.
     */
    private ChatClient resolveEvaluatorClient() {
        String modelId = parameterSetService.getActive()
                .map(AiParameterSet::getEvaluatorModel)
                .filter(m -> m != null && !m.isBlank())
                .orElse(aiModelProperties.evaluatorModel());

        ChatClient client = chatClientMap.get(modelId);
        if (client == null) {
            log.warn("Evaluator model '{}' not in chatClientMap, falling back to '{}'",
                    modelId, aiModelProperties.evaluatorModel());
            client = chatClientMap.get(aiModelProperties.evaluatorModel());
        }
        if (client == null) {
            log.warn("Fallback evaluator model '{}' also not in chatClientMap — using first available",
                    aiModelProperties.evaluatorModel());
            if (chatClientMap.isEmpty()) {
                throw new IllegalStateException(
                        "No ChatClient available — check app.ai.models configuration");
            }
            client = chatClientMap.values().iterator().next();
        }
        return client;
    }
}
