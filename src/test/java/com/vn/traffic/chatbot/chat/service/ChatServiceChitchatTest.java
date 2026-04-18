package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.chat.intent.IntentClassifier;
import com.vn.traffic.chatbot.chat.intent.IntentDecision;
import com.vn.traffic.chatbot.chatlog.service.ChatLogService;
import com.vn.traffic.chatbot.chunk.service.ChunkInspectionService;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import com.vn.traffic.chatbot.retrieval.RetrievalPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

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
 * Plan 08-03: IntentClassifier-driven short-circuit tests.
 *
 * <p>Replaces the P7 regex-gate test: asserts that CHITCHAT / OFF_TOPIC intents
 * bypass retrieval + LLM entirely and that LEGAL intent proceeds through the
 * retrieval pipeline. No CHITCHAT_PATTERN regex remains in production code
 * (ARCH-03); the {@link IntentClassifier} now owns dispatch.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceChitchatTest {

    @Mock private ChatClient chatClient;
    @Mock private VectorStore vectorStore;
    @Mock private RetrievalPolicy retrievalPolicy;
    @Mock private CitationMapper citationMapper;
    @Mock private AnswerComposer answerComposer;
    @Mock private ChatPromptFactory chatPromptFactory;
    @Mock private ChunkInspectionService chunkInspectionService;
    @Mock private AnswerCompositionPolicy answerCompositionPolicy;
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
                vectorStore,
                retrievalPolicy,
                citationMapper,
                answerComposer,
                chatPromptFactory,
                chunkInspectionService,
                answerCompositionPolicy,
                chatLogService,
                chatMemory,
                intentClassifier
        );
        ReflectionTestUtils.setField(chatService, "retrievalTopK", 5);
    }

    @Test
    void chitchatIntentShortCircuitsBeforeRetrieval() {
        ChatAnswerResponse canned = cannedResponse();
        when(intentClassifier.classify(eq("Xin chào"), any()))
                .thenReturn(new IntentDecision(IntentDecision.Intent.CHITCHAT, 0.98));
        when(answerComposer.composeChitchat()).thenReturn(canned);

        ChatAnswerResponse response = chatService.answer("Xin chào", null);

        assertThat(response).isSameAs(canned);
        verify(answerComposer).composeChitchat();
        // D-02 + ARCH-03: retrieval + LLM must NOT be invoked for CHITCHAT intent
        verifyNoInteractions(vectorStore);
        verifyNoInteractions(retrievalPolicy);
        verifyNoInteractions(citationMapper);
        verifyNoInteractions(chatPromptFactory);
        verifyNoInteractions(chatClient);
        // chat log should still be written (async)
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
        // D-09: OFF_TOPIC uses distinct template, not composeChitchat
        verify(answerComposer, never()).composeChitchat();
        verifyNoInteractions(vectorStore);
        verifyNoInteractions(retrievalPolicy);
        verifyNoInteractions(chatClient);
        verify(chatLogService).save(anyString(), eq(canned), eq(GroundingStatus.REFUSED),
                any(), anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    void legalIntentGoesThroughRetrievalPipeline() {
        when(intentClassifier.classify(anyString(), any()))
                .thenReturn(new IntentDecision(IntentDecision.Intent.LEGAL, 0.9));
        when(retrievalPolicy.buildRequest(anyString(), anyInt())).thenReturn(null);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(cannedResponse());

        chatService.answer("Vượt đèn đỏ với xe máy thì bị phạt bao nhiêu tiền?", null);

        verify(retrievalPolicy).buildRequest(anyString(), anyInt());
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
