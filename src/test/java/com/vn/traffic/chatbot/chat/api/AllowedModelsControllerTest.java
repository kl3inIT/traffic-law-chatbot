package com.vn.traffic.chatbot.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.common.config.AiModelProperties;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for AllowedModelsController — verifies GET /api/v1/admin/allowed-models.
 */
@ExtendWith(MockitoExtension.class)
class AllowedModelsControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AiModelProperties modelProperties = new AiModelProperties(
                "http://localhost:20128",
                "claude-sonnet-4-6",
                "claude-haiku-4-5-20251001",
                List.of(
                        new AiModelProperties.ModelEntry("gpt-5.4", "GPT-5.4", "", ""),
                        new AiModelProperties.ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6", "", ""),
                        new AiModelProperties.ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5", "", "")
                )
        );

        AllowedModelsController controller = new AllowedModelsController(modelProperties);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getAllowedModelsReturns200() throws Exception {
        mockMvc.perform(get(ApiPaths.ALLOWED_MODELS))
                .andExpect(status().isOk());
    }

    @Test
    void getAllowedModelsReturnsArrayWithThreeEntries() throws Exception {
        mockMvc.perform(get(ApiPaths.ALLOWED_MODELS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void firstEntryHasCorrectModelIdAndDisplayName() throws Exception {
        mockMvc.perform(get(ApiPaths.ALLOWED_MODELS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].modelId").value("gpt-5.4"))
                .andExpect(jsonPath("$.data[0].displayName").value("GPT-5.4"));
    }

    @Test
    void allEntriesHaveModelIdAndDisplayNameFields() throws Exception {
        mockMvc.perform(get(ApiPaths.ALLOWED_MODELS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].modelId").value("claude-sonnet-4-6"))
                .andExpect(jsonPath("$.data[1].displayName").value("Claude Sonnet 4.6"))
                .andExpect(jsonPath("$.data[2].modelId").value("claude-haiku-4-5-20251001"))
                .andExpect(jsonPath("$.data[2].displayName").value("Claude Haiku 4.5"));
    }
}
