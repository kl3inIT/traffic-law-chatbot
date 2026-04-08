package com.vn.traffic.chatbot.ingestion.parser.springai;

import com.vn.traffic.chatbot.ingestion.chunking.ChunkResult;
import com.vn.traffic.chatbot.ingestion.chunking.TextChunker;
import com.vn.traffic.chatbot.ingestion.parser.ParsedDocument;
import com.vn.traffic.chatbot.ingestion.parser.TikaDocumentParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiPdfParserParityTest {

    private final TikaDocumentParser tikaDocumentParser = new TikaDocumentParser();
    private final TextChunker textChunker = new TextChunker();

    @Test
    void parse_mapsMultipagePdfIntoPageAwareSectionsWithStableRefs() throws Exception {
        SpringAiPdfParser parser = new SpringAiPdfParser();

        ParsedDocument parsedDocument = parser.parse(
                new ByteArrayInputStream(multiPagePdfBytes()),
                "application/pdf",
                "traffic-law.pdf"
        );

        assertThat(parsedDocument.mimeType()).isEqualTo("application/pdf");
        assertThat(parsedDocument.sections()).hasSize(2);
        assertThat(parsedDocument.sections()).extracting(ParsedDocument.PageSection::pageNumber)
                .containsExactly(1, 2);
        assertThat(parsedDocument.sections()).extracting(ParsedDocument.PageSection::sectionRef)
                .containsExactly("page-1", "page-2");
        assertThat(normalizeWhitespace(parsedDocument.sections().get(0).text())).contains("Page 1 traffic law");
        assertThat(normalizeWhitespace(parsedDocument.sections().get(1).text())).contains("Page 2 traffic light");
    }

    @Test
    void parse_exposesExplicitParserIdentityAndVersionForPersistence() throws Exception {
        SpringAiPdfParser parser = new SpringAiPdfParser();

        ParsedDocument parsedDocument = parser.parse(
                new ByteArrayInputStream(singlePagePdfBytes("Speed limit article 10")),
                "application/pdf",
                "speed-limit.pdf"
        );

        assertThat(parsedDocument.parserName()).isEqualTo("spring-ai-pdf-reader");
        assertThat(parsedDocument.parserVersion()).isEqualTo("2.0.0-M4");
    }

    @Test
    void parse_preservesChunkMetadataCompatibilityThroughExistingChunker() throws Exception {
        SpringAiPdfParser parser = new SpringAiPdfParser();
        ParsedDocument parsedDocument = parser.parse(
                new ByteArrayInputStream(multiPagePdfBytes()),
                "application/pdf",
                "traffic-law.pdf"
        );

        List<ChunkResult> chunks = textChunker.chunk(parsedDocument, "source-1", "version-1", "1.0");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.chunkOrdinal()).isGreaterThanOrEqualTo(0);
            assertThat(chunk.pageNumber()).isGreaterThan(0);
            assertThat(chunk.sectionRef()).startsWith("page-");
            assertThat(chunk.contentHash()).isNotBlank();
        });
    }

    @Test
    void parse_matchesCurrentTikaPdfProvenanceContractForMultipageSections() throws Exception {
        byte[] pdfBytes = multiPagePdfBytes();
        SpringAiPdfParser springAiParser = new SpringAiPdfParser();

        ParsedDocument tikaDocument = tikaDocumentParser.parse(
                new ByteArrayInputStream(pdfBytes),
                "application/pdf",
                "traffic-law.pdf"
        );
        ParsedDocument springAiDocument = springAiParser.parse(
                new ByteArrayInputStream(pdfBytes),
                "application/pdf",
                "traffic-law.pdf"
        );

        assertThat(tikaDocument.parserName()).isEqualTo("tika");
        assertThat(tikaDocument.parserVersion()).isEqualTo("3.3.0");
        assertThat(springAiDocument.sections()).hasSize(2);
        assertThat(springAiDocument.sections()).extracting(ParsedDocument.PageSection::pageNumber)
                .containsExactly(1, 2);
        assertThat(springAiDocument.sections()).extracting(ParsedDocument.PageSection::sectionRef)
                .containsExactly("page-1", "page-2");
        assertThat(textChunker.chunk(springAiDocument, "source-1", "version-1", "1.0"))
                .allSatisfy(chunk -> {
                    assertThat(chunk.pageNumber()).isGreaterThan(0);
                    assertThat(chunk.sectionRef()).startsWith("page-");
                    assertThat(chunk.contentHash()).isNotBlank();
                });
    }

    private byte[] multiPagePdfBytes() throws IOException {
        return pdfBytes(List.of(
                "Page 1 traffic law license requirement.",
                "Page 2 traffic light compliance requirement."
        ));
    }

    private byte[] singlePagePdfBytes(String text) throws IOException {
        return pdfBytes(List.of(text));
    }

    private byte[] pdfBytes(List<String> pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(72, 720);
                    contentStream.showText(text);
                    contentStream.endText();
                }
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private String normalizeWhitespace(String text) {
        return text == null ? null : text.replaceAll("\\s+", " ").trim();
    }
}
