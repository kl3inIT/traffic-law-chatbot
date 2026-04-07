package com.vn.traffic.chatbot.chunk.api.dto;

public record IndexSummaryResponse(
        long totalChunks,
        long approvedChunks,
        long trustedChunks,
        long activeChunks,
        long pendingApprovalChunks,
        long failedChunks
) {
}
