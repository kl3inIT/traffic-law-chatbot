package com.vn.traffic.chatbot.ingestion.api.dto;

import com.vn.traffic.chatbot.source.domain.SourceType;
import com.vn.traffic.chatbot.source.domain.TrustTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BatchImportItemRequest(
        @NotBlank String url,
        String title,
        @NotNull SourceType sourceType,
        TrustTier trustCategory
) {}
