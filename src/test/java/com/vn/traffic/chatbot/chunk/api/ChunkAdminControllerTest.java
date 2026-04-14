package com.vn.traffic.chatbot.chunk.api;

import com.vn.traffic.chatbot.chunk.service.ChunkInspectionService;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.error.GlobalExceptionHandler;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChunkAdminControllerTest {

    @Mock
    private ChunkInspectionService chunkInspectionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChunkAdminController(chunkInspectionService))
                .setControllerAdvice(new GlobalExceptionHandler(new MockEnvironment()))
                .build();
    }

    @Test
    void getReadinessReturnsCountsWithoutRoutingToChunkById() throws Exception {
        when(chunkInspectionService.getRetrievalReadinessCounts())
                .thenReturn(new ChunkInspectionService.RetrievalReadinessCounts(1L, 1L, 1L, 1L));

        mockMvc.perform(get(ApiPaths.CHUNK_READINESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvedChunks").value(1))
                .andExpect(jsonPath("$.data.trustedChunks").value(1))
                .andExpect(jsonPath("$.data.activeChunks").value(1))
                .andExpect(jsonPath("$.data.eligibleChunks").value(1));

        verify(chunkInspectionService).getRetrievalReadinessCounts();
        verify(chunkInspectionService, never()).getChunk(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
}
