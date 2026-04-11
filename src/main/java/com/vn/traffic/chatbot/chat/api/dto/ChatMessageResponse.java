package com.vn.traffic.chatbot.chat.api.dto;

import com.vn.traffic.chatbot.chat.domain.ChatMessageRole;
import com.vn.traffic.chatbot.chat.domain.ChatMessageType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        ChatMessageRole role,
        ChatMessageType messageType,
        String content,
        OffsetDateTime createdAt
) {
}
