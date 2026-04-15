package com.vn.traffic.chatbot.chatlog.domain;

import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "chat_log",
    indexes = {
        @Index(name = "idx_chat_log_created_date", columnList = "created_date"),
        @Index(name = "idx_chat_log_grounding_status", columnList = "grounding_status"),
        @Index(name = "idx_chat_log_conversation_id", columnList = "conversation_id")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ChatLog extends BaseEntity {

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sources", columnDefinition = "TEXT")
    private String sources;

    @Enumerated(EnumType.STRING)
    @Column(name = "grounding_status", length = 30)
    private GroundingStatus groundingStatus;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "response_time")
    private Integer responseTime;

    @Column(name = "pipeline_log", columnDefinition = "TEXT")
    private String pipelineLog;

    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private OffsetDateTime createdDate;
}
