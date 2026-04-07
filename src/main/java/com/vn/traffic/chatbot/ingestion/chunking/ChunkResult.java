package com.vn.traffic.chatbot.ingestion.chunking;

public record ChunkResult(
        String text,
        int chunkOrdinal,
        int pageNumber,
        String sectionRef,
        String contentHash,
        String processingVersion,
        String sourceId,
        String sourceVersionId
) {}
