package com.vn.traffic.chatbot.source.api.dto;

import com.vn.traffic.chatbot.source.domain.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SourceDetailResponse(
        UUID id,
        SourceType sourceType,
        String title,
        OriginKind originKind,
        String originValue,
        String publisherName,
        String languageCode,
        SourceStatus status,
        TrustedState trustedState,
        ApprovalState approvalState,
        UUID activeVersionId,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy,
        List<KbSourceVersion> versions
) {
}
