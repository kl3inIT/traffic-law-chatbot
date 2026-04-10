package com.vn.traffic.chatbot.chat.api.dto;

import com.vn.traffic.chatbot.chat.service.GroundingStatus;

import java.util.List;

public record ChatAnswerResponse(
        GroundingStatus groundingStatus,
        String answer,
        String conclusion,
        String disclaimer,
        String uncertaintyNotice,
        List<String> legalBasis,
        List<String> penalties,
        List<String> requiredDocuments,
        List<String> procedureSteps,
        List<String> nextSteps,
        List<CitationResponse> citations,
        List<SourceReferenceResponse> sources
) {
}
