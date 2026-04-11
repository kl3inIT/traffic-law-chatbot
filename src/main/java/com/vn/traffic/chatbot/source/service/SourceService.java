package com.vn.traffic.chatbot.source.service;

import com.vn.traffic.chatbot.chunk.service.ChunkMetadataUpdater;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.source.api.dto.ApprovalRequest;
import com.vn.traffic.chatbot.source.api.dto.CreateSourceRequest;
import com.vn.traffic.chatbot.source.domain.*;
import com.vn.traffic.chatbot.source.repo.KbSourceApprovalEventRepository;
import com.vn.traffic.chatbot.source.repo.KbSourceRepository;
import com.vn.traffic.chatbot.source.repo.KbSourceVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SourceService {

    private final KbSourceRepository sourceRepo;
    private final KbSourceVersionRepository versionRepo;
    private final KbSourceApprovalEventRepository approvalEventRepo;
    private final ChunkMetadataUpdater chunkMetadataUpdater;

    public KbSource createSource(CreateSourceRequest req) {
        var source = KbSource.builder()
                .sourceType(req.sourceType())
                .title(req.title())
                .originKind(req.originKind())
                .originValue(req.originValue())
                .publisherName(req.publisherName())
                .languageCode(req.languageCode() != null ? req.languageCode() : "vi")
                .status(SourceStatus.DRAFT)
                .approvalState(ApprovalState.PENDING)
                .trustedState(TrustedState.UNTRUSTED)
                .createdBy(req.createdBy())
                .build();
        return sourceRepo.save(source);
    }

    @Transactional(readOnly = true)
    public KbSource getById(UUID id) {
        return sourceRepo.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SOURCE_NOT_FOUND, "Source not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<KbSource> list(Pageable pageable) {
        return sourceRepo.findAll(pageable);
    }

    public KbSource approve(UUID sourceId, ApprovalRequest req) {
        var source = getById(sourceId);
        if (source.getApprovalState() != ApprovalState.PENDING) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Source is not in PENDING state");
        }
        String previousState = source.getApprovalState().name();
        source.setApprovalState(ApprovalState.APPROVED);
        chunkMetadataUpdater.updateApprovalState(sourceId.toString(), ApprovalState.APPROVED.name());
        approvalEventRepo.save(KbSourceApprovalEvent.builder()
                .source(source)
                .action("APPROVE")
                .previousState(previousState)
                .newState(ApprovalState.APPROVED.name())
                .reason(req.reason())
                .actedBy(req.actedBy())
                .build());
        return sourceRepo.save(source);
    }

    public KbSource reject(UUID sourceId, ApprovalRequest req) {
        var source = getById(sourceId);
        if (source.getApprovalState() != ApprovalState.PENDING) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Source is not in PENDING state");
        }
        String previousState = source.getApprovalState().name();
        source.setApprovalState(ApprovalState.REJECTED);
        chunkMetadataUpdater.updateApprovalState(sourceId.toString(), ApprovalState.REJECTED.name());
        approvalEventRepo.save(KbSourceApprovalEvent.builder()
                .source(source)
                .action("REJECT")
                .previousState(previousState)
                .newState(ApprovalState.REJECTED.name())
                .reason(req.reason())
                .actedBy(req.actedBy())
                .build());
        return sourceRepo.save(source);
    }

    public KbSource activate(UUID sourceId, String actedBy) {
        var source = getById(sourceId);
        if (source.getApprovalState() != ApprovalState.APPROVED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Source must be approved before activation");
        }
        source.setStatus(SourceStatus.ACTIVE);
        source.setTrustedState(TrustedState.TRUSTED);
        chunkMetadataUpdater.updateChunkMetadata(sourceId.toString(), true, true);
        approvalEventRepo.save(KbSourceApprovalEvent.builder()
                .source(source)
                .action("ACTIVATE")
                .previousState(SourceStatus.DRAFT.name())
                .newState(SourceStatus.ACTIVE.name())
                .actedBy(actedBy)
                .build());
        return sourceRepo.save(source);
    }

    public KbSource deactivate(UUID sourceId, String actedBy) {
        var source = getById(sourceId);
        String previousStatus = source.getStatus().name();
        source.setStatus(SourceStatus.DISABLED);
        source.setTrustedState(TrustedState.REVOKED);
        chunkMetadataUpdater.updateChunkMetadata(sourceId.toString(), false, false);
        approvalEventRepo.save(KbSourceApprovalEvent.builder()
                .source(source)
                .action("DEACTIVATE")
                .previousState(previousStatus)
                .newState(SourceStatus.DISABLED.name())
                .actedBy(actedBy)
                .build());
        return sourceRepo.save(source);
    }

    public KbSource reingest(UUID sourceId) {
        var source = getById(sourceId);
        String previousApprovalState = source.getApprovalState().name();
        String previousStatus = source.getStatus().name();
        source.setApprovalState(ApprovalState.PENDING);
        source.setStatus(SourceStatus.DRAFT);
        source.setTrustedState(TrustedState.UNTRUSTED);
        chunkMetadataUpdater.updateChunkMetadata(sourceId.toString(), false, false);
        approvalEventRepo.save(KbSourceApprovalEvent.builder()
                .source(source)
                .action("REINGEST")
                .previousState(previousApprovalState + "/" + previousStatus)
                .newState(ApprovalState.PENDING.name() + "/" + SourceStatus.DRAFT.name())
                .build());
        return sourceRepo.save(source);
    }
}
