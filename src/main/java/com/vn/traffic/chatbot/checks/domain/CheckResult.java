package com.vn.traffic.chatbot.checks.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "check_result",
    indexes = {
        @Index(name = "idx_check_result_check_def", columnList = "check_def_id"),
        @Index(name = "idx_check_result_check_run", columnList = "check_run_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_def_id")
    private CheckDef checkDef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_run_id")
    private CheckRun checkRun;

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "reference_answer", columnDefinition = "TEXT")
    private String referenceAnswer;

    @Column(name = "actual_answer", columnDefinition = "TEXT")
    private String actualAnswer;

    @Column(name = "score")
    private Double score;

    @Column(name = "log", columnDefinition = "TEXT")
    private String log;

    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private OffsetDateTime createdDate;
}
