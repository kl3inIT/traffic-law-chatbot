package com.vn.traffic.chatbot.ingestion.api;

import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.api.PageResponse;
import com.vn.traffic.chatbot.common.api.ResponseGeneral;
import com.vn.traffic.chatbot.common.error.AppException;
import com.vn.traffic.chatbot.common.error.ErrorCode;
import com.vn.traffic.chatbot.common.log.CrlfSanitizer;
import com.vn.traffic.chatbot.ingestion.api.dto.*;
import com.vn.traffic.chatbot.ingestion.domain.IngestionJobStatus;
import com.vn.traffic.chatbot.ingestion.domain.KbIngestionJob;
import com.vn.traffic.chatbot.ingestion.service.IngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class IngestionAdminController {

    private final IngestionService ingestionService;

    /**
     * POST /api/v1/admin/sources/upload
     * Upload a file (PDF/Word/structured) for ingestion. Returns 202 Accepted.
     */
    @PostMapping(ApiPaths.SOURCES_UPLOAD)
    public ResponseEntity<ResponseGeneral<IngestionAcceptedResponse>> uploadSource(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") UploadSourceRequest metadata) {
        log.info("Upload source request: title={}", CrlfSanitizer.sanitize(metadata.getTitle()));
        IngestionAcceptedResponse response = ingestionService.submitUpload(
                file, metadata.getTitle(), metadata.getPublisherName(), metadata.getCreatedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseGeneral.ofCreated("Upload accepted", response));
    }

    /**
     * POST /api/v1/admin/ingestion/batch
     * Submit multiple URLs for ingestion in one call. Returns 200 with per-item results.
     * Each item is processed independently — a duplicate or error on one item does NOT abort others.
     * CRITICAL: method is NOT @Transactional — each item uses its own transaction via IngestionService.
     */
    @PostMapping(ApiPaths.INGESTION_BATCH)
    public ResponseEntity<ResponseGeneral<BatchImportResponse>> batchImport(
            @RequestBody @Valid BatchImportRequest request) {
        List<BatchImportItemResult> results = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;

        for (BatchImportItemRequest item : request.items()) {
            String sanitizedUrl = CrlfSanitizer.sanitize(item.url());
            try {
                var urlReq = new UrlSourceRequest(
                        item.url(), item.title(), null, null,
                        item.sourceType(), item.trustCategory());
                IngestionAcceptedResponse response = ingestionService.submitUrl(urlReq);
                results.add(new BatchImportItemResult(
                        item.url(), "ACCEPTED", response.sourceId(), response.jobId(), null));
                accepted++;
            } catch (AppException ex) {
                if (ex.getErrorCode() == ErrorCode.DUPLICATE_SOURCE) {
                    results.add(new BatchImportItemResult(
                            item.url(), "DUPLICATE", null, null, "Duplicate source URL"));
                } else {
                    log.error("Application error processing batch item url={}: {}", sanitizedUrl, ex.getMessage());
                    results.add(new BatchImportItemResult(
                            item.url(), "ERROR", null, null, ex.getMessage()));
                }
                rejected++;
            } catch (Exception ex) {
                log.error("Unexpected error processing batch item url={}: {}", sanitizedUrl, ex.getMessage(), ex);
                results.add(new BatchImportItemResult(
                        item.url(), "ERROR", null, null, "Ingestion failed"));
                rejected++;
            }
        }

        log.info("Batch import: {} items, {} accepted, {} rejected", results.size(), accepted, rejected);
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Batch import", new BatchImportResponse(results, accepted, rejected)));
    }

    /**
     * POST /api/v1/admin/sources/url
     * Submit a URL for page ingestion. Returns 202 Accepted.
     */
    @PostMapping(ApiPaths.SOURCES_URL)
    public ResponseEntity<ResponseGeneral<IngestionAcceptedResponse>> submitUrl(
            @RequestBody @Valid UrlSourceRequest req) {
        log.info("URL ingest request: url={}", CrlfSanitizer.sanitize(req.url()));
        IngestionAcceptedResponse response = ingestionService.submitUrl(req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseGeneral.ofCreated("URL ingestion accepted", response));
    }

    /**
     * GET /api/v1/admin/ingestion/jobs
     * List ingestion jobs with optional status filter.
     */
    @GetMapping(ApiPaths.INGESTION_JOBS)
    public ResponseEntity<ResponseGeneral<PageResponse<IngestionJobResponse>>> listJobs(
            @RequestParam(required = false) IngestionJobStatus status,
            Pageable pageable) {
        Page<KbIngestionJob> page = ingestionService.listJobs(status, pageable);
        Page<IngestionJobResponse> responsePage = page.map(this::toResponse);
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Ingestion jobs", PageResponse.from(responsePage)));
    }

    /**
     * GET /api/v1/admin/ingestion/jobs/{jobId}
     * Get full job detail.
     */
    @GetMapping(ApiPaths.JOB_BY_ID)
    public ResponseEntity<ResponseGeneral<IngestionJobResponse>> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Ingestion job detail", toResponse(ingestionService.getJob(jobId))));
    }

    /**
     * POST /api/v1/admin/ingestion/jobs/{jobId}/retry
     * Retry a failed job. Returns 202.
     */
    @PostMapping(ApiPaths.JOB_RETRY)
    public ResponseEntity<ResponseGeneral<IngestionJobResponse>> retryJob(@PathVariable UUID jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ResponseGeneral.ofCreated("Job retry accepted", toResponse(ingestionService.retryJob(jobId))));
    }

    /**
     * POST /api/v1/admin/ingestion/jobs/{jobId}/cancel
     * Cancel a queued or running job. Returns 200.
     */
    @PostMapping(ApiPaths.JOB_CANCEL)
    public ResponseEntity<ResponseGeneral<IngestionJobResponse>> cancelJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ResponseGeneral.ofSuccess("Job cancelled", toResponse(ingestionService.cancelJob(jobId))));
    }

    private IngestionJobResponse toResponse(KbIngestionJob job) {
        return new IngestionJobResponse(
                job.getId(),
                job.getSource() != null ? job.getSource().getId() : null,
                job.getStatus(),
                job.getStepName(),
                job.getQueuedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getRetryCount(),
                job.getErrorCode(),
                job.getErrorMessage()
        );
    }
}
