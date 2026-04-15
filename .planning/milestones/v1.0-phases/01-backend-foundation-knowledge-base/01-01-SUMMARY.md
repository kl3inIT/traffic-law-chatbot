---
phase: 01-backend-foundation-knowledge-base
plan: 01
subsystem: backend-common
tags: [spring-boot, liquibase, pgvector, async, rest-conventions]
dependency_graph:
  requires: []
  provides:
    - Liquibase-managed PostgreSQL schema (5 kb_* tables + pgvector extension)
    - Spring AI PgVectorStore bean (kb_vector_store, HNSW, 1536 dims)
    - Async task executor (ingestionExecutor)
    - REST path constants (ApiPaths)
    - Shared page DTO (PageResponse)
    - Centralized error handling (GlobalExceptionHandler)
  affects: [all subsequent plans in phase 01]
tech_stack:
  added:
    - liquibase-core (5.0.2 via Boot BOM)
    - org.apache.tika:tika-core:3.3.0
    - org.jsoup:jsoup:1.19.1
    - spring-boot-starter-validation (Boot-managed)
    - Spring AI BOM 2.0.0-M4 (added to dependencyManagement)
    - Spring milestone/snapshot repositories
  patterns:
    - Liquibase XML changeset migrations (no auto-DDL)
    - ProblemDetail error responses (RFC 9457)
    - ThreadPoolTaskExecutor for async ingestion
    - PgVectorStore.builder() API
key_files:
  created:
    - build.gradle (updated: milestone repos, Spring AI BOM, 4 new deps)
    - src/main/resources/application.yaml
    - src/main/resources/db/changelog/db.changelog-master.xml
    - src/main/resources/db/changelog/001-schema-foundation.xml
    - src/main/java/com/vn/traffic/chatbot/common/config/AsyncConfig.java
    - src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java
    - src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java
    - src/main/java/com/vn/traffic/chatbot/common/api/PageResponse.java
    - src/main/java/com/vn/traffic/chatbot/common/error/ErrorCode.java
    - src/main/java/com/vn/traffic/chatbot/common/error/AppException.java
    - src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java
    - src/test/java/com/vn/traffic/chatbot/common/SpringBootSmokeTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/TrafficLawChatbotApplication.java (added @EnableAsync)
decisions:
  - "Added Spring milestone + snapshot repositories to resolve Spring AI 2.0.0-M4 artifacts"
  - "Added Spring AI BOM to dependencyManagement — artifacts lacked version declarations"
  - "VectorStore targets table kb_vector_store with COSINE_DISTANCE, HNSW index, 1536 dimensions"
  - "Liquibase owns all DDL; spring.ai.vectorstore.pgvector.initialize-schema=false prevents Spring AI DDL"
  - "Smoke test excludes Liquibase, DataSource, JPA auto-configuration to avoid real DB dependency"
metrics:
  duration: ~25 minutes
  completed: 2026-04-08
  tasks_completed: 2
  files_created: 12
  files_modified: 1
---

# Phase 01 Plan 01: Backend Foundation & Knowledge Base Summary

Established the Java 25 Spring Boot skeleton with Liquibase-managed PostgreSQL + pgvector schema, shared REST conventions (ApiPaths, PageResponse, GlobalExceptionHandler), async task executor, and PgVectorStore bean — all infrastructure required by subsequent knowledge-base modules.

## Tasks Completed

| Task | Name | Commit |
|------|------|--------|
| 1 | Add dependencies, application.yaml, and Liquibase schema migrations | ecdf0fd |
| 2 | Wire shared REST conventions, async executor, VectorStore bean, and smoke test | b2beddc |

## Dependencies Added

| Artifact | Version | Purpose |
|----------|---------|--------|
| `org.liquibase:liquibase-core` | 5.0.2 (Boot-managed) | Explicit schema migration control |
| `org.apache.tika:tika-core` | 3.3.0 | Document parsing facade for PDF/Word/HTML |
| `org.jsoup:jsoup` | 1.19.1 | HTML extraction for URL-based ingestion |
| `spring-boot-starter-validation` | Boot-managed | Request validation + ProblemDetail errors |

## Tables Created by Migration

`001-schema-foundation.xml` creates the following in a single changeSet (id=`001`, author=`system`):

| Table | Purpose |
|-------|---------|
| `kb_source` | Trusted knowledge source registry with status/trust/approval lifecycle |
| `kb_source_version` | Versioned ingestion snapshot per source with content hash and processing provenance |
| `kb_ingestion_job` | Async ingestion job tracking with status, retry, step detail |
| `kb_source_approval_event` | Approval audit trail (action, previous/new state, actor) |
| `kb_source_fetch_snapshot` | HTTP fetch provenance per version (URL, ETag, SHA256) |

Also enables the pgvector extension via `CREATE EXTENSION IF NOT EXISTS vector;`.

Indexes created:
- `idx_kb_source_status_approval_trust` on `kb_source(status, approval_state, trusted_state)`
- `idx_kb_ingestion_job_source_status` on `kb_ingestion_job(source_id, status)`
- `idx_kb_source_version_source` on `kb_source_version(source_id)`

## ApiPaths Constants Defined

All constants in `com.vn.traffic.chatbot.common.api.ApiPaths`:

- `ADMIN_BASE` = `/api/v1/admin`
- `SOURCES`, `SOURCES_UPLOAD`, `SOURCES_URL`, `SOURCE_BY_ID`
- `SOURCE_APPROVE`, `SOURCE_REJECT`, `SOURCE_ACTIVATE`, `SOURCE_DEACTIVATE`, `SOURCE_REINGEST`
- `INGESTION_JOBS`, `JOB_BY_ID`, `JOB_RETRY`, `JOB_CANCEL`
- `CHUNKS`, `CHUNK_BY_ID`, `INDEX_SUMMARY`

## VectorStore Configuration

| Setting | Value | Reason |
|---------|-------|--------|
| Table name | `kb_vector_store` | Namespace-prefixed to avoid collisions |
| Dimensions | 1536 | OpenAI text-embedding-ada-002 / text-embedding-3-small output size |
| Distance type | `COSINE_DISTANCE` | Standard for semantic similarity with normalized embeddings |
| Index type | `HNSW` | Best recall/speed trade-off for OLTP-scale vector search |
| Initialize schema | `false` | Liquibase owns all DDL |
| Schema name | `public` | Default PostgreSQL schema |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added Spring milestone repository and Spring AI BOM**
- **Found during:** Task 1 pre-flight dependency resolution
- **Issue:** Spring AI 2.0.0-M4 artifacts could not be resolved — `build.gradle` lacked the Spring milestone Maven repository and the Spring AI BOM was not declared in `dependencyManagement`. All six Spring AI starters showed as `FAILED` in the dependency tree.
- **Fix:** Added `https://repo.spring.io/milestone` and `https://repo.spring.io/snapshot` to the `repositories` block; added `mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}"` to `dependencyManagement`.
- **Files modified:** `build.gradle`
- **Commit:** ecdf0fd

## Known Stubs

None. All files created are infrastructure — no data flows to UI rendering in this plan.

## Threat Surface Review

All mitigations from the plan's threat model were applied:
- T-01-01: DB credentials and OpenAI key read from environment variables only (`${VAR:default}` pattern in application.yaml).
- T-01-02: Liquibase checksums enforced — no manual schema bypasses.
- T-01-03: `initialize-schema: false` prevents Spring AI from silently creating the vector table.
- T-01-04: `ThreadPoolTaskExecutor` bounded to max 10 threads and queue capacity 100.

No new threat surface introduced beyond the plan's scope.

## Self-Check: PASSED

Files verified present:
- `build.gradle` — FOUND
- `src/main/resources/application.yaml` — FOUND
- `src/main/resources/db/changelog/db.changelog-master.xml` — FOUND
- `src/main/resources/db/changelog/001-schema-foundation.xml` — FOUND
- `src/main/java/com/vn/traffic/chatbot/common/config/AsyncConfig.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/common/api/PageResponse.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/common/error/ErrorCode.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/common/error/AppException.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java` — FOUND
- `src/test/java/com/vn/traffic/chatbot/common/SpringBootSmokeTest.java` — FOUND

Commits verified present:
- `ecdf0fd` — FOUND
- `b2beddc` — FOUND

Build status: `./gradlew compileJava compileTestJava` — BUILD SUCCESSFUL
