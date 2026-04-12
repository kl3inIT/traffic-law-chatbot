package com.vn.traffic.chatbot.checks.api.dto;

import com.vn.traffic.chatbot.checks.domain.CheckRun;
import com.vn.traffic.chatbot.checks.domain.CheckRunStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CheckRunDetailResponse(
        UUID id,
        CheckRunStatus status,
        Double averageScore,
        String parameterSetName,
        Integer checkCount,
        OffsetDateTime createdDate
) {
    public static CheckRunDetailResponse fromEntity(CheckRun run) {
        return new CheckRunDetailResponse(
                run.getId(),
                run.getStatus(),
                run.getAverageScore(),
                run.getParameterSetName(),
                run.getCheckCount(),
                run.getCreatedDate()
        );
    }
}
