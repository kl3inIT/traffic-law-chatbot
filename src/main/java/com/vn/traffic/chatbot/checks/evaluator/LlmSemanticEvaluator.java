package com.vn.traffic.chatbot.checks.evaluator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-judge semantic evaluator for answer quality assessment.
 * Uses a Vietnamese-first scoring prompt to evaluate answer similarity.
 * Returns 0.0 on any failure — never propagates exceptions.
 */
@Component
@Slf4j
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

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public LlmSemanticEvaluator(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public double evaluate(String referenceAnswer, String actualAnswer) {
        try {
            String content = chatClient.prompt()
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
}
