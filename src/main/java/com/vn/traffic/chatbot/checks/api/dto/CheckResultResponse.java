package com.vn.traffic.chatbot.checks.api.dto;

import com.vn.traffic.chatbot.checks.domain.CheckResult;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CheckResultResponse(
        UUID id,
        String question,
        String referenceAnswer,
        String actualAnswer,
        Double score,
        String log,
        OffsetDateTime createdDate
) {
    public static CheckResultResponse fromEntity(CheckResult result) {
        return new CheckResultResponse(
                result.getId(),
                result.getQuestion(),
                result.getReferenceAnswer(),
                result.getActualAnswer(),
                result.getScore(),
                result.getLog(),
                result.getCreatedDate()
        );
    }
}
