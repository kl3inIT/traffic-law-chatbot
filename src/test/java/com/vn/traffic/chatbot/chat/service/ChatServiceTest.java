package com.vn.traffic.chatbot.chat.service;

import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

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

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec chatClientRequestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;
    @Mock private VectorStore vectorStore;
    @Mock private RetrievalPolicy retrievalPolicy;
    @Mock private CitationMapper citationMapper;
    @Mock private AnswerComposer answerComposer;
    @Mock private ChatPromptFactory chatPromptFactory;
    @Mock private ChunkInspectionService chunkInspectionService;
    @Mock private AnswerCompositionPolicy answerCompositionPolicy;
    @Mock private ChatLogService chatLogService;
    @Mock private org.springframework.ai.chat.memory.ChatMemory chatMemory;
    @Mock private IntentClassifier intentClassifier;

    private AiModelProperties aiModelProperties;
    private Map<String, ChatClient> chatClientMap;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        aiModelProperties = new AiModelProperties(
                "http://localhost:20128",
                "claude-sonnet-4-6",
                "claude-haiku-4-5-20251001",
                List.of(
                        new AiModelProperties.ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6", "", "", true),
                        new AiModelProperties.ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5", "", "", true)
                )
        );
        chatClientMap = Map.of(
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

    private void stubLegalIntent() {
        when(intentClassifier.classify(anyString(), any()))
                .thenReturn(new IntentDecision(IntentDecision.Intent.LEGAL, 0.95));
    }

    @Test
    void answerUsesRetrievalPolicyAndReturnsGroundedWhenDocumentsExist() {
        stubLegalIntent();
        String question = "Vượt đèn đỏ bị phạt thế nào?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"), document("2"), document("3"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Source A", "https://example.com/a", 4, "section", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Source A", "https://example.com/a", 4, "section"));
        ChatAnswerResponse expected = standardResponse(GroundingStatus.GROUNDED, citations, sources);

        LegalAnswerDraft draft = new LegalAnswerDraft(
                "Kết luận", "unused", null,
                List.of("basis-1"), List.of("penalty-1"),
                List.of("doc-1"), List.of("step-1"), List.of("next-1"));

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations)))
                .thenReturn("prompt-body");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt-body")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.advisors(any(Consumer.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(LegalAnswerDraft.class)).thenReturn(draft);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, null);

        assertThat(response).isSameAs(expected);
        verify(retrievalPolicy).buildRequest(question, 5);
        verify(chatPromptFactory).buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations));
        verify(callResponseSpec).entity(LegalAnswerDraft.class);
        verify(answerComposer).compose(any(), any(), any(), any());
    }

    @Test
    void answerWithKnownModelIdUsesCorrectClient() {
        stubLegalIntent();
        String question = "Vượt đèn đỏ bị phạt thế nào?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Source A", "https://example.com/a", 4, "section", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Source A", "https://example.com/a", 4, "section"));
        ChatAnswerResponse expected = standardResponse(GroundingStatus.GROUNDED, citations, sources);
        LegalAnswerDraft draft = new LegalAnswerDraft("ok", "", null, List.of(), List.of(), List.of(), List.of(), List.of());

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations))).thenReturn("prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.advisors(any(Consumer.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(LegalAnswerDraft.class)).thenReturn(draft);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, "claude-sonnet-4-6");
        assertThat(response).isSameAs(expected);
    }

    @Test
    void answerWithNullModelIdFallsBackToDefaultModel() {
        stubLegalIntent();
        String question = "Quên mang đăng ký xe thì sao?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Source A", "https://example.com/a", 4, "section", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Source A", "https://example.com/a", 4, "section"));
        ChatAnswerResponse expected = standardResponse(GroundingStatus.GROUNDED, citations, sources);
        LegalAnswerDraft draft = new LegalAnswerDraft("ok", "", null, List.of(), List.of(), List.of(), List.of(), List.of());

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations))).thenReturn("prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.advisors(any(Consumer.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(LegalAnswerDraft.class)).thenReturn(draft);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, null);
        assertThat(response).isSameAs(expected);
    }

    @Test
    void answerWithUnknownModelIdFallsBackWithoutException() {
        stubLegalIntent();
        String question = "Quên mang đăng ký xe thì sao?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Source A", "https://example.com/a", 4, "section", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Source A", "https://example.com/a", 4, "section"));
        ChatAnswerResponse expected = standardResponse(GroundingStatus.GROUNDED, citations, sources);
        LegalAnswerDraft draft = new LegalAnswerDraft("ok", "", null, List.of(), List.of(), List.of(), List.of(), List.of());

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations))).thenReturn("prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.advisors(any(Consumer.class))).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(LegalAnswerDraft.class)).thenReturn(draft);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, "unknown-model-xyz");
        assertThat(response).isSameAs(expected);
    }

    @Test
    void answerReturnsRefusedWithoutModelCallWhenNoDocumentsExistAndCollectsReadinessDiagnostics() {
        stubLegalIntent();
        String question = "Tình huống không có căn cứ";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        ChatAnswerResponse expected = refusedResponse();

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(List.of());
        when(citationMapper.toCitations(List.of())).thenReturn(List.of());
        when(citationMapper.toSources(List.of())).thenReturn(List.of());
        when(chunkInspectionService.getRetrievalReadinessCounts())
                .thenReturn(new ChunkInspectionService.RetrievalReadinessCounts(12L, 9L, 8L, 0L));
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, null);

        assertThat(response).isSameAs(expected);
        verify(chunkInspectionService).getRetrievalReadinessCounts();
        verify(chatClient, never()).prompt();
        verify(chatPromptFactory, never()).buildPrompt(any(), any(), any());
    }

    @Test
    void answerTreatsNullSimilaritySearchResultsAsHandledRefusalInsteadOfThrowing() {
        stubLegalIntent();
        String question = "Không có dữ liệu đủ điều kiện";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        ChatAnswerResponse expected = refusedResponse();

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(null);
        when(citationMapper.toCitations(List.of())).thenReturn(List.of());
        when(citationMapper.toSources(List.of())).thenReturn(List.of());
        when(chunkInspectionService.getRetrievalReadinessCounts())
                .thenReturn(new ChunkInspectionService.RetrievalReadinessCounts(0L, 0L, 0L, 0L));
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, null);

        assertThat(response).isSameAs(expected);
        verify(chunkInspectionService).getRetrievalReadinessCounts();
        verify(chatClient, never()).prompt();
    }

    private ChatAnswerResponse standardResponse(
            GroundingStatus groundingStatus,
            List<CitationResponse> citations,
            List<SourceReferenceResponse> sources
    ) {
        return new ChatAnswerResponse(
                groundingStatus,
                null,
                com.vn.traffic.chatbot.chat.domain.ResponseMode.STANDARD,
                "final answer",
                "Kết luận [Nguồn 1].",
                AnswerCompositionPolicy.DEFAULT_DISCLAIMER,
                null,
                List.of("basis-1"),
                List.of("penalty-1"),
                List.of("doc-1"),
                List.of("step-1"),
                List.of("next-1"),
                List.of(),
                null,
                citations,
                sources
        );
    }

    private ChatAnswerResponse refusedResponse() {
        return new ChatAnswerResponse(
                GroundingStatus.REFUSED,
                null,
                com.vn.traffic.chatbot.chat.domain.ResponseMode.STANDARD,
                "refusal answer",
                null,
                AnswerCompositionPolicy.DEFAULT_DISCLAIMER,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NARROW_SCOPE,
                        AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NAME_DOCUMENT,
                        AnswerCompositionPolicy.REFUSAL_NEXT_STEP_VERIFY_SOURCE
                ),
                List.of(),
                null,
                List.of(),
                List.of()
        );
    }

    private Document document(String suffix) {
        return new Document("Tài liệu " + suffix, Map.of(
                "sourceId", "source-" + suffix,
                "sourceVersionId", "version-" + suffix,
                "origin", "https://example.com/doc-" + suffix
        ));
    }
}
