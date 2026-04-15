---
phase: 01-backend-foundation-knowledge-base
plan: 02
subsystem: source
tags: [source-registry, jpa, rest, approval-lifecycle, provenance]
dependency_graph:
  requires: []
  provides:
    - KbSource JPA entity with full approval/trust lifecycle
    - KbSourceVersion JPA entity with processingVersion and jsonb summaryJson
    - KbSourceApprovalEvent JPA entity for audit trail
    - SourceService with createSource/approve/reject/activate/deactivate/getById/list
    - SourceAdminController REST endpoints under /api/v1/admin/sources
    - Common infrastructure: ApiPaths, PageResponse, AppException, ErrorCode
  affects:
    - Plan 01 (common classes overlap — both plans define ApiPaths/PageResponse/AppException/ErrorCode in Wave 1)
    - Plan 03 (ingestion plan attaches jobs and versions to KbSource)
tech_stack:
  added:
    - spring-boot-starter-validation (for @NotNull/@NotBlank on DTOs)
    - Spring AI BOM import in dependencyManagement block
    - Spring Milestone/Snapshot repositories in build.gradle
  patterns:
    - JPA entities with @Builder @Data Lombok, @Enumerated(STRING) for all state enums
    - Record DTOs for request/response types
    - @Service @RequiredArgsConstructor @Transactional service layer
    - @RestController @RequestMapping with constructor injection via @RequiredArgsConstructor
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/source/domain/SourceType.java
    - src/main/java/com/vn/traffic/chatbot/source/domain/SourceStatus.java
    - src/main/java/com/vn/traffic/chatbot/source/domain/ApprovalState.java
    - src/main/java/com/vn/traffic/chatbot/source/domain/TrustedState.java
    - src/main/java/com/vn/traffic/chatbot/source/domain/OriginKind.java
    - src/main/java/com/vn/traffic/chatbot/source/domain/KbSource.java
    - src/main/java/com/vn/traffic/chatbot/source/domain/KbSourceVersion.java
    - src/main/java/com/vn/traffic/chatbot/source/domain/KbSourceApprovalEvent.java
    - src/main/java/com/vn/traffic/chatbot/source/repo/KbSourceRepository.java
    - src/main/java/com/vn/traffic/chatbot/source/repo/KbSourceVersionRepository.java
    - src/main/java/com/vn/traffic/chatbot/source/repo/KbSourceApprovalEventRepository.java
    - src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java
    - src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java
    - src/main/java/com/vn/traffic/chatbot/source/api/dto/CreateSourceRequest.java
    - src/main/java/com/vn/traffic/chatbot/source/api/dto/SourceSummaryResponse.java
    - src/main/java/com/vn/traffic/chatbot/source/api/dto/SourceDetailResponse.java
    - src/main/java/com/vn/traffic/chatbot/source/api/dto/ApprovalRequest.java
    - src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java
    - src/main/java/com/vn/traffic/chatbot/common/api/PageResponse.java
    - src/main/java/com/vn/traffic/chatbot/common/error/AppException.java
    - src/main/java/com/vn/traffic/chatbot/common/error/ErrorCode.java
    - src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java
  modified:
    - build.gradle (added Spring AI BOM, milestone repo, spring-boot-starter-validation)
decisions:
  - Use @Builder.Default for enum fields to set DRAFT/PENDING/UNTRUSTED starting state in KbSource
  - Common infrastructure classes (ApiPaths, PageResponse, AppException, ErrorCode) created in this plan as Plan 01 runs in parallel in Wave 1; orchestrator merge resolves any overlap
  - SourceDetailResponse loads versions as empty list at controller level; Plan 03 ingestion will wire the actual version data when versions are created
  - SourceAdminController delegates all mapping to service; toSummary/toDetail mapper methods stay in controller to avoid extra mapper class
metrics:
  completed_date: "2026-04-08"
  tasks_completed: 2
  files_created: 23
  files_modified: 1
---

# Phase 1 Plan 02: Source Registry Domain Summary

**One-liner:** Source registry domain with 5 state enums, 3 JPA entities, full approval/trust lifecycle service, and 7 REST admin endpoints backed by 9 passing unit tests.

## What Was Built

### Enums (5)

| Enum | Values |
|------|--------|
| SourceType | PDF, WORD, STRUCTURED_REGULATION, WEBSITE_PAGE |
| SourceStatus | DRAFT, READY_FOR_REVIEW, ACTIVE, ARCHIVED, DISABLED |
| ApprovalState | PENDING, APPROVED, REJECTED |
| TrustedState | UNTRUSTED, TRUSTED, REVOKED |
| OriginKind | FILE_UPLOAD, URL_IMPORT, SYSTEM_IMPORT |

### JPA Entities (3)

- **KbSource** — maps to `kb_source` table. Carries sourceType, title, originKind, originValue, publisherName, languageCode, status (default DRAFT), trustedState (default UNTRUSTED), approvalState (default PENDING), activeVersionId, audit timestamps, and createdBy/updatedBy.
- **KbSourceVersion** — maps to `kb_source_version` table. Tracks versionNo, contentHash, ingest metadata, processingVersion, indexStatus, and jsonb summaryJson. @ManyToOne to KbSource.
- **KbSourceApprovalEvent** — maps to `kb_source_approval_event` table. Audit record for every state change with action, previousState, newState, reason, actedBy, actedAt.

### Repositories (3)

- **KbSourceRepository** — JpaRepository + findByStatusAndApprovalStateAndTrustedState + findAll(Pageable)
- **KbSourceVersionRepository** — JpaRepository + findBySourceId
- **KbSourceApprovalEventRepository** — JpaRepository + findBySourceIdOrderByActedAtDesc

### Service Method Signatures

```java
public KbSource createSource(CreateSourceRequest req)
public KbSource getById(UUID id)                          // throws SOURCE_NOT_FOUND
public Page<KbSource> list(Pageable pageable)
public KbSource approve(UUID sourceId, ApprovalRequest req)   // requires PENDING
public KbSource reject(UUID sourceId, ApprovalRequest req)    // requires PENDING
public KbSource activate(UUID sourceId, String actedBy)       // requires APPROVED
public KbSource deactivate(UUID sourceId, String actedBy)
```

### Controller Endpoints (7)

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/v1/admin/sources | Create source (returns 201) |
| GET | /api/v1/admin/sources | List sources (paged) |
| GET | /api/v1/admin/sources/{sourceId} | Get source detail |
| POST | /api/v1/admin/sources/{sourceId}/approve | Approve source |
| POST | /api/v1/admin/sources/{sourceId}/reject | Reject source |
| POST | /api/v1/admin/sources/{sourceId}/activate | Activate source |
| POST | /api/v1/admin/sources/{sourceId}/deactivate | Deactivate source |

### Unit Test Results

All 9 behavioral contracts in `SourceServiceTest` pass:

1. createSource returns DRAFT/PENDING/UNTRUSTED defaults
2. createSource captures provenance (sourceType, title, originKind, originValue, createdBy)
3. approve() on PENDING source sets APPROVED + saves approval event with action="APPROVE"
4. approve() on non-PENDING source throws AppException(VALIDATION_ERROR)
5. reject() on PENDING source sets REJECTED + saves approval event with action="REJECT"
6. activate() on APPROVED source sets ACTIVE + TRUSTED
7. activate() on PENDING source throws AppException(VALIDATION_ERROR)
8. deactivate() sets DISABLED
9. getById() on unknown UUID throws AppException(SOURCE_NOT_FOUND)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Spring AI BOM missing from build.gradle**
- **Found during:** Task 1 compile
- **Issue:** build.gradle declared `springAiVersion` ext property but had no `dependencyManagement` BOM import, causing all Spring AI artifacts (pgvector, openai, rag, etc.) to fail resolution with "Could not find" errors.
- **Fix:** Added `dependencyManagement { imports { mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}" } }` block and added Spring Milestone/Snapshot repositories.
- **Files modified:** build.gradle
- **Commit:** a21f4ed

**2. [Rule 3 - Blocking] spring-boot-starter-validation missing from build.gradle**
- **Found during:** Task 2 test compilation
- **Issue:** CreateSourceRequest uses @NotNull/@NotBlank from jakarta.validation.constraints, but the validation starter was not in build.gradle (it is added by Plan 01 which runs in parallel).
- **Fix:** Added `implementation 'org.springframework.boot:spring-boot-starter-validation'` to build.gradle.
- **Files modified:** build.gradle
- **Commit:** dc9beb0

**3. [Rule 2 - Missing critical] Common infrastructure classes created in Plan 02**
- **Found during:** Task 1 (need ApiPaths, PageResponse, AppException, ErrorCode)
- **Issue:** Plan 02 depends on common classes produced by Plan 01 (same Wave 1 parallel run). Since this worktree runs independently, these classes must be created here.
- **Fix:** Created ApiPaths, PageResponse, AppException, ErrorCode in `com.vn.traffic.chatbot.common` packages.
- **Files created:** ApiPaths.java, PageResponse.java, AppException.java, ErrorCode.java
- **Commit:** a21f4ed

### Known Stubs

- **SourceAdminController.getSource** — versions list returned as `Collections.emptyList()`. No ingestion pipeline yet; Plan 03 will create KbSourceVersion records and the controller can be updated to call `versionRepo.findBySourceId(sourceId)` once ingestion is wired.

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes beyond those documented in the plan's threat model. All mitigations from T-02-01 (state transition guards) and T-02-02 (approval event audit log) are implemented and tested.

## Commits

| Hash | Type | Description |
|------|------|-------------|
| a21f4ed | feat | Source domain entities, enums, JPA repositories, and common classes |
| dc9beb0 | test | Failing SourceServiceTest (RED) + DTOs + build.gradle fixes |
| af18de2 | feat | SourceService and SourceAdminController (GREEN — all 9 tests pass) |

## Self-Check: PASSED
