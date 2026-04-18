package com.vn.traffic.chatbot.chat.citation;

import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class CitationMapper {


    /** Inline-label prefix for Vietnamese [Nguồn n] format — Pitfall 4 defense. */
    public static final String INLINE_LABEL_PREFIX = "Nguồn ";

    private static final int EXCERPT_LIMIT = 280;

    public List<CitationResponse> toCitations(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        return java.util.stream.IntStream.range(0, documents.size())
                .mapToObj(index -> toCitation(documents.get(index), index + 1))
                .toList();
    }

    public List<SourceReferenceResponse> toSources(List<CitationResponse> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }

        Map<String, SourceReferenceResponse> deduplicated = new LinkedHashMap<>();
        for (CitationResponse citation : citations) {
            if (citation == null) {
                continue;
            }
            String key = String.join("|",
                    safe(citation.sourceId()),
                    safe(citation.sourceVersionId()),
                    safe(citation.origin()),
                    String.valueOf(citation.pageNumber()),
                    safe(citation.sectionRef()));
            deduplicated.putIfAbsent(key, new SourceReferenceResponse(
                    citation.inlineLabel(),
                    citation.sourceId(),
                    citation.sourceVersionId(),
                    citation.sourceTitle(),
                    citation.origin(),
                    citation.pageNumber(),
                    citation.sectionRef()
            ));
        }
        return deduplicated.values().stream().toList();
    }

    private CitationResponse toCitation(Document document, int labelNumber) {
        Map<String, Object> metadata = document.getMetadata();
        String origin = stringValue(metadata.get("origin"));
        return new CitationResponse(
                INLINE_LABEL_PREFIX + labelNumber,
                stringValue(metadata.get("sourceId")),
                stringValue(metadata.get("sourceVersionId")),
                resolveSourceTitle(metadata, origin),
                origin,
                integerValue(metadata.get("pageNumber")),
                stringValue(metadata.get("sectionRef")),
                buildExcerpt(document.getText())
        );
    }

    private String resolveSourceTitle(Map<String, Object> metadata, String origin) {
        String sourceTitle = stringValue(metadata.get("sourceTitle"));
        return hasText(sourceTitle) ? sourceTitle : origin;
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String buildExcerpt(String text) {
        String normalized = normalizeWhitespace(text);
        if (normalized.length() <= EXCERPT_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, EXCERPT_LIMIT);
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private String stringValue(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
