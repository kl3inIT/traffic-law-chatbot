---
phase: 08
plan: 04
subsystem: backend-chat-regression-and-live-validation
tags: [spring-ai, spring-ai-test, regression, evaluator, live, testing]
requires: [08-01, 08-02, 08-03]
provides:
  - "VietnameseRegressionIT — 3 GREEN live tests: 20-query ≥95% pass-rate, refusal ±10% baseline, two-turn memory"
  - "StructuredOutputMatrixIT — cross-model matrix across all 8 AiModelProperties.ModelEntry rows"
  - "IntentClassifierIT — 3 GREEN live tests: LEGAL + CHITCHAT + OFF_TOPIC classifications"
  - "Raw ChatClient.Builder for evaluators via nested @TestConfiguration (evaluatorChatClientBuilder)"
affects:
  - "Live validation gate for Phase 8 acceptance — SC5 (20-query ≥95%, refusal parity, two-turn memory) now testable under ./gradlew liveTest"
tech-stack:
  added:
    - "org.springframework.ai.chat.evaluation.RelevancyEvaluator (Spring AI 2.0.0-M4 spring-ai-client-chat)"
    - "org.springframework.ai.chat.evaluation.FactCheckingEvaluator (Spring AI 2.0.0-M4 spring-ai-client-chat)"
    - "org.springframework.ai.evaluation.EvaluationRequest / EvaluationResponse (spring-ai-commons 2.0.0-M4)"
    - "org.yaml.snakeyaml.Yaml (transitively from spring-boot-starter test scope; version 2.5)"
  patterns:
    - "Evaluator isolation via dedicated raw ChatClient.Builder — keeps evaluator calls off the production 6-advisor chain so grounding/refusal advisors cannot interfere with evaluator prompts"
    - "EvaluationRequest(String userText, List<Document>, String responseContent) — NOT List<String> (plan skeleton was API-inaccurate vs 2.0.0-M4)"
    - "RelevancyEvaluator.builder().chatClientBuilder(builder).build() + FactCheckingEvaluator.builder(builder).build() — Spring AI 2.0.0-M4 canonical builder form"
    - "NaN baseline fails loud (Math.abs(rate - NaN) → NaN, never ≤ tolerance) — per Plan 08-01 Case B contract"
key-files:
  modified:
    - src/test/java/com/vn/traffic/chatbot/chat/regression/VietnameseRegressionIT.java
    - src/test/java/com/vn/traffic/chatbot/chat/regression/StructuredOutputMatrixIT.java
    - src/test/java/com/vn/traffic/chatbot/chat/intent/IntentClassifierIT.java
  unchanged:
    - src/test/resources/regression/vietnamese-queries-20.yaml (already covers canonical topic spread at 20 entries)
    - src/test/java/com/vn/traffic/chatbot/chat/regression/Phase7Baseline.java (NaN contract kept — no P7 aggregate baseline available)
    - src/main/resources/application.yaml (no supports-structured-output flips triggered yet — live run required to surface any A1/A2 assumption failures)
decisions:
  - "D-05 realized via direct @Autowired RelevancyEvaluator + FactCheckingEvaluator (built in test) — BasicEvaluationTest class does not exist in spring-ai-test 2.0.0-M4; deviation from RESEARCH §9.2 is the same one already documented in 08-01 SUMMARY"
  - "Evaluator uses a dedicated raw ChatClient.Builder built from app.ai.evaluator-model via a nested @TestConfiguration — prevents the production 6-advisor chain (GroundingGuard in/out, Memory, 3 NoOps) from intercepting evaluator prompts"
  - "YAML fixture (20 queries) kept as-authored in Plan 08-01 — topic spread already covers red-light ×2 (motorbike + car), alcohol, helmet, license, registration, speeding, lane violation, signaling, accident procedure, overloading, seat belt, hit-and-run, license classes, right-of-way, phone use, insurance. Further refinement deferred until a live run surfaces low-pass topics."
  - "Phase7Baseline.REFUSAL_RATE_PERCENT remains Double.NaN — Case B contract from Plan 08-01 preserved (fail-loud on parity check until a real baseline lands)"
  - "INTENT_SYSTEM_VI prompt unchanged — revisions would require observed misclassifications from a live run; current prompt is the Plan 08-02 GREEN version"
  - "No supports-structured-output YAML flips made — flipping requires observed 400 from OpenRouter, which only a live matrix run can expose"
metrics:
  duration: "~40m (single session; live execution deferred to human-action gate)"
  tasks: 2
  commits: 2
  files_changed: 3
  completed: 2026-04-18
requirements: [ARCH-02, ARCH-03, ARCH-04]
---

# Phase 8 Plan 04: Wave 3 Live Regression + Cross-Model Matrix Summary

Wave-3 closure for Phase 8: the three `@Tag("live")` RED stubs from Plan 08-01 are upgraded to fully implemented integration tests that exercise the full 6-advisor chain (Plan 08-02) + structured-output pipeline (Plan 08-03) against real OpenRouter traffic. All three tests compile, skip cleanly when `OPENROUTER_API_KEY` is unset, and are ready to prove Phase 8 SC5 under `./gradlew liveTest`.

## Tasks Completed

| # | Task | Commit | Outcome |
|---|------|--------|---------|
| 1 | VietnameseRegressionIT GREEN (20-query + refusal + two-turn) | `69708e5` | 3 live tests implemented; evaluator pair instantiated via nested @TestConfiguration isolated from production advisor chain |
| 2 | StructuredOutputMatrixIT + IntentClassifierIT GREEN | `d5aa697` | 8-row matrix iteration + LEGAL/CHITCHAT/OFF_TOPIC live classifier assertions |

## Critical API Correction vs PLAN

The plan skeleton (and RESEARCH §9.3) showed:

```java
new EvaluationRequest(question, List.of(citationsText), answer)  // List<String>
```

This does NOT compile against Spring AI 2.0.0-M4. Actual constructor (verified via `javap` on
`spring-ai-commons-2.0.0-M4.jar`):

```java
public EvaluationRequest(String userText, List<Document> dataList, String responseContent);
```

Implemented correctly in `VietnameseRegressionIT.citationsToDocuments(...)` which converts each
`CitationResponse.excerpt()` into a `org.springframework.ai.document.Document`. Recorded as a
deviation below (Rule 3 — blocking compile issue).

## Evaluator Isolation Design

The production `chatClientMap` (Plan 08-02) wires a 6-advisor chain per `ChatClient`:
GroundingGuardInput → Memory → NoOpRAG → NoOpCache → NoOpValidation → GroundingGuardOutput.
If `RelevancyEvaluator` / `FactCheckingEvaluator` reused those clients, every evaluator prompt
would get intercepted by the grounding guards (and might even be refused for being
"non-grounded" — evaluator prompts are inherently meta-queries, not legal questions). To avoid
that, `VietnameseRegressionIT.EvaluatorConfig` builds a **dedicated raw `ChatClient.Builder`**
from `AiModelProperties.evaluatorModel()` — same OpenRouter base URL + API key, same model by
default, but zero advisors attached. The evaluators receive this builder via
`@Qualifier("evaluatorChatClientBuilder")`.

This keeps:
- Plan 08-02 `ChatClientConfig.defaultAdvisors(...)` untouched (same 6-advisor chain in prod).
- Evaluator behaviour matching Spring AI reference documentation verbatim.
- A clean `./gradlew test` (non-live) run — the `@TestConfiguration` only materialises when
  the live-tagged `@SpringBootTest` actually runs.

## Live Execution Deferred (Human-Action Gate)

`OPENROUTER_API_KEY` is not set in the executing shell. Therefore the live ITs' acceptance
criteria — `≥95% pass rate`, `refusal within ±10% of Phase 7 baseline`, `two-turn memory
references turn-1`, `all 8 models return non-blank response`, `CHITCHAT/LEGAL/OFF_TOPIC
correctly classified` — are implemented as code but NOT executed in this session.

**User action required before `/gsd-verify-phase 8`:**

```bash
export OPENROUTER_API_KEY=sk-or-...
./gradlew liveTest --tests "com.vn.traffic.chatbot.chat.regression.*" \
                   --tests "com.vn.traffic.chatbot.chat.intent.IntentClassifierIT" \
                   2>&1 | tee /tmp/p8-live.log
```

Expected GREEN count: 7 (3 regression + 1 matrix + 3 classifier).
If `refusalRateWithinTenPercentOfPhase7Baseline` fails because
`Phase7Baseline.REFUSAL_RATE_PERCENT == NaN`, that is intentional (Plan 08-01 Case B) and
the remediation is to backfill the baseline in `Phase7Baseline.java` — NOT to relax the
assertion.

## Verification Evidence (non-live)

- `./gradlew compileTestJava --no-daemon` → BUILD SUCCESSFUL (0 errors, 4 pre-existing
  `MappingJackson2HttpMessageConverter` deprecation warnings).
- `./gradlew test --no-daemon` → BUILD SUCCESSFUL (live tag excluded; none of Plan 08-04's
  new code runs here because all three classes are `@Tag("live")` + `@DisabledIfEnvironmentVariable`).
- `unzip -l spring-ai-client-chat-2.0.0-M4.jar | grep evaluation` → both
  `RelevancyEvaluator` and `FactCheckingEvaluator` present as compiled classes.
- `javap -p RelevancyEvaluator.class` → confirms `public RelevancyEvaluator(ChatClient.Builder)`
  and `public static RelevancyEvaluator.Builder builder()` match the test's usage.
- Fixture: `grep -c "^  - id:" src/test/resources/regression/vietnamese-queries-20.yaml` → 20.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking compile] `EvaluationRequest` requires `List<Document>`, not `List<String>`**
- **Found during:** Task 1 compile (first `./gradlew compileTestJava` attempt).
- **Issue:** Plan skeleton and RESEARCH §9.3 showed
  `new EvaluationRequest(question, List.of(citationsText), answer)`; actual 2.0.0-M4 signature
  is `EvaluationRequest(String, List<Document>, String)`.
- **Fix:** Added `citationsToDocuments(List<CitationResponse>)` helper converting each
  `CitationResponse.excerpt()` to `new Document(excerpt)`. Null/blank excerpts dropped.
- **Files modified:** `VietnameseRegressionIT.java`.
- **Commit:** `69708e5`.

**2. [Rule 3 — Blocking compile] `CitationResponse` has `excerpt()`, not `snippet()`**
- **Found during:** Task 1 second compile attempt.
- **Issue:** Plan skeleton referenced `c.snippet()`; actual `CitationResponse` record field is
  `excerpt`.
- **Fix:** Used `c.excerpt()` when building `Document` list.
- **Commit:** `69708e5`.

**3. [Rule 2 — Critical correctness] Dedicated evaluator `ChatClient.Builder`, not production clients**
- **Found during:** Task 1 design.
- **Issue:** Naively autowiring a production `ChatClient` from `chatClientMap` and calling
  `.mutate()` would still carry the 6-advisor chain (`GroundingGuardInputAdvisor` +
  `GroundingGuardOutputAdvisor` among them) through the evaluator's prompt → response path.
  An evaluator prompt ("Is this answer relevant to the question?") would then be subject to
  grounding-refusal policy, which is semantically wrong (evaluator queries are meta-queries).
- **Fix:** Added a nested `@TestConfiguration EvaluatorConfig` inside
  `VietnameseRegressionIT` that materialises an independent `OpenAiChatModel` + raw
  `ChatClient.Builder` pointed at `AiModelProperties.evaluatorModel()`. No advisors attached.
  Qualifier: `evaluatorChatClientBuilder`.
- **Files modified:** `VietnameseRegressionIT.java`.
- **Commit:** `69708e5`.

### Architectural changes asked

None — all three deviations above were scope-local bug/correctness fixes.

### Authentication gates (documented per executor protocol)

**OPENROUTER_API_KEY** — required to execute any live assertion in this plan.
Not set in the executing shell; all seven live tests compile and are skipped.
User must export the key before running `./gradlew liveTest`.

## Known Stubs / Deferred Items

- **Phase7Baseline.REFUSAL_RATE_PERCENT = Double.NaN** — preserved from Plan 08-01 Case B.
  Until either (a) the 20-query suite is retroactively run against pre-P8 `main` to backfill
  the baseline, or (b) the parity check is redefined as an absolute band, the refusal-parity
  test will fail loud on any live run. This is the explicit contract and is not a blocker for
  Plan 08-04 being "complete" — the code infrastructure is in place.
- **No YAML `supports-structured-output` flips** — no model has been observed to 400 on
  `response_format: json_schema` because no live matrix run has executed. If a live run flags
  one, flip that single row to `false` in `application.yaml` and re-run per Plan 08-04 Task 2
  guidance.
- **INTENT_SYSTEM_VI prompt unchanged** — no live misclassification data available to tune
  against. Current wording (Plan 08-02) is the baseline.
- **Fixture refinement deferred** — the 20-query YAML already covers the canonical topic
  spread prescribed by Step 1 of Plan Task 1. Wording tuning is data-driven and requires a
  live pass-rate baseline to identify weak topics.

## Threat Flags

None. The three files are all in `src/test/java` and the new `@TestConfiguration` is only
loaded under `@SpringBootTest` for the live IT class — it does not affect the production
bean graph. `OPENROUTER_API_KEY` flows through `spring.ai.openai.api-key` property only
when present (no new logging surface).

## Self-Check: PASSED

**Files created/modified (all confirmed present):**
- `src/test/java/com/vn/traffic/chatbot/chat/regression/VietnameseRegressionIT.java` — FOUND
- `src/test/java/com/vn/traffic/chatbot/chat/regression/StructuredOutputMatrixIT.java` — FOUND
- `src/test/java/com/vn/traffic/chatbot/chat/intent/IntentClassifierIT.java` — FOUND

**Commits on `gsd/phase-08-structured-output-groundingguardadvisor`:**
- `69708e5` test(08-04): implement VietnameseRegressionIT GREEN — FOUND
- `d5aa697` test(08-04): implement StructuredOutputMatrixIT + IntentClassifierIT GREEN — FOUND

**Build evidence:**
- `./gradlew compileTestJava --no-daemon` → BUILD SUCCESSFUL
- `./gradlew test --no-daemon` → BUILD SUCCESSFUL (live tests correctly skipped without key)

## Phase 8 Ready for Verification

Plan 08-04 closes the Wave-3 code work for Phase 8. All three RED stubs from Plan 08-01 are
upgraded to GREEN-ready live ITs. The non-live test suite remains green. Phase 8 is ready for
`/gsd-verify-phase 8` once the user runs `./gradlew liveTest` with `OPENROUTER_API_KEY` set
and confirms the seven expected GREENs (or iterates on prompt/YAML per Plan Task 2's
feedback loop).
