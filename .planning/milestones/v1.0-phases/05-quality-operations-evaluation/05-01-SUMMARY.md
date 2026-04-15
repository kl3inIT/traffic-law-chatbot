---
phase: 05-quality-operations-evaluation
plan: 01
subsystem: backend
tags: [chat-log, check-engine, domain-entities, liquibase, jpa, api]
dependency_graph:
  requires: []
  provides:
    - ChatLog JPA entity + repository + service
    - ChatLogAdminController REST API
    - CheckDef, CheckRun, CheckResult domain entities
    - AllowedModel enum
    - AiParameterSet chatModel/evaluatorModel extension
    - Liquibase migrations 008-011
    - ApiPaths constants for Phase 5 endpoints
    - GroundingStatus top-level enum (confirmed existing)
    - ChatService chat log hook (try/catch side-effect)
  affects:
    - ChatService (retrofit with ChatLogService injection and chatResponse() usage)
    - AiParameterSet (added chatModel, evaluatorModel columns)
    - ApiPaths (9 new constants added)
    - db.changelog-master.xml (4 new includes)
tech_stack:
  added:
    - JpaSpecificationExecutor for combined filtering on ChatLogRepository
    - SpringDataJacksonConfiguration.pageModule() for PageImpl serialization in tests
  patterns:
    - Side-effect log hook pattern (try/catch wrapping chatLogService.save() after chat compose)
    - JPA Specification pattern for optional filter predicates
    - Wave 0 disabled stub tests enabling incremental TDD
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/chatlog/domain/ChatLog.java
    - src/main/java/com/vn/traffic/chatbot/chatlog/repo/ChatLogRepository.java
    - src/main/java/com/vn/traffic/chatbot/chatlog/service/ChatLogService.java
    - src/main/java/com/vn/traffic/chatbot/chatlog/api/ChatLogAdminController.java
    - src/main/java/com/vn/traffic/chatbot/chatlog/api/dto/ChatLogResponse.java
    - src/main/java/com/vn/traffic/chatbot/chatlog/api/dto/ChatLogDetailResponse.java
    - src/main/java/com/vn/traffic/chatbot/checks/domain/CheckRunStatus.java
    - src/main/java/com/vn/traffic/chatbot/checks/domain/CheckDef.java
    - src/main/java/com/vn/traffic/chatbot/checks/domain/CheckRun.java
    - src/main/java/com/vn/traffic/chatbot/checks/domain/CheckResult.java
    - src/main/java/com/vn/traffic/chatbot/parameter/domain/AllowedModel.java
    - src/main/resources/db/changelog/008-chat-log-schema.xml
    - src/main/resources/db/changelog/009-check-def-schema.xml
    - src/main/resources/db/changelog/010-check-run-schema.xml
    - src/main/resources/db/changelog/011-ai-parameter-set-model-columns.xml
    - src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogControllerTest.java
    - src/test/java/com/vn/traffic/chatbot/checks/CheckDefServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/checks/CheckRunnerTest.java
    - src/test/java/com/vn/traffic/chatbot/checks/LlmSemanticEvaluatorTest.java
    - src/test/java/com/vn/traffic/chatbot/checks/CheckRunControllerTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    - src/main/java/com/vn/traffic/chatbot/parameter/domain/AiParameterSet.java
    - src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java
    - src/main/resources/db/changelog/db.changelog-master.xml
    - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java
decisions:
  - Use JpaSpecificationExecutor for ChatLogRepository to enable combined optional filter predicates (groundingStatus + dateRange + questionLike)
  - Log hook uses try/catch wrapping at ChatService level so log failure never breaks the chat response
  - Use Spring AI Usage.getCompletionTokens() (not getGenerationTokens()) — confirmed via javap on spring-ai-model-2.0.0-M4.jar
  - ChatLogAdminController is append-only (no DELETE) per D-04 threat model disposition
  - Wave 0 test stubs use @Disabled pattern to keep test suite green during incremental Phase 5 delivery
metrics:
  duration: "~90 minutes"
  completed_date: "2026-04-12"
  tasks: 3
  files_created: 21
  files_modified: 6
---

# Phase 05 Plan 01: Phase 5 Foundation — Chat Log, Domain Entities, Migrations Summary

**One-liner:** Chat log persistence hook with JPA entities (ChatLog, CheckDef, CheckRun, CheckResult), Liquibase migrations 008-011, AllowedModel enum, AiParameterSet model columns, and ApiPaths constants for all Phase 5 admin endpoints.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Wave 0 — Create all Phase 5 test stub files | `8b43377` | 6 test stubs created |
| 2 | Domain entities, Liquibase migrations 008-011, AllowedModel, AiParameterSet extension, ApiPaths | `8494015` | 17 files created/modified |
| 3 | ChatService retrofit + ChatLogAdminController REST API | `0b97414` | 6 files created/modified |

## What Was Built

### Task 1: Wave 0 Test Stubs
Six `@Disabled` stub test files created with correct package structure and failing placeholder assertions. All use `@ExtendWith(MockitoExtension.class)`. Stubs provide the test skeleton for:
- `ChatLogServiceTest` — log persistence and failure swallowing
- `ChatLogControllerTest` — GET list with filters and GET by ID
- `CheckDefServiceTest` — CRUD operations for check definitions
- `CheckRunnerTest` — run completion and failure paths
- `LlmSemanticEvaluatorTest` — score parsing and failure handling
- `CheckRunControllerTest` — trigger endpoint and results retrieval

### Task 2: Domain Entities and Migrations
- `GroundingStatus` — confirmed already extracted at `chat/service/GroundingStatus.java`
- `AllowedModel` enum — 3 Claude model entries with `modelId` and `displayName`
- `AiParameterSet` — extended with nullable `chatModel` and `evaluatorModel` columns
- `ChatLog` entity — `chat_log` table with 3 indexes (created_date, grounding_status, conversation_id)
- `ChatLogRepository` — extends `JpaRepository<ChatLog, UUID>` + `JpaSpecificationExecutor<ChatLog>`
- `ChatLogService` — `save()`, `findAll()`, `findById()`, `findFiltered()` with optional predicates via JPA Specifications
- `ChatLogResponse` / `ChatLogDetailResponse` — DTOs with static `fromEntity()` factories; list view truncates question at 200 chars
- `CheckRunStatus` enum — RUNNING, COMPLETED, FAILED
- `CheckDef` entity — `check_def` table with question, reference_answer, category, active
- `CheckRun` entity — `check_run` table with averageScore, parameterSetId snapshot, status
- `CheckResult` entity — `check_result` table with ManyToOne to CheckDef and CheckRun + 2 indexes
- Liquibase 008-011 — createTable statements with proper column types, indexes, and FK constraints
- `db.changelog-master.xml` — includes 008-011 after existing 007
- `ApiPaths` — 9 new constants: CHAT_LOGS, CHAT_LOG_BY_ID, CHECK_DEFS, CHECK_DEF_BY_ID, CHECK_RUNS, CHECK_RUN_BY_ID, CHECK_RUNS_TRIGGER, CHECK_RUN_RESULTS, ALLOWED_MODELS

### Task 3: ChatService Retrofit + ChatLogAdminController
- `ChatService` — injected `ChatLogService` via `@RequiredArgsConstructor`; changed `.call().content()` to `.call().chatResponse()` to capture `Usage` metadata; added `startTime` capture; extracts `promptTokens`, `completionTokens` (via `getCompletionTokens()`), `responseTime`; try/catch log hook after `answerComposer.compose()` and in refusal path
- `ChatLogAdminController` — standalone `@RestController` with GET list (pageable, optional filters) and GET by ID endpoints; no DELETE (append-only)
- `ChatLogServiceTest` — fully implemented: ArgumentCaptor verifies field values; RuntimeException path verifies repo exception propagates (for ChatService try/catch)
- `ChatLogControllerTest` — fully implemented: uses `SpringDataJacksonConfiguration.pageModule()` for PageImpl serialization; verifies response JSON fields

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Usage API method name mismatch**
- **Found during:** Task 3 compilation
- **Issue:** Plan specified `usage.getGenerationTokens()` but Spring AI 2.0.0-M4 `Usage` interface has `getCompletionTokens()` (verified via `javap` on spring-ai-model-2.0.0-M4.jar)
- **Fix:** Changed to `usage.getCompletionTokens()` in ChatService
- **Files modified:** `ChatService.java`
- **Commit:** `0b97414`

**2. [Rule 1 - Bug] ChatServiceTest constructor mismatch**
- **Found during:** Task 3 test compilation
- **Issue:** Existing `ChatServiceTest` constructed `ChatService` with 9 arguments; adding `ChatLogService` made it 10 arguments and broke compilation
- **Fix:** Added `@Mock ChatLogService chatLogService` to test class; updated `setUp()` to pass as 10th constructor arg; replaced `.call().content()` stubs with `stubChatResponse()` helper using real `AssistantMessage` + `Generation` objects; changed to `callResponseSpec.chatResponse()` stub
- **Files modified:** `ChatServiceTest.java`
- **Commit:** `0b97414`

**3. [Rule 1 - Bug] ChatFlowIntegrationTest missing ChatLogService bean**
- **Found during:** Task 3 full test run
- **Issue:** `ChatFlowIntegrationTest` uses `AnnotationConfigApplicationContext` and manually registers beans; `ChatService` now requires `ChatLogService` which was not registered
- **Fix:** Added `context.registerBean(ChatLogService.class, () -> org.mockito.Mockito.mock(ChatLogService.class))` before `context.refresh()`
- **Files modified:** `ChatFlowIntegrationTest.java`
- **Commit:** `0b97414`

**4. [Rule 1 - Bug] ChatLogControllerTest PageImpl serialization failure**
- **Found during:** Task 3 test execution
- **Issue:** `MockMvc` test returning `PageImpl<ChatLogResponse>` failed with HTTP 500 due to `HttpMessageNotWritableException: UnsupportedOperationException` — Jackson 2 `ObjectMapper` cannot serialize `PageImpl` without Spring Data's `PageModule`
- **Fix:** Registered `new SpringDataJacksonConfiguration().pageModule()` on the test `ObjectMapper` in `setUp()`
- **Files modified:** `ChatLogControllerTest.java`
- **Commit:** `0b97414`

**5. [Worktree base-fix deviation] Planning artifacts deleted by git reset**
- **Found during:** Initial worktree base alignment
- **Issue:** `git reset --soft 3564226` to fix worktree base caused planning files to appear as staged deletions (the worktree was created from `b06ee4d`, a merge point that predates the planning files in `3564226`)
- **Fix:** Restored all `.planning/phases/05-quality-operations-evaluation/` files via `git checkout 3564226 -- .planning/`
- **Commit:** `cf1e8ab`

## Pre-existing Infrastructure Failures (Not Caused by This Plan)

The full `./gradlew test` suite reports 8 failures from 4 test classes:
- `TrafficLawChatbotApplicationTests` — `@SpringBootTest` without exclusions, fails because PostgreSQL is not running on localhost:5432
- `SpringBootSmokeTest` — `@SpringBootTest` with JPA/Liquibase auto-config exclusions, still fails because Postgres is unavailable
- `LoggingAspectTest` — cascades from same Spring context failure
- `AppPropertiesTest` — cascades from same Spring context failure

These tests require a live PostgreSQL instance and are unrelated to Phase 5 changes. The same failures existed at the base commit `3564226`.

All 159 non-infrastructure tests pass (147 pass + 10 skipped Wave 0 stubs + 2 new ChatLog tests).

## Known Stubs

The following Wave 0 stubs remain `@Disabled` (intentional — to be enabled in subsequent Phase 5 plans):
- `CheckDefServiceTest` — 4 methods (enabled in 05-02)
- `CheckRunnerTest` — 2 methods (enabled in 05-02)
- `LlmSemanticEvaluatorTest` — 2 methods (enabled in 05-02)
- `CheckRunControllerTest` — 2 methods (enabled in 05-02)

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: information_disclosure | `ChatLogAdminController.java` | New unauthenticated admin endpoint exposes full question/answer history; accepted per T-05-01 (admin-only network boundary, auth deferred to v2) |

## Self-Check: PASSED

Checked:
- `src/main/java/com/vn/traffic/chatbot/chatlog/domain/ChatLog.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/chatlog/api/ChatLogAdminController.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/checks/domain/CheckResult.java` — FOUND
- `src/main/resources/db/changelog/008-chat-log-schema.xml` — FOUND
- `src/main/resources/db/changelog/011-ai-parameter-set-model-columns.xml` — FOUND
- Commit `8b43377` — FOUND (Wave 0 stubs)
- Commit `8494015` — FOUND (domain entities + migrations)
- Commit `0b97414` — FOUND (ChatService retrofit + ChatLogAdminController)
