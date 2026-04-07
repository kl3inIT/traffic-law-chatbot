package com.vn.traffic.chatbot.source.api.dto;

public record ApprovalRequest(
        String reason,
        String actedBy
) {
}
