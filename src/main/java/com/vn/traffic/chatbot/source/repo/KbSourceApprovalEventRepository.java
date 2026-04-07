package com.vn.traffic.chatbot.source.repo;

import com.vn.traffic.chatbot.source.domain.KbSourceApprovalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KbSourceApprovalEventRepository extends JpaRepository<KbSourceApprovalEvent, UUID> {

    List<KbSourceApprovalEvent> findBySourceIdOrderByActedAtDesc(UUID sourceId);
}
