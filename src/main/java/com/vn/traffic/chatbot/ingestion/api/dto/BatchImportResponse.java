package com.vn.traffic.chatbot.ingestion.api.dto;

import java.util.List;

public record BatchImportResponse(
        List<BatchImportItemResult> results,
        int accepted,
        int rejected
) {}
