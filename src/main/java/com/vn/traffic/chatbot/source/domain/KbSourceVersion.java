package com.vn.traffic.chatbot.source.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "kb_source_version")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbSourceVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KbSource source;

    @Builder.Default
    @Column(name = "version_no")
    private Integer versionNo = 1;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "ingest_method", length = 50)
    private String ingestMethod;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "storage_uri", columnDefinition = "TEXT")
    private String storageUri;

    @Column(name = "canonical_url", columnDefinition = "TEXT")
    private String canonicalUrl;

    @Column(name = "fetched_at")
    private OffsetDateTime fetchedAt;

    @Column(name = "parser_name", length = 100)
    private String parserName;

    @Column(name = "parser_version", length = 50)
    private String parserVersion;

    @Column(name = "chunking_strategy", length = 100)
    private String chunkingStrategy;

    @Column(name = "chunking_version", length = 50)
    private String chunkingVersion;

    @Column(name = "processing_version", length = 50)
    private String processingVersion;

    @Builder.Default
    @Column(name = "index_status", length = 50)
    private String indexStatus = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", columnDefinition = "jsonb")
    private Map<String, Object> summaryJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
