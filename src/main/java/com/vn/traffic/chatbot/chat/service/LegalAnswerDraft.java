package com.vn.traffic.chatbot.chat.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured legal answer draft produced by the chat pipeline (ARCH-02).
 *
 * <p>Consumed by {@code ChatClient.prompt()....call().entity(LegalAnswerDraft.class)}
 * via {@code BeanOutputConverter} — Jackson {@code @JsonPropertyDescription}
 * annotations feed the generated JSON schema so the LLM knows what each field
 * is expected to contain. Do NOT add {@code @JsonProperty(required = true)}
 * (RESEARCH §2.4) — it breaks transitional paths; Plan 08-03 ChatService
 * rewrite owns the drop of the legacy fallback paths.
 */
@JsonClassDescription("Structured legal answer draft for Vietnamese traffic-law questions")
public record LegalAnswerDraft(
        @JsonPropertyDescription("One-sentence conclusion (Vietnamese)")
        String conclusion,
        @JsonPropertyDescription("Full answer text (Vietnamese) grounded in cited legal sources")
        String answer,
        @JsonPropertyDescription("Explicit uncertainty notice if the grounded sources do not fully answer")
        String uncertaintyNotice,
        @JsonPropertyDescription("Relevant legal-basis citations (điều/khoản, nghị định/luật)")
        List<String> legalBasis,
        @JsonPropertyDescription("Applicable penalties with amounts if stated in the sources")
        List<String> penalties,
        @JsonPropertyDescription("Required documents the user must prepare")
        List<String> requiredDocuments,
        @JsonPropertyDescription("Ordered procedure steps")
        List<String> procedureSteps,
        @JsonPropertyDescription("Recommended next steps for the user")
        List<String> nextSteps
) {
}
