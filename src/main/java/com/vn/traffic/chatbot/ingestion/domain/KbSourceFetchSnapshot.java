package com.vn.traffic.chatbot.ingestion.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kb_source_fetch_snapshot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbSourceFetchSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "source_version_id", columnDefinition = "uuid", nullable = false)
    private UUID sourceVersionId;

    @Column(name = "requested_url", columnDefinition = "TEXT", nullable = false)
    private String requestedUrl;

    @Column(name = "final_url", columnDefinition = "TEXT")
    private String finalUrl;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "etag", length = 255)
    private String etag;

    @Column(name = "last_modified")
    private OffsetDateTime lastModified;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private OffsetDateTime fetchedAt;
}
