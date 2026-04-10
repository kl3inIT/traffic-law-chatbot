package com.vn.traffic.chatbot.chat.api.dto;

public record SourceReferenceResponse(
        String inlineLabel,
        String sourceId,
        String sourceVersionId,
        String sourceTitle,
        String origin,
        Integer pageNumber,
        String sectionRef
) {
}
