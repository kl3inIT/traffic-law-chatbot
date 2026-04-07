package com.vn.traffic.chatbot.source.repo;

import com.vn.traffic.chatbot.source.domain.KbSourceVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KbSourceVersionRepository extends JpaRepository<KbSourceVersion, UUID> {

    List<KbSourceVersion> findBySourceId(UUID sourceId);
}
