package com.vn.traffic.chatbot.ingestion.api.dto;

import java.util.UUID;

public record BatchImportItemResult(
        String url,
        String status,       // "ACCEPTED" | "DUPLICATE" | "ERROR"
        UUID sourceId,
        UUID jobId,
        String errorMessage
) {}
