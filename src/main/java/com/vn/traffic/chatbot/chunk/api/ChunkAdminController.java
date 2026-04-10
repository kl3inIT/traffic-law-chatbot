package com.vn.traffic.chatbot.chunk.api;

import com.vn.traffic.chatbot.chunk.api.dto.ChunkDetailResponse;
import com.vn.traffic.chatbot.chunk.api.dto.ChunkSummaryResponse;
import com.vn.traffic.chatbot.chunk.api.dto.IndexSummaryResponse;
import com.vn.traffic.chatbot.chunk.service.ChunkInspectionService.RetrievalReadinessCounts;
import com.vn.traffic.chatbot.chunk.service.ChunkInspectionService;
import com.vn.traffic.chatbot.common.api.ApiPaths;
import com.vn.traffic.chatbot.common.api.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChunkAdminController {

    private final ChunkInspectionService chunkInspectionService;

    @GetMapping(ApiPaths.CHUNKS)
    public ResponseEntity<PageResponse<ChunkSummaryResponse>> listChunks(
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) String sourceVersionId,
            Pageable pageable) {
        var chunks = chunkInspectionService.listChunks(sourceId, sourceVersionId, pageable);
        long totalCount = chunkInspectionService.countChunks(sourceId, sourceVersionId);
        var page = new PageImpl<>(chunks, pageable, totalCount);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @GetMapping(ApiPaths.CHUNK_READINESS)
    public ResponseEntity<RetrievalReadinessCounts> getReadiness() {
        return ResponseEntity.ok(chunkInspectionService.getRetrievalReadinessCounts());
    }

    @GetMapping(ApiPaths.CHUNK_BY_ID)
    public ResponseEntity<ChunkDetailResponse> getChunk(@PathVariable UUID chunkId) {
        return ResponseEntity.ok(chunkInspectionService.getChunk(chunkId));
    }

    @GetMapping(ApiPaths.INDEX_SUMMARY)
    public ResponseEntity<IndexSummaryResponse> getIndexSummary() {
        return ResponseEntity.ok(chunkInspectionService.getIndexSummary());
    }
}
