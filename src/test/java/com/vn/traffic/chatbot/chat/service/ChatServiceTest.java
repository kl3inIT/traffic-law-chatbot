package com.vn.traffic.chatbot.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.chatlog.service.ChatLogService;
import com.vn.traffic.chatbot.chunk.service.ChunkInspectionService;
import com.vn.traffic.chatbot.retrieval.RetrievalPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    @Mock private ChatResponse aiChatResponse;
    @Mock private Generation generation;
    @Mock private ChatResponseMetadata chatResponseMetadata;
    @Mock private Usage usage;

    private final ObjectMapper objectMapper = new ObjectMapper();
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
                        new AiModelProperties.ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6"),
                        new AiModelProperties.ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5")
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
        ReflectionTestUtils.setField(chatService, "limitedGroundingThreshold", 2);
    }

    @Test
    void answerUsesRetrievalPolicyAndReturnsGroundedWhenThreeOrMoreDocumentsExist() {
        String question = "Vượt đèn đỏ bị phạt thế nào?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"), document("2"), document("3"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 4, "Điều 6", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 4, "Điều 6"));
        ChatAnswerResponse expected = standardResponse(GroundingStatus.GROUNDED, citations, sources);

        String jsonPayload = """
                {
                  "conclusion": "Kết luận [Nguồn 1].",
                  "answer": "unused",
                  "uncertaintyNotice": null,
                  "legalBasis": ["Điều 6 [Nguồn 1]"],
                  "penalties": ["Phạt tiền [Nguồn 1]"],
                  "requiredDocuments": ["GPLX"],
                  "procedureSteps": ["Làm việc với cơ quan chức năng"],
                  "nextSteps": ["Kiểm tra biên bản"],
                  "scenarioFacts": ["Phương tiện: xe máy"],
                  "scenarioRule": ["Áp dụng Điều 6 [Nguồn 1]"],
                  "scenarioOutcome": ["Có thể bị xử phạt [Nguồn 1]"],
                  "scenarioActions": ["Đối chiếu biên bản"]
                }
                """;

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations), anyList())).thenReturn("prompt-body");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt-body")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        stubChatResponse(jsonPayload);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, null);

        assertThat(response).isSameAs(expected);
        verify(retrievalPolicy).buildRequest(question, 5);
        verify(chatPromptFactory).buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations), anyList());
        verify(answerComposer).compose(any(), any(), any(), any());
    }

    @Test
    void answerWithKnownModelIdUsesCorrectClient() {
        String question = "Vượt đèn đỏ bị phạt thế nào?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"), document("2"), document("3"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 4, "Điều 6", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 4, "Điều 6"));
        ChatAnswerResponse expected = standardResponse(GroundingStatus.GROUNDED, citations, sources);
        String jsonPayload = "{\"conclusion\":\"ok [Nguồn 1]\",\"answer\":\"\",\"uncertaintyNotice\":null,\"legalBasis\":[\"Điều 6 [Nguồn 1]\"],\"penalties\":[],\"requiredDocuments\":[],\"procedureSteps\":[],\"nextSteps\":[],\"scenarioFacts\":[],\"scenarioRule\":[],\"scenarioOutcome\":[],\"scenarioActions\":[]}";

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations), anyList())).thenReturn("prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        stubChatResponse(jsonPayload);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        // Use explicit model ID
        ChatAnswerResponse response = chatService.answer(question, "claude-sonnet-4-6");
        assertThat(response).isSameAs(expected);
    }

    @Test
    void answerWithNullModelIdFallsBackToDefaultModel() {
        String question = "Quên mang đăng ký xe thì sao?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"), document("2"), document("3"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"));
        ChatAnswerResponse expected = standardResponse(GroundingStatus.GROUNDED, citations, sources);
        String jsonPayload = "{\"conclusion\":\"ok [Nguồn 1]\",\"answer\":\"\",\"uncertaintyNotice\":null,\"legalBasis\":[\"Điều 7 [Nguồn 1]\"],\"penalties\":[],\"requiredDocuments\":[],\"procedureSteps\":[],\"nextSteps\":[],\"scenarioFacts\":[],\"scenarioRule\":[],\"scenarioOutcome\":[],\"scenarioActions\":[]}";

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations), anyList())).thenReturn("prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        stubChatResponse(jsonPayload);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        // null modelId must fall back to default without throwing
        ChatAnswerResponse response = chatService.answer(question, null);
        assertThat(response).isSameAs(expected);
    }

    @Test
    void answerWithUnknownModelIdFallsBackWithoutException() {
        String question = "Quên mang đăng ký xe thì sao?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"), document("2"), document("3"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"));
        ChatAnswerResponse expected = standardResponse(GroundingStatus.GROUNDED, citations, sources);
        String jsonPayload = "{\"conclusion\":\"ok [Nguồn 1]\",\"answer\":\"\",\"uncertaintyNotice\":null,\"legalBasis\":[\"Điều 7 [Nguồn 1]\"],\"penalties\":[],\"requiredDocuments\":[],\"procedureSteps\":[],\"nextSteps\":[],\"scenarioFacts\":[],\"scenarioRule\":[],\"scenarioOutcome\":[],\"scenarioActions\":[]}";

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations), anyList())).thenReturn("prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        stubChatResponse(jsonPayload);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        // Unknown model ID: must NOT throw, must fall back to default
        ChatAnswerResponse response = chatService.answer(question, "unknown-model-xyz");
        assertThat(response).isSameAs(expected);
    }

    @Test
    void answerParsesModelResponseWrappedInMarkdownCodeBlock() {
        String question = "Vượt đèn đỏ bị phạt thế nào?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"), document("2"), document("3"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 7, "Điều 7", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 7, "Điều 7"));
        ChatAnswerResponse expected = standardResponse(GroundingStatus.GROUNDED, citations, sources);

        String jsonPayload = """
                ```json
                {
                  "conclusion": "Kết luận [Nguồn 1].",
                  "answer": "unused",
                  "uncertaintyNotice": null,
                  "legalBasis": ["Điều 7 [Nguồn 1]"],
                  "penalties": ["Phạt tiền [Nguồn 1]"],
                  "requiredDocuments": [],
                  "procedureSteps": [],
                  "nextSteps": [],
                  "scenarioFacts": ["Vượt đèn đỏ"],
                  "scenarioRule": ["Áp dụng Điều 7 [Nguồn 1]"],
                  "scenarioOutcome": ["Có thể bị xử phạt [Nguồn 1]"],
                  "scenarioActions": []
                }
                ```""";

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(eq(question), eq(GroundingStatus.GROUNDED), eq(citations), anyList())).thenReturn("prompt-body");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt-body")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        stubChatResponse(jsonPayload);
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, null);

        assertThat(response).isSameAs(expected);
        verify(answerComposer).compose(any(), any(), any(), any());
        verify(chatClient).prompt();
    }

    @Test
    void answerReturnsRefusedWithoutModelCallWhenNoDocumentsExistAndCollectsReadinessDiagnostics() {
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
        verify(chatPromptFactory, never()).buildPrompt(any(), any(), any(), anyList());
    }

    @Test
    void answerRefusesIrrelevantApprovedContentWithoutCallingModel() {
        String question = "Xe máy vượt đèn đỏ bị phạt thế nào?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"), document("2"), document("3"));
        List<CitationResponse> irrelevantCitations = List.of(
                new CitationResponse("Nguồn 1", "source-1", "version-1", "Chapter 1", null, 12, "page-12", "The Evolution of Database Systems"),
                new CitationResponse("Nguồn 2", "source-2", "version-2", "Chapter 1", null, 8, "page-8", "Relational Database Systems"),
                new CitationResponse("Nguồn 3", "source-3", "version-3", "Chapter 1", null, 4, "page-4", "Overview of DBMS")
        );
        ChatAnswerResponse expected = refusedResponse();

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(irrelevantCitations);
        when(citationMapper.toSources(irrelevantCitations)).thenReturn(List.of());
        when(chunkInspectionService.getRetrievalReadinessCounts())
                .thenReturn(new ChunkInspectionService.RetrievalReadinessCounts(3L, 3L, 3L, 3L));
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question, null);

        assertThat(response).isSameAs(expected);
        verify(chunkInspectionService).getRetrievalReadinessCounts();
        verify(chatClient, never()).prompt();
        verify(chatPromptFactory, never()).buildPrompt(any(), any(), any(), anyList());
    }

    @Test
    void answerTreatsNullSimilaritySearchResultsAsHandledRefusalInsteadOfThrowing() {
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

    // Helper to stub the chatResponse() call chain
    private void stubChatResponse(String textPayload) {
        AssistantMessage assistantMsg = new AssistantMessage(textPayload);
        Generation gen = new Generation(assistantMsg);
        when(aiChatResponse.getResult()).thenReturn(gen);
        when(aiChatResponse.getMetadata()).thenReturn(chatResponseMetadata);
        when(chatResponseMetadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(100);
        when(usage.getCompletionTokens()).thenReturn(200);
        when(callResponseSpec.chatResponse()).thenReturn(aiChatResponse);
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
                List.of("Điều 6 [Nguồn 1]"),
                List.of("Phạt tiền [Nguồn 1]"),
                List.of("GPLX"),
                List.of("Làm việc với cơ quan chức năng"),
                List.of("Kiểm tra biên bản"),
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
                "origin", "https://vbpl.vn/doc-" + suffix
        ));
    }
}
