package com.vn.traffic.chatbot.ai.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AiModelProperties — verifies record construction and field access.
 * Uses direct constructor calls (no Spring context needed for a record).
 */
class AiModelPropertiesTest {

    private AiModelProperties buildProperties() {
        return new AiModelProperties(
                "claude-sonnet-4-6",
                "claude-haiku-4-5-20251001",
                List.of(
                        new AiModelProperties.ModelEntry("gpt-5.4", "GPT-5.4"),
                        new AiModelProperties.ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6"),
                        new AiModelProperties.ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5")
                )
        );
    }

    @Test
    void chatModelBindsToCorrectDefault() {
        AiModelProperties props = buildProperties();

        assertThat(props.chatModel())
                .as("chatModel should be claude-sonnet-4-6")
                .isEqualTo("claude-sonnet-4-6");
    }

    @Test
    void modelsListHasThreeEntries() {
        AiModelProperties props = buildProperties();

        assertThat(props.models())
                .as("models list should have exactly 3 entries")
                .hasSize(3);
    }

    @Test
    void firstModelEntryHasCorrectIdAndDisplayName() {
        AiModelProperties props = buildProperties();

        AiModelProperties.ModelEntry first = props.models().get(0);
        assertThat(first.id()).as("first model id").isEqualTo("gpt-5.4");
        assertThat(first.displayName()).as("first model displayName").isEqualTo("GPT-5.4");
    }

    @Test
    void evaluatorModelBindsCorrectly() {
        AiModelProperties props = buildProperties();

        assertThat(props.evaluatorModel())
                .as("evaluatorModel should be claude-haiku-4-5-20251001")
                .isEqualTo("claude-haiku-4-5-20251001");
    }
}
