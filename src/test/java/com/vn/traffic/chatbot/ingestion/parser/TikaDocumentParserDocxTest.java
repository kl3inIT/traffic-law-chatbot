package com.vn.traffic.chatbot.ingestion.parser;

import com.vn.traffic.chatbot.ingestion.chunking.ChunkResult;
import com.vn.traffic.chatbot.ingestion.chunking.TokenChunkingService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TikaDocumentParserDocxTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();
    private final TokenChunkingService tokenChunkingService = new TokenChunkingService();

    @Test
    void parse_extractsVisibleTextFromMinimalDocx() throws Exception {
        ParsedDocument parsedDocument = parser.parse(
                new ByteArrayInputStream(minimalDocxBytes(List.of(
                        "Nghị định 168/2024/NĐ-CP - mẫu kiểm thử UAT phase 2.",
                        "Người điều khiển xe mô tô vượt đèn tín hiệu màu đỏ bị xử phạt tiền từ 4.000.000 đồng đến 6.000.000 đồng.",
                        "Căn cứ pháp lý: Điều 7 Nghị định 168/2024/NĐ-CP."
                ))),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "uat-phase2-legal.docx"
        );

        assertThat(parsedDocument.parserName()).isEqualTo("tika");
        assertThat(parsedDocument.mimeType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(normalizeWhitespace(parsedDocument.rawText()))
                .contains("Nghị định 168/2024/NĐ-CP - mẫu kiểm thử UAT phase 2.")
                .contains("Người điều khiển xe mô tô vượt đèn tín hiệu màu đỏ")
                .contains("Căn cứ pháp lý: Điều 7 Nghị định 168/2024/NĐ-CP.");
        assertThat(parsedDocument.sections()).hasSize(1);
        assertThat(normalizeWhitespace(parsedDocument.sections().getFirst().text()))
                .contains("Nghị định 168/2024/NĐ-CP - mẫu kiểm thử UAT phase 2.");
    }

    @Test
    void parse_producesChunkableContentForMinimalDocx() throws Exception {
        ParsedDocument parsedDocument = parser.parse(
                new ByteArrayInputStream(minimalDocxBytes(List.of(
                        "Nghị định 168/2024/NĐ-CP - mẫu kiểm thử UAT phase 2.",
                        "Người điều khiển xe mô tô vượt đèn tín hiệu màu đỏ bị xử phạt tiền từ 4.000.000 đồng đến 6.000.000 đồng.",
                        "Căn cứ pháp lý: Điều 7 Nghị định 168/2024/NĐ-CP."
                ))),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "uat-phase2-legal.docx"
        );

        List<ChunkResult> chunks = tokenChunkingService.chunk(parsedDocument, "source-1", "version-1", "1.0");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.getFirst().text()).contains("Nghị định 168/2024/NĐ-CP");
    }

    private byte[] minimalDocxBytes(List<String> paragraphs) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeEntry(zip, "[Content_Types].xml", """
                    <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
                    <Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">
                      <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>
                      <Default Extension=\"xml\" ContentType=\"application/xml\"/>
                      <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>
                    </Types>
                    """);
            writeEntry(zip, "_rels/.rels", """
                    <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
                    <Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">
                      <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>
                    </Relationships>
                    """);
            writeEntry(zip, "word/document.xml", buildDocumentXml(paragraphs));
            zip.finish();
            return output.toByteArray();
        }
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String buildDocumentXml(List<String> paragraphs) {
        String body = paragraphs.stream()
                .map(this::paragraphXml)
                .reduce("", String::concat);
        return """
                <?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
                <w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">
                  <w:body>
                """ + body + """
                    <w:sectPr>
                      <w:pgSz w:w=\"11906\" w:h=\"16838\"/>
                      <w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>
                    </w:sectPr>
                  </w:body>
                </w:document>
                """;
    }

    private String paragraphXml(String text) {
        return "<w:p><w:r><w:t>" + escapeXml(text) + "</w:t></w:r></w:p>";
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String normalizeWhitespace(String text) {
        return text == null ? null : text.replaceAll("\\s+", " ").trim();
    }
}
