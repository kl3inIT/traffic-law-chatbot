package com.vn.traffic.chatbot.chat.intent;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Structured intent classification of a user chat message (D-09).
 *
 * <p>Produced by {@link IntentClassifier} via
 * {@code ChatClient.prompt()....call().entity(IntentDecision.class)} using
 * {@code BeanOutputConverter} schema generation. Jackson description
 * annotations feed the schema the LLM consumes.
 */
@JsonClassDescription("Intent classification of a user chat message about Vietnamese traffic law")
public record IntentDecision(
        @JsonPropertyDescription("LEGAL=traffic-law question; CHITCHAT=greeting/social; OFF_TOPIC=unrelated domain")
        Intent intent,
        @JsonPropertyDescription("Classifier confidence in [0.0, 1.0]; 0 if unsure")
        double confidence
) {
    public enum Intent { CHITCHAT, LEGAL, OFF_TOPIC }
}
