package com.vn.traffic.chatbot.checks.api.dto;

import com.vn.traffic.chatbot.checks.domain.CheckDef;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CheckDefResponse(
        UUID id,
        String question,
        String referenceAnswer,
        String category,
        boolean active,
        OffsetDateTime createdAt
) {
    public static CheckDefResponse fromEntity(CheckDef def) {
        return new CheckDefResponse(
                def.getId(),
                def.getQuestion(),
                def.getReferenceAnswer(),
                def.getCategory(),
                def.isActive(),
                def.getCreatedAt()
        );
    }
}
