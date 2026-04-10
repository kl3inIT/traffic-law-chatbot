package com.vn.traffic.chatbot.chat.api.dto;

import java.util.List;

public record ScenarioAnalysisResponse(
        List<String> facts,
        String rule,
        String outcome,
        List<String> actions,
        List<SourceReferenceResponse> sources
) {
}
