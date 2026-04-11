package com.vn.traffic.chatbot.ingestion.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchImportRequest(
        @NotEmpty @Valid List<BatchImportItemRequest> items
) {}
