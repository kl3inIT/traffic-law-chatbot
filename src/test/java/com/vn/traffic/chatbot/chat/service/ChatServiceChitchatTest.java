package com.vn.traffic.chatbot.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-02: chitchat/greeting short-circuit tests. Verifies that clear greetings bypass
 * the retrieval pipeline and LLM entirely — keeps p50 low for trivial intents.
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        AiModelProperties aiModelProperties = new AiModelProperties(
                "http://localhost:20128",
                "claude-sonnet-4-6",
                "claude-haiku-4-5-20251001",
                List.of(
                        new AiModelProperties.ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6", "", ""),
                        new AiModelProperties.ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5", "", "")
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
                objectMapper,
                retrievalPolicy,
                citationMapper,
                answerComposer,
                chatPromptFactory,
                chunkInspectionService,
                answerCompositionPolicy,
                chatLogService,
                chatMemory
        );
        ReflectionTestUtils.setField(chatService, "retrievalTopK", 5);
    }

    @Test
    void shortGreetingShortCircuitsBeforeRetrieval() {
        ChatAnswerResponse canned = new ChatAnswerResponse(
                GroundingStatus.GROUNDED, null, null,
                "Xin chào!", null, "disclaimer", null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, List.of(), List.of()
        );
        when(answerComposer.composeChitchat()).thenReturn(canned);

        ChatAnswerResponse response = chatService.answer("Xin chào", null);

        assertThat(response).isSameAs(canned);
        verify(answerComposer).composeChitchat();
        // D-02: retrieval + LLM must NOT be invoked for greetings
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
    void thankYouShortCircuits() {
        ChatAnswerResponse canned = cannedResponse();
        when(answerComposer.composeChitchat()).thenReturn(canned);

        ChatAnswerResponse response = chatService.answer("cảm ơn", null);

        assertThat(response).isSameAs(canned);
        verifyNoInteractions(vectorStore, retrievalPolicy, chatClient);
    }

    @Test
    void englishHelloShortCircuits() {
        ChatAnswerResponse canned = cannedResponse();
        when(answerComposer.composeChitchat()).thenReturn(canned);

        ChatAnswerResponse response = chatService.answer("hello", null);

        assertThat(response).isSameAs(canned);
        verifyNoInteractions(vectorStore, retrievalPolicy, chatClient);
    }

    @Test
    void longLegalQuestionDoesNotShortCircuit() {
        // Real legal question, > 8 words, not a chitchat match — must go through retrieval pipeline.
        // We don't stub the downstream pipeline (retrieval returns empty by default) so this will
        // exercise the refusal path. The critical assertion is that composeChitchat was NOT called.
        when(retrievalPolicy.buildRequest(anyString(), anyInt())).thenReturn(null);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(cannedResponse());

        chatService.answer("Vượt đèn đỏ với xe máy thì bị phạt bao nhiêu tiền theo quy định mới nhất?", null);

        verify(retrievalPolicy).buildRequest(anyString(), anyInt());
        // composeChitchat must NOT be called for real legal questions
        org.mockito.Mockito.verify(answerComposer, org.mockito.Mockito.never()).composeChitchat();
    }

    @Test
    void blankQuestionDoesNotShortCircuitAsChitchat() {
        // Edge case: empty/blank strings should not hit the chitchat path (they should go through
        // normal pipeline so refusal logic applies).
        when(retrievalPolicy.buildRequest(anyString(), anyInt())).thenReturn(null);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(cannedResponse());

        chatService.answer("", null);

        org.mockito.Mockito.verify(answerComposer, org.mockito.Mockito.never()).composeChitchat();
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
