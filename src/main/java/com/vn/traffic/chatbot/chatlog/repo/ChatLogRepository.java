package com.vn.traffic.chatbot.chatlog.repo;

import com.vn.traffic.chatbot.chat.service.GroundingStatus;
import com.vn.traffic.chatbot.chatlog.domain.ChatLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ChatLogRepository extends JpaRepository<ChatLog, UUID>, JpaSpecificationExecutor<ChatLog> {

    Page<ChatLog> findByGroundingStatusAndCreatedDateBetween(
            GroundingStatus status, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<ChatLog> findByQuestionContainingIgnoreCase(String query, Pageable pageable);
}
