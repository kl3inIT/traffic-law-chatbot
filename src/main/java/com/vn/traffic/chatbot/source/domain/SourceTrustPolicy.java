package com.vn.traffic.chatbot.source.domain;

import com.vn.traffic.chatbot.common.domain.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * JPA entity representing a trust policy record in the source_trust_policy table.
 *
 * <p>Trust policies classify sources by domain pattern and/or source type into
 * PRIMARY, SECONDARY, or MANUAL_REVIEW tiers. This classification is additive
 * metadata only — it does NOT alter ApprovalState, TrustedState, or SourceStatus
 * on KbSource. The retrieval gate remains solely governed by those three fields.
 */
@Entity
@Table(name = "source_trust_policy")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SourceTrustPolicy extends BaseAuditableEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "domain_pattern", length = 500)
    private String domainPattern;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_tier", nullable = false, length = 50)
    private TrustTier trustTier;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
