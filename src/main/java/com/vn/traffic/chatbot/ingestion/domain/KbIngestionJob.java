package com.vn.traffic.chatbot.ingestion.domain;

import com.vn.traffic.chatbot.common.domain.BaseEntity;
import com.vn.traffic.chatbot.source.domain.KbSource;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "kb_ingestion_job")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class KbIngestionJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KbSource source;

    @Column(name = "source_version_id", columnDefinition = "uuid")
    private UUID sourceVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    private JobType jobType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private IngestionJobStatus status = IngestionJobStatus.QUEUED;

    @Column(name = "queued_at")
    private OffsetDateTime queuedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Builder.Default
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "triggered_by", length = 255)
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 50)
    private TriggerType triggerType;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_name", length = 50)
    private IngestionStep stepName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_detail_json", columnDefinition = "jsonb")
    private Map<String, Object> stepDetailJson;

    @PrePersist
    public void prePersist() {
        if (queuedAt == null) {
            queuedAt = OffsetDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
