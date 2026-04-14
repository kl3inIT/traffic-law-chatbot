package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.domain.ChatMessage;
import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ChatPromptFactory {

    // Safety-critical hardcoded fallback (D-13): core instruction structure is never
    // fully replaced by parameter set — only the opening system context line is configurable.
    private static final String SYSTEM_CONTEXT_FALLBACK =
            "Ban la tro ly hoi dap phap luat giao thong Viet Nam.\n" +
            "Hay tra loi bang tieng Viet voi giong dieu ro rang, trang trong, de hieu, plain but formal.\n" +
            "Thong tin chi mang tinh chat tham khao, khong phai tu van phap ly chinh thuc va khong duoc suy doan vuot qua nguon da truy xuat.";

    private static final String CLARIFICATION_RULES =
            "Quy tắc hỏi lại:\n" +
            "- Ưu tiên trả lời trực tiếp. Nếu câu hỏi có thể trả lời được dù thiếu chi tiết phụ, hãy trả lời và nêu rõ các trường hợp khác nhau.\n" +
            "- Chỉ hỏi lại khi thông tin thiếu thực sự khiến không thể xác định được quy định áp dụng (ví dụ: câu hỏi quá chung chung hoặc mơ hồ đến mức không liên quan đến bất kỳ điều luật nào).\n" +
            "- Nếu cần hỏi lại, chỉ hỏi TỐI ĐA 1 câu ngắn gọn, cụ thể, bằng tiếng Việt.\n" +
            "- Không bao giờ hỏi lại điều đã nêu rõ trong lịch sử hội thoại.";

    private static final int MAX_HISTORY_MESSAGES = 10;

    private final ActiveParameterSetProvider paramProvider;

    public String buildPrompt(String question, GroundingStatus groundingStatus, List<CitationResponse> citations) {
        return buildPrompt(question, groundingStatus, citations, List.of());
    }

    public String buildPrompt(String question, GroundingStatus groundingStatus, List<CitationResponse> citations, List<ChatMessage> conversationHistory) {
        String citationLines = citations == null || citations.isEmpty()
                ? "- Không có trích dẫn khả dụng"
                : citations.stream().map(this::formatCitation).collect(Collectors.joining("\n"));

        // Read configurable system prompt from parameter set; fall back to hardcoded context
        String systemPrompt = paramProvider.getString("systemPrompt", SYSTEM_CONTEXT_FALLBACK);

        // Build conversation history block
        String historyBlock = buildHistoryBlock(conversationHistory);

        StringBuilder prompt = new StringBuilder();
        prompt.append(systemPrompt).append("\n");
        prompt.append(CLARIFICATION_RULES).append("\n");
        // Safety-critical citation and JSON schema instructions remain hardcoded (D-13)
        prompt.append("Bắt đầu bằng phần Kết luận trước, sau đó mới đến các phần hỗ trợ theo đúng thứ tự khi thật sự liên quan.").append("\n");
        prompt.append("Chỉ bao gồm các mục liên quan theo danh sách: ").append(String.join(", ", PromptSectionRules.SECTION_ORDER)).append(".").append("\n");
        prompt.append("Các phần nội dung được phép điền là: ").append(String.join(", ", PromptSectionRules.SUPPORTED_SECTION_NAMES)).append(".").append("\n");
        prompt.append("Mọi nhận định có căn cứ phải gắn nhãn trích dẫn nội dòng đúng định dạng [Nguồn n]; tuyệt đối không tự tạo nhãn ngoài danh sách được cung cấp.").append("\n");
        prompt.append(groundingStatus == GroundingStatus.LIMITED_GROUNDING
                ? "Ngữ cảnh hiện có là LIMITED_GROUNDING: chỉ giữ các phần được nguồn hỗ trợ trực tiếp, phải lược bỏ phần không đủ căn cứ thay vì đoán hoặc bổ sung theo hiểu biết chung."
                : "Ngữ cảnh hiện có là GROUNDED: chỉ sử dụng thông tin được hỗ trợ trực tiếp từ nguồn truy xuất.").append("\n");
        prompt.append("Nếu một phần như mức phạt, giấy tờ, thủ tục hoặc bước tiếp theo không được nguồn hỗ trợ thì phải bỏ hẳn phần đó.").append("\n");
        prompt.append("Chỉ trả về duy nhất một object JSON hợp lệ, không dùng markdown, không dùng ```json, không thêm giải thích trước hoặc sau JSON.").append("\n");
        prompt.append("Tất cả các khóa conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps, scenarioFacts, scenarioRule, scenarioOutcome, scenarioActions phải luôn xuất hiện trong JSON.").append("\n");
        prompt.append("Các trường danh sách phải luôn là mảng JSON; nếu không có nội dung thì trả về []. Các trường chuỗi có thể là null hoặc chuỗi rỗng nhưng không được bỏ khóa.").append("\n");
        prompt.append("Trả về JSON hợp lệ với đúng các khóa sau: conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps, scenarioFacts, scenarioRule, scenarioOutcome, scenarioActions.").append("\n");
        prompt.append("Giá trị answer có thể để trống vì hệ thống sẽ tự tổng hợp lại câu trả lời cuối cùng.").append("\n");
        if (!historyBlock.isEmpty()) {
            prompt.append("Lịch sử hội thoại:\n").append(historyBlock).append("\n");
        }
        prompt.append("Câu hỏi người dùng: ").append(question).append("\n");
        prompt.append("Danh sách trích dẫn được phép dùng:").append("\n");
        prompt.append(citationLines);

        return prompt.toString();
    }

    private String buildHistoryBlock(List<ChatMessage> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return "";
        }
        List<ChatMessage> recent = conversationHistory.size() > MAX_HISTORY_MESSAGES
                ? conversationHistory.subList(conversationHistory.size() - MAX_HISTORY_MESSAGES, conversationHistory.size())
                : conversationHistory;
        return recent.stream()
                .map(m -> m.getRole().name() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String formatCitation(CitationResponse citation) {
        return "- [" + citation.inlineLabel() + "] "
                + nullSafe(citation.sourceTitle())
                + " | origin=" + nullSafe(citation.origin())
                + " | page=" + (citation.pageNumber() == null ? "null" : citation.pageNumber())
                + " | section=" + nullSafe(citation.sectionRef())
                + " | excerpt=" + nullSafe(citation.excerpt());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
