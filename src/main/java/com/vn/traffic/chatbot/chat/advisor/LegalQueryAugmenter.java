package com.vn.traffic.chatbot.chat.advisor;

import com.vn.traffic.chatbot.chat.advisor.context.ChatAdvisorContextKeys;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.chat.service.PromptSectionRules;
import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public final class LegalQueryAugmenter implements QueryAugmenter {

    static final String SYSTEM_CONTEXT_FALLBACK =
            "Bạn là trợ lý hỏi đáp pháp luật giao thông Việt Nam.\n" +
            "Hãy trả lời bằng tiếng Việt với giọng điệu rõ ràng.";

    private static final String LABEL_RULE =
            "Mọi nhận định có căn cứ phải gắn nhãn trích dẫn nội dòng đúng định dạng [Nguồn n]; tuyệt đối không tự tạo nhãn ngoài danh sách được cung cấp.";

    private static final PromptTemplate EMPTY_CONTEXT_TEMPLATE = new PromptTemplate(
            "Không có ngữ cảnh pháp luật liên quan được truy xuất cho câu hỏi này.");

    private final ActiveParameterSetProvider paramProvider;

    private volatile ContextualQueryAugmenter delegateCache;
    private volatile String cachedSystemPrompt;

    @Override
    public Query augment(Query query, List<Document> documents) {
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(documents, "documents cannot be null");
        return getOrBuildDelegate().augment(query, documents);
    }

    private ContextualQueryAugmenter getOrBuildDelegate() {
        String systemPrompt = paramProvider.getString("systemPrompt", SYSTEM_CONTEXT_FALLBACK);
        ContextualQueryAugmenter cached = this.delegateCache;
        if (cached != null && systemPrompt.equals(this.cachedSystemPrompt)) {
            return cached;
        }
        synchronized (this) {
            if (this.delegateCache == null || !systemPrompt.equals(this.cachedSystemPrompt)) {
                this.delegateCache = ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .promptTemplate(buildPromptTemplate(systemPrompt))
                        .emptyContextPromptTemplate(EMPTY_CONTEXT_TEMPLATE)
                        .documentFormatter(new LegalCitationBlockFormatter())
                        .build();
                this.cachedSystemPrompt = systemPrompt;
            }
            return this.delegateCache;
        }
    }

    static PromptTemplate buildPromptTemplate(String systemPrompt) {
        // Use angle-bracket delimiters so the literal { } braces in the one-shot JSON
        // example are passed through verbatim by StringTemplate (the default { }
        // delimiters would treat the JSON braces as variable expressions and fail to
        // compile). Variable placeholders therefore use <query> and <context>; the
        // PromptAssert.templateHasRequiredPlaceholders check is a substring scan and
        // accepts either delimiter style.
        String template = systemPrompt + "\n"
                + "Chỉ bao gồm các mục liên quan theo danh sách: " + String.join(", ", PromptSectionRules.SECTION_ORDER) + ".\n"
                + "Các phần nội dung được phép điền là: " + String.join(", ", PromptSectionRules.SUPPORTED_SECTION_NAMES) + ".\n"
                + LABEL_RULE + "\n"
                + "Chỉ trả về duy nhất một object JSON hợp lệ, không dùng markdown, không thêm giải thích.\n"
                + "Tất cả các khóa conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps phải luôn xuất hiện trong JSON.\n"
                + "Các trường legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps phải là mảng JSON của các chuỗi (JSON array of strings), không được trả về dạng chuỗi đơn.\n"
                + "Không thêm confidence, intent, note, hoặc bất kỳ khóa top-level nào khác không nằm trong 8 khóa: conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps.\n"
                + "Ví dụ đúng định dạng (one-shot):\n"
                + "{\n"
                + "  \"conclusion\": \"...\",\n"
                + "  \"answer\": \"...\",\n"
                + "  \"uncertaintyNotice\": \"\",\n"
                + "  \"legalBasis\": [\"Điều 6 Nghị định 100/2019/NĐ-CP\"],\n"
                + "  \"penalties\": [\"Phạt tiền từ 800.000 đến 1.000.000 đồng\"],\n"
                + "  \"requiredDocuments\": [],\n"
                + "  \"procedureSteps\": [],\n"
                + "  \"nextSteps\": []\n"
                + "}\n"
                + "Câu hỏi người dùng: <query>\n"
                + "Danh sách trích dẫn được phép dùng:\n"
                + "<context>";
        return PromptTemplate.builder()
                .template(template)
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .validationMode(ValidationMode.THROW)
                        .build())
                .build();
    }

    static final class LegalCitationBlockFormatter implements Function<List<Document>, String> {
        @Override
        public String apply(List<Document> documents) {
            if (documents == null || documents.isEmpty()) {
                return "- Không có trích dẫn khả dụng";
            }
            return documents.stream()
                    .map(LegalCitationBlockFormatter::formatDoc)
                    .collect(Collectors.joining("\n"));
        }

        private static String formatDoc(Document doc) {
            Map<String, Object> md = doc.getMetadata();
            Object labelNumber = md.get(ChatAdvisorContextKeys.LABEL_NUMBER_METADATA);
            String inlineLabel = CitationMapper.INLINE_LABEL_PREFIX
                    + (labelNumber == null ? "?" : labelNumber.toString());
            Integer pageNumber = toInt(md.get("pageNumber"));
            return "- [" + inlineLabel + "] "
                    + nullSafe(stringValue(md.get("sourceTitle")))
                    + " | origin=" + nullSafe(stringValue(md.get("origin")))
                    + " | page=" + (pageNumber == null ? "null" : pageNumber)
                    + " | section=" + nullSafe(stringValue(md.get("sectionRef")))
                    + " | excerpt=" + nullSafe(stringValue(md.get("excerpt")));
        }

        private static String stringValue(Object value) {
            return value == null ? null : value.toString();
        }

        private static String nullSafe(String value) {
            return value == null ? "" : value;
        }

        private static Integer toInt(Object value) {
            if (value == null) return null;
            if (value instanceof Number n) return n.intValue();
            if (value instanceof String s && !s.isBlank()) {
                try {
                    return Integer.valueOf(s.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
    }
}
