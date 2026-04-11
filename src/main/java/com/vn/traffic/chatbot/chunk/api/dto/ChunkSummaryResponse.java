package com.vn.traffic.chatbot.chunk.api.dto;

import java.util.List;
import java.util.UUID;

public record ChunkSummaryResponse(
        UUID id,
        String sourceId,
        String sourceVersionId,
        int chunkOrdinal,
        int pageNumber,
        String sectionRef,
        String approvalState,
        String trusted,
        String active,
        String contentPreview,
        List<Double> embeddingPreview,
        int vectorDimension
) {
}
