package com.vn.traffic.chatbot.chunk.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkMetadataUpdaterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ChunkMetadataUpdater chunkMetadataUpdater;

    @Test
    void updateChunkMetadata_setsTrustedAndActiveTrue() throws Exception {
        when(jdbcTemplate.update(anyString(), any(PreparedStatementSetter.class))).thenReturn(1);

        chunkMetadataUpdater.updateChunkMetadata("source-1", true, true);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), pssCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("jsonb_set");
        assertThat(sqlCaptor.getValue()).contains("metadata->>'sourceId'");

        PreparedStatement ps = org.mockito.Mockito.mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setString(1, "true");
        verify(ps).setString(2, "true");
        verify(ps).setString(3, "source-1");
    }

    @Test
    void updateChunkMetadata_setsTrustedAndActiveFalse() throws Exception {
        when(jdbcTemplate.update(anyString(), any(PreparedStatementSetter.class))).thenReturn(1);

        chunkMetadataUpdater.updateChunkMetadata("source-2", false, false);

        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        verify(jdbcTemplate).update(anyString(), pssCaptor.capture());

        PreparedStatement ps = org.mockito.Mockito.mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setString(1, "false");
        verify(ps).setString(2, "false");
        verify(ps).setString(3, "source-2");
    }
}
