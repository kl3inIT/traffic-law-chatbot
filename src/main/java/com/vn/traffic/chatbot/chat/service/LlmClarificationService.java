package com.vn.traffic.chatbot.chat.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.dto.PendingFactResponse;
import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Determines whether a user question needs clarification by asking the LLM directly,
 * instead of relying on hardcoded keyword/regex rules.
 *
 * <p>Falls through to final analysis (no clarification) if the LLM call fails,
 * so a transient error never blocks the user from getting an answer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClarificationService {

    private static final int DEFAULT_MAX_CLARIFICATIONS = 2;

    private static final String PROMPT_TEMPLATE = """
            Bạn là trợ lý pháp luật giao thông Việt Nam.

            Nhiệm vụ: Đánh giá xem câu hỏi dưới đây có đủ thông tin để đưa ra câu trả lời pháp lý cụ thể không.

            Nguyên tắc:
            - Chỉ hỏi thêm nếu thiếu thông tin QUAN TRỌNG ảnh hưởng trực tiếp đến mức phạt hoặc quy định áp dụng.
            - Nếu hành vi vi phạm đã rõ (ví dụ: uống rượu lái xe, vượt đèn đỏ, không đội mũ, lái xe không có bằng), KHÔNG hỏi thêm chỉ vì chưa biết loại xe.
            - Nếu câu hỏi đã nêu cả loại xe lẫn hành vi vi phạm, KHÔNG hỏi thêm.
            - Chỉ đặt TỐI ĐA 1 câu hỏi ngắn, thiết thực nhất.
            - Không hỏi lại điều người dùng đã nêu rõ trong câu hỏi hoặc tình tiết đã biết.
            - Trả về JSON hợp lệ, không markdown, không giải thích thêm.

            Tình tiết đã biết từ hội thoại: %s

            Câu hỏi người dùng: %s

            Trả về đúng định dạng JSON:
            {"needsClarification": true/false, "question": "câu hỏi làm rõ bằng tiếng Việt, hoặc null nếu không cần"}
            """;

    private final ChatClient chatClient;
    private final ActiveParameterSetProvider paramProvider;
    private final ObjectMapper objectMapper;

    public ClarificationPolicy.ClarificationDecision decide(
            String question,
            Map<String, String> factMap,
            int clarificationCount
    ) {
        int maxClarifications = paramProvider.getInt("caseAnalysis.maxClarifications", DEFAULT_MAX_CLARIFICATIONS);

        if (clarificationCount >= maxClarifications) {
            log.debug("Max clarifications ({}) reached — proceeding to answer", maxClarifications);
            return ClarificationPolicy.ClarificationDecision.finalAnalysis();
        }

        try {
            String factsText = factMap.isEmpty()
                    ? "Chưa có"
                    : factMap.entrySet().stream()
                            .map(e -> e.getKey() + ": " + e.getValue())
                            .collect(Collectors.joining("; "));

            String prompt = String.format(PROMPT_TEMPLATE, factsText, question);
            String raw = chatClient.prompt().user(prompt).call().content();
            log.debug("LLM clarification raw response: {}", raw);

            LlmResult result = parseResult(raw);

            if (result == null || !result.needsClarification()
                    || result.question() == null || result.question().isBlank()) {
                return ClarificationPolicy.ClarificationDecision.finalAnalysis();
            }

            List<PendingFactResponse> pending = List.of(
                    new PendingFactResponse("llmClarification", result.question(), "")
            );
            return ClarificationPolicy.ClarificationDecision.clarificationNeeded(pending, clarificationCount + 1);

        } catch (Exception ex) {
            log.warn("LLM clarification check failed — falling through to answer: {}", ex.getMessage());
            return ClarificationPolicy.ClarificationDecision.finalAnalysis();
        }
    }

    private LlmResult parseResult(String raw) {
        try {
            String json = extractJson(raw);
            return objectMapper.readValue(json, LlmResult.class);
        } catch (Exception ex) {
            log.warn("Failed to parse clarification JSON ({}), raw: {}", ex.getMessage(), raw);
            return null;
        }
    }

    private String extractJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        String text = payload.trim();
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
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmResult(boolean needsClarification, String question) {}
}
