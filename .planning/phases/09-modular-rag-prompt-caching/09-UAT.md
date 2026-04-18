---
status: complete_with_known_carryover
phase: 09-modular-rag-prompt-caching
source: [09-01-SUMMARY.md, 09-02-SUMMARY.md, 09-04-SUMMARY.md, 09-05-SUMMARY.md]
started: 2026-04-19T00:00:00Z
updated: 2026-04-19T01:40:00Z
---

## Current Test

[testing complete — 6 passed, 2 known-carryover (G6/G7 documented out-of-scope)]

## Tests

### 1. Cold Start Smoke Test
expected: App boots cleanly; advisor chain wired with 7 advisors in correct order; no bean-graph errors; Liquibase completes; health probe returns 200.
result: pass
evidence: |
  `./gradlew bootRun` → `Started TrafficLawChatbotApplication in 11.39 seconds`.
  Liquibase UPDATE SUMMARY: Run=0, Previously run=21.
  Advisor chain log line: `GroundingGuardInputAdvisor → Memory(+200) → RAG(+300) → CitationStashAdvisor(+310) → NoOpPromptCacheAdvisor(+500) → Structured Output Validation Advisor(+1000) → GroundingGuardOutputAdvisor`.
  8 ChatClient models registered. `curl http://localhost:8089/actuator/health` → HTTP 200.

### 2. Vietnamese 20-Query Regression (live LLM)
expected: ≥19/20 queries GREEN; two-turn memory GREEN; refusal rate within ±10% of P7 baseline.
result: partial_known_carryover
evidence: |
  liveTest ran with OPENROUTER_API_KEY loaded from .env:
  - twentyQueryRegressionSuiteAtLeast95Percent() → FAIL: 6/20 pass (14× `relevancy=true fact=false`). This is G7 (retrieval/grounding quality) — documented as "out of scope" in 09-05-SUMMARY.md ("needs separate plan"). NOT a Phase-9 blocker.
  - twoTurnConversationMemoryWorks() → **PASS** (G4 VARCHAR(36) fix validated end-to-end via real persistence).
  - refusalRateWithinTenPercentOfPhase7Baseline() → FAIL: refusal rate 0.00% vs baseline NaN%. This is G6, documented as expected-RED per 09-04-SUMMARY.md + 09-VERIFICATION.md deferred table. NOT a Phase-9 blocker (owned by Plan 08-04 Task 1).

### 3. Citation Format Byte-for-Byte Parity (ARCH-05)
expected: ChatAnswerResponse JSON byte-identical to Phase-8 baseline.
result: pass
evidence: CitationFormatRegressionIT.v1FixtureReplayPreservesCitationsAndSourcesByteForByte() → **GREEN**.

### 4. Empty-Context Refusal Wording (T-9-02)
expected: Nonsense query returns AnswerComposer.composeRefusal() verbatim.
result: pass
evidence: EmptyContextRefusalIT.emptyRetrievalRoutesThroughGroundingGuardRefusalVerbatim() → **GREEN**.

### 5. IntentClassifier Routing (G5 close-out)
expected: 3/3 GREEN — legal/chitchat/off-topic via @Qualifier("intentChatClientMap").
result: pass
evidence: IntentClassifierIT → **3/3 GREEN** (legalQuestionClassifiedAsLegal, greetingClassifiedAsChitchat, offTopicQuestionClassifiedAsOffTopic). Confirms G5 close-out and validation-advisor bypass for intent classification.

### 6. Ephemeral Conversation Memory (G4 fix)
expected: Chat with no conversationId persists memory under bare 36-char UUID; no VARCHAR(36) overflow.
result: pass
evidence: Indirectly validated by VietnameseRegressionIT.twoTurnConversationMemoryWorks() GREEN (memory row persisted + retrieved across turns → G4 fix live-confirmed). Unit coverage: ChatServiceEphemeralConversationIdTest (5 assertions) GREEN.

### 7. StructuredOutputValidationAdvisor Retry
expected: Full suite GREEN; maxRepeatAttempts=1 cap honored.
result: pass
evidence: ./gradlew test → 217/0/0/0. StructuredOutputValidationAdvisorIT: 3/3 GREEN.

### 8. Code-Review Fix Regression Check
expected: ./gradlew test GREEN after WR-01/WR-02/WR-03 fixes (b401c62, c1a6426, 5390e84).
result: pass
evidence: 217/0/0/0. Bean-graph smoke via bootRun confirms chatClientMap + intentChatClientMap coexist without duplicate-bean errors.

## Summary

total: 8
passed: 6
issues: 0
pending: 0
skipped: 0
blocked: 0
known_carryover: 2

## Gaps

[none new — 2 pre-existing carryover items (G6 Phase7Baseline NaN, G7 retrieval fact-grounding) are documented out-of-scope for Phase 9.]

## Notes

- Live test run 2026-04-19 ~01:35 used OPENROUTER_API_KEY loaded from project .env.
- G6 (refusalRate NaN comparison): owned by Plan 08-04 Task 1, acknowledged in 09-VERIFICATION.md deferred table.
- G7 (VN regression `fact=false` on ~14/20 queries): flagged in 09-05-SUMMARY.md "Out of Scope" — retrieval/grounding quality needs a separate plan. This is the next most impactful phase-10 candidate.
- CACHE-01 (prompt caching ttl=1h + cached_tokens IT) remains deferred with no assigned phase (09-CONTEXT.md D-07).
