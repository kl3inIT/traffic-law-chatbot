package com.vn.traffic.chatbot.chunk.api.dto;

import java.util.List;
import java.util.UUID;

public record ChunkDetailResponse(
        UUID id,
        String content,
        String sourceId,
        String sourceVersionId,
        int chunkOrdinal,
        int pageNumber,
        String sectionRef,
        String contentHash,
        String processingVersion,
        String approvalState,
        String trusted,
        String active,
        String origin,
        List<Double> embedding,
        int vectorDimension
) {
}
