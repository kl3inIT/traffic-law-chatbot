package com.vn.traffic.chatbot.ingestion.api.dto;

import com.vn.traffic.chatbot.source.domain.SourceType;
import com.vn.traffic.chatbot.source.domain.TrustTier;
import jakarta.validation.constraints.NotBlank;

public record UrlSourceRequest(
        @NotBlank String url,
        String title,
        String publisherName,
        String createdBy,
        SourceType sourceType,
        TrustTier trustCategory
) {}
