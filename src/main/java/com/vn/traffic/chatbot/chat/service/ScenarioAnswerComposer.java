package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ScenarioAnalysisResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class ScenarioAnswerComposer {

    public ScenarioComposition compose(
            GroundingStatus groundingStatus,
            LegalAnswerDraft draft,
            List<SourceReferenceResponse> sources
    ) {
        List<String> facts = buildFacts(draft);
        String rule = firstNonBlank(joinLines(draft == null ? null : draft.scenarioRule()), joinLines(draft == null ? null : draft.legalBasis()));
        String outcome = firstNonBlank(joinLines(draft == null ? null : draft.scenarioOutcome()), draft == null ? null : draft.conclusion());
        List<String> actions = buildActions(draft);
        List<SourceReferenceResponse> safeSources = safeList(sources);

        boolean canFinalize = groundingStatus == GroundingStatus.GROUNDED;
        boolean hasStructuredScenario = canFinalize && !facts.isEmpty() && hasText(rule) && hasText(outcome);
        ResponseMode responseMode = groundingStatus == GroundingStatus.REFUSED
                ? ResponseMode.REFUSED
                : hasStructuredScenario
                ? ResponseMode.FINAL_ANALYSIS
                : ResponseMode.SCENARIO_ANALYSIS;

        ScenarioAnalysisResponse scenarioAnalysis = hasStructuredScenario
                ? new ScenarioAnalysisResponse(facts, rule.trim(), outcome.trim(), actions, safeSources)
                : null;

        return new ScenarioComposition(responseMode, scenarioAnalysis);
    }

    private List<String> buildFacts(LegalAnswerDraft draft) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        safeList(draft == null ? null : draft.scenarioFacts()).stream()
                .filter(this::hasText)
                .map(String::trim)
                .forEach(facts::add);
        return List.copyOf(facts);
    }

    private List<String> buildActions(LegalAnswerDraft draft) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        safeList(draft == null ? null : draft.scenarioActions()).stream()
                .filter(this::hasText)
                .map(String::trim)
                .forEach(actions::add);
        safeList(draft == null ? null : draft.nextSteps()).stream()
                .filter(this::hasText)
                .map(String::trim)
                .forEach(actions::add);
        return List.copyOf(actions);
    }

    private String joinLines(List<String> lines) {
        List<String> safeLines = safeList(lines).stream()
                .filter(this::hasText)
                .map(String::trim)
                .toList();
        if (safeLines.isEmpty()) {
            return null;
        }
        return String.join("\n", safeLines);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (hasText(primary)) {
            return primary;
        }
        return hasText(fallback) ? fallback : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> safeList(List<T> values) {
        if (values == null) {
            return List.of();
        }
        List<T> safe = new ArrayList<>();
        for (T value : values) {
            if (value != null) {
                safe.add(value);
            }
        }
        return List.copyOf(safe);
    }

    public record ScenarioComposition(ResponseMode responseMode, ScenarioAnalysisResponse scenarioAnalysis) {
    }
}
