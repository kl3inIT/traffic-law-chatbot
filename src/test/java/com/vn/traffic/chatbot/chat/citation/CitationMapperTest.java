package com.vn.traffic.chatbot.chat.citation;

import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationMapperTest {

    private final CitationMapper citationMapper = new CitationMapper();

    @Test
    void toCitationsMapsMetadataIntoCitationResponse() {
        Document document = new Document(
                "  Người điều khiển xe máy vượt đèn đỏ có thể bị xử phạt tiền.  ",
                Map.of(
                        "sourceId", "source-1",
                        "sourceVersionId", "version-1",
                        "origin", "https://vbpl.vn/doc-1",
                        "pageNumber", 4,
                        "sectionRef", "Điều 6"
                )
        );

        List<CitationResponse> citations = citationMapper.toCitations(List.of(document));

        assertThat(citations).hasSize(1);
        CitationResponse citation = citations.getFirst();
        assertThat(citation.inlineLabel()).isEqualTo("Nguồn 1");
        assertThat(citation.sourceId()).isEqualTo("source-1");
        assertThat(citation.sourceVersionId()).isEqualTo("version-1");
        assertThat(citation.sourceTitle()).isEqualTo("https://vbpl.vn/doc-1");
        assertThat(citation.origin()).isEqualTo("https://vbpl.vn/doc-1");
        assertThat(citation.pageNumber()).isEqualTo(4);
        assertThat(citation.sectionRef()).isEqualTo("Điều 6");
        assertThat(citation.excerpt()).isEqualTo("Người điều khiển xe máy vượt đèn đỏ có thể bị xử phạt tiền.");
    }

    @Test
    void toSourcesMapsCitationIntoDedicatedSourceReference() {
        CitationResponse citation = new CitationResponse(
                "Nguồn 1",
                "source-1",
                "version-1",
                "Nghị định 100",
                "https://vbpl.vn/nd100",
                12,
                "Điều 6",
                "excerpt"
        );

        List<SourceReferenceResponse> sources = citationMapper.toSources(List.of(citation));

        assertThat(sources).containsExactly(new SourceReferenceResponse(
                "Nguồn 1",
                "source-1",
                "version-1",
                "Nghị định 100",
                "https://vbpl.vn/nd100",
                12,
                "Điều 6"
        ));
    }

    @Test
    void toCitationsAllowsMissingPageNumberAndPreservesOtherFields() {
        Document document = new Document(
                "Thiếu số trang nhưng vẫn phải giữ thông tin nguồn.",
                Map.of(
                        "sourceId", "source-2",
                        "sourceVersionId", "version-2",
                        "origin", "https://vbpl.vn/doc-2",
                        "sectionRef", "Khoản 1"
                )
        );

        List<CitationResponse> citations = citationMapper.toCitations(List.of(document));

        assertThat(citations).hasSize(1);
        CitationResponse citation = citations.getFirst();
        assertThat(citation.pageNumber()).isNull();
        assertThat(citation.sourceId()).isEqualTo("source-2");
        assertThat(citation.sourceVersionId()).isEqualTo("version-2");
        assertThat(citation.origin()).isEqualTo("https://vbpl.vn/doc-2");
    }

    @Test
    void toSourcesCollapsesDuplicateSourcesWhileCitationsKeepDistinctExcerpts() {
        Document first = new Document(
                "Đoạn trích thứ nhất về mức phạt tại Điều 6.",
                Map.of(
                        "sourceId", "source-1",
                        "sourceVersionId", "version-1",
                        "origin", "https://vbpl.vn/nd100",
                        "pageNumber", "5",
                        "sectionRef", "Điều 6"
                )
        );
        Document second = new Document(
                "Đoạn trích thứ hai về biện pháp khắc phục hậu quả tại Điều 6.",
                Map.of(
                        "sourceId", "source-1",
                        "sourceVersionId", "version-1",
                        "origin", "https://vbpl.vn/nd100",
                        "pageNumber", 5,
                        "sectionRef", "Điều 6"
                )
        );

        List<CitationResponse> citations = citationMapper.toCitations(List.of(first, second));
        List<SourceReferenceResponse> sources = citationMapper.toSources(citations);

        assertThat(citations).hasSize(2);
        assertThat(citations).extracting(CitationResponse::inlineLabel)
                .containsExactly("Nguồn 1", "Nguồn 2");
        assertThat(citations).extracting(CitationResponse::excerpt)
                .containsExactly(
                        "Đoạn trích thứ nhất về mức phạt tại Điều 6.",
                        "Đoạn trích thứ hai về biện pháp khắc phục hậu quả tại Điều 6."
                );
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst()).isEqualTo(new SourceReferenceResponse(
                "Nguồn 1",
                "source-1",
                "version-1",
                "https://vbpl.vn/nd100",
                "https://vbpl.vn/nd100",
                5,
                "Điều 6"
        ));
    }
}
