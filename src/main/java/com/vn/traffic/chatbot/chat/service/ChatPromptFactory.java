package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ChatPromptFactory {

    public String buildPrompt(String question, GroundingStatus groundingStatus, List<CitationResponse> citations) {
        String citationLines = citations == null || citations.isEmpty()
                ? "- Không có trích dẫn khả dụng"
                : citations.stream().map(this::formatCitation).collect(Collectors.joining("\n"));

        return String.join("\n",
                "Bạn là trợ lý hỏi đáp pháp luật giao thông Việt Nam.",
                "Hãy trả lời bằng tiếng Việt với giọng điệu rõ ràng, trang trọng, dễ hiểu, plain but formal.",
                "Thông tin chỉ mang tính chất tham khảo, không phải tư vấn pháp lý chính thức và không được suy đoán vượt quá nguồn đã truy xuất.",
                "Bắt đầu bằng phần Kết luận trước, sau đó mới đến các phần hỗ trợ theo đúng thứ tự khi thật sự liên quan.",
                "Chỉ bao gồm các mục liên quan theo danh sách: " + String.join(", ", PromptSectionRules.SECTION_ORDER) + ".",
                "Các phần nội dung được phép điền là: " + String.join(", ", PromptSectionRules.SUPPORTED_SECTION_NAMES) + ".",
                "Mọi nhận định có căn cứ phải gắn nhãn trích dẫn nội dòng đúng định dạng [Nguồn n]; tuyệt đối không tự tạo nhãn ngoài danh sách được cung cấp.",
                groundingStatus == GroundingStatus.LIMITED_GROUNDING
                        ? "Ngữ cảnh hiện có là LIMITED_GROUNDING: chỉ giữ các phần được nguồn hỗ trợ trực tiếp, phải lược bỏ phần không đủ căn cứ thay vì đoán hoặc bổ sung theo hiểu biết chung."
                        : "Ngữ cảnh hiện có là GROUNDED: chỉ sử dụng thông tin được hỗ trợ trực tiếp từ nguồn truy xuất.",
                "Nếu một phần như mức phạt, giấy tờ, thủ tục hoặc bước tiếp theo không được nguồn hỗ trợ thì phải bỏ hẳn phần đó.",
                "Chỉ trả về duy nhất một object JSON hợp lệ, không dùng markdown, không dùng ```json, không thêm giải thích trước hoặc sau JSON.",
                "Tất cả các khóa conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps phải luôn xuất hiện trong JSON.",
                "Các trường danh sách phải luôn là mảng JSON; nếu không có nội dung thì trả về []. Các trường chuỗi có thể là null hoặc chuỗi rỗng nhưng không được bỏ khóa.",
                "Trả về JSON hợp lệ với đúng các khóa sau: conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps.",
                "Giá trị answer có thể để trống vì hệ thống sẽ tự tổng hợp lại câu trả lời cuối cùng.",
                "Câu hỏi người dùng: " + question,
                "Danh sách trích dẫn được phép dùng:",
                citationLines
        );
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
