package com.vn.traffic.chatbot.checks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vn.traffic.chatbot.checks.api.CheckRunAdminController;
import com.vn.traffic.chatbot.checks.domain.CheckResult;
import com.vn.traffic.chatbot.checks.domain.CheckRun;
import com.vn.traffic.chatbot.checks.domain.CheckRunStatus;
import com.vn.traffic.chatbot.checks.service.CheckRunService;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CheckRunControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private CheckRunService checkRunService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CheckRunAdminController(checkRunService))
                .setControllerAdvice(new GlobalExceptionHandler(new MockEnvironment()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void testTriggerRunReturns202WithRunId() throws Exception {
        UUID runId = UUID.randomUUID();
        CheckRun run = CheckRun.builder()
                .id(runId)
                .status(CheckRunStatus.RUNNING)
                .parameterSetName("Default")
                .build();

        when(checkRunService.trigger()).thenReturn(run);

        mockMvc.perform(post(ApiPaths.CHECK_RUNS_TRIGGER))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(runId.toString()));
    }

    @Test
    void testGetCheckRunResults() throws Exception {
        UUID runId = UUID.randomUUID();

        CheckResult result1 = CheckResult.builder()
                .id(UUID.randomUUID())
                .question("Câu hỏi thứ nhất?")
                .referenceAnswer("Câu trả lời tham chiếu thứ nhất.")
                .actualAnswer("Câu trả lời thực tế thứ nhất.")
                .score(0.85)
                .createdDate(OffsetDateTime.now())
                .build();

        CheckResult result2 = CheckResult.builder()
                .id(UUID.randomUUID())
                .question("Câu hỏi thứ hai?")
                .referenceAnswer("Câu trả lời tham chiếu thứ hai.")
                .actualAnswer("Câu trả lời thực tế thứ hai.")
                .score(0.70)
                .createdDate(OffsetDateTime.now())
                .build();

        when(checkRunService.findResults(any(UUID.class))).thenReturn(List.of(result1, result2));

        mockMvc.perform(get(ApiPaths.CHECK_RUN_RESULTS.replace("{runId}", runId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
