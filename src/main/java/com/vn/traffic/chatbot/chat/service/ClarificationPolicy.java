package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.PendingFactResponse;
import com.vn.traffic.chatbot.chat.domain.ThreadFact;
import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ClarificationPolicy {

    private static final int DEFAULT_MAX_CLARIFICATIONS = 2;

    private final ActiveParameterSetProvider paramProvider;

    public ClarificationDecision evaluate(String question, List<ThreadFact> activeFacts, int clarificationCount) {
        int maxClarifications = paramProvider.getInt("caseAnalysis.maxClarifications", DEFAULT_MAX_CLARIFICATIONS);

        Map<String, String> factMap = activeFacts == null ? Map.of() : activeFacts.stream()
                .collect(java.util.stream.Collectors.toMap(ThreadFact::getFactKey, ThreadFact::getFactValue, (left, right) -> right, java.util.LinkedHashMap::new));

        Set<String> requiredKeys = determineRequiredFactKeys(question, factMap);
        List<PendingFactResponse> pendingFacts = new ArrayList<>();
        for (String key : requiredKeys) {
            if (!hasMeaningfulFact(factMap.get(key))) {
                pendingFacts.add(toPendingFact(key));
            }
        }

        if (pendingFacts.isEmpty()) {
            return ClarificationDecision.finalAnalysis();
        }
        if (clarificationCount >= maxClarifications) {
            return ClarificationDecision.refused(pendingFacts, maxClarifications);
        }
        return ClarificationDecision.clarificationNeeded(pendingFacts, clarificationCount + 1);
    }

    private Set<String> determineRequiredFactKeys(String question, Map<String, String> factMap) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        Set<String> required = new HashSet<>();

        // Read required facts from parameter set; fall back to built-in rules
        List<Map<String, Object>> configuredFacts = paramProvider.getList("caseAnalysis.requiredFacts");
        if (configuredFacts.isEmpty()) {
            required.addAll(builtInRequiredFactKeys(normalized, factMap));
        } else {
            for (Map<String, Object> factConfig : configuredFacts) {
                String key = (String) factConfig.get("key");
                if (key == null) {
                    continue;
                }
                boolean alwaysRequired = Boolean.TRUE.equals(factConfig.get("alwaysRequired"));
                if (alwaysRequired) {
                    required.add(key);
                    continue;
                }
                // Check trigger keywords
                Object triggerKeywordsObj = factConfig.get("triggerKeywords");
                if (triggerKeywordsObj instanceof List<?> triggerKeywords) {
                    for (Object kw : triggerKeywords) {
                        if (kw instanceof String keyword && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                            required.add(key);
                            break;
                        }
                    }
                }
                // Also include if factMap already has the key (user supplied it previously)
                if (factMap.containsKey(key)) {
                    required.add(key);
                }
            }
        }

        return required;
    }

    /**
     * Built-in required fact rules used when no parameter set is active.
     */
    private Set<String> builtInRequiredFactKeys(String normalized, Map<String, String> factMap) {
        Set<String> required = new HashSet<>();
        required.add("vehicleType");
        required.add("violationType");

        if (normalized.contains("tai nạn") || normalized.contains("va chạm") || factMap.containsKey("injuryStatus")) {
            required.add("injuryStatus");
        }
        if (normalized.contains("nồng độ cồn") || normalized.contains("rượu") || normalized.contains("bia") || factMap.containsKey("alcoholStatus")) {
            required.add("alcoholStatus");
        }
        if (normalized.contains("bằng lái") || normalized.contains("giấy phép lái xe") || factMap.containsKey("licenseStatus")) {
            required.add("licenseStatus");
        }
        if (normalized.contains("đăng ký xe") || normalized.contains("cà vẹt") || normalized.contains("giấy tờ") || factMap.containsKey("documentStatus")) {
            required.add("documentStatus");
        }
        return required;
    }

    private boolean hasMeaningfulFact(String value) {
        return value != null && !value.isBlank();
    }

    private PendingFactResponse toPendingFact(String key) {
        return switch (key) {
            case "vehicleType" -> new PendingFactResponse("vehicleType", "Bạn điều khiển loại phương tiện nào?", "Thiếu loại phương tiện nên chưa thể đối chiếu đúng khung xử phạt.");
            case "violationType" -> new PendingFactResponse("violationType", "Bạn muốn hỏi chính xác về hành vi nào?", "Thiếu hành vi vi phạm cụ thể.");
            case "injuryStatus" -> new PendingFactResponse("injuryStatus", "Vụ việc có gây tai nạn hoặc có ai bị thương không?", "Mức độ hậu quả ảnh hưởng trực tiếp đến đánh giá pháp lý.");
            case "alcoholStatus" -> new PendingFactResponse("alcoholStatus", "Kết quả kiểm tra nồng độ cồn là không có, có, hay vượt mức nào?", "Thiếu thông tin về nồng độ cồn.");
            case "licenseStatus" -> new PendingFactResponse("licenseStatus", "Tại thời điểm đó bạn có giấy phép lái xe phù hợp hay không?", "Thiếu trạng thái giấy phép lái xe.");
            case "documentStatus" -> new PendingFactResponse("documentStatus", "Bạn có mang đăng ký xe hoặc giấy tờ liên quan không?", "Thiếu thông tin về giấy tờ.");
            default -> new PendingFactResponse(key, "Bạn có thể bổ sung thêm thông tin còn thiếu không?", "Thiếu tình tiết quan trọng.");
        };
    }

    public record ClarificationDecision(
            boolean clarificationNeeded,
            boolean shouldRefuse,
            int updatedClarificationCount,
            List<PendingFactResponse> pendingFacts
    ) {
        static ClarificationDecision finalAnalysis() {
            return new ClarificationDecision(false, false, 0, List.of());
        }

        static ClarificationDecision clarificationNeeded(List<PendingFactResponse> pendingFacts, int updatedClarificationCount) {
            return new ClarificationDecision(true, false, updatedClarificationCount, List.copyOf(pendingFacts));
        }

        static ClarificationDecision refused(List<PendingFactResponse> pendingFacts, int updatedClarificationCount) {
            return new ClarificationDecision(false, true, updatedClarificationCount, List.copyOf(pendingFacts));
        }
    }
}
