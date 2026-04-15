package com.vn.traffic.chatbot.parameter.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiParameterSetResponse(
        UUID id,
        String name,
        boolean active,
        String content,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
