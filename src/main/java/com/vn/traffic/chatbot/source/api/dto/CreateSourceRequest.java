package com.vn.traffic.chatbot.source.api.dto;

import com.vn.traffic.chatbot.source.domain.OriginKind;
import com.vn.traffic.chatbot.source.domain.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSourceRequest(
        @NotNull SourceType sourceType,
        @NotBlank String title,
        @NotNull OriginKind originKind,
        String originValue,
        String publisherName,
        String languageCode,
        String createdBy
) {
}
