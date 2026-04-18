---
phase: 08
plan: 01
subsystem: backend-infrastructure
tags: [spring-ai, structured-output, gradle, model-entry, test-infra]
requires: [phase-07-chat-latency-foundation]
provides:
  - AiModelProperties.ModelEntry (5-arg with supportsStructuredOutput)
  - build.gradle liveTest task + @Tag('live') filter
  - spring-ai-test 2.0.0-M4 on testRuntimeClasspath
  - RED stubs for Plan 08-02 (GroundingGuardAdvisorTest, IntentClassifierIT)
  - RED stubs for Plan 08-04 (VietnameseRegressionIT, StructuredOutputMatrixIT)
  - Phase7Baseline.REFUSAL_RATE_PERCENT constant
  - 20-query Vietnamese regression fixture
affects:
  - 9 test files (ModelEntry call-site migration)
  - application.yaml (8 ai.models entries carry supports-structured-output)
tech-stack:
  added:
    - "org.springframework.ai:spring-ai-test:2.0.0-M4 (test scope)"
  patterns:
    - "YAML kebab-case → record camelCase binding (Spring Boot relaxed binding)"
    - "@Tag('live') + @DisabledIfEnvironmentVariable triad for CI-optional live IT"
key-files:
  created:
    - src/test/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardAdvisorTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/intent/IntentClassifierIT.java
    - src/test/java/com/vn/traffic/chatbot/chat/regression/VietnameseRegressionIT.java
    - src/test/java/com/vn/traffic/chatbot/chat/regression/StructuredOutputMatrixIT.java
    - src/test/java/com/vn/traffic/chatbot/chat/regression/Phase7Baseline.java
    - src/test/resources/regression/vietnamese-queries-20.yaml
  modified:
    - src/main/java/com/vn/traffic/chatbot/common/config/AiModelProperties.java
    - src/main/resources/application.yaml
    - build.gradle
    - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceChitchatTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/config/ChatClientConfigTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/api/AllowedModelsControllerTest.java
    - src/test/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheContextLoadTest.java
    - src/test/java/com/vn/traffic/chatbot/ai/config/AiModelPropertiesTest.java
    - src/test/java/com/vn/traffic/chatbot/checks/LlmSemanticEvaluatorTest.java
decisions:
  - "D-03 realized: ModelEntry 5th arg is `boolean supportsStructuredOutput`; all 17 test call-sites updated atomically in one commit (mirrors P7 f72440b)."
  - "YAML matrix: 7 entries `true`, deepseek/deepseek-v3.2 `false` per RESEARCH §3 / Pitfall 9."
  - "application-dev.yaml / application-prod.yaml do NOT override `ai.models`; no mirror updates required."
  - "spring-ai-test version resolved from spring-ai-bom → 2.0.0-M4 (no hardcoded version)."
  - "Default `test` task excludes @Tag('live'); new `liveTest` task includes only @Tag('live')."
  - "Phase7Baseline Case B (NaN): P7 artifacts have no aggregate refusal-rate number; NaN guarantees fail-loud on the Plan 08-04 parity check until a real baseline lands."
metrics:
  completed: 2026-04-18
  duration: ~25m
  tasks: 4
  commits: 4
  files_changed: 15
requirements: [ARCH-02, ARCH-03, ARCH-04]
---

# Phase 8 Plan 01: Structured Output + GroundingGuardAdvisor — Wave 0 Foundation Summary

Wave-0 foundation for Phase 8 landed: ModelEntry record extended 4→5 args with
`boolean supportsStructuredOutput`, spring-ai-test wired via BOM + default-test @Tag('live')
exclusion + new `liveTest` task, 4 RED test stubs + 20-query Vietnamese fixture authored,
and `Phase7Baseline.REFUSAL_RATE_PERCENT = Double.NaN` (Case B) prepared for Plan 08-04 SC5.

## Tasks Completed

| # | Task | Commit | Outcome |
|---|------|--------|---------|
| 1 | Atomic ModelEntry 4→5 arg migration | `577bf0e` | Record + 17 test call-sites + 8 YAML entries updated in one commit; `./gradlew compileTestJava` passes |
| 2 | build.gradle wiring (spring-ai-test + liveTest + tag filter) | `c3505b5` | `spring-ai-test` resolves to 2.0.0-M4 via BOM; `test` excludes `live`, `liveTest` includes only `live` |
| 3 | RED test stubs + 20-query Vietnamese fixture | `4e9021c` | 4 stub classes compile; all 5 unit RED tests fail with "RED — Plan 08-02/04"; 20 queries in fixture |
| 4 | Extract P7 refusal baseline into Phase7Baseline.java | `1398eeb` | Case B (NaN + TODO) — P7 artifacts contain no aggregate refusal-rate number |

## Exact Line Diffs — Task 1 (ModelEntry migration)

Record extended at `AiModelProperties.java:33` from 4-arg to 5-arg. All test call-sites append `, true` (or `, false` for future DeepSeek test entries) as the 5th positional argument:

| # | File | Lines | Change |
|---|------|-------|--------|
| 1 | `chat/service/ChatServiceTest.java` | 71-72 | `, true` × 2 |
| 2 | `chat/service/ChatServiceChitchatTest.java` | 60-61 | `, true` × 2 (kept pending Plan 08-03 deletion) |
| 3 | `chat/config/ChatClientConfigTest.java` | 27-29 | `, true` × 3 (uses null baseUrl/apiKey pattern) |
| 4 | `chat/ChatFlowIntegrationTest.java` | 78-79 | `, true` × 2 |
| 5 | `chat/api/AllowedModelsControllerTest.java` | 37-39 | `, true` × 3 |
| 6 | `ai/embedding/EmbeddingCacheContextLoadTest.java` | 119-120 | `, true` × 1 (multi-line) |
| 7 | `ai/config/AiModelPropertiesTest.java` | 22-24 | `, true` × 3 |
| 8 | `checks/LlmSemanticEvaluatorTest.java` | 43-44 | `, true` × 2 |

Total: 17 call-sites across 8 files updated atomically.

YAML (application.yaml lines 72-107): 8 `supports-structured-output` keys added. DeepSeek = `false`, all others `true`.

## spring-ai-test Version (resolved via BOM)

```
+--- org.springframework.ai:spring-ai-test -> 2.0.0-M4
```

No hardcoded version in `build.gradle`.

## RED Stub Test Names (cross-reference index for Plan 08-02 / 08-04)

**GroundingGuardAdvisorTest** (unit, default `test` task, Plan 08-02):
- `inputAdvisorOrderIsHighestPrecedencePlus100`
- `outputAdvisorOrderIsLowestPrecedenceMinus100`
- `noOpRetrievalAdvisorDelegatesToChain`
- `noOpPromptCacheAdvisorDelegatesToChain`
- `noOpValidationAdvisorDelegatesToChain`

**IntentClassifierIT** (@Tag("live"), Plan 08-02):
- `entityIntentDecisionReturnsNonNull`
- `classifierFailureFallsBackToLegal`

**VietnameseRegressionIT** (@Tag("live"), Plan 08-04):
- `twentyQueryRegressionSuiteAtLeast95Percent`
- `refusalRateWithinTenPercentOfPhase7Baseline` (depends on `Phase7Baseline.REFUSAL_RATE_PERCENT`)
- `twoTurnConversationMemoryWorks`

**StructuredOutputMatrixIT** (@Tag("live"), Plan 08-04):
- `allEightCatalogedModelsReturnLegalAnswerDraft`

## Phase7Baseline — Case B (NaN + TODO)

Grep of `.planning/phases/07-chat-latency-foundation/07-SUMMARY.md` and `07-SMOKE-REPORT.md` returned no aggregate refusal-rate percentage. P7 SMOKE-REPORT row 3 only confirms one legal query returned a grounded answer (not a refusal); no 20-query sweep was executed in P7. Therefore `REFUSAL_RATE_PERCENT = Double.NaN` with an in-source TODO pointing at Plan 08-04 Task 1.

Rationale (fail-loud contract): `Math.abs(observed - NaN) <= 10` is always `false` → parity check fails until Plan 08-04 either (a) backfills the baseline by running the 20-query suite against pre-P8 main, or (b) redefines parity as an absolute band (e.g., `refusalRate in [0%, 40%]`).

## Profile Override Notes

- `application-dev.yaml`: no `ai.models` block; untouched.
- `application-prod.yaml`: no `ai.models` block; untouched.
- Only `application.yaml` required the 8 `supports-structured-output` additions.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking Issue] Dropped `extends BasicEvaluationTest` from VietnameseRegressionIT**

- **Found during:** Task 3 compile verification.
- **Issue:** `org.springframework.ai.evaluation.BasicEvaluationTest` referenced in RESEARCH §9.2 does NOT exist in `spring-ai-test:2.0.0-M4`. Confirmed by enumerating the jar contents at `~/.gradle/caches/.../spring-ai-test-2.0.0-M4.jar` — class was removed/renamed before the M4 release.
- **Fix:** Removed the `extends BasicEvaluationTest` clause and the `import`. Added javadoc note explaining Plan 08-04 will inject `org.springframework.ai.chat.evaluation.RelevancyEvaluator` + `FactCheckingEvaluator` directly as `@Autowired` beans instead of inheriting a harness. Both concrete evaluators ARE present in spring-ai-chat 2.0.0-M4.
- **Files modified:** `src/test/java/com/vn/traffic/chatbot/chat/regression/VietnameseRegressionIT.java`
- **Commit:** `4e9021c`

### Architectural changes asked

None — all deviations resolved inline per Rule 3.

### Authentication gates

None — Task 3 live-IT stubs are `@DisabledIfEnvironmentVariable(OPENROUTER_API_KEY matches ^$)` so no key was needed.

## Verification Evidence

- `./gradlew compileTestJava` — zero errors (warnings only, pre-existing deprecation notices).
- `./gradlew tasks` — both `test` and `liveTest` listed.
- `./gradlew dependencies --configuration testRuntimeClasspath | grep spring-ai-test` →
  `+--- org.springframework.ai:spring-ai-test -> 2.0.0-M4`.
- `./gradlew test --tests "*.GroundingGuardAdvisorTest"` → `5 tests completed, 5 failed` with "RED — Plan 08-02" messages (expected RED outcome).
- `grep -c "supports-structured-output" src/main/resources/application.yaml` → 8.
- `grep -c "question:" src/test/resources/regression/vietnamese-queries-20.yaml` → 20.
- `grep -c "new AiModelProperties\.ModelEntry" src/test/java -r` → 17 call-sites, every one carrying the new 5th arg.

## Known Stubs

All 4 new test classes are RED stubs by design — `fail("RED — implemented in Plan 08-XX")`. Documented in frontmatter and per-class javadoc with the implementing plan ID. These are the contracted RED gate for Plan 08-02 (advisor chain) and Plan 08-04 (regression + matrix); Plan 08-01 is not responsible for making them green.

## Threat Flags

None — only new surface is a boolean record field + a Gradle test task. Both threats T-08-02 (scope leak) and T-08-03 (liveTest in CI) were mitigated exactly as planned: `testImplementation` scope + `@DisabledIfEnvironmentVariable` guard.

## Self-Check: PASSED

**Created files (all confirmed present):**
- `src/test/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardAdvisorTest.java` — FOUND
- `src/test/java/com/vn/traffic/chatbot/chat/intent/IntentClassifierIT.java` — FOUND
- `src/test/java/com/vn/traffic/chatbot/chat/regression/VietnameseRegressionIT.java` — FOUND
- `src/test/java/com/vn/traffic/chatbot/chat/regression/StructuredOutputMatrixIT.java` — FOUND
- `src/test/java/com/vn/traffic/chatbot/chat/regression/Phase7Baseline.java` — FOUND
- `src/test/resources/regression/vietnamese-queries-20.yaml` — FOUND

**Commits (all on `gsd/phase-08-structured-output-groundingguardadvisor`):**
- `577bf0e` refactor(08-01): extend ModelEntry record to 5 args — FOUND
- `c3505b5` build(08-01): spring-ai-test + liveTest + tag filter — FOUND
- `4e9021c` test(08-01): RED stubs + 20-query fixture — FOUND
- `1398eeb` test(08-01): Phase7Baseline Case B — FOUND
