package com.vn.traffic.chatbot.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatQuestionRequest;
import com.vn.traffic.chatbot.chat.api.dto.CitationResponse;
import com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new PublicChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void assertValidationProblemDetail(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(content).isNotBlank();

        Map<String, Object> body = objectMapper.readValue(content, Map.class);
        assertThat(body).containsEntry("detail", "Validation failed");
        assertThat(body).containsKey("properties");
        Map<String, Object> properties = (Map<String, Object>) body.get("properties");
        assertThat(properties).containsKey("errors");
        assertThat(((Map<String, Object>) properties.get("errors"))).containsKey("question");
        assertThat(content).contains("errors", "question");
    }

    @Test
    void postChatReturnsStructuredGroundedAnswerContract() throws Exception {
        ChatAnswerResponse response = new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                "Xe máy vượt đèn đỏ có thể bị xử phạt tiền theo quy định hiện hành.",
                "Xe máy vượt đèn đỏ có thể bị xử phạt.",
                "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.",
                null,
                List.of("Điều 7 [Nguồn 1]"),
                List.of("Phạt tiền từ 4.000.000 đồng đến 6.000.000 đồng [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of("Đối chiếu biên bản và tình tiết thực tế"),
                List.of(new CitationResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7", "Người điều khiển xe máy vượt đèn đỏ...")),
                List.of(new SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
        );
        when(chatService.answer("Xe máy vượt đèn đỏ bị phạt thế nào?")).thenReturn(response);

        mockMvc.perform(post(ApiPaths.CHAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatQuestionRequest("Xe máy vượt đèn đỏ bị phạt thế nào?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groundingStatus").value("GROUNDED"))
                .andExpect(jsonPath("$.answer").value("Xe máy vượt đèn đỏ có thể bị xử phạt tiền theo quy định hiện hành."))
                .andExpect(jsonPath("$.disclaimer").value("Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức."))
                .andExpect(jsonPath("$.citations[0].sourceId").value("source-1"))
                .andExpect(jsonPath("$.sources[0].sourceVersionId").value("version-1"));

        verify(chatService, times(1)).answer("Xe máy vượt đèn đỏ bị phạt thế nào?");
        verifyNoMoreInteractions(chatService);
    }

    @Test
    void postChatRejectsBlankQuestionWithProblemDetailErrors() throws Exception {
        MvcResult result = mockMvc.perform(post(ApiPaths.CHAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertValidationProblemDetail(result);
    }

    @Test
    void postChatRejectsOversizedQuestionWithProblemDetailErrors() throws Exception {
        String oversizedQuestion = "x".repeat(4001);

        MvcResult result = mockMvc.perform(post(ApiPaths.CHAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatQuestionRequest(oversizedQuestion))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertValidationProblemDetail(result);
    }
}
