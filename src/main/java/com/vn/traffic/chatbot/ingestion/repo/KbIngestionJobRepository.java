package com.vn.traffic.chatbot.ingestion.repo;

import com.vn.traffic.chatbot.ingestion.domain.IngestionJobStatus;
import com.vn.traffic.chatbot.ingestion.domain.KbIngestionJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KbIngestionJobRepository extends JpaRepository<KbIngestionJob, UUID> {

    Page<KbIngestionJob> findByStatus(IngestionJobStatus status, Pageable pageable);

    List<KbIngestionJob> findBySourceIdOrderByQueuedAtDesc(UUID sourceId);
}
