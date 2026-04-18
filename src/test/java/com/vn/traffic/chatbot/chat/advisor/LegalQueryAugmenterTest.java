package com.vn.traffic.chatbot.chat.advisor;

import com.vn.traffic.chatbot.chat.advisor.context.ChatAdvisorContextKeys;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.parameter.service.ActiveParameterSetProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalQueryAugmenterTest {

    private final ActiveParameterSetProvider paramProvider = mock(ActiveParameterSetProvider.class);

    @Test
    void emitsCitationBlockByteForByteMatchingChatPromptFactoryFormatCitation() {
        when(paramProvider.getString(anyString(), any()))
                .thenAnswer(inv -> inv.getArgument(1));

        LegalQueryAugmenter augmenter = new LegalQueryAugmenter(paramProvider);

        List<Document> labeledDocs = List.of(
                buildDoc(1, "Luat Giao thong", "https://vbpl.vn/doc-1", 42,
                        "Section-6", "excerpt-alpha"),
                buildDoc(2, "Nghi dinh 100", "https://vbpl.vn/doc-2", null,
                        "Section-5", "excerpt-beta"),
                buildDoc(3, "Thong tu 65", "https://vbpl.vn/doc-3", 7,
                        "Section-3", "excerpt-gamma")
        );

        Query augmented = augmenter.augment(new Query("question-text"), labeledDocs);
        String text = augmented.text();

        String labelPrefix = CitationMapper.INLINE_LABEL_PREFIX;
        assertThat(text).contains("- [" + labelPrefix + "1] Luat Giao thong | origin=https://vbpl.vn/doc-1 | page=42 | section=Section-6 | excerpt=excerpt-alpha");
        assertThat(text).contains("- [" + labelPrefix + "2] Nghi dinh 100 | origin=https://vbpl.vn/doc-2 | page=null | section=Section-5 | excerpt=excerpt-beta");
        assertThat(text).contains("- [" + labelPrefix + "3] Thong tu 65 | origin=https://vbpl.vn/doc-3 | page=7 | section=Section-3 | excerpt=excerpt-gamma");
    }

    @Test
    void emptyDocumentsReturnsOriginalQueryBecauseAllowEmptyContextIsTrue() {
        when(paramProvider.getString(anyString(), any()))
                .thenAnswer(inv -> inv.getArgument(1));

        LegalQueryAugmenter augmenter = new LegalQueryAugmenter(paramProvider);

        Query original = new Query("no-docs-query");
        Query augmented = augmenter.augment(original, List.of());

        assertThat(augmented).isSameAs(original);
    }

    @Test
    void promptTemplateForbidsExtraTopLevelFieldsConfidence() {
        PromptTemplate template = LegalQueryAugmenter.buildPromptTemplate("S");
        String text = template.getTemplate();
        assertThat(text).contains("Khong them confidence");
    }

    @Test
    void promptTemplateMentionsForbiddenIntentAndNoteFields() {
        PromptTemplate template = LegalQueryAugmenter.buildPromptTemplate("S");
        String text = template.getTemplate();
        // intent and note must appear inside the forbidden-fields rule (which begins with "Khong them")
        assertThat(text).contains("Khong them");
        assertThat(text).contains("intent");
        assertThat(text).contains("note");
    }

    @Test
    void promptTemplateMandatesJsonArraysForListTypedFields() {
        PromptTemplate template = LegalQueryAugmenter.buildPromptTemplate("S");
        String text = template.getTemplate();
        assertThat(text).contains("phai la mang JSON");
        assertThat(text).contains("legalBasis");
        assertThat(text).contains("penalties");
        assertThat(text).contains("requiredDocuments");
        assertThat(text).contains("procedureSteps");
        assertThat(text).contains("nextSteps");
    }

    @Test
    void promptTemplateContainsOneShotExampleWithJsonArray() {
        PromptTemplate template = LegalQueryAugmenter.buildPromptTemplate("S");
        String text = template.getTemplate();
        // One-shot example must show legalBasis as a JSON array literal.
        assertThat(text).contains("\"legalBasis\": [");
    }

    @Test
    void promptTemplatePreservesByteForByteLabelRuleAndAllKeysClause() {
        PromptTemplate template = LegalQueryAugmenter.buildPromptTemplate("S");
        String text = template.getTemplate();
        // Existing LABEL_RULE byte-for-byte parity (citations gating) — must still be present.
        assertThat(text).contains("[Nguồn n]");
        // Existing "all 8 keys must appear" clause unchanged.
        assertThat(text).contains("conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps phai luon xuat hien");
    }

    private static Document buildDoc(int labelNumber, String sourceTitle, String origin,
                                     Integer pageNumber, String sectionRef, String excerpt) {
        Map<String, Object> md = new HashMap<>();
        md.put(ChatAdvisorContextKeys.LABEL_NUMBER_METADATA, labelNumber);
        md.put("sourceTitle", sourceTitle);
        md.put("origin", origin);
        md.put("sectionRef", sectionRef);
        md.put("excerpt", excerpt);
        if (pageNumber != null) {
            md.put("pageNumber", pageNumber);
        }
        return Document.builder().id("doc-" + labelNumber).text(excerpt).metadata(md).build();
    }
}
