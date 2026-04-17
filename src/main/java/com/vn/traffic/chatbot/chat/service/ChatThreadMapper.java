package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatThreadResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.domain.ChatThread;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ChatThreadMapper {

    private final ScenarioAnswerComposer scenarioAnswerComposer;

    public ChatThreadMapper(ScenarioAnswerComposer scenarioAnswerComposer) {
        this.scenarioAnswerComposer = scenarioAnswerComposer;
    }

    public ChatThreadResponse toThreadResponse(ChatThread thread) {
        return new ChatThreadResponse(thread.getId(), thread.getCreatedAt(), thread.getUpdatedAt());
    }

    public ChatAnswerResponse attachScenarioContext(
            ChatAnswerResponse answer,
            UUID threadId,
            List<SourceReferenceResponse> sources
    ) {
        List<SourceReferenceResponse> effectiveSources = sources == null ? answer.sources() : sources;
        ScenarioAnswerComposer.ScenarioComposition composition = scenarioAnswerComposer.compose(
                answer,
                effectiveSources
        );
        return new ChatAnswerResponse(
                answer.groundingStatus(),
                threadId,
                composition.responseMode(),
                answer.answer(),
                answer.conclusion(),
                answer.disclaimer(),
                answer.uncertaintyNotice(),
                answer.legalBasis(),
                answer.penalties(),
                answer.requiredDocuments(),
                answer.procedureSteps(),
                answer.nextSteps(),
                answer.scenarioFacts(),
                composition.scenarioAnalysis(),
                answer.citations(),
                effectiveSources
        );
    }

}
