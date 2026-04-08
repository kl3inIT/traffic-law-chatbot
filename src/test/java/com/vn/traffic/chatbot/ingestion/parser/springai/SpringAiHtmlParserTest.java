package com.vn.traffic.chatbot.ingestion.parser.springai;

import com.vn.traffic.chatbot.ingestion.fetch.FetchResult;
import com.vn.traffic.chatbot.ingestion.parser.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiHtmlParserTest {

    private final SpringAiHtmlParser parser = new SpringAiHtmlParser();

    @Test
    void parse_mapsFetchedHtmlIntoParsedDocumentWithoutLeakingSpringAiTypes() {
        FetchResult fetchResult = new FetchResult(
                "https://example.vn/original",
                "https://example.vn/final",
                200,
                "text/html; charset=UTF-8",
                "Fallback title",
                """
                        <html>
                          <head>
                            <title>Luật giao thông đường bộ</title>
                            <meta name=\"description\" content=\"Tổng hợp quy định\" />
                          </head>
                          <body>
                            <h1>Điều 1</h1>
                            <p>Nội dung thứ nhất.</p>
                            <p>Nội dung thứ hai.</p>
                          </body>
                        </html>
                        """,
                "etag-1",
                OffsetDateTime.parse("2026-04-08T01:02:03Z")
        );

        ParsedDocument parsedDocument = parser.parse(fetchResult);

        assertThat(parsedDocument).isInstanceOf(ParsedDocument.class);
        assertThat(parsedDocument.title()).isEqualTo("Luật giao thông đường bộ");
        assertThat(parsedDocument.mimeType()).isEqualTo("text/html; charset=UTF-8");
        assertThat(parsedDocument.rawText()).contains("Điều 1", "Nội dung thứ nhất.", "Nội dung thứ hai.");
        assertThat(parsedDocument.sections()).containsExactly(
                new ParsedDocument.PageSection(1, "full", parsedDocument.rawText())
        );
    }

    @Test
    void parse_setsExplicitParserIdentityAndVersionForUrlHtmlIngestion() {
        ParsedDocument parsedDocument = parser.parse(new FetchResult(
                "https://example.vn/source",
                "https://example.vn/source",
                200,
                "text/html",
                "Title hint",
                "<html><body>Nội dung</body></html>",
                null,
                null
        ));

        assertThat(parsedDocument.parserName()).isEqualTo("spring-ai-jsoup-reader");
        assertThat(parsedDocument.parserVersion()).isEqualTo("2.0.0-M4");
    }

    @Test
    void parse_preservesSinglePageSingleSectionSemanticsForHtmlImports() {
        ParsedDocument parsedDocument = parser.parse(new FetchResult(
                "https://example.vn/source",
                "https://example.vn/source",
                200,
                "text/html",
                "Title hint",
                "<html><body><article>Một trang HTML duy nhất</article></body></html>",
                null,
                null
        ));

        assertThat(parsedDocument.sections()).hasSize(1);
        assertThat(parsedDocument.sections().getFirst().pageNumber()).isEqualTo(1);
        assertThat(parsedDocument.sections().getFirst().sectionRef()).isEqualTo("full");
        assertThat(parsedDocument.sections().getFirst().text()).contains("Một trang HTML duy nhất");
    }

    @Test
    void parse_usesTitleHintWhenReaderMetadataDoesNotProvideTitle() {
        ParsedDocument parsedDocument = parser.parse(new FetchResult(
                "https://example.vn/source",
                "https://example.vn/source",
                200,
                "text/html",
                "Tiêu đề gợi ý",
                "<html><body>Không có thẻ title</body></html>",
                null,
                null
        ));

        assertThat(parsedDocument.title()).isEqualTo("Tiêu đề gợi ý");
    }
}
