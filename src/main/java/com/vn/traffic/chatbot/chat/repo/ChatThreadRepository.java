package com.vn.traffic.chatbot.chat.repo;

import com.vn.traffic.chatbot.chat.domain.ChatThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatThreadRepository extends JpaRepository<ChatThread, UUID> {

    List<ChatThread> findAllByOrderByUpdatedAtDesc();
}
