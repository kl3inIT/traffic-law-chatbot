package com.vn.traffic.chatbot.source.api.dto;

import com.vn.traffic.chatbot.source.domain.TrustTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TrustPolicyRequest(
        @NotBlank String name,
        String domainPattern,
        String sourceType,
        @NotNull TrustTier trustTier,
        String description
) {}
