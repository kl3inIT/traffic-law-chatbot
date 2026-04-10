package com.vn.traffic.chatbot.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chat.api.dto.ChatAnswerResponse;
import com.vn.traffic.chatbot.chat.api.dto.ChatThreadMessageRequest;
import com.vn.traffic.chatbot.chat.api.dto.CreateChatThreadRequest;
import com.vn.traffic.chatbot.chat.domain.ResponseMode;
import com.vn.traffic.chatbot.chat.service.ChatService;
import com.vn.traffic.chatbot.chat.service.ChatThreadService;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatThreadControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChatService chatService;

    @Mock
    private ChatThreadService chatThreadService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new PublicChatController(chatService, chatThreadService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    void createThreadReturnsUuidBackedThreadIdentityAndPhase2Fields() throws Exception {
        UUID threadId = UUID.randomUUID();
        when(chatThreadService.createThread("Tôi vượt đèn đỏ bằng xe máy thì sao?"))
                .thenReturn(threadResponse(threadId));

        mockMvc.perform(post(ApiPaths.CHAT_THREADS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateChatThreadRequest("Tôi vượt đèn đỏ bằng xe máy thì sao?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value(threadId.toString()))
                .andExpect(jsonPath("$.responseMode").value("STANDARD"))
                .andExpect(jsonPath("$.groundingStatus").value("GROUNDED"))
                .andExpect(jsonPath("$.citations[0].sourceId").value("source-1"))
                .andExpect(jsonPath("$.sources[0].sourceVersionId").value("version-1"));

        verify(chatThreadService).createThread("Tôi vượt đèn đỏ bằng xe máy thì sao?");
    }

    @Test
    void continueThreadRequiresValidUuidPathParameter() throws Exception {
        mockMvc.perform(post(ApiPaths.CHAT + "/threads/not-a-uuid/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatThreadMessageRequest("Tiếp theo thì xử lý sao?"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.properties.errors.threadId").value("Invalid value"));

        verifyNoInteractions(chatThreadService);
    }

    @Test
    void continueThreadRejectsMalformedPayloadsWithProblemDetail() throws Exception {
        UUID threadId = UUID.randomUUID();
        String oversized = "x".repeat(4001);

        mockMvc.perform(post(ApiPaths.CHAT + "/threads/" + threadId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatThreadMessageRequest(oversized))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.properties.errors.question").exists());

        verifyNoInteractions(chatThreadService);
    }

    @Test
    void continueThreadReturnsNotFoundProblemDetailForUnknownThread() throws Exception {
        UUID threadId = UUID.randomUUID();
        when(chatThreadService.postMessage(threadId, "Tôi có bị giữ bằng lái không?"))
                .thenThrow(new AppException(ErrorCode.CHAT_THREAD_NOT_FOUND, "Chat thread not found: " + threadId));

        mockMvc.perform(post(ApiPaths.CHAT + "/threads/" + threadId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatThreadMessageRequest("Tôi có bị giữ bằng lái không?"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.properties.errorCode").value("CHAT_THREAD_NOT_FOUND"));
    }

    private ChatAnswerResponse threadResponse(UUID threadId) {
        return new ChatAnswerResponse(
                GroundingStatus.GROUNDED,
                threadId,
                ResponseMode.STANDARD,
                "Trả lời có căn cứ.",
                "Kết luận có căn cứ.",
                "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.",
                null,
                List.of("Điều 7 [Nguồn 1]"),
                List.of("Phạt tiền [Nguồn 1]"),
                List.of(),
                List.of(),
                List.of("Đối chiếu biên bản"),
                List.of(),
                List.of(),
                null,
                List.of(new com.vn.traffic.chatbot.chat.api.dto.CitationResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7", "Trích dẫn")),
                List.of(new com.vn.traffic.chatbot.chat.api.dto.SourceReferenceResponse("[Nguồn 1]", "source-1", "version-1", "Nghị định 168", "https://vbpl.vn/nd168", 4, "Điều 7"))
        );
    }
}
