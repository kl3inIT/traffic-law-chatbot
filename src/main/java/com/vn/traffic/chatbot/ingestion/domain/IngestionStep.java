package com.vn.traffic.chatbot.ingestion.domain;

public enum IngestionStep {
    FETCH,
    PARSE,
    NORMALIZE,
    CHUNK,
    EMBED,
    INDEX,
    FINALIZE
}
