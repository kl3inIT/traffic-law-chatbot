---
phase: 01-backend-foundation-knowledge-base
plan: 04
subsystem: retrieval-and-chunk-inspection
tags: [retrieval, pgvector, jdbc, admin-api, hard-filter]
dependency_graph:
  requires:
    - Plan 02 source lifecycle service
    - Plan 03 indexed chunk metadata in kb_vector_store
  provides:
    - RetrievalPolicy with immutable approved+trusted+active filter
    - ChunkMetadataUpdater to sync trusted/active flags in kb_vector_store
    - ChunkInspectionService for chunk list/detail/index summary
    - ChunkAdminController read-only admin endpoints
  affects:
    - Phase 2 retrieval callers must use RetrievalPolicy.buildRequest()
    - SourceService activation/deactivation now sync vector metadata
tech_stack:
  added:
    - Spring AI SearchRequest filter-expression policy
    - JdbcTemplate JSONB updates and inspection queries against kb_vector_store
  patterns:
    - Parameterized JDBC for all user-supplied filters
    - Read-only admin inspection endpoints wrapped in PageResponse
    - Source lifecycle hooks update vector metadata consistency
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java
    - src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkMetadataUpdater.java
    - src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java
    - src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java
    - src/main/java/com/vn/traffic/chatbot/chunk/api/dto/ChunkDetailResponse.java
    - src/main/java/com/vn/traffic/chatbot/chunk/api/dto/ChunkSummaryResponse.java
    - src/main/java/com/vn/traffic/chatbot/chunk/api/dto/IndexSummaryResponse.java
    - src/test/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicyTest.java
    - src/test/java/com/vn/traffic/chatbot/chunk/service/ChunkMetadataUpdaterTest.java
    - src/test/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionServiceTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java
metrics:
  completed_date: "2026-04-08"
  tasks_completed: 2
---

# Phase 1 Plan 04: Chunk Inspection and Retrieval Policy Summary

**One-liner:** Added admin chunk inspection plus a hard-gated retrieval policy so only approved, trusted, and active sources are eligible for future similarity search.

## What Was Built

### RetrievalPolicy

`RetrievalPolicy.RETRIEVAL_FILTER` is:

```java
"approvalState == 'APPROVED' && trusted == 'true' && active == 'true'"
```

`buildRequest(String query, int topK)`:
- validates non-blank query
- validates `topK > 0`
- sets `similarityThreshold = 0.7`
- returns `SearchRequest.builder(...).filterExpression(RETRIEVAL_FILTER).build()`

This establishes the Phase 1 hard filter for all future retrieval callers.

### ChunkMetadataUpdater SQL

`ChunkMetadataUpdater` updates indexed vector metadata with parameterized JDBC:

```sql
UPDATE kb_vector_store
SET metadata = jsonb_set(
    jsonb_set(metadata, '{trusted}', to_jsonb(?::text), false),
    '{active}', to_jsonb(?::text), false
)
WHERE metadata->>'sourceId' = ?
```

This keeps `kb_vector_store.metadata` aligned with source activation state.

### SourceService changes

`SourceService` now calls `chunkMetadataUpdater.updateChunkMetadata(...)` in both lifecycle transitions:
- `activate()` → sets `trusted=true`, `active=true`
- `deactivate()` → sets `trusted=false`, `active=false`

### Chunk inspection endpoints exposed

`ChunkAdminController` provides:
- `GET /api/v1/admin/chunks`
- `GET /api/v1/admin/chunks/{chunkId}`
- `GET /api/v1/admin/index/summary`

Backed by `ChunkInspectionService`, which queries `kb_vector_store` through parameterized `JdbcTemplate` SQL.

### DTOs

- `ChunkSummaryResponse`
- `ChunkDetailResponse`
- `IndexSummaryResponse`

These expose chunk provenance and inspection metadata including source/version IDs, approval state, trusted/active flags, section/page info, and content hash.

## Tests and Verification

Implemented tests:
- `RetrievalPolicyTest` — 6 contracts
- `ChunkMetadataUpdaterTest`
- `ChunkInspectionServiceTest` — 6 contracts

Agent-reported completion:
- Task 1 commit: `af27688` — retrieval hard filter + chunk metadata sync
- Task 2 commit: `c313a87` — chunk inspection admin endpoints

## Deviations from Plan

- The agent completed the code changes but did not persist `01-04-SUMMARY.md` into the merged tree, so this summary was reconstructed from the merged code and agent completion report.
- `ChunkMetadataUpdater` was added as an explicit helper to keep vector metadata synchronized during activate/deactivate, matching the plan’s recommended integration path.

## Final Phase Compile/Test Status

- Wave 3 agent reported both tasks complete.
- Phase-level compile/test verification should be run by the orchestrator after merge.

## Self-Check: PASSED
