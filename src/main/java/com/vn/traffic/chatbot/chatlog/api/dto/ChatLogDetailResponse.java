package com.vn.traffic.chatbot.chatlog.api.dto;

import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.chatlog.domain.ChatLog;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatLogDetailResponse(
        UUID id,
        String conversationId,
        String question,
        GroundingStatus groundingStatus,
        Integer promptTokens,
        Integer completionTokens,
        Integer responseTime,
        OffsetDateTime createdDate,
        String answer,
        String sources
) {
    public static ChatLogDetailResponse fromEntity(ChatLog log) {
        return new ChatLogDetailResponse(
                log.getId(),
                log.getConversationId(),
                log.getQuestion(),
                log.getGroundingStatus(),
                log.getPromptTokens(),
                log.getCompletionTokens(),
                log.getResponseTime(),
                log.getCreatedDate(),
                log.getAnswer(),
                log.getSources()
        );
    }
}
