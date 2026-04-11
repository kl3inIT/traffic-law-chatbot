package com.vn.traffic.chatbot.chat.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatThreadSummaryResponse(
        UUID threadId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String firstMessage
) {
}
