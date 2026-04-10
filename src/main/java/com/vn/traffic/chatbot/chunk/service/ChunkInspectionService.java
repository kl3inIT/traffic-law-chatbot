package com.vn.traffic.chatbot.chunk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chunk.api.dto.ChunkDetailResponse;
import com.vn.traffic.chatbot.chunk.api.dto.ChunkSummaryResponse;
import com.vn.traffic.chatbot.chunk.api.dto.IndexSummaryResponse;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChunkInspectionService {

    static final String TABLE = "kb_vector_store";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ChunkSummaryResponse> listChunks(String sourceId, String sourceVersionId, Pageable pageable) {
        List<Object> args = new ArrayList<>();
        String whereClause = buildWhereClause(sourceId, sourceVersionId, args);
        String sql = """
                SELECT id, content, metadata
                FROM kb_vector_store
                WHERE 1=1
                %s
                ORDER BY id
                LIMIT ? OFFSET ?
                """.formatted(whereClause);
        args.add(pageable.getPageSize());
        args.add((int) pageable.getOffset());

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSummary(rs), args.toArray());
    }

    public long countChunks(String sourceId, String sourceVersionId) {
        List<Object> args = new ArrayList<>();
        String whereClause = buildWhereClause(sourceId, sourceVersionId, args);
        String sql = "SELECT COUNT(*) FROM kb_vector_store WHERE 1=1 " + whereClause;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count != null ? count : 0L;
    }

    public ChunkDetailResponse getChunk(UUID id) {
        List<ChunkDetailResponse> results = jdbcTemplate.query(
                "SELECT id, content, metadata FROM kb_vector_store WHERE id = ?",
                (rs, rowNum) -> mapDetail(rs),
                id
        );
        if (results.isEmpty()) {
            throw new AppException(ErrorCode.INGESTION_FAILED, "Chunk not found: " + id);
        }
        return results.getFirst();
    }

    public IndexSummaryResponse getIndexSummary() {
        RetrievalReadinessCounts readinessCounts = getRetrievalReadinessCounts();
        long totalChunks = countBySql("SELECT COUNT(*) FROM kb_vector_store");
        long pendingApprovalChunks = countBySql("SELECT COUNT(*) FROM kb_vector_store WHERE metadata->>'approvalState' = 'PENDING'");

        return new IndexSummaryResponse(
                totalChunks,
                readinessCounts.approvedChunks(),
                readinessCounts.trustedChunks(),
                readinessCounts.activeChunks(),
                pendingApprovalChunks,
                readinessCounts.eligibleChunks()
        );
    }

    public RetrievalReadinessCounts getRetrievalReadinessCounts() {
        return new RetrievalReadinessCounts(
                countBySql("SELECT COUNT(*) FROM kb_vector_store WHERE metadata->>'approvalState' = 'APPROVED'"),
                countBySql("SELECT COUNT(*) FROM kb_vector_store WHERE metadata->>'trusted' = 'true'"),
                countBySql("SELECT COUNT(*) FROM kb_vector_store WHERE metadata->>'active' = 'true'"),
                countBySql("""
                        SELECT COUNT(*)
                        FROM kb_vector_store
                        WHERE metadata->>'approvalState' = 'APPROVED'
                          AND metadata->>'trusted' = 'true'
                          AND metadata->>'active' = 'true'
                        """)
        );
    }

    public record RetrievalReadinessCounts(
            long approvedChunks,
            long trustedChunks,
            long activeChunks,
            long eligibleChunks
    ) {
    }

    private long countBySql(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    private String buildWhereClause(String sourceId, String sourceVersionId, List<Object> args) {
        StringBuilder where = new StringBuilder();
        if (sourceId != null && !sourceId.isBlank()) {
            where.append(" AND metadata->>'sourceId' = ?");
            args.add(sourceId);
        }
        if (sourceVersionId != null && !sourceVersionId.isBlank()) {
            where.append(" AND metadata->>'sourceVersionId' = ?");
            args.add(sourceVersionId);
        }
        return where.toString();
    }

    private ChunkSummaryResponse mapSummary(ResultSet rs) throws SQLException {
        Map<String, Object> metadata = readMetadata(rs);
        return new ChunkSummaryResponse(
                rs.getObject("id", UUID.class),
                asString(metadata.get("sourceId")),
                asString(metadata.get("sourceVersionId")),
                asInt(metadata.get("chunkOrdinal")),
                asInt(metadata.get("pageNumber")),
                asString(metadata.get("sectionRef")),
                asString(metadata.get("approvalState")),
                asString(metadata.get("trusted")),
                asString(metadata.get("active"))
        );
    }

    private ChunkDetailResponse mapDetail(ResultSet rs) throws SQLException {
        Map<String, Object> metadata = readMetadata(rs);
        return new ChunkDetailResponse(
                rs.getObject("id", UUID.class),
                rs.getString("content"),
                asString(metadata.get("sourceId")),
                asString(metadata.get("sourceVersionId")),
                asInt(metadata.get("chunkOrdinal")),
                asInt(metadata.get("pageNumber")),
                asString(metadata.get("sectionRef")),
                asString(metadata.get("contentHash")),
                asString(metadata.get("processingVersion")),
                asString(metadata.get("approvalState")),
                asString(metadata.get("trusted")),
                asString(metadata.get("active")),
                asString(metadata.get("origin"))
        );
    }

    private Map<String, Object> readMetadata(ResultSet rs) throws SQLException {
        try {
            String json = rs.getString("metadata");
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new SQLException("Failed to parse chunk metadata", ex);
        }
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
