package com.vn.traffic.chatbot.chunk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vn.traffic.chatbot.chunk.api.dto.ChunkDetailResponse;
import com.vn.traffic.chatbot.chunk.api.dto.ChunkSummaryResponse;
import com.vn.traffic.chatbot.chunk.api.dto.IndexSummaryResponse;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChunkInspectionService {

    static final String TABLE = "kb_vector_store";
    private static final int EMBEDDING_PREVIEW_SIZE = 10;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ChunkSummaryResponse> listChunks(String sourceId, String sourceVersionId, Pageable pageable) {
        List<Object> args = new ArrayList<>();
        String whereClause = buildWhereClause(sourceId, sourceVersionId, args);
        String sql = """
                SELECT id, content, metadata, embedding
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
                "SELECT id, content, metadata, embedding FROM kb_vector_store WHERE id = ?",
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
        String content = rs.getString("content");
        String contentPreview = content != null && content.length() > 150
                ? content.substring(0, 150) : content;
        List<Double> embeddingFull = readEmbedding(rs);
        List<Double> embeddingPreview = embeddingFull != null && embeddingFull.size() > EMBEDDING_PREVIEW_SIZE
                ? embeddingFull.subList(0, EMBEDDING_PREVIEW_SIZE) : embeddingFull;
        int dim = embeddingFull != null ? embeddingFull.size() : 0;
        return new ChunkSummaryResponse(
                rs.getObject("id", UUID.class),
                asString(metadata.get("sourceId")),
                asString(metadata.get("sourceVersionId")),
                asInt(metadata.get("chunkOrdinal")),
                asInt(metadata.get("pageNumber")),
                asString(metadata.get("sectionRef")),
                asString(metadata.get("approvalState")),
                asString(metadata.get("trusted")),
                asString(metadata.get("active")),
                contentPreview,
                embeddingPreview,
                dim
        );
    }

    private ChunkDetailResponse mapDetail(ResultSet rs) throws SQLException {
        Map<String, Object> metadata = readMetadata(rs);
        List<Double> embedding = readEmbedding(rs);
        int dim = embedding != null ? embedding.size() : 0;
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
                asString(metadata.get("origin")),
                embedding,
                dim
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

    /**
     * Reads the pgvector embedding column.
     * The JDBC driver returns pgvector as a PGobject with type "vector" and value like "[0.1,0.2,...]".
     * Falls back gracefully if the column is null or driver returns unexpected type.
     */
    private List<Double> readEmbedding(ResultSet rs) throws SQLException {
        try {
            Object obj = rs.getObject("embedding");
            if (obj == null) return null;
            String vectorStr;
            if (obj instanceof PGobject pgObj) {
                vectorStr = pgObj.getValue();
            } else {
                vectorStr = obj.toString();
            }
            if (vectorStr == null || vectorStr.isBlank()) return null;
            // Strip surrounding brackets: "[0.1,0.2,...]" -> "0.1,0.2,..."
            vectorStr = vectorStr.trim();
            if (vectorStr.startsWith("[")) vectorStr = vectorStr.substring(1);
            if (vectorStr.endsWith("]")) vectorStr = vectorStr.substring(0, vectorStr.length() - 1);
            return Arrays.stream(vectorStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Double::parseDouble)
                    .toList();
        } catch (Exception ex) {
            // Embedding not critical for inspection — return null rather than failing
            return null;
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
