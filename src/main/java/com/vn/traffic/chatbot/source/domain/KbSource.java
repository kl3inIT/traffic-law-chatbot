package com.vn.traffic.chatbot.source.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kb_source")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbSource {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_kind", nullable = false, length = 50)
    private OriginKind originKind;

    @Column(name = "origin_value", columnDefinition = "TEXT")
    private String originValue;

    @Column(name = "publisher_name", length = 255)
    private String publisherName;

    @Builder.Default
    @Column(name = "language_code", length = 10)
    private String languageCode = "vi";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SourceStatus status = SourceStatus.DRAFT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "trusted_state", nullable = false, length = 50)
    private TrustedState trustedState = TrustedState.UNTRUSTED;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_state", nullable = false, length = 50)
    private ApprovalState approvalState = ApprovalState.PENDING;

    @Column(name = "active_version_id", columnDefinition = "uuid")
    private UUID activeVersionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
