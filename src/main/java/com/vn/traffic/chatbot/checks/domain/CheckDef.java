package com.vn.traffic.chatbot.checks.domain;

import com.vn.traffic.chatbot.common.domain.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "check_def")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CheckDef extends BaseAuditableEntity {

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "reference_answer", columnDefinition = "TEXT")
    private String referenceAnswer;

    @Column(name = "category", length = 100)
    private String category;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;
}
