---
phase: 01-backend-foundation-knowledge-base
plan: 03
subsystem: ingestion
tags: [ingestion, async, tika, jsoup, pgvector, ssrf]
dependency_graph:
  requires:
    - Plan 01 common infrastructure (ApiPaths, ErrorCode, AsyncConfig, VectorStoreConfig)
    - Plan 02 source registry (KbSource, KbSourceVersion, SourceService)
  provides:
    - KbIngestionJob and KbSourceFetchSnapshot persistence
    - TikaDocumentParser for PDF/Word/structured documents
    - UrlPageParser with SSRF host validation
    - TextChunker with 1500-char chunks and 150-char overlap
    - IngestionOrchestrator async pipeline with VectorStore writes
    - IngestionService and IngestionAdminController upload/url/job endpoints
  affects:
    - Plan 04 chunk inspection and retrieval policy
tech_stack:
  added:
    - Apache Tika parser usage for uploaded document extraction
    - JSoup page fetch for URL imports
    - Spring Async executor via @Async("ingestionExecutor")
    - Spring AI Document + VectorStore integration
  patterns:
    - Async orchestrator updates job stepName/status on each transition
    - Service layer creates source/version/job records before non-blocking execution
    - REST endpoints return 202 Accepted for ingestion-triggering operations
key_files:
  created:
    - src/main/resources/db/changelog/002-ingestion-job-indexes.xml
    - src/main/java/com/vn/traffic/chatbot/ingestion/domain/IngestionJobStatus.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/domain/IngestionStep.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/domain/JobType.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/domain/TriggerType.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/domain/KbIngestionJob.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/domain/KbSourceFetchSnapshot.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/repo/KbIngestionJobRepository.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/repo/KbSourceFetchSnapshotRepository.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/parser/DocumentParser.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/parser/ParsedDocument.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParser.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/parser/UrlPageParser.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/chunking/TextChunker.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/chunking/ChunkResult.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/api/dto/UploadSourceRequest.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/api/dto/UrlSourceRequest.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/api/dto/IngestionAcceptedResponse.java
    - src/main/java/com/vn/traffic/chatbot/ingestion/api/dto/IngestionJobResponse.java
    - src/test/java/com/vn/traffic/chatbot/ingestion/service/IngestionServiceTest.java
  modified:
    - src/main/resources/db/changelog/db.changelog-master.xml
    - build.gradle
metrics:
  completed_date: "2026-04-08"
  tasks_completed: 2
---

# Phase 1 Plan 03: Async Ingestion Pipeline Summary

**One-liner:** Added async ingestion for file and URL sources with job tracking, SSRF validation, Tika/JSoup parsing, overlapping chunking, and pgvector indexing metadata gated as untrusted/inactive by default.

## What Was Built

### Parsers

- **TikaDocumentParser** — parses uploaded PDF, Word, and structured documents using `AutoDetectParser`.
  - Detects MIME type from Tika metadata.
  - Uses `WriteOutContentHandler` with a 10 MB character limit.
  - Splits PDFs by form-feed into page sections.
  - Falls back to a single `full` section for non-PDF content.
- **UrlPageParser** — fetches one HTTP/HTTPS page with JSoup.
  - Validates host before any request.
  - Extracts page title and `body().text()` into a single parsed section.

### Chunking Strategy

- **Chunk size:** 1500 characters
- **Overlap:** 150 characters
- **Advance:** 1350 characters
- **Ordinaling:** global `chunkOrdinal` across the whole parsed document
- **Hashing:** SHA-256 per chunk text

### Job Model and Pipeline

`KbIngestionJob` persists queue/run/failure lifecycle plus step visibility.

Pipeline sequence inside `IngestionOrchestrator.runPipeline()`:

1. **FETCH**
   - URL jobs call `UrlPageParser.fetchAndParse()` and store a `KbSourceFetchSnapshot`
   - file jobs load bytes from temp-file `storageUri`
2. **PARSE**
   - file jobs call `TikaDocumentParser.parse()`
   - parser metadata is written back to `KbSourceVersion`
3. **CHUNK**
   - parsed sections are split by `TextChunker`
4. **EMBED**
   - chunk records are converted into Spring AI `Document`
5. **INDEX**
   - `vectorStore.add(documents)` writes all chunks to pgvector-backed store
6. **FINALIZE**
   - job becomes `SUCCEEDED`
   - version `indexStatus` becomes `INDEXED`

On any exception:
- job status becomes `FAILED`
- `errorMessage` is stored
- `finishedAt` is set

### Metadata Written to pgvector

Each indexed chunk carries:

- `sourceId`
- `sourceVersionId`
- `sourceType`
- `trusted = "false"`
- `active = "false"`
- `approvalState`
- `origin`
- `locationType`
- `pageNumber`
- `sectionRef`
- `contentHash`
- `processingVersion`
- `chunkOrdinal`

This enforces the Phase 1 rule that newly indexed content is not retrievable as trusted/active until explicit approval flow changes it later.

### REST Endpoints

| Method | Path | Behavior |
|---|---|---|
| POST | `/api/v1/admin/sources/upload` | Accepts multipart file + metadata, returns 202 with sourceId/jobId |
| POST | `/api/v1/admin/sources/url` | Accepts URL request, returns 202 with sourceId/jobId |
| GET | `/api/v1/admin/ingestion/jobs` | Lists jobs, optional status filter |
| GET | `/api/v1/admin/ingestion/jobs/{jobId}` | Returns one job |
| POST | `/api/v1/admin/ingestion/jobs/{jobId}/retry` | Retries failed job, returns 202 |
| POST | `/api/v1/admin/ingestion/jobs/{jobId}/cancel` | Cancels queued/running job |

### SSRF Validation Approach

`UrlPageParser.validateHost()` rejects:
- non-HTTP/HTTPS schemes
- missing/invalid hosts
- loopback addresses
- link-local addresses
- site-local/private addresses
- `169.254.0.0/16`

Failures raise `AppException(URL_NOT_ALLOWED, ...)` before any DB write for URL ingestion.

### Tests and Verification

`IngestionServiceTest` covers 7 service contracts:
1. upload creates QUEUED FILE_UPLOAD job
2. upload creates source version with mime type and file name
3. upload returns sourceId/jobId
4. URL submit creates URL_IMPORT job
5. private-IP URL raises `URL_NOT_ALLOWED` before job creation
6. unknown jobId raises `JOB_NOT_FOUND`
7. listJobs returns paged results

Verification completed:
- `./gradlew test --tests "com.vn.traffic.chatbot.ingestion.service.IngestionServiceTest"`
- `./gradlew compileJava`

## Deviations from Plan

- `UrlPageParser.fetchAndParse()` currently returns parsed page content and uses a fixed parser version constant instead of persisting the fetched final URL into the returned record; the fetch snapshot stores a basic `httpStatus=200` entry for Phase 1 visibility.
- Task 2 implementation existed in the interrupted worktree but had not yet been committed or summarized; it was validated and completed during orchestration recovery.

## Commits

| Hash | Type | Description |
|------|------|-------------|
| 8094b81 | feat | ingestion entities, parsers, chunker, and Liquibase indexes |
| pending | feat/docs | orchestrator, service, controller, tests, and summary |

## Self-Check: PASSED
