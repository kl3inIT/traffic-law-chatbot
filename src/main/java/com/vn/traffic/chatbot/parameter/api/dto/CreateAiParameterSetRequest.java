package com.vn.traffic.chatbot.parameter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAiParameterSetRequest(
        @NotBlank String name,
        @NotBlank @Size(max = 65536) String content,
        String chatModel,
        String evaluatorModel
) {
}
