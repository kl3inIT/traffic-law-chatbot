package com.vn.traffic.chatbot.ingestion.chunking;

import com.vn.traffic.chatbot.ingestion.parser.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Splits ParsedDocument sections into fixed-size overlapping chunks.
 * Strategy: max 1500 characters per chunk, 150-character overlap (advance 1350 per chunk).
 */
@Component
@Slf4j
public class TextChunker {

    private static final int CHUNK_SIZE = 1500;
    private static final int OVERLAP = 150;
    private static final int ADVANCE = CHUNK_SIZE - OVERLAP; // 1350

    public List<ChunkResult> chunk(ParsedDocument doc, String sourceId, String sourceVersionId,
                                   String processingVersion) {
        List<ChunkResult> results = new ArrayList<>();
        int ordinal = 0;

        for (ParsedDocument.PageSection section : doc.sections()) {
            String text = section.text();
            if (text == null || text.isBlank()) {
                continue;
            }

            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + CHUNK_SIZE, text.length());
                String chunkText = text.substring(start, end);

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

                if (end == text.length()) {
                    break;
                }
                start += ADVANCE;
            }
        }

        log.debug("Chunked document into {} chunks (sourceId={}, versionId={})",
                results.size(), sourceId, sourceVersionId);
        return results;
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is always available in Java
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
