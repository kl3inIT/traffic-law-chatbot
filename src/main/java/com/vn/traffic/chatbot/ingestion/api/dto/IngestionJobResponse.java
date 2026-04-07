package com.vn.traffic.chatbot.ingestion.api.dto;

import com.vn.traffic.chatbot.ingestion.domain.IngestionJobStatus;
import com.vn.traffic.chatbot.ingestion.domain.IngestionStep;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IngestionJobResponse(
        UUID id,
        UUID sourceId,
        IngestionJobStatus status,
        IngestionStep stepName,
        OffsetDateTime queuedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Integer retryCount,
        String errorCode,
        String errorMessage
) {}
