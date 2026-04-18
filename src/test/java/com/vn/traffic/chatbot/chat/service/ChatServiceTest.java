package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.advisor.context.ChatAdvisorContextKeys;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.intent.IntentClassifier;
import com.vn.traffic.chatbot.chat.intent.IntentDecision;
import com.vn.traffic.chatbot.chatlog.service.ChatLogService;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 9 ChatService tests. ChatService is now a thin legal-answer
 * orchestrator: retrieval, citation stamping, and prompt augmentation run
 * inside the RAG advisor chain. These tests assert that:
 *
 * <ul>
 *   <li>Citations + sources are read from {@link ChatClientResponse#context()}
 *       under {@code ChatAdvisorContextKeys.CITATIONS_KEY /
 *       SOURCES_KEY} (published by {@code CitationStashAdvisor}).</li>
 *   <li>Empty citations list in context -> refusal path without touching
 *       {@link AnswerComposer#compose} for a grounded draft.</li>
 *   <li>modelId fallback chain preserved (known / null / unknown).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec chatClientRequestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;
    @Mock private AnswerComposer answerComposer;
    @Mock private ChatLogService chatLogService;
    @Mock private ChatMemory chatMemory;
    @Mock private IntentClassifier intentClassifier;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        AiModelProperties aiModelProperties = new AiModelProperties(
                "http://localhost:20128",
                "claude-sonnet-4-6",
                "claude-haiku-4-5-20251001",
                List.of(
                        new AiModelProperties.ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6", "", "", true),
                        new AiModelProperties.ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5", "", "", true)
                )
        );
        Map<String, ChatClient> chatClientMap = Map.of(
                "claude-sonnet-4-6", chatClient,
                "claude-haiku-4-5-20251001", chatClient
        );
        chatService = new ChatService(
                chatClientMap,
                aiModelProperties,
                answerComposer,
                chatLogService,
                chatMemory,
                intentClassifier
        );
    }

    private void stubLegalIntent() {
        when(intentClassifier.classify(anyString(), any()))
                .thenReturn(new IntentDecision(IntentDecision.Intent.LEGAL, 0.95));
    }

    @Test
    void answerReturnsGroundedResponseWhenCitationsAreStashedInContext() {
        stubLegalIntent();
        String question = "Vượt đèn đỏ bị phạt thế nào?";
        List<CitationResponse> citations = List.of(new CitationResponse(
                "Nguồn 1", "source-1", "version-1", "Source A",
                "https://example.com/a", 4, "section", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse(
                "Nguồn 1", "source-1", "version-1", "Source A",
                "https://example.com/a", 4, "section"));
        ChatAnswerResponse expected = stubResponse(GroundingStatus.GROUNDED);

        stubCall(chatClientResponseWith(validDraftJson(), citations, sources));
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, null);

        assertThat(response).isSameAs(expected);
        verify(answerComposer).compose(eq(GroundingStatus.GROUNDED), any(), eq(citations), eq(sources));
    }

    @Test
    void answerReturnsRefusedWhenCitationsContextIsEmpty() {
        stubLegalIntent();
        ChatAnswerResponse refusal = stubResponse(GroundingStatus.REFUSED);

        stubCall(chatClientResponseWith(validDraftJson(), List.of(), List.of()));
        when(answerComposer.compose(eq(GroundingStatus.REFUSED), any(), eq(List.of()), eq(List.of())))
                .thenReturn(refusal);

        ChatAnswerResponse response = chatService.answer("Tình huống không có căn cứ", null);

        assertThat(response).isSameAs(refusal);
        verify(answerComposer, never()).compose(eq(GroundingStatus.GROUNDED), any(), any(), any());
    }

    @Test
    void answerWithKnownModelIdUsesCorrectClient() {
        stubLegalIntent();
        List<CitationResponse> citations = List.of(new CitationResponse(
                "Nguồn 1", "s-1", "v-1", "A", "https://example.com/a", 1, "s", "e"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse(
                "Nguồn 1", "s-1", "v-1", "A", "https://example.com/a", 1, "s"));
        ChatAnswerResponse expected = stubResponse(GroundingStatus.GROUNDED);

        stubCall(chatClientResponseWith(validDraftJson(), citations, sources));
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer("Q", "claude-sonnet-4-6");
        assertThat(response).isSameAs(expected);
    }

    @Test
    void answerWithUnknownModelIdFallsBackWithoutException() {
        stubLegalIntent();
        List<CitationResponse> citations = List.of(new CitationResponse(
                "Nguồn 1", "s-1", "v-1", "A", "https://example.com/a", 1, "s", "e"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse(
                "Nguồn 1", "s-1", "v-1", "A", "https://example.com/a", 1, "s"));
        ChatAnswerResponse expected = stubResponse(GroundingStatus.GROUNDED);

        stubCall(chatClientResponseWith(validDraftJson(), citations, sources));
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer("Q", "unknown-model-xyz");
        assertThat(response).isSameAs(expected);
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private void stubCall(ChatClientResponse toReturn) {
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.advisors(any(Consumer.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatClientResponse()).thenReturn(toReturn);
    }

    private static ChatClientResponse chatClientResponseWith(
            String draftJson,
            List<CitationResponse> citations,
            List<SourceReferenceResponse> sources) {
        AssistantMessage msg = new AssistantMessage(draftJson);
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(msg)));
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(ChatAdvisorContextKeys.CITATIONS_KEY, citations);
        ctx.put(ChatAdvisorContextKeys.SOURCES_KEY, sources);
        return ChatClientResponse.builder().chatResponse(chatResponse).context(ctx).build();
    }

    private static String validDraftJson() {
        return "{\"conclusion\":\"ok\",\"answer\":\"\",\"uncertaintyNotice\":null,"
                + "\"legalBasis\":[],\"penalties\":[],\"requiredDocuments\":[],"
                + "\"procedureSteps\":[],\"nextSteps\":[]}";
    }

    private static ChatAnswerResponse stubResponse(GroundingStatus status) {
        return new ChatAnswerResponse(
                status, null,
                com.vn.traffic.chatbot.chat.domain.ResponseMode.STANDARD,
                "answer", null, "disclaimer", null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, List.of(), List.of());
    }
}
