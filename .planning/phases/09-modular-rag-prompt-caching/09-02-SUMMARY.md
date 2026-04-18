---
phase: 9
plan: "09-02"
subsystem: chat
tags: [modular-rag, spring-ai, advisor-chain, structured-output, validation-retry, arch-01-closeout]
requires:
  - 09-01-SUMMARY  # Plan 01 wired RAG + CitationStash; left NoOpValidation at +1000
provides:
  - StructuredOutputValidationAdvisor (real Spring AI 2.0.0-M4 advisor at +1000)
affects:
  - ChatClientConfig (defaultAdvisors list now contains zero NoOp retrieval/validation placeholders)
tech-stack:
  added:
    - org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor
    - org.springframework.ai.chat.client.advisor.api.Advisor (interface import)
  patterns:
    - JSON schema validation + single-retry amplification cap (maxRepeatAttempts=1, Pitfall 6)
    - Mockito stub of CallAdvisorChain.copy(this) + sequential nextCall(...) for retry ITs
key-files:
  created:
    - src/test/java/com/vn/traffic/chatbot/chat/advisor/StructuredOutputValidationAdvisorIT.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java
    - src/test/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardAdvisorTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/config/ChatClientConfigTest.java
  deleted:
    - src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpValidationAdvisor.java
decisions:
  - Keep maxRepeatAttempts=1 per D-10 (not 0); accept 2× cost cap on rare parse failures (T-9-04 accept)
  - Mock chain.copy(any()) to return same chain (advisor internally calls chain.copy(this).nextCall(...) per Spring AI 2.0.0-M4 source)
metrics:
  duration: "~40m"
  tasks-completed: 1
  files-created: 1
  files-modified: 3
  files-deleted: 1
  tests-passing: 205
  tests-failing: 0
  tests-delta-vs-plan-01: "+3 (retry IT x3) -1 (NoOp validation delegate) +1 (config bean-graph) = +3 net"
  completed: 2026-04-18
---

# Phase 9 Plan 02: StructuredOutputValidationAdvisor Swap Summary

Replaces the Phase-8 `NoOpValidationAdvisor` placeholder with Spring AI
2.0.0-M4 `StructuredOutputValidationAdvisor`, configured with
`outputType(LegalAnswerDraft.class)` + `maxRepeatAttempts(1)`. Closes Phase-8
D-10 and, together with Plan 09-01, closes ARCH-01 (the advisor chain now
contains exactly one NoOp placeholder — `NoOpPromptCacheAdvisor`, deferred
per D-07).

## Commits

| Task | Commit    | Title                                                                                 |
| ---- | --------- | ------------------------------------------------------------------------------------- |
| 1 RED   | `eba118f` | test(09-02): add retry-on-bad-JSON IT for StructuredOutputValidationAdvisor        |
| 1 GREEN | `2f13c9f` | feat(09-02): swap NoOpValidationAdvisor → real StructuredOutputValidationAdvisor   |

## Architecture Changes

### Advisor chain (order-preserved from P8 / Plan 09-01)

```
guardIn                        (HIGHEST_PRECEDENCE + 100)
Memory                         (HIGHEST_PRECEDENCE + 200)
RAG                            (HIGHEST_PRECEDENCE + 300)   <-- Plan 09-01
CitationStash                  (HIGHEST_PRECEDENCE + 310)   <-- Plan 09-01
NoOpPromptCache                (HIGHEST_PRECEDENCE + 500)   <-- D-07 preserved, byte-identical
StructuredOutputValidation     (HIGHEST_PRECEDENCE + 1000)  <-- Plan 09-02 (NEW REAL)
guardOut                       (LOWEST_PRECEDENCE  - 100)
```

### StructuredOutputValidationAdvisor bean

```java
@Bean
public Advisor validationAdvisor() {
    return StructuredOutputValidationAdvisor.builder()
        .outputType(LegalAnswerDraft.class)
        .maxRepeatAttempts(1)
        .advisorOrder(Ordered.HIGHEST_PRECEDENCE + 1000)
        .build();
}
```

- **`outputType`** — `LegalAnswerDraft.class` (8-field slim schema from P7;
  `@JsonClassDescription` + `@JsonPropertyDescription` annotations feed the
  advisor's auto-generated JSON schema).
- **`maxRepeatAttempts=1`** — one ADDITIONAL attempt = 2 total LLM calls
  worst case (Pitfall 6). Verified end-to-end by
  `StructuredOutputValidationAdvisorIT.retryOnBadJsonThenSucceedOnGoodJson`.
- **`advisorOrder`** — `Ordered.HIGHEST_PRECEDENCE + 1000` preserves the
  Phase-8 slot (D-13). Boot log now reads
  `Structured Output Validation Advisor order=...`.

### `chatClientMap(...)` parameter swap

```diff
-            NoOpPromptCacheAdvisor noOpCache,
-            NoOpValidationAdvisor noOpValidation,
+            NoOpPromptCacheAdvisor noOpCache,
+            Advisor validationAdvisor,
             GroundingGuardOutputAdvisor guardOut)
...
                            noOpCache,                      // HIGHEST_PRECEDENCE + 500
-                            noOpValidation,                // HIGHEST_PRECEDENCE + 1000
+                            validationAdvisor,             // HIGHEST_PRECEDENCE + 1000
                            guardOut
```

The `Advisor` interface (`org.springframework.ai.chat.client.advisor.api.Advisor`)
is used as the bean type so the builder-constructed final class remains
injectable by name.

### Retry IT (Wave-4 RED)

`StructuredOutputValidationAdvisorIT` stands up the advisor directly with a
mocked `CallAdvisorChain` — no Spring context, no live key. Three tests:

1. **`retryOnBadJsonThenSucceedOnGoodJson`** — bad JSON → valid JSON
   sequence. Asserts `verify(chain, times(2)).nextCall(...)` and the final
   returned `ChatClientResponse` is the good one with expected field text.
   Resolves **Q-03**.
2. **`exhaustsRetriesWhenAllCallsReturnMalformedJson`** — both calls bad.
   Asserts exactly 2 total calls (loop exits on `counter > maxRepeatAttempts`)
   and the advisor does NOT throw — the last invalid response is returned
   for downstream `BeanOutputConverter` to observe. Validates the
   T-9-04 amplification cap.
3. **`advisorOrderAndNameMatchContract`** — `getOrder() ==
   HIGHEST_PRECEDENCE + 1000`, `getName() == "Structured Output Validation Advisor"`.

Mock stubbing note: Spring AI 2.0.0-M4 internally calls
`callAdvisorChain.copy(this).nextCall(request)`, so the mock stubs both
`chain.copy(any())` → same chain and `chain.nextCall(...)` → sequential
responses.

## ChatClientConfigTest extension

New `beanGraphWiresStructuredOutputValidationAdvisorAndDropsNoOpValidation`:
- `StructuredOutputValidationAdvisor` bean present at order
  `HIGHEST_PRECEDENCE + 1000` (D-13).
- `noOpValidationAdvisor` bean absent (placeholder deleted).
- `noOpPromptCacheAdvisor` bean still present (D-07 preserved).

## Deviations from Plan

None. Task 1 executed exactly as specified. The RED→GREEN TDD cycle
collapsed into a single standalone unit-IT run (the advisor instance under
test is built directly and is independent of `ChatClientConfig` wiring, so
all 3 tests passed immediately post-authoring). This matches the plan's
acknowledgment that "standalone unit ITs, which is acceptable." Separate
RED and GREEN commits retained for gate-compliance traceability.

## Test Results

| Suite                             | Before Plan 09-01 | After Plan 09-01 | After Plan 09-02 |
| --------------------------------- | ----------------- | ---------------- | ---------------- |
| `./gradlew test` (non-live)       | 208 pass          | 202 pass         | **205 pass**     |
| Failures                          | 0                 | 0                | **0**            |
| Net tests added (Plan 09-02)      | —                 | —                | +3 (retry IT) +1 (config bean-graph) −1 (NoOp delegate) = **+3 net** |

Live ITs (`@Tag("live")`, excluded from `./gradlew test`):
- **`VietnameseRegressionIT` + `CitationFormatRegressionIT` + `EmptyContextRefusalIT`**
  — Not runnable in the executor environment (no `OPENROUTER_API_KEY`).
  To be re-run post-merge as part of Phase-9 closeout. Expected parity
  with Plan 09-01 (≥95% regression pass) because the validation advisor
  runs BEFORE `GuardOut` and does not touch refusal wording or citation
  formatting; it only augments the user prompt with a validation-error
  message on parse failure and re-calls the chain.

## NoOpPromptCacheAdvisor — D-07 byte-identical confirmation

```
$ git diff eba118f^..HEAD -- src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpPromptCacheAdvisor.java
(empty — zero changes)
```

Prompt caching remains deferred for Phase 10+ per D-07. `NoOpPromptCacheAdvisor`
stays as the sole NoOp placeholder in the chain. No `CACHE-01` claim
made by this plan.

## Retry-rate observation (Pitfall 6)

Live `liveTest` run is deferred to phase closeout. The future-tightening
candidate documented by the plan — flipping `maxRepeatAttempts=0` if zero
`"validation failed, retrying attempt"` log lines are observed across the
20-query regression — **cannot be evaluated here** and is an open
post-deploy item (T-9-04 monitoring plan: OpenRouter token-usage log
review). The default `maxRepeatAttempts=1` remains in place per D-10.

## Gate Verification

| Gate                                                                        | Status |
| --------------------------------------------------------------------------- | ------ |
| Real `StructuredOutputValidationAdvisor` bean at `HIGHEST_PRECEDENCE+1000` (D-13) | GREEN |
| `outputType(LegalAnswerDraft.class)` (grep-verified)                        | GREEN |
| `maxRepeatAttempts(1)` (grep-verified, D-10)                                | GREEN |
| `NoOpValidationAdvisor.java` deleted                                        | GREEN |
| `NoOpPromptCacheAdvisor.java` byte-identical (D-07)                         | GREEN |
| `NoOpPromptCacheAdvisor` bean still registered                              | GREEN |
| `grep -rn NoOpValidationAdvisor src/main/java src/test/java` → only historical comments (zero code refs) | GREEN |
| Retry IT: `verify(chain, times(2)).nextCall(...)` on bad→good (Q-03)         | GREEN |
| Retry IT: exhausts-retries path caps at 2 calls (T-9-04)                    | GREEN |
| `./gradlew test` → 205/205 pass                                             | GREEN |
| `./gradlew liveTest` regression ≥95% (phase-closeout gate)                  | DEFERRED (no API key in executor env) |

## Requirements / Decisions closeout

- **ARCH-01** (advisor-chain closeout): Satisfied together with Plan 09-01.
  Advisor chain contains only ONE NoOp placeholder after this plan
  (`NoOpPromptCacheAdvisor`, deferred per D-07) — all other slots are real
  Spring AI (`RetrievalAugmentationAdvisor`, `MessageChatMemoryAdvisor`,
  `StructuredOutputValidationAdvisor`) or custom advisors
  (`GroundingGuardInputAdvisor`, `GroundingGuardOutputAdvisor`,
  `CitationStashAdvisor`).
- **ARCH-05**: Spring AI-idiomatic structured-output retry now in place;
  no hand-rolled try/catch around `BeanOutputConverter.convert()`.
- **D-10**: Closed — `StructuredOutputValidationAdvisor` wired with
  `maxRepeatAttempts=1`; retry IT proves exactly 2 chain calls on
  bad-then-good JSON.
- **D-13**: Preserved — advisor order slots unchanged.
- **D-07**: Preserved — `NoOpPromptCacheAdvisor` untouched
  (`CACHE-01` still deferred; no Phase-9 claim).
- **Q-03**: Resolved — retry IT asserts 2 chain calls (1 ADDITIONAL = 2 total).
- **T-9-04**: Accepted — amplification capped at 2×; projected <1% rate;
  post-deploy monitoring via OpenRouter usage log.

## Self-Check: PASSED

- `src/test/java/com/vn/traffic/chatbot/chat/advisor/StructuredOutputValidationAdvisorIT.java` exists on disk.
- `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpValidationAdvisor.java` DELETED (verified via `test ! -f`).
- `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpPromptCacheAdvisor.java` exists (D-07 preserved).
- All required strings present in `ChatClientConfig.java`:
  `StructuredOutputValidationAdvisor.builder()`, `outputType(LegalAnswerDraft.class)`,
  `maxRepeatAttempts(1)`, `HIGHEST_PRECEDENCE + 1000`.
- Commits present on branch `worktree-agent-af170b73`: `eba118f`, `2f13c9f`.
- `./gradlew test`: 205/205 green, 0 failures.
