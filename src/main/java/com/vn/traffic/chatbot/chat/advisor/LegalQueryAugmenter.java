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
            "Ban la tro ly hoi dap phap luat giao thong Viet Nam.\n" +
            "Hay tra loi bang tieng Viet voi giong dieu ro rang.";

    private static final String LABEL_RULE =
            "Mọi nhận định có căn cứ phải gắn nhãn trích dẫn nội dòng đúng định dạng [Nguồn n]; tuyệt đối không tự tạo nhãn ngoài danh sách được cung cấp.";

    private static final PromptTemplate EMPTY_CONTEXT_TEMPLATE = new PromptTemplate(
            "Khong co ngu canh phap luat lien quan duoc truy xuat cho cau hoi nay.");

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
        String template = systemPrompt + "\n"
                + "Chi bao gom cac muc lien quan theo danh sach: " + String.join(", ", PromptSectionRules.SECTION_ORDER) + ".\n"
                + "Cac phan noi dung duoc phep dien la: " + String.join(", ", PromptSectionRules.SUPPORTED_SECTION_NAMES) + ".\n"
                + LABEL_RULE + "\n"
                + "Chi tra ve duy nhat mot object JSON hop le, khong dung markdown, khong them giai thich.\n"
                + "Tat ca cac khoa conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps phai luon xuat hien trong JSON.\n"
                + "Cau hoi nguoi dung: {query}\n"
                + "Danh sach trich dan duoc phep dung:\n"
                + "{context}";
        return new PromptTemplate(template);
    }

    static final class LegalCitationBlockFormatter implements Function<List<Document>, String> {
        @Override
        public String apply(List<Document> documents) {
            if (documents == null || documents.isEmpty()) {
                return "- Khong co trich dan kha dung";
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
