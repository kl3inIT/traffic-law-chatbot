package com.vn.traffic.chatbot.chat.api.dto;

public record PendingFactResponse(
        String code,
        String prompt,
        String reason
) {
}
