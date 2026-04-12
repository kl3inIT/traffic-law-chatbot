---
phase: 05-quality-operations-evaluation
plan: 02
subsystem: backend
tags: [check-engine, llm-as-judge, async, rest, tdd, semantic-evaluation]
dependency_graph:
  requires:
    - 05-01-PLAN.md (CheckDef, CheckRun, CheckResult entities, ApiPaths constants)
  provides:
    - CheckDefRepository, CheckRunRepository, CheckResultRepository JPA repos
    - CheckDefService with CRUD + findActive
    - CheckDefAdminController REST (GET, POST, PUT, DELETE)
    - CheckRunService with trigger() + findAll + findById + findResults
    - CheckRunner with @Async("ingestionExecutor") runAll — per-def chat+eval, COMPLETED/FAILED
    - SemanticEvaluator interface
    - LlmSemanticEvaluator with Vietnamese scoring prompt, LANGUAGE_MISMATCH_CAP
    - CheckRunAdminController REST (POST trigger 202, GET list, GET by id, GET results)
    - AllowedModels endpoint on AiParameterSetController
    - DTOs: CheckDefRequest/Response, CheckRunResponse, CheckRunDetailResponse, CheckResultResponse
  affects:
    - AiParameterSetController (added GET /allowed-models endpoint + AllowedModelResponse record)
tech_stack:
  added: []
  patterns:
    - "@Async(\"ingestionExecutor\") fire-and-forget pattern for check run execution"
    - LLM-as-judge evaluation with Vietnamese-first scoring system prompt
    - JSON extraction via regex DOTALL pattern + Jackson parse for robust score parsing
    - Language mismatch cap (0.2) on LLM evaluator score
    - 0.0-on-failure contract for SemanticEvaluator (never throws)
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/checks/repo/CheckDefRepository.java
    - src/main/java/com/vn/traffic/chatbot/checks/repo/CheckRunRepository.java
    - src/main/java/com/vn/traffic/chatbot/checks/repo/CheckResultRepository.java
    - src/main/java/com/vn/traffic/chatbot/checks/service/CheckDefService.java
    - src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunService.java
    - src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java
    - src/main/java/com/vn/traffic/chatbot/checks/evaluator/SemanticEvaluator.java
    - src/main/java/com/vn/traffic/chatbot/checks/evaluator/LlmSemanticEvaluator.java
    - src/main/java/com/vn/traffic/chatbot/checks/api/CheckDefAdminController.java
    - src/main/java/com/vn/traffic/chatbot/checks/api/CheckRunAdminController.java
    - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckDefRequest.java
    - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckDefResponse.java
    - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckRunResponse.java
    - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckRunDetailResponse.java
    - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckResultResponse.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java
    - src/test/java/com/vn/traffic/chatbot/checks/CheckDefServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/checks/CheckRunnerTest.java
    - src/test/java/com/vn/traffic/chatbot/checks/CheckRunControllerTest.java
    - src/test/java/com/vn/traffic/chatbot/checks/LlmSemanticEvaluatorTest.java
decisions:
  - Use public modifier on LlmSemanticEvaluator.parseScore() to allow cross-package test access (test is in com.vn.traffic.chatbot.checks, evaluator in .checks.evaluator)
  - Use ChatClient.ChatClientRequestSpec and CallResponseSpec (not inner ChatClientRequest types) for Spring AI mock chains in tests — consistent with ChatServiceTest pattern
  - ChatAnswerResponse is a record — use .answer() accessor not .getAnswer() in CheckRunner.runSingle()
  - AiParameterSetService uses getActive() not findActive() — verified before writing CheckRunService
metrics:
  duration: "~60 minutes"
  completed_date: "2026-04-12"
  tasks: 3
  files_created: 15
  files_modified: 5
---

# Phase 05 Plan 02: Check Engine — CRUD, Async Runner, LLM Evaluator Summary

**One-liner:** Full check engine backend: CheckDef CRUD REST + async CheckRunner with LLM-as-judge SemanticEvaluator using Vietnamese scoring prompt, CheckRun REST (202 trigger + history + results), and AllowedModel endpoint.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | CheckDef CRUD — repos, service, controller, DTOs + AllowedModels | `6e162af` | 12 files created/modified |
| 2 | Async CheckRunner + CheckRunService + CheckRunAdminController | `e42dbe2` | 6 files created/modified |
| 3 | LLM-as-judge SemanticEvaluator | `3062e66` | 2 files created/modified |

## What Was Built

### Task 1: CheckDef CRUD + Repositories + AllowedModels

- `CheckDefRepository` — `findByActiveTrue()` + `findAllByOrderByCreatedAtDesc()`
- `CheckRunRepository` — `findAllByOrderByCreatedDateDesc()`
- `CheckResultRepository` — `findByCheckRunId(UUID checkRunId)`
- `CheckDefService` — `create()`, `update()`, `delete()` (throws NOT_FOUND AppException), `findAll()`, `findActive()`, `findById()`
- `CheckDefAdminController` — GET list, POST create (201), PUT update, DELETE (204) targeting `ApiPaths.CHECK_DEFS / CHECK_DEF_BY_ID`
- DTOs: `CheckDefRequest` (validated: @NotBlank @Size(min=10)), `CheckDefResponse`, `CheckRunResponse`, `CheckRunDetailResponse`, `CheckResultResponse` — all with static `fromEntity()` factories
- `AiParameterSetController` — added `GET /api/v1/admin/allowed-models` returning `AllowedModelResponse(modelId, displayName)` for all `AllowedModel` enum values
- `CheckDefServiceTest` — all 4 Wave 0 stubs enabled and passing (create, update, delete NOT_FOUND, findActive size)

### Task 2: Async CheckRunner + CheckRunService + CheckRunAdminController

- `SemanticEvaluator` — interface with `double evaluate(String referenceAnswer, String actualAnswer)`
- `CheckRunner` — `@Async("ingestionExecutor") @Transactional runAll(UUID checkRunId)`: fetches active defs, calls `chatService.answer()` + `evaluator.evaluate()` per def, catches per-def exceptions (score=0.0, log="error:..."), `saveAll(results)`, sets COMPLETED with averageScore and checkCount; sets FAILED if no active defs
- `CheckRunService` — `trigger()` snapshots active parameter set name/id, saves RUNNING run, calls `checkRunner.runAll(run.getId())` (returns immediately); `findAll()`, `findById()`, `findResults()`
- `CheckRunAdminController` — POST trigger returns 202 + `{"runId": uuid}`, GET list, GET by id (404 if absent), GET results
- `CheckRunnerTest` — 2 Wave 0 stubs enabled and passing (COMPLETED path + FAILED empty-defs path)
- `CheckRunControllerTest` — 2 Wave 0 stubs enabled and passing (202 trigger + results array size)

### Task 3: LLM-as-judge SemanticEvaluator

- `LlmSemanticEvaluator` implements `SemanticEvaluator` via `ChatClient`
- Vietnamese-first system prompt: semantic accuracy 60%, completeness 30%, contradiction penalty 10%, language mismatch penalty
- `evaluate()` — top-level try/catch always returns 0.0 on exception (never propagates)
- `parseScore()` — regex `\{.*\}` with DOTALL, Jackson parse, `languageMatch=false` caps score at 0.2, clamps result to [0.0, 1.0]
- `LlmSemanticEvaluatorTest` — 3 tests (2 stub + 1 added): parse valid JSON score, language mismatch cap ≤ 0.2, LLM failure returns 0.0

## Test Results

| Test Class | Tests | Passed | Failed | Skipped |
|------------|-------|--------|--------|---------|
| CheckDefServiceTest | 4 | 4 | 0 | 0 |
| CheckRunnerTest | 2 | 2 | 0 | 0 |
| CheckRunControllerTest | 2 | 2 | 0 | 0 |
| LlmSemanticEvaluatorTest | 3 | 3 | 0 | 0 |
| **Total (check engine)** | **11** | **11** | **0** | **0** |

Pre-existing infra failures (require live PostgreSQL): `TrafficLawChatbotApplicationTests`, `SpringBootSmokeTest`, `LoggingAspectTest`, `AppPropertiesTest` — unchanged from base commit, not caused by this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ChatAnswerResponse.getAnswer() does not exist — it is a record**
- **Found during:** Task 2 — reading the class before writing CheckRunner
- **Issue:** Plan specified `response.getAnswer()` but `ChatAnswerResponse` is a Java record; accessor is `response.answer()`
- **Fix:** Used `response.answer()` in `CheckRunner.runSingle()`
- **Files modified:** `CheckRunner.java`
- **Commit:** `e42dbe2`

**2. [Rule 1 - Bug] AiParameterSetService method is getActive() not findActive()**
- **Found during:** Task 2 — reading the service before writing CheckRunService
- **Issue:** Plan specified `aiParameterSetService.findActive()` but the method is named `getActive()`
- **Fix:** Used `aiParameterSetService.getActive()` in `CheckRunService.trigger()`
- **Files modified:** `CheckRunService.java`
- **Commit:** `e42dbe2`

**3. [Rule 1 - Bug] parseScore() was package-private — inaccessible from test in different package**
- **Found during:** Task 3 compilation
- **Issue:** `LlmSemanticEvaluator.parseScore()` was package-private (no modifier), but `LlmSemanticEvaluatorTest` is in `com.vn.traffic.chatbot.checks` while evaluator is in `com.vn.traffic.chatbot.checks.evaluator`
- **Fix:** Changed `parseScore()` to `public`
- **Files modified:** `LlmSemanticEvaluator.java`
- **Commit:** `3062e66`

**4. [Rule 1 - Bug] Wrong Spring AI ChatClient mock types in LlmSemanticEvaluatorTest**
- **Found during:** Task 3 compilation
- **Issue:** Used non-existent `ChatClient.ChatClientRequest` and `ChatClient.ChatClientRequest.ChatClientRequestSpec` types; Spring AI uses `ChatClient.ChatClientRequestSpec` and `ChatClient.CallResponseSpec`
- **Fix:** Rewrote test mock chain using correct types (consistent with `ChatServiceTest` pattern)
- **Files modified:** `LlmSemanticEvaluatorTest.java`
- **Commit:** `3062e66`

**5. [Rule 1 - Bug] ChatAnswerResponse constructor arg count wrong in CheckRunnerTest**
- **Found during:** Task 2 compilation
- **Issue:** Record has 18 fields but constructor call had 17 args
- **Fix:** Added missing null arg
- **Files modified:** `CheckRunnerTest.java`
- **Commit:** `e42dbe2`

## Known Stubs

None — all Wave 0 stubs from Plan 01 that were assigned to Plan 02 are now enabled and passing:
- `CheckDefServiceTest` — 4 methods enabled
- `CheckRunnerTest` — 2 methods enabled
- `CheckRunControllerTest` — 2 methods enabled
- `LlmSemanticEvaluatorTest` — 2 methods enabled + 1 new test added (testLanguageMismatchCapScore)

## Threat Flags

None — all threat items in the plan's threat register (T-05-06 through T-05-10) were handled per their dispositions (on-demand only, try/catch per def, append-only audit trail already in CheckRun). No new unplanned network endpoints or auth paths introduced.

## Self-Check: PASSED

Checked:
- `src/main/java/com/vn/traffic/chatbot/checks/repo/CheckDefRepository.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/checks/service/CheckDefService.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/checks/api/CheckDefAdminController.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunService.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/checks/api/CheckRunAdminController.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/checks/evaluator/SemanticEvaluator.java` — FOUND
- `src/main/java/com/vn/traffic/chatbot/checks/evaluator/LlmSemanticEvaluator.java` — FOUND
- Commit `6e162af` — FOUND (Task 1: CheckDef CRUD + repos + DTOs + AllowedModels)
- Commit `e42dbe2` — FOUND (Task 2: CheckRunner + CheckRunService + CheckRunAdminController)
- Commit `3062e66` — FOUND (Task 3: LlmSemanticEvaluator)
