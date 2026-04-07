package com.vn.traffic.chatbot.ingestion.api.dto;

import java.util.UUID;

public record IngestionAcceptedResponse(
        UUID sourceId,
        UUID jobId,
        String status
) {}
