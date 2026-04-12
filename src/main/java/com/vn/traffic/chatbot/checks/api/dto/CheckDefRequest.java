package com.vn.traffic.chatbot.checks.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckDefRequest(
        @NotBlank @Size(min = 10) String question,
        @NotBlank @Size(min = 10) String referenceAnswer,
        String category,
        Boolean active
) {
}
