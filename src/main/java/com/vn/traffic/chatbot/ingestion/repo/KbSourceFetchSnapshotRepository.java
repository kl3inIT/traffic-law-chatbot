package com.vn.traffic.chatbot.ingestion.repo;

import com.vn.traffic.chatbot.ingestion.domain.KbSourceFetchSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KbSourceFetchSnapshotRepository extends JpaRepository<KbSourceFetchSnapshot, UUID> {
}
