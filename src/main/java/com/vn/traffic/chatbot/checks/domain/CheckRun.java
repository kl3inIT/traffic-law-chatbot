package com.vn.traffic.chatbot.checks.domain;

import com.vn.traffic.chatbot.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "check_run")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CheckRun extends BaseEntity {

    @Column(name = "average_score")
    private Double averageScore;

    @Column(name = "parameter_set_id", columnDefinition = "uuid")
    private UUID parameterSetId;

    @Column(name = "parameter_set_name", length = 255)
    private String parameterSetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private CheckRunStatus status;

    @Column(name = "check_count")
    private Integer checkCount;

    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private OffsetDateTime createdDate;
}
