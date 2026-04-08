package com.vn.traffic.chatbot.ingestion.chunking;

import com.vn.traffic.chatbot.ingestion.parser.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Splits ParsedDocument sections into token-aware chunks while preserving source metadata.
 */
@Component
@Slf4j
public class TokenChunkingService {

    static final String STRATEGY = "spring-ai-token-text-splitter";
    static final String VERSION = "2.0.0-M4";

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int MIN_CHUNK_SIZE_CHARS = 400;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 10;
    private static final int MAX_NUM_CHUNKS = 10_000;

    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(DEFAULT_CHUNK_SIZE)
            .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
            .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
            .withMaxNumChunks(MAX_NUM_CHUNKS)
            .withKeepSeparator(true)
            .build();

    public List<ChunkResult> chunk(ParsedDocument doc, String sourceId, String sourceVersionId,
                                   String processingVersion) {
        List<ChunkResult> results = new ArrayList<>();
        int ordinal = 0;

        for (ParsedDocument.PageSection section : doc.sections()) {
            String text = section.text();
            if (text == null || text.isBlank()) {
                continue;
            }

            List<Document> splitDocuments = splitter.apply(List.of(new Document(text, Map.of(
                    "pageNumber", section.pageNumber(),
                    "sectionRef", section.sectionRef()
            ))));

            for (Document splitDocument : splitDocuments) {
                String chunkText = splitDocument.getText();
                if (chunkText == null || chunkText.isBlank()) {
                    continue;
                }

                results.add(new ChunkResult(
                        chunkText,
                        ordinal++,
                        section.pageNumber(),
                        section.sectionRef(),
                        sha256Hex(chunkText),
                        processingVersion,
                        sourceId,
                        sourceVersionId
                ));
            }
        }

        log.debug("Chunked document into {} chunks using {} {} (sourceId={}, versionId={})",
                results.size(), STRATEGY, VERSION, sourceId, sourceVersionId);
        return results;
    }

    public String strategy() {
        return STRATEGY;
    }

    public String version() {
        return VERSION;
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
