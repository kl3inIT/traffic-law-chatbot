package com.vn.traffic.chatbot.ingestion.service;

import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.ingestion.api.dto.IngestionAcceptedResponse;
import com.vn.traffic.chatbot.ingestion.api.dto.UrlSourceRequest;
import com.vn.traffic.chatbot.ingestion.domain.*;
import com.vn.traffic.chatbot.ingestion.orchestrator.IngestionOrchestrator;
import com.vn.traffic.chatbot.ingestion.parser.UrlPageParser;
import com.vn.traffic.chatbot.ingestion.repo.KbIngestionJobRepository;
import com.vn.traffic.chatbot.ingestion.repo.KbSourceFetchSnapshotRepository;
import com.vn.traffic.chatbot.source.api.dto.CreateSourceRequest;
import com.vn.traffic.chatbot.source.domain.KbSource;
import com.vn.traffic.chatbot.source.domain.KbSourceVersion;
import com.vn.traffic.chatbot.source.domain.OriginKind;
import com.vn.traffic.chatbot.source.domain.SourceType;
import com.vn.traffic.chatbot.source.repo.KbSourceRepository;
import com.vn.traffic.chatbot.source.repo.KbSourceVersionRepository;
import com.vn.traffic.chatbot.source.service.SourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class IngestionService {

    private final SourceService sourceService;
    private final KbSourceRepository sourceRepo;
    private final KbSourceVersionRepository versionRepo;
    private final KbIngestionJobRepository jobRepo;
    private final KbSourceFetchSnapshotRepository fetchSnapshotRepo;
    private final IngestionOrchestrator orchestrator;
    private final UrlPageParser urlPageParser;

    public IngestionAcceptedResponse submitUpload(MultipartFile file, String title,
                                                   String publisherName, String createdBy) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "File must not be empty");
        }
        if (title == null || title.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Title must not be blank");
        }

        String mimeType = file.getContentType();
        SourceType sourceType = resolveSourceType(mimeType);

        // Store to temp file
        Path tempPath;
        try {
            String originalFilename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "upload.bin";
            tempPath = Files.createTempFile("kb-", "-" + originalFilename);
            file.transferTo(tempPath);
        } catch (IOException ex) {
            throw new AppException(ErrorCode.INGESTION_FAILED, "Failed to store upload: " + ex.getMessage());
        }

        // Compute content hash
        String contentHash;
        try {
            byte[] bytes = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            contentHash = HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            contentHash = null;
        }

        // Create source
        CreateSourceRequest createReq = new CreateSourceRequest(
                sourceType, title, OriginKind.FILE_UPLOAD, null,
                publisherName, null, createdBy);
        KbSource source = sourceService.createSource(createReq);

        // Create version
        KbSourceVersion version = KbSourceVersion.builder()
                .source(source)
                .versionNo(1)
                .mimeType(mimeType)
                .fileName(file.getOriginalFilename())
                .storageUri(tempPath.toString())
                .contentHash(contentHash)
                .processingVersion("1.0")
                .build();
        version = versionRepo.save(version);

        // Create job
        KbIngestionJob job = KbIngestionJob.builder()
                .source(source)
                .sourceVersionId(version.getId())
                .jobType(JobType.FILE_UPLOAD)
                .status(IngestionJobStatus.QUEUED)
                .triggerType(TriggerType.ADMIN_API)
                .triggeredBy(createdBy)
                .build();
        job = jobRepo.save(job);

        schedulePipelineAfterCommit(job.getId());

        return new IngestionAcceptedResponse(source.getId(), job.getId(), "QUEUED");
    }

    public IngestionAcceptedResponse submitUrl(UrlSourceRequest req) {
        // SSRF check before any DB writes
        urlPageParser.validateHost(req.url());

        KbSource source = sourceService.createSource(new CreateSourceRequest(
                SourceType.WEBSITE_PAGE,
                req.title() != null ? req.title() : req.url(),
                OriginKind.URL_IMPORT,
                req.url(),
                req.publisherName(),
                null,
                req.createdBy()));

        KbSourceVersion version = KbSourceVersion.builder()
                .source(source)
                .versionNo(1)
                .canonicalUrl(req.url())
                .processingVersion("1.0")
                .build();
        version = versionRepo.save(version);

        KbIngestionJob job = KbIngestionJob.builder()
                .source(source)
                .sourceVersionId(version.getId())
                .jobType(JobType.URL_IMPORT)
                .status(IngestionJobStatus.QUEUED)
                .triggerType(TriggerType.ADMIN_API)
                .triggeredBy(req.createdBy())
                .build();
        job = jobRepo.save(job);

        schedulePipelineAfterCommit(job.getId());

        return new IngestionAcceptedResponse(source.getId(), job.getId(), "QUEUED");
    }

    @Transactional(readOnly = true)
    public KbIngestionJob getJob(UUID jobId) {
        return jobRepo.findById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.JOB_NOT_FOUND, jobId.toString()));
    }

    @Transactional(readOnly = true)
    public Page<KbIngestionJob> listJobs(IngestionJobStatus statusFilter, Pageable pageable) {
        if (statusFilter != null) {
            return jobRepo.findByStatus(statusFilter, pageable);
        }
        return jobRepo.findAll(pageable);
    }

    public KbIngestionJob retryJob(UUID jobId) {
        KbIngestionJob job = getJob(jobId);
        if (job.getStatus() != IngestionJobStatus.FAILED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Only FAILED jobs can be retried");
        }
        job.setStatus(IngestionJobStatus.QUEUED);
        job.setRetryCount(job.getRetryCount() != null ? job.getRetryCount() + 1 : 1);
        job.setErrorMessage(null);
        job.setErrorCode(null);
        job = jobRepo.save(job);
        orchestrator.runPipeline(job.getId());
        return job;
    }

    public KbIngestionJob cancelJob(UUID jobId) {
        KbIngestionJob job = getJob(jobId);
        if (job.getStatus() != IngestionJobStatus.QUEUED
                && job.getStatus() != IngestionJobStatus.RUNNING) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Only QUEUED or RUNNING jobs can be cancelled");
        }
        job.setStatus(IngestionJobStatus.CANCELLED);
        return jobRepo.save(job);
    }

    private void schedulePipelineAfterCommit(UUID jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            orchestrator.runPipeline(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orchestrator.runPipeline(jobId);
            }
        });
    }

    private SourceType resolveSourceType(String mimeType) {
        if (mimeType == null) return SourceType.PDF;
        return switch (mimeType.toLowerCase()) {
            case "application/pdf" -> SourceType.PDF;
            case "application/msword",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                         -> SourceType.WORD;
            default -> SourceType.STRUCTURED_REGULATION;
        };
    }
}
