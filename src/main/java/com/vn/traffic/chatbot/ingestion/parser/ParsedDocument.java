package com.vn.traffic.chatbot.ingestion.parser;

import java.util.List;

public record ParsedDocument(
        String rawText,
        String title,
        String mimeType,
        String parserName,
        String parserVersion,
        List<PageSection> sections
) {
    public ParsedDocument {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    public record PageSection(
            int pageNumber,
            String sectionRef,
            String text
    ) {}
}
