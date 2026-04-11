package com.vn.traffic.chatbot.source.api.dto;

import com.vn.traffic.chatbot.source.domain.TrustTier;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TrustPolicyResponse(
        UUID id,
        String name,
        String domainPattern,
        String sourceType,
        TrustTier trustTier,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
