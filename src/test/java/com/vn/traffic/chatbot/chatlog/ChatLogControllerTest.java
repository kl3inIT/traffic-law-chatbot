package com.vn.traffic.chatbot.chatlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.chatlog.api.ChatLogAdminController;
import com.vn.traffic.chatbot.chatlog.domain.ChatLog;
import com.vn.traffic.chatbot.chatlog.service.ChatLogService;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.SpringDataJacksonConfiguration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ChatLogService chatLogService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new SpringDataJacksonConfiguration().pageModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatLogAdminController(chatLogService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void testGetChatLogsListWithFilters() throws Exception {
        UUID id = UUID.randomUUID();
        ChatLog log = ChatLog.builder()
                .id(id)
                .question("Tốc độ tối đa là bao nhiêu?")
                .answer("50 km/h")
                .groundingStatus(GroundingStatus.GROUNDED)
                .promptTokens(100)
                .completionTokens(200)
                .responseTime(500)
                .createdDate(OffsetDateTime.now())
                .build();

        when(chatLogService.findFiltered(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        mockMvc.perform(get(ApiPaths.CHAT_LOGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].groundingStatus").value("GROUNDED"));
    }

    @Test
    void testGetChatLogById() throws Exception {
        UUID id = UUID.randomUUID();
        ChatLog log = ChatLog.builder()
                .id(id)
                .question("Vượt đèn đỏ bị phạt bao nhiêu?")
                .answer("Phạt từ 3-5 triệu đồng.")
                .sources("Nghị định 100/2019/NĐ-CP")
                .groundingStatus(GroundingStatus.GROUNDED)
                .promptTokens(150)
                .completionTokens(300)
                .responseTime(750)
                .createdDate(OffsetDateTime.now())
                .build();

        when(chatLogService.findById(id)).thenReturn(Optional.of(log));

        mockMvc.perform(get(ApiPaths.CHAT_LOGS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.answer").value("Phạt từ 3-5 triệu đồng."))
                .andExpect(jsonPath("$.sources").value("Nghị định 100/2019/NĐ-CP"))
                .andExpect(jsonPath("$.groundingStatus").value("GROUNDED"));
    }
}
