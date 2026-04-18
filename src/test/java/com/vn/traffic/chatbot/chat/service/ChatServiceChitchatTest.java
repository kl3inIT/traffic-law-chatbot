package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.advisor.context.ChatAdvisorContextKeys;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 9 IntentClassifier-driven short-circuit tests. CHITCHAT / OFF_TOPIC
 * intents bypass the advisor chain entirely; LEGAL intent flows through
 * {@link ChatClient} (retrieval + prompt augmentation now live inside the
 * RAG advisor chain — ChatService no longer touches VectorStore / CitationMapper).
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceChitchatTest {

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

    @Test
    void chitchatIntentShortCircuitsBeforeAdvisorChain() {
        ChatAnswerResponse canned = cannedResponse();
        when(intentClassifier.classify(eq("Xin chào"), any()))
                .thenReturn(new IntentDecision(IntentDecision.Intent.CHITCHAT, 0.98));
        when(answerComposer.composeChitchat()).thenReturn(canned);

        ChatAnswerResponse response = chatService.answer("Xin chào", null);

        assertThat(response).isSameAs(canned);
        verify(answerComposer).composeChitchat();
        verifyNoInteractions(chatClient);
        verify(chatLogService).save(eq("Xin chào"), eq(canned), eq(GroundingStatus.GROUNDED),
                any(), anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    void offTopicIntentShortCircuitsWithDistinctRefusal() {
        ChatAnswerResponse canned = cannedResponse();
        when(intentClassifier.classify(eq("Tin tức bóng đá hôm nay?"), any()))
                .thenReturn(new IntentDecision(IntentDecision.Intent.OFF_TOPIC, 0.92));
        when(answerComposer.composeOffTopicRefusal()).thenReturn(canned);

        ChatAnswerResponse response = chatService.answer("Tin tức bóng đá hôm nay?", null);

        assertThat(response).isSameAs(canned);
        verify(answerComposer).composeOffTopicRefusal();
        verify(answerComposer, never()).composeChitchat();
        verifyNoInteractions(chatClient);
        verify(chatLogService).save(anyString(), eq(canned), eq(GroundingStatus.REFUSED),
                any(), anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void legalIntentFlowsThroughChatClientAdvisorChain() {
        when(intentClassifier.classify(anyString(), any()))
                .thenReturn(new IntentDecision(IntentDecision.Intent.LEGAL, 0.9));
        AssistantMessage msg = new AssistantMessage(
                "{\"conclusion\":\"ok\",\"answer\":\"\",\"uncertaintyNotice\":null,"
                        + "\"legalBasis\":[],\"penalties\":[],\"requiredDocuments\":[],"
                        + "\"procedureSteps\":[],\"nextSteps\":[]}");
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(ChatAdvisorContextKeys.CITATIONS_KEY, List.of());
        ctx.put(ChatAdvisorContextKeys.SOURCES_KEY, List.of());
        ChatClientResponse resp = ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(msg))))
                .context(ctx).build();

        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(anyString())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.advisors(any(Consumer.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.chatClientResponse()).thenReturn(resp);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(cannedResponse());

        chatService.answer("Vượt đèn đỏ với xe máy thì bị phạt bao nhiêu tiền?", null);

        verify(chatClient).prompt();
        verify(answerComposer, never()).composeChitchat();
        verify(answerComposer, never()).composeOffTopicRefusal();
    }

    private ChatAnswerResponse cannedResponse() {
        return new ChatAnswerResponse(
                GroundingStatus.GROUNDED, null, null,
                "Xin chào!", null, "disclaimer", null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, List.of(), List.of()
        );
    }
}
