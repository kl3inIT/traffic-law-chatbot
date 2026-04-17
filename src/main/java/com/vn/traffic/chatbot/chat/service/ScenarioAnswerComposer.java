package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ScenarioAnalysisResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class ScenarioAnswerComposer {

    /**
     * Compose scenario analysis from an existing ChatAnswerResponse plus effective sources.
     * After plan 07-03 slimmed LegalAnswerDraft to 8 fields, scenario-specific rule/outcome/actions
     * data is carried on the existing ChatAnswerResponse (scenarioFacts + scenarioAnalysis wrapper)
     * rather than on LegalAnswerDraft.
     */
    public ScenarioComposition compose(
            ChatAnswerResponse answer,
            List<SourceReferenceResponse> sources
    ) {
        GroundingStatus groundingStatus = answer == null ? null : answer.groundingStatus();
        ScenarioAnalysisResponse existing = answer == null ? null : answer.scenarioAnalysis();

        List<String> facts = buildFacts(answer, existing);
        String rule = firstNonBlank(
                existing == null ? null : existing.rule(),
                joinLines(answer == null ? null : answer.legalBasis())
        );
        String outcome = firstNonBlank(
                existing == null ? null : existing.outcome(),
                answer == null ? null : answer.conclusion()
        );
        List<String> actions = buildActions(answer, existing);
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

    private List<String> buildFacts(ChatAnswerResponse answer, ScenarioAnalysisResponse existing) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        if (existing != null) {
            safeList(existing.facts()).stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .forEach(facts::add);
        }
        if (answer != null) {
            safeList(answer.scenarioFacts()).stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .forEach(facts::add);
        }
        return List.copyOf(facts);
    }

    private List<String> buildActions(ChatAnswerResponse answer, ScenarioAnalysisResponse existing) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if (existing != null) {
            safeList(existing.actions()).stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .forEach(actions::add);
        }
        if (answer != null) {
            safeList(answer.nextSteps()).stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .forEach(actions::add);
        }
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
