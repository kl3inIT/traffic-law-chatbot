package com.vn.traffic.chatbot.chunk.service;

import com.vn.traffic.chatbot.chunk.api.dto.ChunkDetailResponse;
import com.vn.traffic.chatbot.chunk.api.dto.ChunkSummaryResponse;
import com.vn.traffic.chatbot.chunk.api.dto.IndexSummaryResponse;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkInspectionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ChunkInspectionService chunkInspectionService;

    @Test
    void listChunks_withoutFilters_queriesVectorTableWithPagination() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new ChunkSummaryResponse(UUID.randomUUID(), "s1", "v1", 1, 2, "sec-1", "APPROVED", "true", "true")));

        var result = chunkInspectionService.listChunks(null, null, PageRequest.of(0, 20));

        assertThat(result).hasSize(1);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getValue()).contains("SELECT id, content, metadata");
        assertThat(sqlCaptor.getValue()).contains("FROM kb_vector_store");
        assertThat(sqlCaptor.getValue()).contains("LIMIT ? OFFSET ?");
    }

    @Test
    void listChunks_withSourceId_addsSourceFilter() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        chunkInspectionService.listChunks("source-1", null, PageRequest.of(0, 10));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getValue()).contains("metadata->>'sourceId' = ?");
    }

    @Test
    void listChunks_withSourceIdAndVersionId_addsBothFilters() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        chunkInspectionService.listChunks("source-1", "version-1", PageRequest.of(1, 10));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sqlCaptor.getValue()).contains("metadata->>'sourceId' = ?");
        assertThat(sqlCaptor.getValue()).contains("metadata->>'sourceVersionId' = ?");
    }

    @Test
    void getChunk_returnsChunkDetailResponse() {
        UUID chunkId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object.class)))
                .thenReturn(List.of(new ChunkDetailResponse(chunkId, "content", "source-1", "version-1", 3, 4, "sec-a", "hash", "1.0", "APPROVED", "true", "true", "origin")));

        ChunkDetailResponse result = chunkInspectionService.getChunk(chunkId);

        assertThat(result.id()).isEqualTo(chunkId);
        assertThat(result.content()).isEqualTo("content");
        assertThat(result.sourceId()).isEqualTo("source-1");
    }

    @Test
    void getChunk_whenMissing_throwsAppException() {
        UUID chunkId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> chunkInspectionService.getChunk(chunkId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appException = (AppException) ex;
                    assertThat(appException.getErrorCode()).isEqualTo(ErrorCode.INGESTION_FAILED);
                    assertThat(appException.getMessage()).contains("Chunk not found");
                });
    }

    @Test
    void getIndexSummary_returnsAggregatedCounts() {
        // getRetrievalReadinessCounts: approved=4, trusted=3, active=2, eligible=1
        // getIndexSummary: totalChunks=10, pendingApproval=6
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(4L, 3L, 2L, 1L, 10L, 6L);

        IndexSummaryResponse result = chunkInspectionService.getIndexSummary();

        assertThat(result.totalChunks()).isEqualTo(10L);
        assertThat(result.approvedChunks()).isEqualTo(4L);
        assertThat(result.trustedChunks()).isEqualTo(3L);
        assertThat(result.activeChunks()).isEqualTo(2L);
        assertThat(result.pendingApprovalChunks()).isEqualTo(6L);
        assertThat(result.eligibleChunks()).isEqualTo(1L);
        verify(jdbcTemplate, times(6)).queryForObject(anyString(), eq(Long.class));
    }
}
