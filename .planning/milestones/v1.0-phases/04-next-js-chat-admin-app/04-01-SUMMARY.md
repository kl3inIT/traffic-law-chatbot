---
phase: 04-next-js-chat-admin-app
plan: "01"
subsystem: backend
tags: [parameter-sets, crud-api, liquibase, chat-threads, clarification]
dependency_graph:
  requires: []
  provides:
    - AiParameterSet CRUD REST API at /api/v1/admin/parameter-sets
    - GET /api/v1/chat/threads thread list endpoint
    - ChatMessageType.CLARIFICATION enum value
    - Default parameter set seeded on empty DB startup
  affects:
    - chat (ChatThreadService, PublicChatController, ChatMessageType)
    - common (ApiPaths, ErrorCode)
tech_stack:
  added:
    - AiParameterSet JPA entity with Liquibase migration (005-ai-parameter-set-schema.xml)
    - DefaultParameterSetSeeder (ApplicationRunner)
    - default-parameter-set.yml resource with full YAML schema
  patterns:
    - Record DTOs for API responses and requests
    - @Transactional service with deactivateAll @Modifying query
    - TDD: unit tests with Mockito, MockMvc controller tests
key_files:
  created:
    - src/main/resources/db/changelog/005-ai-parameter-set-schema.xml
    - src/main/java/com/vn/traffic/chatbot/parameter/domain/AiParameterSet.java
    - src/main/java/com/vn/traffic/chatbot/parameter/repo/AiParameterSetRepository.java
    - src/main/java/com/vn/traffic/chatbot/parameter/service/AiParameterSetService.java
    - src/main/java/com/vn/traffic/chatbot/parameter/service/DefaultParameterSetSeeder.java
    - src/main/resources/default-parameter-set.yml
    - src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java
    - src/main/java/com/vn/traffic/chatbot/parameter/api/dto/AiParameterSetResponse.java
    - src/main/java/com/vn/traffic/chatbot/parameter/api/dto/CreateAiParameterSetRequest.java
    - src/main/java/com/vn/traffic/chatbot/parameter/api/dto/UpdateAiParameterSetRequest.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatThreadSummaryResponse.java
    - src/test/java/com/vn/traffic/chatbot/parameter/service/AiParameterSetServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetControllerTest.java
  modified:
    - src/main/resources/db/changelog/db.changelog-master.xml
    - src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java
    - src/main/java/com/vn/traffic/chatbot/common/error/ErrorCode.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessageType.java
    - src/main/java/com/vn/traffic/chatbot/chat/repo/ChatThreadRepository.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java
    - src/test/java/com/vn/traffic/chatbot/chat/api/ChatThreadControllerTest.java
decisions:
  - "Used GenerationType.UUID for AiParameterSet PK (consistent with Spring 6 best practice)"
  - "Added @Size(max=65536) on content field per threat model T-04-02 to prevent DoS via oversized YAML"
  - "Fixed ChatThreadControllerTest objectMapper to include JavaTimeModule (pre-existing gap exposed by new OffsetDateTime response)"
metrics:
  duration_minutes: 35
  completed_date: "2026-04-11"
  tasks_completed: 3
  files_changed: 21
---

# Phase 4 Plan 01: AiParameterSet Backend Domain and Thread List Endpoint Summary

**One-liner:** Full CRUD REST API for AI parameter sets with Liquibase migration, default YAML seed, activate/copy operations, and GET /api/v1/chat/threads returning first-message previews.

## What Was Built

### Task 1: AiParameterSet entity, migration, repository, service, and seed

- **005-ai-parameter-set-schema.xml** — Liquibase changeSet creating `ai_parameter_set` table with UUID PK, name (VARCHAR 255), active (BOOLEAN DEFAULT FALSE), content (TEXT), created_at and updated_at TIMESTAMPTZ columns, and an index on `active`.
- **AiParameterSet.java** — JPA entity with `@GeneratedValue(strategy = GenerationType.UUID)`, `@Builder.Default active = false`, Hibernate `@CreationTimestamp` and `@UpdateTimestamp`.
- **AiParameterSetRepository.java** — `findByActiveTrue()`, `findAllByOrderByCreatedAtDesc()`, and `@Modifying @Query` `deactivateAll()`.
- **AiParameterSetService.java** — Full service with `create`, `update`, `delete` (throws on active), `activate` (deactivateAll then set target true), `copy` (name + " (ban sao)", active=false), `findAll`, `findById`, `getActive`.
- **default-parameter-set.yml** — Default YAML covering `model`, `retrieval`, `systemPrompt`, `caseAnalysis`, and `messages` keys, written in full Vietnamese with diacritics (UTF-8).
- **DefaultParameterSetSeeder.java** — `ApplicationRunner` that checks `repository.count() == 0` and seeds from `classpath:default-parameter-set.yml` on empty DB.
- Added `PARAMETER_SET_NOT_FOUND(404)` and `NOT_FOUND(404)` to `ErrorCode`.

### Task 2: AiParameterSet REST controller — 7 endpoints

- Added `PARAMETER_SETS`, `PARAMETER_SET_BY_ID`, `PARAMETER_SET_ACTIVATE`, `PARAMETER_SET_COPY` path constants to `ApiPaths.java`.
- **AiParameterSetController.java** — `GET /api/v1/admin/parameter-sets` (list), `GET /{id}` (by id), `POST` (create, 201), `PUT /{id}` (update), `DELETE /{id}` (204), `POST /{id}/activate`, `POST /{id}/copy` (201).
- **AiParameterSetResponse** record: `id, name, active, content, createdAt, updatedAt`.
- **CreateAiParameterSetRequest** and **UpdateAiParameterSetRequest** records with `@NotBlank` name and `@NotBlank @Size(max=65536)` content (T-04-02 mitigated).
- 8 MockMvc tests covering all endpoints including a 404 not-found case.

### Task 3: Thread list GET endpoint and ChatMessageType.CLARIFICATION fix

- Added `CLARIFICATION` to `ChatMessageType` enum.
- Fixed `countClarificationMessages()` in `ChatThreadService` to filter by `ChatMessageType.CLARIFICATION` (previously used `[CLARIFICATION]` string matching against ANSWER-typed messages — broken logic).
- Updated `clarificationResponse()` to persist messages with `ChatMessageType.CLARIFICATION` instead of `ChatMessageType.ANSWER`.
- Created `ChatThreadSummaryResponse` record: `threadId, createdAt, updatedAt, firstMessage`.
- Added `listThreads()` to `ChatThreadService` — returns all threads ordered by `updatedAt` desc, with first USER message truncated to 100 chars.
- Added `findAllByOrderByUpdatedAtDesc()` to `ChatThreadRepository`.
- Added `GET /threads` to `PublicChatController`, returning `List<ChatThreadSummaryResponse>`.
- Added `listThreadsReturns200WithJsonArrayOfSummaries` test to `ChatThreadControllerTest`.

## Commits

| Task | Hash | Message |
|------|------|---------|
| Task 1 | f21f765 | feat(04-01): AiParameterSet entity, Liquibase migration, repository, CRUD service, and default seed |
| Task 2 | 8b9268b | feat(04-01): AiParameterSet REST controller with full CRUD + activate + copy endpoints |
| Task 3 | 54f4ae1 | feat(04-01): thread list GET endpoint and ChatMessageType.CLARIFICATION fix |

## Verification

All tests pass on `./gradlew test`:
- `AiParameterSetServiceTest` — 5 tests (create, activate, copy, delete-guard, findAll)
- `AiParameterSetControllerTest` — 8 tests (all 7 endpoints + 404 case)
- `ChatThreadControllerTest` — 6 tests (existing 5 + new listThreads test)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed ChatMessageType.CLARIFICATION counting using string matching**
- **Found during:** Task 3
- **Issue:** `countClarificationMessages()` was filtering by `ChatMessageType.ANSWER` and then checking string content for `[CLARIFICATION]` — this would never correctly count clarification messages since clarification messages were stored as ANSWER type.
- **Fix:** Added `CLARIFICATION` to the enum, updated `clarificationResponse()` to persist with `CLARIFICATION` type, and updated `countClarificationMessages()` to filter by `CLARIFICATION` type directly.
- **Files modified:** `ChatMessageType.java`, `ChatThreadService.java`
- **Commit:** 54f4ae1

**2. [Rule 1 - Bug] Fixed ChatThreadControllerTest ObjectMapper missing JavaTimeModule**
- **Found during:** Task 3 test run
- **Issue:** Existing `ChatThreadControllerTest` used a bare `new ObjectMapper()` without `JavaTimeModule`. Adding `ChatThreadSummaryResponse` (which has `OffsetDateTime` fields) to the response caused HTTP 500 in MockMvc due to serialization failure.
- **Fix:** Added `JavaTimeModule` import and `.registerModule(new JavaTimeModule())` to the objectMapper initialization.
- **Files modified:** `ChatThreadControllerTest.java`
- **Commit:** 54f4ae1

## Known Stubs

None — all endpoints are fully implemented with real service delegation.

## Threat Flags

No new trust boundaries introduced beyond what the plan's threat model already captured. The `@Size(max=65536)` constraint on content fields mitigates T-04-02 (DoS via oversized YAML content).

## Self-Check

**Checking created files exist:**
- `src/main/resources/db/changelog/005-ai-parameter-set-schema.xml` — FOUND
- `src/main/java/com/vn/traffic/chatbot/parameter/domain/AiParameterSet.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java` — FOUND
- `src/main/resources/default-parameter-set.yml` — FOUND
- `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatThreadSummaryResponse.java` — FOUND

**Checking commits exist:**
- f21f765 — FOUND
- 8b9268b — FOUND
- 54f4ae1 — FOUND

## Self-Check: PASSED
