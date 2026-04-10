package com.vn.traffic.chatbot.chat.service;

import java.util.List;

public record LegalAnswerDraft(
        String conclusion,
        String answer,
        String uncertaintyNotice,
        List<String> legalBasis,
        List<String> penalties,
        List<String> requiredDocuments,
        List<String> procedureSteps,
        List<String> nextSteps,
        List<String> scenarioFacts,
        List<String> scenarioRule,
        List<String> scenarioOutcome,
        List<String> scenarioActions
) {
}
