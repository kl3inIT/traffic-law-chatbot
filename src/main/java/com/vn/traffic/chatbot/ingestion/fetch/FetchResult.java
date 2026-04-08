package com.vn.traffic.chatbot.ingestion.fetch;

import java.time.OffsetDateTime;

public record FetchResult(
        String requestedUrl,
        String finalUrl,
        Integer httpStatus,
        String contentType,
        String titleHint,
        String body,
        String etag,
        OffsetDateTime lastModified
) {}
