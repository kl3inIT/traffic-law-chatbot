package com.vn.traffic.chatbot.parameter.domain;

import com.vn.traffic.chatbot.common.domain.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ai_parameter_set")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AiParameterSet extends BaseAuditableEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = false;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "chat_model", length = 200)
    private String chatModel;

    @Column(name = "evaluator_model", length = 200)
    private String evaluatorModel;
}
