package com.vn.traffic.chatbot.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatQuestionRequest(
        @NotBlank
        @Size(max = 4000)
        String question,

        // Per D-15: nullable, no @NotBlank — model selection is optional
        String modelId
) {

    public CreateChatThreadRequest toCreateThreadRequest() {
        return new CreateChatThreadRequest(question);
    }
}
