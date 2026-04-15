package com.vn.traffic.chatbot.parameter.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.common.error.GlobalExceptionHandler;
import org.springframework.mock.env.MockEnvironment;
import com.vn.traffic.chatbot.parameter.api.dto.AiParameterSetResponse;
import com.vn.traffic.chatbot.parameter.api.dto.CreateAiParameterSetRequest;
import com.vn.traffic.chatbot.parameter.api.dto.UpdateAiParameterSetRequest;
import com.vn.traffic.chatbot.parameter.domain.AiParameterSet;
import com.vn.traffic.chatbot.parameter.service.AiParameterSetService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AiParameterSetControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private AiParameterSetService service;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiParameterSetController(service))
                .setControllerAdvice(new GlobalExceptionHandler(new MockEnvironment()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturns200WithJsonArray() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findAll()).thenReturn(List.of(paramSet(id, "Set A", false)));

        mockMvc.perform(get(ApiPaths.PARAMETER_SETS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Set A"))
                .andExpect(jsonPath("$.data[0].active").value(false));

        verify(service).findAll();
    }

    @Test
    void getByIdReturns200WithSingleObject() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(paramSet(id, "Set B", true));

        mockMvc.perform(get(ApiPaths.PARAMETER_SETS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.name").value("Set B"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.content").exists())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());

        verify(service).findById(id);
    }

    @Test
    void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), any())).thenReturn(paramSet(id, "New Set", false));

        mockMvc.perform(post(ApiPaths.PARAMETER_SETS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAiParameterSetRequest("New Set", "model:\n  name: openai"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.name").value("New Set"));

        verify(service).create("New Set", "model:\n  name: openai");
    }

    @Test
    void updateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any(), any())).thenReturn(paramSet(id, "Updated Set", false));

        mockMvc.perform(put(ApiPaths.PARAMETER_SETS + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateAiParameterSetRequest("Updated Set", "model:\n  name: openai"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Set"));

        verify(service).update(eq(id), eq("Updated Set"), any());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(service).delete(id);

        mockMvc.perform(delete(ApiPaths.PARAMETER_SETS + "/" + id))
                .andExpect(status().isNoContent());

        verify(service).delete(id);
    }

    @Test
    void activateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.activate(id)).thenReturn(paramSet(id, "Active Set", true));

        mockMvc.perform(post(ApiPaths.PARAMETER_SETS + "/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        verify(service).activate(id);
    }

    @Test
    void copyReturns201() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID copyId = UUID.randomUUID();
        when(service.copy(sourceId)).thenReturn(paramSet(copyId, "Set A (ban sao)", false));

        mockMvc.perform(post(ApiPaths.PARAMETER_SETS + "/" + sourceId + "/copy"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Set A (ban sao)"))
                .andExpect(jsonPath("$.data.active").value(false));

        verify(service).copy(sourceId);
    }

    @Test
    void getByIdReturns404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(
                new AppException(ErrorCode.PARAMETER_SET_NOT_FOUND, "AI parameter set not found: " + id));

        mockMvc.perform(get(ApiPaths.PARAMETER_SETS + "/" + id))
                .andExpect(status().isNotFound());
    }

    private AiParameterSet paramSet(UUID id, String name, boolean active) {
        return AiParameterSet.builder()
                .id(id)
                .name(name)
                .active(active)
                .content("model:\n  name: openai")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
