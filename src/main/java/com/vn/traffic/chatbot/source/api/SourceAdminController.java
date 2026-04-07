package com.vn.traffic.chatbot.source.api;

import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.api.PageResponse;
import com.vn.traffic.chatbot.source.api.dto.*;
import com.vn.traffic.chatbot.source.domain.KbSource;
import com.vn.traffic.chatbot.source.domain.SourceStatus;
import com.vn.traffic.chatbot.source.service.SourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.SOURCES)
@RequiredArgsConstructor
@Slf4j
public class SourceAdminController {

    private final SourceService sourceService;

    @PostMapping
    public ResponseEntity<SourceSummaryResponse> createSource(
            @RequestBody @Valid CreateSourceRequest request) {
        log.info("Creating source: {}", request.title());
        var source = sourceService.createSource(request);
        return ResponseEntity.status(201).body(toSummary(source));
    }

    @GetMapping
    public ResponseEntity<PageResponse<SourceSummaryResponse>> listSources(
            @RequestParam(required = false) SourceStatus status,
            Pageable pageable) {
        var page = sourceService.list(pageable);
        return ResponseEntity.ok(PageResponse.from(page.map(this::toSummary)));
    }

    @GetMapping("/{sourceId}")
    public ResponseEntity<SourceDetailResponse> getSource(@PathVariable UUID sourceId) {
        var source = sourceService.getById(sourceId);
        java.util.List<com.vn.traffic.chatbot.source.domain.KbSourceVersion> versions =
                java.util.Collections.emptyList();
        return ResponseEntity.ok(toDetail(source, versions));
    }

    @PostMapping("/{sourceId}/approve")
    public ResponseEntity<SourceSummaryResponse> approve(
            @PathVariable UUID sourceId,
            @RequestBody ApprovalRequest request) {
        var source = sourceService.approve(sourceId, request);
        return ResponseEntity.ok(toSummary(source));
    }

    @PostMapping("/{sourceId}/reject")
    public ResponseEntity<SourceSummaryResponse> reject(
            @PathVariable UUID sourceId,
            @RequestBody ApprovalRequest request) {
        var source = sourceService.reject(sourceId, request);
        return ResponseEntity.ok(toSummary(source));
    }

    @PostMapping("/{sourceId}/activate")
    public ResponseEntity<SourceSummaryResponse> activate(
            @PathVariable UUID sourceId,
            @RequestParam(required = false) String actedBy) {
        var source = sourceService.activate(sourceId, actedBy);
        return ResponseEntity.ok(toSummary(source));
    }

    @PostMapping("/{sourceId}/deactivate")
    public ResponseEntity<SourceSummaryResponse> deactivate(
            @PathVariable UUID sourceId,
            @RequestParam(required = false) String actedBy) {
        var source = sourceService.deactivate(sourceId, actedBy);
        return ResponseEntity.ok(toSummary(source));
    }

    private SourceSummaryResponse toSummary(KbSource source) {
        return new SourceSummaryResponse(
                source.getId(),
                source.getTitle(),
                source.getSourceType(),
                source.getStatus(),
                source.getTrustedState(),
                source.getApprovalState(),
                source.getCreatedAt()
        );
    }

    private SourceDetailResponse toDetail(KbSource source,
            java.util.List<com.vn.traffic.chatbot.source.domain.KbSourceVersion> versions) {
        return new SourceDetailResponse(
                source.getId(),
                source.getSourceType(),
                source.getTitle(),
                source.getOriginKind(),
                source.getOriginValue(),
                source.getPublisherName(),
                source.getLanguageCode(),
                source.getStatus(),
                source.getTrustedState(),
                source.getApprovalState(),
                source.getActiveVersionId(),
                source.getCreatedAt(),
                source.getCreatedBy(),
                source.getUpdatedAt(),
                source.getUpdatedBy(),
                versions
        );
    }
}
