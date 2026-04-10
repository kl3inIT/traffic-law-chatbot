package com.vn.traffic.chatbot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.PublicChatController;
import com.vn.traffic.chatbot.chat.citation.CitationMapper;
import com.vn.traffic.chatbot.chat.config.ChatClientConfig;
import com.vn.traffic.chatbot.chat.service.AnswerComposer;
import com.vn.traffic.chatbot.chat.service.ChatPromptFactory;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.chunk.service.ChunkInspectionService;
import com.vn.traffic.chatbot.common.error.GlobalExceptionHandler;
import com.vn.traffic.chatbot.retrieval.RetrievalPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatFlowIntegrationTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChunkInspectionService chunkInspectionService;

    private AnnotationConfigApplicationContext context;
    private MockMvc mockMvc;
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean("openAiChatModel", ChatModel.class, () -> chatModel);
        context.registerBean(VectorStore.class, () -> vectorStore);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(AnswerComposer.class, AnswerComposer::new);
        context.registerBean(ChunkInspectionService.class, () -> chunkInspectionService);
        context.register(ChatClientConfig.class, CitationMapper.class, ChatPromptFactory.class, RetrievalPolicy.class, ChatService.class);
        context.refresh();

        chatClient = context.getBean(ChatClient.class);
        ChatService chatService = context.getBean(ChatService.class);
        ReflectionTestUtils.setField(chatService, "retrievalTopK", 5);
        ReflectionTestUtils.setField(chatService, "limitedGroundingThreshold", 2);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new PublicChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(context.getBean(ObjectMapper.class)))
                .setValidator(validator)
                .build();
    }

    @Test
    void groundedLegalBasisPenaltyAndProcedureQuestionReturnsStructured200Response() throws Exception {
        assertThat(chatClient).isNotNull();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new org.springframework.ai.document.Document("Điều 7 quy định xử phạt vượt đèn đỏ", Map.of(
                        "sourceId", "source-1",
                        "sourceVersionId", "version-1",
                        "sourceTitle", "Nghị định 168",
                        "origin", "https://vbpl.vn/nd168",
                        "pageNumber", 4,
                        "sectionRef", "Điều 7"
                )),
                new org.springframework.ai.document.Document("Căn cứ pháp lý bổ sung", Map.of(
                        "sourceId", "source-2",
                        "sourceVersionId", "version-2",
                        "sourceTitle", "Luật Trật tự, an toàn giao thông đường bộ",
                        "origin", "https://vbpl.vn/law-2",
                        "pageNumber", 12,
                        "sectionRef", "Khoản 3 Điều 11"
                )),
                new org.springframework.ai.document.Document("Hướng dẫn xử lý", Map.of(
                        "sourceId", "source-3",
                        "sourceVersionId", "version-3",
                        "sourceTitle", "Văn bản hướng dẫn",
                        "origin", "https://vbpl.vn/guide-3",
                        "pageNumber", 2,
                        "sectionRef", "Mục 1"
                ))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {
                  \"conclusion\": \"Xe máy vượt đèn đỏ có thể bị phạt tiền theo quy định hiện hành [Nguồn 1].\",
                  \"answer\": \"\",
                  \"uncertaintyNotice\": null,
                  \"legalBasis\": [\"Điều 7 [Nguồn 1]\"],
                  \"penalties\": [\"Phạt tiền từ 4.000.000 đồng đến 6.000.000 đồng [Nguồn 1]\"],
                  \"requiredDocuments\": [\"Giấy phép lái xe\"],
                  \"procedureSteps\": [\"Làm việc với cơ quan có thẩm quyền khi được yêu cầu\"],
                  \"nextSteps\": [\"Đối chiếu biên bản với tình tiết thực tế\"]
                }
                """)))));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType("application/json")
                        .content("""
                                {"question":"Xe máy vượt đèn đỏ bị phạt thế nào?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groundingStatus").value("GROUNDED"))
                .andExpect(jsonPath("$.disclaimer").isNotEmpty())
                .andExpect(jsonPath("$.citations[0].inlineLabel").value("Nguồn 1"))
                .andExpect(jsonPath("$.citations[0].sourceId").value("source-1"))
                .andExpect(jsonPath("$.citations[0].sourceVersionId").value("version-1"))
                .andExpect(jsonPath("$.legalBasis[0]").value("Điều 7 [Nguồn 1]"))
                .andExpect(jsonPath("$.penalties[0]").value("Phạt tiền từ 4.000.000 đồng đến 6.000.000 đồng [Nguồn 1]"))
                .andExpect(jsonPath("$.procedureSteps[0]").value("Làm việc với cơ quan có thẩm quyền khi được yêu cầu"))
                .andExpect(jsonPath("$.sources[0].sourceId").exists())
                .andExpect(jsonPath("$.sources[0].sourceVersionId").exists());
    }

    @Test
    void refusedFlowReturns200InsteadOf500WhenNoEligibleDocumentsMatch() throws Exception {
        assertThat(chatClient).isNotNull();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(chunkInspectionService.getRetrievalReadinessCounts())
                .thenReturn(new ChunkInspectionService.RetrievalReadinessCounts(5L, 5L, 5L, 0L));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType("application/json")
                        .content("""
                                {"question":"Tình huống chưa có nguồn phù hợp"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groundingStatus").value("REFUSED"))
                .andExpect(jsonPath("$.disclaimer").isNotEmpty())
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void malformedModelPayloadReturnsStructuredResponseInsteadOf500() throws Exception {
        assertThat(chatClient).isNotNull();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new org.springframework.ai.document.Document("Điều 7 quy định xử phạt vượt đèn đỏ", Map.of(
                        "sourceId", "source-1",
                        "sourceVersionId", "version-1",
                        "sourceTitle", "Nghị định 168",
                        "origin", "https://vbpl.vn/nd168",
                        "pageNumber", 4,
                        "sectionRef", "Điều 7"
                )),
                new org.springframework.ai.document.Document("Căn cứ pháp lý bổ sung", Map.of(
                        "sourceId", "source-2",
                        "sourceVersionId", "version-2",
                        "sourceTitle", "Luật Trật tự, an toàn giao thông đường bộ",
                        "origin", "https://vbpl.vn/law-2",
                        "pageNumber", 12,
                        "sectionRef", "Khoản 3 Điều 11"
                )),
                new org.springframework.ai.document.Document("Hướng dẫn xử lý", Map.of(
                        "sourceId", "source-3",
                        "sourceVersionId", "version-3",
                        "sourceTitle", "Văn bản hướng dẫn",
                        "origin", "https://vbpl.vn/guide-3",
                        "pageNumber", 2,
                        "sectionRef", "Mục 1"
                ))
        ));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("không phải json")))));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType("application/json")
                        .content("""
                                {"question":"Cho tôi căn cứ pháp lý về lỗi vượt đèn đỏ xe máy."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groundingStatus").value("GROUNDED"))
                .andExpect(jsonPath("$.disclaimer").isNotEmpty())
                .andExpect(jsonPath("$.uncertaintyNotice").isNotEmpty())
                .andExpect(jsonPath("$.legalBasis[0]").exists())
                .andExpect(jsonPath("$.nextSteps[0]").isNotEmpty())
                .andExpect(jsonPath("$.citations[0].inlineLabel").value("Nguồn 1"))
                .andExpect(jsonPath("$.sources[0].sourceId").value("source-1"));
    }
}
