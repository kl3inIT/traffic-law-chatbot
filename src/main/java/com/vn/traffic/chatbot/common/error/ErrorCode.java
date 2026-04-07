package com.vn.traffic.chatbot.common.error;

public enum ErrorCode {
    SOURCE_NOT_FOUND(404),
    JOB_NOT_FOUND(404),
    INGESTION_FAILED(500),
    URL_NOT_ALLOWED(400),
    DUPLICATE_SOURCE(409),
    VALIDATION_ERROR(400);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
