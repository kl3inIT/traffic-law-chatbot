package com.vn.traffic.chatbot.ingestion.parser.springai;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.ingestion.parser.DocumentParser;
import com.vn.traffic.chatbot.ingestion.parser.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PdfDocumentParser implements DocumentParser {

    static final String PARSER_NAME = "spring-ai-pdf-reader";
    static final String PARSER_VERSION = "2.0.0-M4";

    private static final PdfDocumentReaderConfig READER_CONFIG = PdfDocumentReaderConfig.builder()
            .withPagesPerDocument(1)
            .build();

    @Override
    public ParsedDocument parse(InputStream content, String mimeType, String fileName) {
        if (!isSupported(mimeType, fileName)) {
            throw new UnsupportedOperationException("PdfDocumentParser supports PDF inputs only");
        }

        try {
            byte[] bytes = content.readAllBytes();
            PagePdfDocumentReader reader = new PagePdfDocumentReader(new NamedByteArrayResource(bytes, fileName), READER_CONFIG);
            List<Document> springDocuments = reader.get();
            List<ParsedDocument.PageSection> sections = new ArrayList<>();
            StringBuilder rawText = new StringBuilder();

            for (Document document : springDocuments) {
                String text = document.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                int pageNumber = extractPageNumber(document.getMetadata());
                sections.add(new ParsedDocument.PageSection(pageNumber, "page-" + pageNumber, text));
                if (!rawText.isEmpty()) {
                    rawText.append('\f');
                }
                rawText.append(text);
            }

            if (sections.isEmpty()) {
                throw new AppException(ErrorCode.INGESTION_FAILED, "PDF parsing produced no page content");
            }

            return new ParsedDocument(
                    rawText.toString(),
                    fileName,
                    mimeType,
                    PARSER_NAME,
                    PARSER_VERSION,
                    sections
            );
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Spring AI PDF parsing failed for file={}: {}", fileName, ex.getMessage());
            throw new AppException(ErrorCode.INGESTION_FAILED, "PDF parsing failed: " + ex.getMessage());
        }
    }

    public boolean isSupported(String mimeType, String fileName) {
        return "application/pdf".equalsIgnoreCase(mimeType)
                || (fileName != null && fileName.toLowerCase().endsWith(".pdf"));
    }

    private int extractPageNumber(Map<String, Object> metadata) {
        Object startPage = metadata.get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        if (startPage instanceof Number number) {
            return number.intValue();
        }
        if (startPage != null) {
            return Integer.parseInt(String.valueOf(startPage));
        }
        return 1;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
