---
phase: 05-quality-operations-evaluation
reviewed: 2026-04-13T10:00:00Z
depth: standard
files_reviewed: 57
files_reviewed_list:
  - frontend/app/(admin)/chat-logs/[id]/page.tsx
  - frontend/app/(admin)/chat-logs/page.tsx
  - frontend/app/(admin)/checks/page.tsx
  - frontend/app/(admin)/checks/runs/[id]/page.tsx
  - frontend/app/(admin)/checks/runs/page.tsx
  - frontend/app/(admin)/layout.tsx
  - frontend/app/(admin)/parameters/page.tsx
  - frontend/components/layout/app-sidebar.tsx
  - frontend/hooks/use-chat-logs.ts
  - frontend/hooks/use-check-defs.ts
  - frontend/hooks/use-check-runs.ts
  - frontend/lib/api/chat-logs.ts
  - frontend/lib/api/check-defs.ts
  - frontend/lib/api/check-runs.ts
  - frontend/types/api.ts
  - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
  - src/main/java/com/vn/traffic/chatbot/chatlog/api/ChatLogAdminController.java
  - src/main/java/com/vn/traffic/chatbot/chatlog/api/dto/ChatLogDetailResponse.java
  - src/main/java/com/vn/traffic/chatbot/chatlog/api/dto/ChatLogResponse.java
  - src/main/java/com/vn/traffic/chatbot/chatlog/domain/ChatLog.java
  - src/main/java/com/vn/traffic/chatbot/chatlog/repo/ChatLogRepository.java
  - src/main/java/com/vn/traffic/chatbot/chatlog/service/ChatLogService.java
  - src/main/java/com/vn/traffic/chatbot/checks/api/CheckDefAdminController.java
  - src/main/java/com/vn/traffic/chatbot/checks/api/CheckRunAdminController.java
  - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckDefRequest.java
  - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckDefResponse.java
  - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckResultResponse.java
  - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckRunDetailResponse.java
  - src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckRunResponse.java
  - src/main/java/com/vn/traffic/chatbot/checks/domain/CheckDef.java
  - src/main/java/com/vn/traffic/chatbot/checks/domain/CheckResult.java
  - src/main/java/com/vn/traffic/chatbot/checks/domain/CheckRun.java
  - src/main/java/com/vn/traffic/chatbot/checks/domain/CheckRunStatus.java
  - src/main/java/com/vn/traffic/chatbot/checks/evaluator/LlmSemanticEvaluator.java
  - src/main/java/com/vn/traffic/chatbot/checks/evaluator/SemanticEvaluator.java
  - src/main/java/com/vn/traffic/chatbot/checks/repo/CheckDefRepository.java
  - src/main/java/com/vn/traffic/chatbot/checks/repo/CheckResultRepository.java
  - src/main/java/com/vn/traffic/chatbot/checks/repo/CheckRunRepository.java
  - src/main/java/com/vn/traffic/chatbot/checks/service/CheckDefService.java
  - src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunService.java
  - src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java
  - src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java
  - src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java
  - src/main/java/com/vn/traffic/chatbot/parameter/domain/AiParameterSet.java
  - src/main/java/com/vn/traffic/chatbot/parameter/domain/AllowedModel.java
  - src/main/resources/db/changelog/008-chat-log-schema.xml
  - src/main/resources/db/changelog/009-check-def-schema.xml
  - src/main/resources/db/changelog/010-check-run-schema.xml
  - src/main/resources/db/changelog/011-ai-parameter-set-model-columns.xml
  - src/main/resources/db/changelog/db.changelog-master.xml
  - src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java
  - src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogControllerTest.java
  - src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogServiceTest.java
  - src/test/java/com/vn/traffic/chatbot/checks/CheckDefServiceTest.java
  - src/test/java/com/vn/traffic/chatbot/checks/CheckRunControllerTest.java
  - src/test/java/com/vn/traffic/chatbot/checks/CheckRunnerTest.java
  - src/test/java/com/vn/traffic/chatbot/checks/LlmSemanticEvaluatorTest.java
findings:
  critical: 0
  warning: 5
  info: 6
  total: 11
status: issues_found
---

# Phase 5: Code Review Report

**Reviewed:** 2026-04-13T10:00:00Z
**Depth:** standard
**Files Reviewed:** 57
**Status:** issues_found

## Summary

Phase 5 delivers chat-log history, check-definition CRUD, check-run execution and evaluation, the LLM-as-judge semantic evaluator, and the parameters two-tab editor. The overall quality is high: the async-after-commit pattern in `CheckRunService` is correctly implemented, error handling is consistent throughout the pipeline, and test coverage addresses critical paths including null-document handling and malformed LLM payloads.

Five warnings were found — all logic/correctness issues rather than style — plus six informational items. No critical (security or data-loss) issues were found.

## Warnings

### WR-01: `CheckRunner.runAll` holds a `@Transactional` boundary that is incompatible with `@Async` dispatch

**File:** `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java:36`

**Issue:** `runAll` is annotated with both `@Async` and `@Transactional`. Spring's async proxy unwraps the calling thread's transaction context before the method executes in the executor thread pool, so the `@Transactional` annotation here opens a *new* transaction in the worker thread. This is technically sound but creates a single, very long-running transaction that holds open DB connections for the full duration of all sequential LLM calls (one per active `CheckDef`). More critically, if `chatService.answer()` itself calls `ChatLogService.save()` which also opens a transaction, the long-running outer transaction can hit connection-pool starvation under load and lock the check_log rows being written during the run. The fact that this works in tests (which mock `chatService`) masks the real-world risk.

**Fix:** Remove `@Transactional` from `runAll` and instead open short-lived transactions only around the DB writes. Save the run status update in a dedicated `@Transactional` helper method, and call `checkResultRepository.saveAll(results)` + `checkRunRepository.save(run)` in a separate transactional block after all LLM work completes:

```java
// Remove @Transactional from runAll — keep @Async only
@Async("ingestionExecutor")
public void runAll(UUID checkRunId) {
    CheckRun run = loadRun(checkRunId);          // short TX
    List<CheckDef> activeDefs = loadActiveDefs(); // short TX
    // ... LLM calls outside any transaction ...
    persistResults(results, run, avg);           // short TX
}

@Transactional
protected CheckRun loadRun(UUID id) { ... }

@Transactional
protected void persistResults(List<CheckResult> results, CheckRun run, double avg) {
    checkResultRepository.saveAll(results);
    run.setAverageScore(avg);
    run.setCheckCount(results.size());
    run.setStatus(CheckRunStatus.COMPLETED);
    checkRunRepository.save(run);
}
```

---

### WR-02: `CheckRunner` does not mark the run as `FAILED` when an unrecoverable error occurs mid-run

**File:** `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java:36-63`

**Issue:** The `runAll` method has no outer `try/catch`. If `checkRunRepository.findById` returns empty (the `orElseThrow` throws `IllegalStateException`), or if `checkResultRepository.saveAll` throws a DB exception, the exception propagates to the `@Async` executor — which swallows it — and the `CheckRun` row remains permanently in `RUNNING` status. The UI will show such runs as "Đang chạy" forever with no way to recover.

**Fix:** Wrap the body of `runAll` in a try/catch and set status to `FAILED` on any unhandled exception:

```java
@Async("ingestionExecutor")
public void runAll(UUID checkRunId) {
    try {
        // ... existing logic ...
    } catch (Exception ex) {
        log.error("CheckRunner: run {} failed unexpectedly: {}", checkRunId, ex.getMessage(), ex);
        checkRunRepository.findById(checkRunId).ifPresent(run -> {
            run.setStatus(CheckRunStatus.FAILED);
            checkRunRepository.save(run);
        });
    }
}
```

---

### WR-03: SQL injection risk in `ChatLogService.findFiltered` via unparameterized LIKE value

**File:** `src/main/java/com/vn/traffic/chatbot/chatlog/service/ChatLogService.java:75-77`

**Issue:** The `q` parameter is lower-cased and concatenated directly into the LIKE pattern string:
```java
predicates.add(cb.like(cb.lower(root.get("question")), "%" + q.toLowerCase() + "%"));
```
The JPA Criteria API does *not* automatically parameterize values passed as raw `String` literals to `cb.like()`. Although most JPA providers (Hibernate) will bind this as a JDBC parameter, the pattern itself can include unescaped SQL wildcard characters `%` and `_`. A user who sends `q=a%b%c` will produce a valid but unintended wildcard query. More importantly, this departs from the safe parameterized pattern. The correct approach uses a `ParameterExpression` or escapes the wildcard characters.

**Fix:** Use `CriteriaBuilder.literal` to keep the value parameterized, and escape wildcards:

```java
if (q != null && !q.isBlank()) {
    String escaped = q.toLowerCase()
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    predicates.add(
        cb.like(cb.lower(root.get("question")),
                cb.literal("%" + escaped + "%"), '!')
    );
}
```

---

### WR-04: `ChatLogServiceTest.testLogPersistedAfterAnswer` calls the 7-argument overload but `ChatLogService.save` is 8-argument

**File:** `src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogServiceTest.java:44`

**Issue:** The test calls:
```java
chatLogService.save(question, response, GroundingStatus.GROUNDED, null, 100, 200, 500);
```
This is a 7-argument call. The actual `ChatLogService.save` signature in `ChatLogService.java:29` takes **8 arguments** — the eighth is `String pipelineLog`. This test either does not compile (if `pipelineLog` was always present) or it was written against an older overload that no longer exists. If it compiles today, there must be an overloaded or different `save` method, but none is visible in the reviewed code. The test validates the wrong method signature, providing a false sense of safety.

**Fix:** Update the test call to include the `pipelineLog` argument:

```java
chatLogService.save(question, response, GroundingStatus.GROUNDED, null, 100, 200, 500, null);
```
Do the same for the `testLogFailureSwallowed` call on line 74.

---

### WR-05: `app-sidebar.tsx` active-link matching is prefix-based and will incorrectly highlight `/checks` when on `/checks/runs`

**File:** `frontend/components/layout/app-sidebar.tsx:62`

**Issue:** The active check uses `pathname.startsWith(item.href)`. Both the "Kiểm tra chất lượng" item (`href: '/checks'`) and "Lịch sử chạy" item (`href: '/checks/runs'`) will be marked active when the user navigates to `/checks/runs`, because `'/checks/runs'.startsWith('/checks')` is `true`. The user will see two nav items highlighted simultaneously, creating confusing visual feedback.

**Fix:** Use exact matching for leaf routes or append a trailing slash guard:

```typescript
isActive={pathname === item.href || pathname.startsWith(item.href + '/')}
```

---

## Info

### IN-01: `CheckRunDetailResponse` and `CheckRunResponse` are identical records

**File:** `src/main/java/com/vn/traffic/chatbot/checks/api/dto/CheckRunDetailResponse.java:1-27`

**Issue:** `CheckRunDetailResponse` and `CheckRunResponse` have exactly the same fields and factory method. The detail response was presumably created to later hold result items inline, but currently it carries no extra data. This creates maintenance overhead — both must be kept in sync for any future field additions.

**Fix:** Consolidate into a single DTO, or add the additional detail fields (e.g., embedded `List<CheckResultResponse>`) to `CheckRunDetailResponse` to justify its existence.

---

### IN-02: `ChatLogRepository` declares unused derived-query methods

**File:** `src/main/java/com/vn/traffic/chatbot/chatlog/repo/ChatLogRepository.java:14-18`

**Issue:** `findByGroundingStatusAndCreatedDateBetween` and `findByQuestionContainingIgnoreCase` are declared but never called — `ChatLogService.findFiltered` uses the `JpaSpecificationExecutor` path exclusively. Dead interface methods inflate the repository API surface and can mislead future maintainers.

**Fix:** Remove the two unused method declarations from `ChatLogRepository`.

---

### IN-03: `yamlToForm` silently returns an empty object on YAML parse failure

**File:** `frontend/app/(admin)/parameters/page.tsx:64-90`

**Issue:** When `parseYaml(content)` throws, `yamlToForm` catches the error and returns `{}`. The call site at line 248 merges this empty object with `DEFAULT_VALUES`, so the form silently resets to defaults rather than showing the user that the stored YAML content is malformed. An admin editing a broken parameter set will see default values with no warning.

**Fix:** Either propagate the error to the UI with an `Alert`, or log a warning and expose an `isParseError` flag to conditionally render a notice:

```typescript
// Return a discriminated result instead of silent empty
function yamlToForm(content: string): { values: Partial<FormValues>; parseError: boolean } {
  try {
    const parsed = parseYaml(content) as ParameterYaml;
    return { values: { /* ... mapped fields ... */ }, parseError: false };
  } catch {
    return { values: {}, parseError: true };
  }
}
```

---

### IN-04: `useCheckRunResults` and `useCheckRunDetail` are not polling — RUNNING run detail page goes stale

**File:** `frontend/hooks/use-check-runs.ts:32-46`

**Issue:** `useCheckRuns` (the list hook) has `refetchInterval` to poll while any run is `RUNNING`, but `useCheckRunDetail` and `useCheckRunResults` (used by the detail page) do not poll. A user who navigates directly to a run detail page during execution will see the initial `RUNNING` state and stale empty results table with no automatic refresh.

**Fix:** Add a `refetchInterval` to both hooks conditioned on the run status:

```typescript
export function useCheckRunDetail(id: string) {
  return useQuery({
    queryKey: ['check-runs', id],
    queryFn: () => getCheckRunById(id),
    enabled: !!id,
    refetchInterval: (query) =>
      query.state.data?.status === 'RUNNING' ? 5000 : false,
  });
}
```
Apply the same pattern to `useCheckRunResults`, keying off the companion `useCheckRunDetail` data.

---

### IN-05: `008-chat-log-schema.xml` is missing the `pipeline_log` column

**File:** `src/main/resources/db/changelog/008-chat-log-schema.xml:1-53`

**Issue:** The `chat_log` table created in changeset `008-chat-log` does not include a `pipeline_log` column, yet the `ChatLog` entity maps `@Column(name = "pipeline_log", columnDefinition = "TEXT")`. The column is added later in a separate changeset referenced as `013-chat-log-pipeline-log-column.xml` (visible in the master changelog). This split is intentional and functional, but it means the schema cannot be reconstructed from changeset `008` alone. The table definition is spread across three separate changesets (008, 012, 013), which complicates schema archaeology.

**Fix:** This is an accepted incremental migration pattern. No immediate fix required, but a comment in `008-chat-log-schema.xml` noting the split would help maintainers:

```xml
<!-- Note: pipeline_log column added in 013-chat-log-pipeline-log-column.xml -->
<!-- Note: additional columns added in 012-chat-log-pipeline-columns.xml -->
```

---

### IN-06: `LlmSemanticEvaluator.parseScore` is public but is an internal implementation detail

**File:** `src/main/java/com/vn/traffic/chatbot/checks/evaluator/LlmSemanticEvaluator.java:60`

**Issue:** `parseScore` is `public` to allow direct unit testing in `LlmSemanticEvaluatorTest`. Making an internal method public to enable testing is a code smell — it widens the class API surface beyond what callers should rely on.

**Fix:** Consider making the test a package-private access (move the test to the same package), or use `@VisibleForTesting` (from Guava/common-annotations) to document intent, or extract `parseScore` into a separate package-private helper class. The simplest option is to annotate it:

```java
@VisibleForTesting
double parseScore(String content) { ... }
```

---

_Reviewed: 2026-04-13T10:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
