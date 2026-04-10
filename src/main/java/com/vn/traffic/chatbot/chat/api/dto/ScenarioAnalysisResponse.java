package com.vn.traffic.chatbot.chat.api.dto;

import java.util.List;

public record ScenarioAnalysisResponse(
        List<String> facts,
        List<String> rules,
        List<String> outcomes,
        List<String> actions,
        List<SourceReferenceResponse> sources
) {
}
