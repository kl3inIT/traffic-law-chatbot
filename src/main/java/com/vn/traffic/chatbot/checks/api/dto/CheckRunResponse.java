package com.vn.traffic.chatbot.checks.api.dto;

import com.vn.traffic.chatbot.checks.domain.CheckRun;
import com.vn.traffic.chatbot.checks.domain.CheckRunStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CheckRunResponse(
        UUID id,
        CheckRunStatus status,
        Double averageScore,
        String parameterSetName,
        Integer checkCount,
        OffsetDateTime createdDate
) {
    public static CheckRunResponse fromEntity(CheckRun run) {
        return new CheckRunResponse(
                run.getId(),
                run.getStatus(),
                run.getAverageScore(),
                run.getParameterSetName(),
                run.getCheckCount(),
                run.getCreatedDate()
        );
    }
}
