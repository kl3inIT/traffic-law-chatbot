package com.vn.traffic.chatbot.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.retrieval.RetrievalPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private RetrievalPolicy retrievalPolicy;

    @Mock
    private CitationMapper citationMapper;

    @Mock
    private AnswerComposer answerComposer;

    @Mock
    private ChatPromptFactory chatPromptFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatClient,
                vectorStore,
                objectMapper,
                retrievalPolicy,
                citationMapper,
                answerComposer,
                chatPromptFactory
        );
    }

    @Test
    void answerUsesRetrievalPolicyAndReturnsGroundedWhenThreeOrMoreDocumentsExist() {
        String question = "Vượt đèn đỏ bị phạt thế nào?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"), document("2"), document("3"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 4, "Điều 6", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Nghị định 100", "https://vbpl.vn/nd100", 4, "Điều 6"));
        LegalAnswerDraft draft = new LegalAnswerDraft(
                "Kết luận [Nguồn 1].",
                null,
                null,
                List.of("Điều 6 [Nguồn 1]"),
                List.of("Phạt tiền [Nguồn 1]"),
                List.of("GPLX"),
                List.of("Làm việc với cơ quan chức năng"),
                List.of("Kiểm tra biên bản")
        );
        ChatAnswerResponse expected = new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                "final answer",
                "Kết luận [Nguồn 1].",
                AnswerCompositionPolicy.DEFAULT_DISCLAIMER,
                null,
                draft.legalBasis(),
                draft.penalties(),
                draft.requiredDocuments(),
                draft.procedureSteps(),
                draft.nextSteps(),
                citations,
                sources
        );

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(question, GroundingStatus.GROUNDED, citations)).thenReturn("prompt-body");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("prompt-body")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("""
                {
                  "conclusion": "Kết luận [Nguồn 1].",
                  "answer": "unused",
                  "uncertaintyNotice": null,
                  "legalBasis": ["Điều 6 [Nguồn 1]"],
                  "penalties": ["Phạt tiền [Nguồn 1]"],
                  "requiredDocuments": ["GPLX"],
                  "procedureSteps": ["Làm việc với cơ quan chức năng"],
                  "nextSteps": ["Kiểm tra biên bản"]
                }
                """);
        when(answerComposer.compose(org.mockito.ArgumentMatchers.eq(GroundingStatus.GROUNDED), any(), org.mockito.ArgumentMatchers.eq(citations), org.mockito.ArgumentMatchers.eq(sources))).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question);

        assertThat(response).isSameAs(expected);
        verify(retrievalPolicy).buildRequest(question, 5);
        verify(chatPromptFactory).buildPrompt(question, GroundingStatus.GROUNDED, citations);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).isEqualTo("prompt-body");
        verify(answerComposer).compose(
                org.mockito.ArgumentMatchers.eq(GroundingStatus.GROUNDED),
                org.mockito.ArgumentMatchers.argThat(parsedDraft -> parsedDraft != null
                        && "Kết luận [Nguồn 1].".equals(parsedDraft.conclusion())
                        && List.of("Điều 6 [Nguồn 1]").equals(parsedDraft.legalBasis())
                        && List.of("Phạt tiền [Nguồn 1]").equals(parsedDraft.penalties())
                        && List.of("GPLX").equals(parsedDraft.requiredDocuments())
                        && List.of("Làm việc với cơ quan chức năng").equals(parsedDraft.procedureSteps())
                        && List.of("Kiểm tra biên bản").equals(parsedDraft.nextSteps())),
                org.mockito.ArgumentMatchers.eq(citations),
                org.mockito.ArgumentMatchers.eq(sources)
        );
    }

    @Test
    void answerReturnsLimitedGroundingForOneOrTwoDocumentsAndUsesLimitedNotice() {
        String question = "Quên mang đăng ký xe thì sao?";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        List<Document> documents = List.of(document("1"), document("2"));
        List<CitationResponse> citations = List.of(new CitationResponse("Nguồn 1", "source-1", "version-1", "Luật Giao thông", "https://vbpl.vn/lgt", 2, "Điều 58", "excerpt"));
        List<SourceReferenceResponse> sources = List.of(new SourceReferenceResponse("Nguồn 1", "source-1", "version-1", "Luật Giao thông", "https://vbpl.vn/lgt", 2, "Điều 58"));
        LegalAnswerDraft draft = new LegalAnswerDraft(
                "Có thể bị xử lý về giấy tờ xe [Nguồn 1].",
                null,
                "Giá trị này phải bị composer bỏ qua trong limited mode",
                List.of("Điều 58 [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of(),
                List.of("Kiểm tra lại loại giấy tờ còn thiếu")
        );
        ChatAnswerResponse expected = new ChatAnswerResponse(
                GroundingStatus.LIMITED_GROUNDING,
                "limited answer",
                draft.conclusion(),
                AnswerCompositionPolicy.DEFAULT_DISCLAIMER,
                AnswerCompositionPolicy.LIMITED_NOTICE,
                draft.legalBasis(),
                List.of(),
                List.of(),
                List.of(),
                draft.nextSteps(),
                citations,
                sources
        );

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(documents);
        when(citationMapper.toCitations(documents)).thenReturn(citations);
        when(citationMapper.toSources(citations)).thenReturn(sources);
        when(chatPromptFactory.buildPrompt(question, GroundingStatus.LIMITED_GROUNDING, citations)).thenReturn("limited-prompt");
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user("limited-prompt")).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("""
                {
                  "conclusion": "Có thể bị xử lý về giấy tờ xe [Nguồn 1].",
                  "answer": "unused",
                  "uncertaintyNotice": "should be ignored",
                  "legalBasis": ["Điều 58 [Nguồn 1]"],
                  "penalties": [],
                  "requiredDocuments": [],
                  "procedureSteps": [],
                  "nextSteps": ["Kiểm tra lại loại giấy tờ còn thiếu"]
                }
                """);
        when(answerComposer.compose(org.mockito.ArgumentMatchers.eq(GroundingStatus.LIMITED_GROUNDING), any(), org.mockito.ArgumentMatchers.eq(citations), org.mockito.ArgumentMatchers.eq(sources))).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question);

        assertThat(response).isSameAs(expected);
        assertThat(response.uncertaintyNotice()).isEqualTo(AnswerCompositionPolicy.LIMITED_NOTICE);
        verify(retrievalPolicy).buildRequest(question, 5);
        verify(chatPromptFactory).buildPrompt(question, GroundingStatus.LIMITED_GROUNDING, citations);
        verify(answerComposer).compose(
                org.mockito.ArgumentMatchers.eq(GroundingStatus.LIMITED_GROUNDING),
                org.mockito.ArgumentMatchers.argThat(parsedDraft -> parsedDraft != null
                        && "Có thể bị xử lý về giấy tờ xe [Nguồn 1].".equals(parsedDraft.conclusion())
                        && List.of("Điều 58 [Nguồn 1]").equals(parsedDraft.legalBasis())
                        && List.of().equals(parsedDraft.penalties())
                        && List.of().equals(parsedDraft.requiredDocuments())
                        && List.of().equals(parsedDraft.procedureSteps())
                        && List.of("Kiểm tra lại loại giấy tờ còn thiếu").equals(parsedDraft.nextSteps())),
                org.mockito.ArgumentMatchers.eq(citations),
                org.mockito.ArgumentMatchers.eq(sources)
        );
    }

    @Test
    void answerReturnsRefusedWithoutModelCallWhenNoDocumentsExist() {
        String question = "Tình huống không có căn cứ";
        SearchRequest request = SearchRequest.builder().query(question).topK(5).build();
        ChatAnswerResponse expected = new ChatAnswerResponse(
                GroundingStatus.REFUSED,
                AnswerCompositionPolicy.REFUSAL_MESSAGE,
                null,
                AnswerCompositionPolicy.DEFAULT_DISCLAIMER,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        when(retrievalPolicy.buildRequest(question, 5)).thenReturn(request);
        when(vectorStore.similaritySearch(request)).thenReturn(List.of());
        when(citationMapper.toCitations(List.of())).thenReturn(List.of());
        when(citationMapper.toSources(List.of())).thenReturn(List.of());
        when(answerComposer.compose(any(), any(), any(), any())).thenReturn(expected);

        ChatAnswerResponse response = chatService.answer(question);

        assertThat(response).isSameAs(expected);
        verify(retrievalPolicy).buildRequest(question, 5);
        verify(answerComposer).compose(
                org.mockito.ArgumentMatchers.eq(GroundingStatus.REFUSED),
                org.mockito.ArgumentMatchers.argThat(draft -> draft != null
                        && draft.conclusion() == null
                        && draft.answer() == null
                        && draft.uncertaintyNotice() == null
                        && draft.legalBasis().isEmpty()
                        && draft.penalties().isEmpty()
                        && draft.requiredDocuments().isEmpty()
                        && draft.procedureSteps().isEmpty()
                        && draft.nextSteps().isEmpty()),
                org.mockito.ArgumentMatchers.eq(List.of()),
                org.mockito.ArgumentMatchers.eq(List.of())
        );
        verify(chatClient, never()).prompt();
        verify(chatPromptFactory, never()).buildPrompt(any(), any(), any());
    }

    private Document document(String suffix) {
        return new Document("Tài liệu " + suffix, Map.of(
                "sourceId", "source-" + suffix,
                "sourceVersionId", "version-" + suffix,
                "origin", "https://vbpl.vn/doc-" + suffix
        ));
    }
}
