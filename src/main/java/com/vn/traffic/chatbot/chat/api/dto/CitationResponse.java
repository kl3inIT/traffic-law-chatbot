package com.vn.traffic.chatbot.chat.api.dto;

public record CitationResponse(
        String inlineLabel,
        String sourceId,
        String sourceVersionId,
        String sourceTitle,
        String origin,
        Integer pageNumber,
        String sectionRef,
        String excerpt
) {
}
