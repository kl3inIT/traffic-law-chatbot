package com.vn.traffic.chatbot.chunk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChunkMetadataUpdater {

    private static final String TRUST_AND_ACTIVE_SQL = """
            UPDATE kb_vector_store
            SET metadata = jsonb_set(
                jsonb_set(metadata, '{trusted}', to_jsonb(?::text), false),
                '{active}', to_jsonb(?::text), false
            )
            WHERE metadata->>'sourceId' = ?
            """;

    private static final String APPROVAL_SQL = """
            UPDATE kb_vector_store
            SET metadata = jsonb_set(metadata, '{approvalState}', to_jsonb(?::text), false)
            WHERE metadata->>'sourceId' = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public void updateChunkMetadata(String sourceId, boolean trusted, boolean active) {
        jdbcTemplate.update(TRUST_AND_ACTIVE_SQL, ps -> {
            ps.setString(1, Boolean.toString(trusted));
            ps.setString(2, Boolean.toString(active));
            ps.setString(3, sourceId);
        });
    }

    public void updateApprovalState(String sourceId, String approvalState) {
        jdbcTemplate.update(APPROVAL_SQL, ps -> {
            ps.setString(1, approvalState);
            ps.setString(2, sourceId);
        });
    }
}
