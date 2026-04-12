package com.vn.traffic.chatbot.common.api;

public final class ApiPaths {

    private ApiPaths() {}

    public static final String ADMIN_BASE = "/api/v1/admin";
    public static final String CHAT = "/api/v1/chat";
    public static final String CHAT_THREADS = CHAT + "/threads";
    public static final String CHAT_THREAD_BY_ID = CHAT_THREADS + "/{threadId}";
    public static final String CHAT_THREAD_MESSAGES = CHAT_THREAD_BY_ID + "/messages";

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

    // AI Parameter Set endpoints
    public static final String PARAMETER_SETS = ADMIN_BASE + "/parameter-sets";
    public static final String PARAMETER_SET_BY_ID = PARAMETER_SETS + "/{parameterSetId}";
    public static final String PARAMETER_SET_ACTIVATE = PARAMETER_SET_BY_ID + "/activate";
    public static final String PARAMETER_SET_COPY = PARAMETER_SET_BY_ID + "/copy";

    // Batch ingestion
    public static final String INGESTION_BATCH = ADMIN_BASE + "/ingestion/batch";

    // Trust policy
    public static final String TRUST_POLICIES = ADMIN_BASE + "/trust-policies";
    public static final String TRUST_POLICY_BY_ID = TRUST_POLICIES + "/{policyId}";

    // Chat logs
    public static final String CHAT_LOGS = ADMIN_BASE + "/chat-logs";
    public static final String CHAT_LOG_BY_ID = CHAT_LOGS + "/{logId}";

    // Check definitions
    public static final String CHECK_DEFS = ADMIN_BASE + "/check-defs";
    public static final String CHECK_DEF_BY_ID = CHECK_DEFS + "/{defId}";

    // Check runs
    public static final String CHECK_RUNS = ADMIN_BASE + "/check-runs";
    public static final String CHECK_RUN_BY_ID = CHECK_RUNS + "/{runId}";
    public static final String CHECK_RUNS_TRIGGER = ADMIN_BASE + "/check-runs/trigger";
    public static final String CHECK_RUN_RESULTS = CHECK_RUN_BY_ID + "/results";

    // Allowed models
    public static final String ALLOWED_MODELS = ADMIN_BASE + "/allowed-models";
}
