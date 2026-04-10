package com.vn.traffic.chatbot.chat.repo;

import com.vn.traffic.chatbot.chat.domain.ThreadFact;
import com.vn.traffic.chatbot.chat.domain.ThreadFactStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ThreadFactRepository extends JpaRepository<ThreadFact, UUID> {

    List<ThreadFact> findByThreadIdAndStatusOrderByCreatedAtAsc(UUID threadId, ThreadFactStatus status);
}
