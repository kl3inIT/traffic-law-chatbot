package com.vn.traffic.chatbot.ingestion.parser;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.WriteOutContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Tika-backed document parser supporting PDF, Word, and structured documents.
 * T-03-03: Uses WriteOutContentHandler with a 10 MB character limit to prevent
 * memory exhaustion from maliciously large documents.
 */
@Component
@Slf4j
public class TikaDocumentParser implements DocumentParser {

    private static final int MAX_CHARS = 10 * 1024 * 1024; // 10 MB character limit
    private static final String PARSER_NAME = "tika";
    private static final String PARSER_VERSION = "3.3.0";

    @Override
    public ParsedDocument parse(InputStream content, String mimeType, String fileName) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            if (mimeType != null) {
                metadata.set(HttpHeaders.CONTENT_TYPE, mimeType);
            }
            if (fileName != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            }

            StringWriter writer = new StringWriter();
            ContentHandler handler = new WriteOutContentHandler(writer, MAX_CHARS);

            parser.parse(content, handler, metadata);

            String rawText = writer.toString();
            String detectedMime = metadata.get(HttpHeaders.CONTENT_TYPE);
            if (detectedMime == null) {
                detectedMime = mimeType;
            }
            String title = metadata.get(TikaCoreProperties.TITLE);
            if (title == null || title.isBlank()) {
                title = fileName;
            }

            List<ParsedDocument.PageSection> sections = buildSections(rawText, detectedMime);

            return new ParsedDocument(rawText, title, detectedMime, PARSER_NAME, PARSER_VERSION, sections);

        } catch (Exception ex) {
            log.error("Tika parsing failed for file={}: {}", fileName, ex.getMessage());
            throw new AppException(ErrorCode.INGESTION_FAILED, "Document parsing failed: " + ex.getMessage());
        }
    }

    private List<ParsedDocument.PageSection> buildSections(String rawText, String detectedMime) {
        List<ParsedDocument.PageSection> sections = new ArrayList<>();

        if ("application/pdf".equalsIgnoreCase(detectedMime)) {
            // Split on form-feed character (\f) to separate PDF pages
            String[] pages = rawText.split("\f", -1);
            for (int i = 0; i < pages.length; i++) {
                String pageText = pages[i];
                if (!pageText.isBlank()) {
                    sections.add(new ParsedDocument.PageSection(i + 1, "page-" + (i + 1), pageText));
                }
            }
        }

        // Fallback: single section for non-PDF or empty PDF split
        if (sections.isEmpty()) {
            sections.add(new ParsedDocument.PageSection(1, "full", rawText));
        }

        return sections;
    }
}
