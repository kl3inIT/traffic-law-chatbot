package com.vn.traffic.chatbot.source.repo;

import com.vn.traffic.chatbot.source.domain.ApprovalState;
import com.vn.traffic.chatbot.source.domain.KbSource;
import com.vn.traffic.chatbot.source.domain.SourceStatus;
import com.vn.traffic.chatbot.source.domain.TrustedState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KbSourceRepository extends JpaRepository<KbSource, UUID> {

    Page<KbSource> findByStatusAndApprovalStateAndTrustedState(
            SourceStatus status,
            ApprovalState approvalState,
            TrustedState trustedState,
            Pageable pageable
    );

    Optional<KbSource> findByOriginValue(String originValue);
}
