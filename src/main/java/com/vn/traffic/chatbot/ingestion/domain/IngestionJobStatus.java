package com.vn.traffic.chatbot.ingestion.domain;

public enum IngestionJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    RETRYING
}
