package com.vn.traffic.chatbot.source.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kb_source_approval_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbSourceApprovalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KbSource source;

    @Column(name = "source_version_id", columnDefinition = "uuid")
    private UUID sourceVersionId;

    @Column(name = "action", length = 50)
    private String action;

    @Column(name = "previous_state", length = 50)
    private String previousState;

    @Column(name = "new_state", length = 50)
    private String newState;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "acted_by", length = 255)
    private String actedBy;

    @CreationTimestamp
    @Column(name = "acted_at", nullable = false, updatable = false)
    private OffsetDateTime actedAt;
}
