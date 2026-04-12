package com.vn.traffic.chatbot.chatlog.api.dto;

import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.chatlog.domain.ChatLog;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatLogResponse(
        UUID id,
        String conversationId,
        String question,
        GroundingStatus groundingStatus,
        Integer promptTokens,
        Integer completionTokens,
        Integer responseTime,
        OffsetDateTime createdDate
) {
    public static ChatLogResponse fromEntity(ChatLog log) {
        String questionPreview = log.getQuestion() != null && log.getQuestion().length() > 200
                ? log.getQuestion().substring(0, 200)
                : log.getQuestion();
        return new ChatLogResponse(
                log.getId(),
                log.getConversationId(),
                questionPreview,
                log.getGroundingStatus(),
                log.getPromptTokens(),
                log.getCompletionTokens(),
                log.getResponseTime(),
                log.getCreatedDate()
        );
    }
}
