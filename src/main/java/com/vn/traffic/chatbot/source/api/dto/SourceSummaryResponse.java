package com.vn.traffic.chatbot.source.api.dto;

import com.vn.traffic.chatbot.source.domain.ApprovalState;
import com.vn.traffic.chatbot.source.domain.SourceStatus;
import com.vn.traffic.chatbot.source.domain.SourceType;
import com.vn.traffic.chatbot.source.domain.TrustedState;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SourceSummaryResponse(
        UUID id,
        String title,
        SourceType sourceType,
        SourceStatus status,
        TrustedState trustedState,
        ApprovalState approvalState,
        OffsetDateTime createdAt
) {
}
