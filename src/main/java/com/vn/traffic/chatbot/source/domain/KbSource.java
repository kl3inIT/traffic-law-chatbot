package com.vn.traffic.chatbot.source.domain;

import com.vn.traffic.chatbot.common.domain.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "kb_source")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class KbSource extends BaseAuditableEntity {

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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "trust_tier", nullable = false, length = 50)
    private TrustTier trustTier = TrustTier.MANUAL_REVIEW;

    @Column(name = "active_version_id", columnDefinition = "uuid")
    private UUID activeVersionId;
}
