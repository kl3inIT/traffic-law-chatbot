package com.vn.traffic.chatbot.chat.api.dto;

import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatAnswerResponse(
        GroundingStatus groundingStatus,
        UUID threadId,
        ResponseMode responseMode,
        String answer,
        String conclusion,
        String disclaimer,
        String uncertaintyNotice,
        List<String> legalBasis,
        List<String> penalties,
        List<String> requiredDocuments,
        List<String> procedureSteps,
        List<String> nextSteps,
        List<String> scenarioFacts,
        ScenarioAnalysisResponse scenarioAnalysis,
        List<CitationResponse> citations,
        List<SourceReferenceResponse> sources
) {
}
