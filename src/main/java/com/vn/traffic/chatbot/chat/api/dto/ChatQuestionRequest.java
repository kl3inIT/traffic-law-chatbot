package com.vn.traffic.chatbot.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatQuestionRequest(
        @NotBlank
        @Size(max = 4000)
        String question
) {
}
