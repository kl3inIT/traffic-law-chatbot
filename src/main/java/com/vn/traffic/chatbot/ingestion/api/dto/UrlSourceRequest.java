package com.vn.traffic.chatbot.ingestion.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UrlSourceRequest(
        @NotBlank String url,
        String title,
        String publisherName,
        String createdBy
) {}
