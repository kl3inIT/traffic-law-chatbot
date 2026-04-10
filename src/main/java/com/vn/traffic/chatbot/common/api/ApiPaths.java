package com.vn.traffic.chatbot.common.api;

public final class ApiPaths {

    private ApiPaths() {}

    public static final String ADMIN_BASE = "/api/v1/admin";
    public static final String CHAT = "/api/v1/chat";

    // Source endpoints
    public static final String SOURCES = ADMIN_BASE + "/sources";
    public static final String SOURCES_UPLOAD = SOURCES + "/upload";
    public static final String SOURCES_URL = SOURCES + "/url";
    public static final String SOURCE_BY_ID = SOURCES + "/{sourceId}";
    public static final String SOURCE_APPROVE = SOURCE_BY_ID + "/approve";
    public static final String SOURCE_REJECT = SOURCE_BY_ID + "/reject";
    public static final String SOURCE_ACTIVATE = SOURCE_BY_ID + "/activate";
    public static final String SOURCE_DEACTIVATE = SOURCE_BY_ID + "/deactivate";
    public static final String SOURCE_REINGEST = SOURCE_BY_ID + "/reingest";

    // Ingestion job endpoints
    public static final String INGESTION_JOBS = ADMIN_BASE + "/ingestion/jobs";
    public static final String JOB_BY_ID = INGESTION_JOBS + "/{jobId}";
    public static final String JOB_RETRY = JOB_BY_ID + "/retry";
    public static final String JOB_CANCEL = JOB_BY_ID + "/cancel";

    // Chunk endpoints
    public static final String CHUNKS = ADMIN_BASE + "/chunks";
    public static final String CHUNK_READINESS = CHUNKS + "/readiness";
    public static final String CHUNK_BY_ID = CHUNKS + "/{chunkId}";

    // Index summary
    public static final String INDEX_SUMMARY = ADMIN_BASE + "/index/summary";
}
