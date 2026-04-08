package com.vn.traffic.chatbot.ingestion.parser.springai;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.ingestion.fetch.FetchResult;
import com.vn.traffic.chatbot.ingestion.parser.DocumentParser;
import com.vn.traffic.chatbot.ingestion.parser.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HtmlDocumentParser implements DocumentParser {

    static final String PARSER_NAME = "spring-ai-jsoup-reader";
    static final String PARSER_VERSION = "2.0.0-M4";

    private static final JsoupDocumentReaderConfig READER_CONFIG = JsoupDocumentReaderConfig.builder()
            .selector("body")
            .separator("\n\n")
            .metadataTag("description")
            .additionalMetadata(Map.of("parserName", PARSER_NAME, "parserVersion", PARSER_VERSION))
            .build();

    @Override
    public ParsedDocument parse(java.io.InputStream content, String mimeType, String fileName) {
        throw new UnsupportedOperationException("HtmlDocumentParser expects a FetchResult input");
    }

    @Override
    public ParsedDocument parse(FetchResult fetchResult) {
        try {
            JsoupDocumentReader reader = new JsoupDocumentReader(
                    new ByteArrayResource(fetchResult.body().getBytes(StandardCharsets.UTF_8)),
                    READER_CONFIG
            );
            List<Document> springDocuments = reader.get();
            String rawText = springDocuments.stream()
                    .map(Document::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n\n"));

            String title = springDocuments.stream()
                    .map(document -> document.getMetadata().get("title"))
                    .filter(value -> value instanceof String)
                    .map(String.class::cast)
                    .filter(text -> !text.isBlank())
                    .findFirst()
                    .orElse(fetchResult.titleHint());

            ParsedDocument.PageSection section = new ParsedDocument.PageSection(1, "full", rawText);
            return new ParsedDocument(
                    rawText,
                    title,
                    fetchResult.contentType(),
                    PARSER_NAME,
                    PARSER_VERSION,
                    List.of(section)
            );
        } catch (Exception ex) {
            log.error("Spring AI HTML parsing failed for finalUrl={}: {}", fetchResult.finalUrl(), ex.getMessage());
            throw new AppException(ErrorCode.INGESTION_FAILED, "HTML parsing failed: " + ex.getMessage());
        }
    }
}
