package com.vn.traffic.chatbot.ingestion.api;

import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.log.CrlfSanitizer;
import com.vn.traffic.chatbot.common.api.PageResponse;
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
    public ResponseEntity<IngestionAcceptedResponse> uploadSource(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") UploadSourceRequest metadata) {
        log.info("Upload source request: title={}", CrlfSanitizer.sanitize(metadata.getTitle()));
        IngestionAcceptedResponse response = ingestionService.submitUpload(
                file, metadata.getTitle(), metadata.getPublisherName(), metadata.getCreatedBy());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * POST /api/v1/admin/sources/url
     * Submit a URL for page ingestion. Returns 202 Accepted.
     */
    @PostMapping(ApiPaths.SOURCES_URL)
    public ResponseEntity<IngestionAcceptedResponse> submitUrl(
            @RequestBody @Valid UrlSourceRequest req) {
        log.info("URL ingest request: url={}", CrlfSanitizer.sanitize(req.url()));
        IngestionAcceptedResponse response = ingestionService.submitUrl(req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * GET /api/v1/admin/ingestion/jobs
     * List ingestion jobs with optional status filter.
     */
    @GetMapping(ApiPaths.INGESTION_JOBS)
    public ResponseEntity<PageResponse<IngestionJobResponse>> listJobs(
            @RequestParam(required = false) IngestionJobStatus status,
            Pageable pageable) {
        Page<KbIngestionJob> page = ingestionService.listJobs(status, pageable);
        Page<IngestionJobResponse> responsePage = page.map(this::toResponse);
        return ResponseEntity.ok(PageResponse.from(responsePage));
    }

    /**
     * GET /api/v1/admin/ingestion/jobs/{jobId}
     * Get full job detail.
     */
    @GetMapping(ApiPaths.JOB_BY_ID)
    public ResponseEntity<IngestionJobResponse> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(toResponse(ingestionService.getJob(jobId)));
    }

    /**
     * POST /api/v1/admin/ingestion/jobs/{jobId}/retry
     * Retry a failed job. Returns 202.
     */
    @PostMapping(ApiPaths.JOB_RETRY)
    public ResponseEntity<IngestionJobResponse> retryJob(@PathVariable UUID jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(toResponse(ingestionService.retryJob(jobId)));
    }

    /**
     * POST /api/v1/admin/ingestion/jobs/{jobId}/cancel
     * Cancel a queued or running job. Returns 200.
     */
    @PostMapping(ApiPaths.JOB_CANCEL)
    public ResponseEntity<IngestionJobResponse> cancelJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(toResponse(ingestionService.cancelJob(jobId)));
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
