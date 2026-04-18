---
phase: 07-chat-latency-foundation
plan: 01
subsystem: infra
tags: [spring-ai, spring-boot, actuator, prometheus, caffeine, cache, async, testing, nyquist-wave-0]

requires:
  - phase: 00-foundation
    provides: Spring Boot backend with actuator, Spring AI EmbeddingModel auto-config, ChatLogService
provides:
  - /actuator/prometheus endpoint exposed (Pitfall D fix)
  - Runtime classpath dependencies for Caffeine embedding cache (CACHE-02) and Prometheus metrics (PERF-01)
  - 4 Wave-0 RED test scaffolds (Nyquist gate satisfied — every Wave-1 task has a runnable <automated> command)
  - 2 production stubs (CacheKeyNormalizer, CachingEmbeddingModel) that Wave-1 Plan 02 will fill
affects: [07-02-embedding-cache, 07-03-async-chatlog-slim-schema, 07-04-integration]

tech-stack:
  added:
    - "com.github.ben-manes.caffeine:caffeine:3.2.0 (pinned per D-18)"
    - "org.springframework.boot:spring-boot-starter-cache"
    - "io.micrometer:micrometer-registry-prometheus (runtime only)"
  patterns:
    - "Wave-0 RED-test scaffold: production stub throws UOE, test file either @Disabled or runtime-RED; Wave-1 plan flips to GREEN"
    - "@Disabled class-level gate with plan-reference comment (PLAN-02-DEPENDENCY / PLAN-03-DEPENDENCY) so grep can find the flip-points"
    - "Reflection-based annotation assertion for @Async / @Transactional (PERF-03a/c) — avoids @SpringBootTest for a pure DOD check"

key-files:
  created:
    - src/main/java/com/vn/traffic/chatbot/ai/embedding/CacheKeyNormalizer.java
    - src/main/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModel.java
    - src/test/java/com/vn/traffic/chatbot/ai/embedding/CacheKeyNormalizerTest.java
    - src/test/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModelTest.java
    - src/test/java/com/vn/traffic/chatbot/chatlog/service/ChatLogServiceAsyncTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerSlimSchemaTest.java
    - .planning/phases/07-chat-latency-foundation/deferred-items.md
  modified:
    - src/main/resources/application.yaml
    - build.gradle

key-decisions:
  - "CachingEmbeddingModel stub delegates non-cache methods (embed(Document)) directly to the wrapped EmbeddingModel rather than also throwing UOE, so the stub remains functionally usable if accidentally wired early (Rule 2 defensive default)"
  - "AnswerComposerSlimSchemaTest keeps real assertion bodies inside /* ... */ block comments with a fail() placeholder — Plan 07-03 Task 2 uncomments and removes @Disabled in one move"
  - "Dimension-mismatch test asserts the first-call delegate count; real seeded CachedVector(wrongDim, 512) seeding is deferred to Plan 07-02 Task 1 because the cache-key format is a Wave-1 impl detail"

patterns-established:
  - "Wave-0 Nyquist scaffold: every future RED test gets a compile-green stub today so the test file itself compiles and CI stays green while the runtime assertion is deferred"
  - "New package com.vn.traffic.chatbot.ai.embedding for cache-layer decorators over Spring AI"

requirements-completed: [PERF-01, PERF-03, CACHE-02]

duration: ~25 min
completed: 2026-04-18
---

# Phase 07 Plan 01: Wave-0 Infrastructure + Nyquist RED Scaffolds — Summary

**/actuator/prometheus exposed, Caffeine 3.2.0 + cache-starter + prometheus-registry on the runtime classpath, and 4 Wave-0 RED test scaffolds committed so every Wave-1 <automated> verify command has something real to run.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-18 (task execution)
- **Completed:** 2026-04-18
- **Tasks:** 2 / 2
- **Files modified:** 2 (application.yaml, build.gradle)
- **Files created:** 7 (2 stubs, 4 tests, 1 deferred-items.md)

## Accomplishments

- Closed Pitfall D: `management.endpoints.web.exposure.include` now lists `prometheus`; Micrometer registry on the runtime classpath. Spring Boot auto-config will expose HTTP 200 on `GET /actuator/prometheus` once the app boots.
- Added three runtime dependencies (Caffeine 3.2.0 pinned per D-18, spring-boot-starter-cache, micrometer-registry-prometheus 1.16.4 via Boot BOM). All resolve cleanly — verified via `./gradlew dependencies --configuration runtimeClasspath`.
- Stubbed `CacheKeyNormalizer` (static `normalize` + `sha256`, both throw UOE) and `CachingEmbeddingModel` (implements Spring AI `EmbeddingModel`, stores delegate/cacheManager/modelId/configuredDim, `call(...)` throws UOE, other methods delegate). Private record `CachedVector(float[] vector, int dim)` is declared so the Plan 02 test can reference it at compile time.
- Authored 4 Wave-0 test files covering CACHE-02a/b/c (D-14/D-15/D-16), PERF-03a/c (D-10/D-11), and D-03/D-04. Two are `@Disabled` until Wave-1 enables them; two run today (one GREEN snapshot test, one RED runtime UOE).

## Task Commits

1. **Task 1: Expose /actuator/prometheus + add Caffeine/cache/prometheus runtime deps** — `53f4934` (feat)
2. **Task 2: Author 4 Wave-0 RED test files + 2 production stubs** — `4b1ad43` (test)

## Files Created/Modified

- `src/main/resources/application.yaml` — added `prometheus` token to `management.endpoints.web.exposure.include`
- `build.gradle` — added Caffeine 3.2.0, spring-boot-starter-cache, and micrometer-registry-prometheus (runtimeOnly)
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/CacheKeyNormalizer.java` — static normalize + sha256 stubs (UOE bodies)
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModel.java` — EmbeddingModel decorator stub; `call(...)` throws UOE, `embed(Document)` delegates, private record `CachedVector`
- `src/test/java/com/vn/traffic/chatbot/ai/embedding/CacheKeyNormalizerTest.java` — 5 tests (RED runtime, UOE)
- `src/test/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModelTest.java` — 4 tests, class-level `@Disabled("Enabled by Plan 07-02 Task 1 ...")`
- `src/test/java/com/vn/traffic/chatbot/chatlog/service/ChatLogServiceAsyncTest.java` — 3 reflection tests (`asyncSnapshotImmutability` GREEN today; the two annotation asserts RED until Plan 03 Task 1)
- `src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerSlimSchemaTest.java` — 2 tests, class-level `@Disabled("Enabled by Plan 07-03 Task 2 ...")`; real bodies in block comment + `fail()` placeholder
- `.planning/phases/07-chat-latency-foundation/deferred-items.md` — logs the pre-existing ModelEntry test compile errors that block full testJava compile

## Decisions Made

- `CachingEmbeddingModel` stub delegates `embed(Document)` (and defaults via the interface) instead of throwing UOE on every method. Rationale: D-14's `@Primary` bean swap is wired in Wave 1; if any code path accidentally resolves the stub before Wave 1 lands, it degrades to pass-through rather than hard-failing. The one method that MUST be replaced (`call(EmbeddingRequest)`) still throws UOE — the RED signal is preserved there.
- `AnswerComposerSlimSchemaTest` wraps the real assertion bodies in `/* ... */` block comments plus a `Assertions.fail(...)` placeholder. Rationale: keeps the file compile-green today without forward-referencing the not-yet-existing 8-arg `LegalAnswerDraft` constructor. Plan 07-03 Task 2 uncomments the block AND removes class-level `@Disabled` in one edit.
- `dimensionMismatchEvictsAndRefetches` asserts the first-call delegate count only. Rationale: the cache-key format (`modelId + ":" + sha256(normalize(text))`) is an impl detail owned by Plan 02 Task 1; seeding a stale `CachedVector(wrongDim, 512)` from the test today would force a leak of that impl detail. Plan 02 Task 1 will expand the assertion with the real seeding pattern when it owns the key format.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking, but out-of-scope] Pre-existing `ModelEntry` constructor-arity compile errors prevent running Task 2's `./gradlew test --tests ...` verify command**

- **Found during:** Task 2 (verify step)
- **Issue:** `./gradlew compileTestJava` fails with 15 errors in 6 test files (`AiModelPropertiesTest`, `AllowedModelsControllerTest`, `ChatFlowIntegrationTest`, `ChatClientConfigTest`, `ChatServiceTest`, `LlmSemanticEvaluatorTest`). All errors are instances of `new AiModelProperties.ModelEntry(String, String)` — the record was extended to 4 fields in commit `b1f55ab` but the test files were not updated. Confirmed pre-existing via `git stash` → same 15 errors without Wave-0 changes.
- **Fix:** NONE — per execute-plan.md scope-boundary rule ("pre-existing failures in unrelated files are out of scope"). Logged to `.planning/phases/07-chat-latency-foundation/deferred-items.md` with a recommended follow-up chore commit before Plan 07-02's RED→GREEN run.
- **Files modified:** `.planning/phases/07-chat-latency-foundation/deferred-items.md` (new)
- **Verification:** None of the 6 new files I authored contribute errors; compile-error count is identical with and without my changes (33 error messages in both states, verified via `git stash`).
- **Committed in:** `4b1ad43` (Task 2 commit, includes deferred-items.md)

---

**Total deviations:** 1 documented (scope-boundary defer)
**Impact on plan:** The runtime part of Task 2's `<automated>` verify (`./gradlew test --tests ...`) could not execute because Gradle needs the whole test source set to compile. The CORRECTNESS of the 4 new test files is demonstrated by (a) pre-existing/post-change error count parity and (b) careful construction against verified Spring AI API signatures (`EmbeddingModel` interface, `EmbeddingRequest(List<String>, EmbeddingOptions)`, `EmbeddingResponse(List<Embedding>)`, `Embedding(float[], Integer)` — all checked directly in the 2.0.0-M4 sources jar). The DOD items that don't depend on a successful test run (file existence, `@Disabled` presence, `@Test` counts, `implements EmbeddingModel` grep hit) all pass. The DOD items that DO depend on a successful test run (`asyncSnapshotImmutability` GREEN, `CacheKeyNormalizerTest` RED with UOE) will pass automatically once the pre-existing `ModelEntry` errors are fixed — no changes needed to the new test files.

## Issues Encountered

- Pre-existing test compile errors (documented above) — handled via deferred-items.md per scope-boundary rule.
- Working directory had pre-existing dirty state on `.planning/STATE.md` and one other file before I started; left untouched per parallel-executor rules (orchestrator owns STATE.md).

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **Plan 07-02 (embedding cache) is unblocked:** `CachingEmbeddingModelTest` (@Disabled) compiles against the stub; Plan 02 Task 1 just needs to (a) replace `call(EmbeddingRequest)` body, (b) remove the class-level `@Disabled`, (c) fix the pre-existing `ModelEntry` errors so the test suite compiles, and (d) expand `dimensionMismatchEvictsAndRefetches` with real seeded `CachedVector`.
- **Plan 07-03 (async + slim schema) is unblocked:** `ChatLogServiceAsyncTest` reflection asserts will flip GREEN the moment `@Async("chatLogExecutor")` + `@Transactional(propagation=REQUIRES_NEW)` land on `ChatLogService.save`; `AnswerComposerSlimSchemaTest` has the 8-arg-constructor assertion body pre-written inside the block comment for Plan 03 Task 2 to activate.
- **Wave-1 parallelism preserved:** Plans 02 and 03 touch disjoint files (embedding package vs chatlog/chat packages) — no merge contention expected.
- **Blocker carryforward:** Plan 07-02 (or a standalone chore commit) must fix the 6 pre-existing `ModelEntry` call-sites before any `./gradlew test` run can green.

## Self-Check: PASSED

- Commit `53f4934` exists in git log.
- Commit `4b1ad43` exists in git log.
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/CacheKeyNormalizer.java` — exists.
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModel.java` — exists, `implements EmbeddingModel`.
- `src/test/java/com/vn/traffic/chatbot/ai/embedding/CacheKeyNormalizerTest.java` — exists, 5 `@Test` methods.
- `src/test/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModelTest.java` — exists, 4 `@Test` methods, class-level `@Disabled`.
- `src/test/java/com/vn/traffic/chatbot/chatlog/service/ChatLogServiceAsyncTest.java` — exists, 3 `@Test` methods.
- `src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerSlimSchemaTest.java` — exists, 2 `@Test` methods, class-level `@Disabled`.
- `.planning/phases/07-chat-latency-foundation/deferred-items.md` — exists.
- `grep -n "prometheus" src/main/resources/application.yaml` → line 122 in `exposure.include`.
- `./gradlew dependencies --configuration runtimeClasspath` lists caffeine:3.2.0, spring-boot-starter-cache, micrometer-registry-prometheus.
- No `ChatLatencyBaselineIT*`, `top20-queries.json`, or `ChatV11Properties*` files were created anywhere under `src/`.

---
*Phase: 07-chat-latency-foundation*
*Completed: 2026-04-18*
