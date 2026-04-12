package com.vn.traffic.chatbot.parameter.domain;

public enum AllowedModel {
    CLAUDE_3_5_SONNET("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet"),
    CLAUDE_3_5_HAIKU("claude-3-5-haiku-20241022", "Claude 3.5 Haiku"),
    CLAUDE_3_OPUS("claude-3-opus-20240229", "Claude 3 Opus");

    private final String modelId;
    private final String displayName;

    AllowedModel(String modelId, String displayName) {
        this.modelId = modelId;
        this.displayName = displayName;
    }

    public String getModelId() {
        return modelId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
