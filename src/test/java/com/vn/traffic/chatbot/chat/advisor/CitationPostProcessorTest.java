package com.vn.traffic.chatbot.chat.advisor;

import com.vn.traffic.chatbot.chat.advisor.context.ChatAdvisorContextKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationPostProcessorTest {

    private final CitationPostProcessor processor = new CitationPostProcessor();

    @Test
    void assignsLabelNumberOneThroughNInRetrievalOrder() {
        List<Document> input = List.of(
                Document.builder().id("a").text("alpha").metadata(Map.of("sourceId", "s-a")).build(),
                Document.builder().id("b").text("beta").metadata(Map.of("sourceId", "s-b")).build(),
                Document.builder().id("c").text("gamma").metadata(Map.of("sourceId", "s-c")).build()
        );

        List<Document> labeled = processor.process(new Query("q"), input);

        assertThat(labeled).hasSize(3);
        assertThat(labeled.get(0).getMetadata().get(ChatAdvisorContextKeys.LABEL_NUMBER_METADATA)).isEqualTo(1);
        assertThat(labeled.get(1).getMetadata().get(ChatAdvisorContextKeys.LABEL_NUMBER_METADATA)).isEqualTo(2);
        assertThat(labeled.get(2).getMetadata().get(ChatAdvisorContextKeys.LABEL_NUMBER_METADATA)).isEqualTo(3);
    }

    @Test
    void preservesOriginalDocumentIdTextAndScore() {
        Document input = Document.builder()
                .id("doc-42")
                .text("noi dung")
                .metadata(Map.of("sourceId", "s-42"))
                .score(0.87d)
                .build();

        List<Document> labeled = processor.process(new Query("q"), List.of(input));

        assertThat(labeled).hasSize(1);
        Document out = labeled.get(0);
        assertThat(out.getId()).isEqualTo("doc-42");
        assertThat(out.getText()).isEqualTo("noi dung");
        assertThat(out.getScore()).isEqualTo(0.87d);
        assertThat(out.getMetadata()).containsEntry("sourceId", "s-42");
    }

    @Test
    void doesNotMutateInputMetadataWhenSourceMapIsImmutable() {
        Document input = Document.builder()
                .id("x")
                .text("text")
                .metadata(Map.of("sourceId", "s-x"))
                .build();

        List<Document> labeled = processor.process(new Query("q"), List.of(input));

        assertThat(input.getMetadata()).doesNotContainKey(ChatAdvisorContextKeys.LABEL_NUMBER_METADATA);
        assertThat(labeled.get(0).getMetadata()).containsEntry(ChatAdvisorContextKeys.LABEL_NUMBER_METADATA, 1);
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertThat(processor.process(new Query("q"), List.of())).isEmpty();
    }
}
