package com.vn.traffic.chatbot.source.service;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.source.api.dto.TrustPolicyRequest;
import com.vn.traffic.chatbot.source.api.dto.TrustPolicyResponse;
import com.vn.traffic.chatbot.source.domain.SourceTrustPolicy;
import com.vn.traffic.chatbot.source.repo.SourceTrustPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing SourceTrustPolicy records.
 *
 * <p>INVARIANT: This service manages only SourceTrustPolicy records. It MUST NOT
 * modify ApprovalState, TrustedState, or SourceStatus on KbSource. Trust tier is
 * additive metadata and never bypasses the retrieval gate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SourceTrustPolicyService {

    private final SourceTrustPolicyRepository trustPolicyRepository;

    @Transactional(readOnly = true)
    public List<TrustPolicyResponse> findAll() {
        return trustPolicyRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public TrustPolicyResponse create(TrustPolicyRequest request) {
        SourceTrustPolicy policy = SourceTrustPolicy.builder()
                .name(request.name())
                .domainPattern(request.domainPattern())
                .sourceType(request.sourceType())
                .trustTier(request.trustTier())
                .description(request.description())
                .build();
        SourceTrustPolicy saved = trustPolicyRepository.save(policy);
        log.info("Trust policy created: name='{}', tier={}", saved.getName(), saved.getTrustTier());
        return toResponse(saved);
    }

    public TrustPolicyResponse update(UUID id, TrustPolicyRequest request) {
        SourceTrustPolicy policy = trustPolicyRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TRUST_POLICY_NOT_FOUND,
                        "Trust policy not found: " + id));
        policy.setName(request.name());
        policy.setDomainPattern(request.domainPattern());
        policy.setSourceType(request.sourceType());
        policy.setTrustTier(request.trustTier());
        policy.setDescription(request.description());
        SourceTrustPolicy saved = trustPolicyRepository.save(policy);
        log.info("Trust policy updated: id={}, name='{}', tier={}", saved.getId(), saved.getName(), saved.getTrustTier());
        return toResponse(saved);
    }

    public void delete(UUID id) {
        SourceTrustPolicy policy = trustPolicyRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TRUST_POLICY_NOT_FOUND,
                        "Trust policy not found: " + id));
        trustPolicyRepository.delete(policy);
        log.info("Trust policy deleted: id={}, name='{}'", id, policy.getName());
    }

    private TrustPolicyResponse toResponse(SourceTrustPolicy policy) {
        return new TrustPolicyResponse(
                policy.getId(),
                policy.getName(),
                policy.getDomainPattern(),
                policy.getSourceType(),
                policy.getTrustTier(),
                policy.getDescription(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
