---
phase: 09-modular-rag-prompt-caching
plan: 04
status: code_complete — awaiting live-LLM gate (Task 5)
completed_at: 2026-04-18
---

# Plan 09-04 Summary — Gap Closure (G4 fix, G5 defer, G6 document, diacritic resync)

## Task 1 — G4 VARCHAR(36) overflow (COMPLETE)

**Source diff** (`ChatService.java` lines 80–81, plus new static helper):

Before:
```java
String memConvId = (conversationId != null && !conversationId.isBlank())
        ? conversationId : "ephemeral-" + UUID.randomUUID();
```

After:
```java
String memConvId = buildMemoryConversationId(conversationId);
// ...
static String buildMemoryConversationId(String callerConversationId) {
    return (callerConversationId != null && !callerConversationId.isBlank())
            ? callerConversationId : UUID.randomUUID().toString();
}
```

**Grep of `src/` for `"ephemeral-"`**: pre-fix → 1 hit (ChatService.java:81); post-fix → 0 hits.
Candidate A (bare UUID) chosen over Candidate B (Liquibase widen) — avoids migration per solo-dev simplicity.

**Unit test** (`ChatServiceEphemeralConversationIdTest`): 5 assertions (null/blank/empty → bare 36-char UUID matching regex; caller pass-through; uniqueness). GREEN on HEAD.

**RED proof (arithmetic)**: pre-fix formula = `"ephemeral-" + UUID.randomUUID()` = 10 chars + 36 chars = **46 chars** > VARCHAR(36). Any `assertThat(result).hasSize(36)` run against the pre-fix helper would return size=46 → AssertionError. Extraction-based testing makes a literal git-stash RED capture noisy (helper doesn't exist pre-fix → compile error, not assertion failure); arithmetic proof plus the length assertion is the audit trail.

**PITFALLS.md Sub-step 1c** — Pitfall 10 "How to avoid" diff:

Before: `pass a sentinel conversation ID (e.g. \`"ephemeral-" + UUID\`) or use a no-op \`ChatMemory\` bean`
After: `use the bare 36-char UUID (\`UUID.randomUUID().toString()\`) — \`SPRING_AI_CHAT_MEMORY.conversation_id\` is \`VARCHAR(36)\`; prefixes overflow. See plan 09-04 G4 resolution.`

Commit: `02eedd3`.

## Task 2 — G5 IntentClassifier misroute (INVESTIGATED → DEFERRED to plan 09-05)

**Root cause: H1 CONFIRMED.** Evidence — `ChatClientConfig.java:154-164`:

```java
ChatClient client = ChatClient.builder(chatModel)
        .defaultAdvisors(
                guardIn, memoryAdvisor, ragAdvisor, citationStash,
                noOpCache, validationAdvisor, guardOut)   // validationAdvisor bound to LegalAnswerDraft
        .build();
map.put(entry.id(), client);
```

Every `ChatClient` entry in `chatClientMap` carries `validationAdvisor` (bound to `LegalAnswerDraft.class` per `ChatClientConfig.java:100`) + the full P8 D-04 chain. `IntentClassifier.classify(...).entity(IntentDecision.class)` is therefore coerced through the LegalAnswerDraft schema → `BeanOutputConverter` parse failure → caught at `IntentClassifier.java:52` → fallback to `IntentDecision(LEGAL, 0.0)` per D-02.

**Fix-in-place estimate**: sibling `@Bean Map<String, ChatClient> intentChatClientMap(...)` built from the same `OpenAiChatModel`s but without any `defaultAdvisors(...)`, plus `IntentClassifier` `@Qualifier` switch. ~30–50 LOC across `ChatClientConfig.java` + `IntentClassifier.java`. Exceeds Plan 09-04 Task 2 scope rule (≤ 1 file, ≤ 30 LOC).

**Disposition**: DEFERRED → **plan 09-05 recommended: "IntentClassifier dedicated ChatClient wiring"**.

**User impact while deferred**: Chitchat/off-topic messages silently fall through the full RAG+validation pipeline (slower, higher token cost) but the `GroundingGuardOutputAdvisor` + `AnswerComposer.composeRefusal()` produce functionally correct refusals. No wrong-answer behaviour — D-02 safety net holds.

## Task 3 — G6 Phase7Baseline NaN (DOCUMENTED)

`Phase7Baseline.java` Javadoc gains a new `<p>TODO (Plan 09-04): ...` paragraph explaining the intentional non-backfill and cross-referencing Plan 08-04 Task 1 as the long-term owner. `REFUSAL_RATE_PERCENT = Double.NaN` line byte-for-byte UNCHANGED.

`09-VERIFICATION.md` gains a "Live-Run UAT Gaps (amended 2026-04-18)" table documenting G1–G6 dispositions. G6 row explicitly states "DEFERRED under Plan 09-04 — Phase7Baseline.REFUSAL_RATE_PERCENT stays NaN ... refusalRateWithinTenPercentOfPhase7Baseline is expected-RED in the live re-run and is NOT a Phase-9 blocker."

Grep: `grep -c "Plan 09-04" Phase7Baseline.java` = 1; `grep -c "DEFERRED under Plan 09-04" 09-VERIFICATION.md` = 1.

## Task 4 — 09-03-PLAN diacritic resync (DOCS-ONLY)

25 ASCII-only Vietnamese substrings → 0. Post-fix counts: `Không thêm` = 10, `phải là mảng JSON` = 7, `Điều 6 Nghị định 100/2019/NĐ-CP` = 1. No `.java` files touched. All assertions now match `LegalQueryAugmenter.java` lines 78–99 (commit 45baf54) byte-for-byte.

Commit: `b1cf7a7` (Tasks 2/3/4 combined — docs + investigation only).

## Task 5 — Live-LLM re-run (HUMAN HANDOFF)

Requires `OPENROUTER_API_KEY` in the operator's shell plus `docker compose up -d postgres`. Run:

```bash
./gradlew liveTest --tests "*VietnameseRegressionIT"
./gradlew liveTest --tests "*CitationFormatRegressionIT"
./gradlew liveTest --tests "*EmptyContextRefusalIT"
```

**Expected per-method outcomes (per G4/G5/G6 dispositions):**

| Test method | Expected | Gap closed? |
|-------------|----------|-------------|
| `twentyQueryRegressionSuiteAtLeast95Percent` | ≥19/20 GREEN | G1+G2+G3+G4 |
| `twoTurnConversationMemoryWorks` | GREEN | G4 (already GREEN, confirm) |
| `refusalRateWithinTenPercentOfPhase7Baseline` | **RED-as-expected** (NaN comparison) | G6 deferred — NOT a blocker |
| `CitationFormatRegressionIT` | GREEN | ARCH-05 |
| `EmptyContextRefusalIT` | GREEN | T-9-02 |

**Failure triage:**
- `value too long for type character varying(36)` → G4 fix did not land; app not restarted.
- `BeanOutputConverter could not parse ... IntentDecision` → G5 misroute visible in logs; NOT a gate blocker because D-02 fallback passes through to LEGAL and tests still evaluate the final answer shape. Count log occurrences for 09-05 planning.
- `property 'confidence' is not defined` → G1 regressed (escalate).
- `string found, array expected` → G2 regressed (escalate).

UAT item 6 (post-deploy 50-sample trust-tier SQL audit, D-03) remains deferred to the manual post-deploy workflow.

## Byte-for-Byte Parity Assertions (unchanged)

- `LegalAnswerDraft.java` — UNCHANGED.
- `LegalQueryAugmenter.java` — UNCHANGED (09-03 fix in `45baf54` is authoritative).
- `ChatClientConfig.validationAdvisor` bean (D-10, D-13) — UNCHANGED.
- No new Liquibase changeset.
- No new feature flag / `@ConfigurationProperties` / WireMock harness / perf baseline.

## Deferred Items Surviving Plan 09-04

- **CACHE-01** — no successor phase assigned (09-CONTEXT.md D-07, 09-VERIFICATION.md escalation note).
- **G5** — plan 09-05 recommended for IntentClassifier dedicated ChatClient wiring.
- **G6 / Phase7Baseline backfill** — owned by Plan 08-04 Task 1.
- **UAT item 6** — manual post-deploy SQL audit per D-03.

## Commits

- `02eedd3` — Task 1 (G4 UUID fix + test + PITFALLS.md).
- `b1cf7a7` — Tasks 2/3/4 (G5 defer, G6 doc, diacritic resync).
