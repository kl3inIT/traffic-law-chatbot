package com.vn.traffic.chatbot.source.repo;

import com.vn.traffic.chatbot.source.domain.SourceTrustPolicy;
import com.vn.traffic.chatbot.source.domain.TrustTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SourceTrustPolicyRepository extends JpaRepository<SourceTrustPolicy, UUID> {

    List<SourceTrustPolicy> findByTrustTier(TrustTier tier);
}
