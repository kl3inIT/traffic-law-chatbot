package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatThreadResponse;
import com.vn.traffic.chatbot.chat.api.dto.RememberedFactResponse;
import com.vn.traffic.chatbot.chat.api.dto.ScenarioAnalysisResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ChatThread;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import com.vn.traffic.chatbot.chat.domain.ThreadFact;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ChatThreadMapper {

    public ChatThreadResponse toThreadResponse(ChatThread thread) {
        return new ChatThreadResponse(thread.getId(), thread.getCreatedAt(), thread.getUpdatedAt());
    }

    public ChatAnswerResponse attachThreadContext(
            ChatAnswerResponse answer,
            UUID threadId,
            ResponseMode responseMode,
            List<ThreadFact> facts
    ) {
        return new ChatAnswerResponse(
                answer.groundingStatus(),
                threadId,
                responseMode,
                answer.answer(),
                answer.conclusion(),
                answer.disclaimer(),
                answer.uncertaintyNotice(),
                answer.legalBasis(),
                answer.penalties(),
                answer.requiredDocuments(),
                answer.procedureSteps(),
                answer.nextSteps(),
                answer.pendingFacts(),
                mapFacts(facts),
                answer.scenarioAnalysis(),
                answer.citations(),
                answer.sources()
        );
    }

    public ChatAnswerResponse attachScenarioContext(
            ChatAnswerResponse answer,
            UUID threadId,
            List<ThreadFact> facts,
            List<?> citations,
            List<SourceReferenceResponse> sources
    ) {
        ScenarioAnalysisResponse scenarioAnalysis = new ScenarioAnalysisResponse(
                mapFacts(facts).stream().map(fact -> fact.key() + ": " + fact.value()).toList(),
                answer.legalBasis(),
                answer.conclusion() == null ? List.of() : List.of(answer.conclusion()),
                answer.nextSteps(),
                sources == null ? List.of() : sources
        );
        return new ChatAnswerResponse(
                answer.groundingStatus(),
                threadId,
                ResponseMode.SCENARIO_ANALYSIS,
                answer.answer(),
                answer.conclusion(),
                answer.disclaimer(),
                answer.uncertaintyNotice(),
                answer.legalBasis(),
                answer.penalties(),
                answer.requiredDocuments(),
                answer.procedureSteps(),
                answer.nextSteps(),
                answer.pendingFacts(),
                mapFacts(facts),
                scenarioAnalysis,
                answer.citations(),
                answer.sources()
        );
    }

    private List<RememberedFactResponse> mapFacts(List<ThreadFact> facts) {
        if (facts == null) {
            return List.of();
        }
        return facts.stream()
                .map(fact -> new RememberedFactResponse(fact.getFactKey(), fact.getFactValue(), fact.getStatus().name()))
                .toList();
    }
}
