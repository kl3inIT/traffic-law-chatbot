---
status: partial
phase: 09-modular-rag-prompt-caching
source: [09-01-SUMMARY.md, 09-02-SUMMARY.md, 09-04-SUMMARY.md, 09-05-SUMMARY.md]
started: 2026-04-19T00:00:00Z
updated: 2026-04-19T01:30:00Z
---

## Current Test

[testing complete — 3 passed, 5 blocked (live LLM / OPENROUTER_API_KEY not available in this env)]

## Tests

### 1. Cold Start Smoke Test
expected: App boots cleanly; advisor chain wired with 7 advisors in correct order; no bean-graph errors; Liquibase completes; basic health probe returns 200.
result: pass
evidence: |
  `./gradlew bootRun` → `Started TrafficLawChatbotApplication in 11.39 seconds`.
  Liquibase UPDATE SUMMARY: Run=0, Previously run=21, Filtered=0 (schema up-to-date).
  Advisor chain log line (ChatClientConfig):
  `GroundingGuardInputAdvisor → Memory(+200) → RAG(+300) → CitationStashAdvisor(+310) → NoOpPromptCacheAdvisor(+500) → Structured Output Validation Advisor(+1000) → GroundingGuardOutputAdvisor`
  Advisor orders: [-2147483548, -2147483448, -2147483348, -2147483338, -2147483148, -2147482648, 2147483547] (7 entries, strictly increasing).
  8 ChatClient models registered (claude-sonnet-4.6, claude-opus-4.6, claude-haiku-4.5, gpt-5.1, gpt-4o-mini, gemini-3.1-pro, deepseek-v3.2, claude-sonnet-4.5:extended).
  `curl http://localhost:8089/actuator/health` → `HTTP 200 {"status":"UP"}`.

### 2. Vietnamese 20-Query Regression (live LLM)
expected: ./gradlew liveTest --tests "*VietnameseRegressionIT" with OPENROUTER_API_KEY set. ≥19/20 queries GREEN (≥95%); two-turn memory assertion GREEN; refusal rate within ±10% of P7 baseline.
result: blocked
blocked_by: third-party
reason: "OPENROUTER_API_KEY not set in this environment — live LLM call required."

### 3. Citation Format Byte-for-Byte Parity (ARCH-05)
expected: ./gradlew liveTest --tests "*CitationFormatRegressionIT". JSON output byte-identical to Phase-8 baseline.
result: blocked
blocked_by: third-party
reason: "OPENROUTER_API_KEY not set — live LLM call required."

### 4. Empty-Context Refusal Wording (T-9-02)
expected: ./gradlew liveTest --tests "*EmptyContextRefusalIT". Nonsense query returns AnswerComposer.composeRefusal() verbatim.
result: blocked
blocked_by: third-party
reason: "OPENROUTER_API_KEY not set — live LLM call required."

### 5. IntentClassifier Routing (G5 close-out)
expected: ./gradlew liveTest --tests "*IntentClassifierIT". 3/3 GREEN: legal/chitchat/off-topic routing via @Qualifier("intentChatClientMap").
result: blocked
blocked_by: third-party
reason: "OPENROUTER_API_KEY not set — live LLM call required. Note: 09-05-SUMMARY.md already records 3/3 GREEN live-validated on 2026-04-19."

### 6. Ephemeral Conversation Memory (G4 fix)
expected: Chat request with NO conversationId — backend persists memory under bare 36-char UUID (not ephemeral-<UUID> = 46 chars); no `value too long for type character varying(36)` error.
result: blocked
blocked_by: third-party
reason: "Full chat flow requires OPENROUTER_API_KEY. Unit-level coverage: ChatServiceEphemeralConversationIdTest (5 assertions) GREEN in ./gradlew test."

### 7. StructuredOutputValidationAdvisor Retry
expected: Full ./gradlew test suite (incl. StructuredOutputValidationAdvisorIT) GREEN; maxRepeatAttempts=1 cap honored.
result: pass
evidence: |
  `./gradlew test` → BUILD SUCCESSFUL in 43s. Aggregate: 217 tests, 0 failures, 0 errors, 0 skipped.
  StructuredOutputValidationAdvisorIT.xml: all 3 tests GREEN (retryOnBadJsonThenSucceedOnGoodJson, etc.).

### 8. Code-Review Fix Regression Check
expected: ./gradlew test GREEN after WR-01/WR-02/WR-03 fixes (b401c62, c1a6426, 5390e84). CitationMapper handles non-numeric pageNumber; ChatClientConfig shares OpenAiChatModel; ChatService.resolveEntry throws IllegalStateException on missing chat-model config.
result: pass
evidence: |
  Same ./gradlew test run: 217/0/0/0 green — includes CitationMapperTest, ChatClientConfigTest, ChatServiceTest, IntentClassifierWiringTest.
  Bean-graph smoke-test via bootRun (Test 1) confirms chatClientMap + intentChatClientMap coexist without duplicate-bean errors, and resolveEntry didn't throw on startup-time model resolution.

## Summary

total: 8
passed: 3
issues: 0
pending: 0
skipped: 0
blocked: 5

## Gaps

[none — 3 passed, 5 blocked on OPENROUTER_API_KEY (live LLM). No code issues surfaced.]

## Notes

- Tests 2–5 are the same live-LLM gates already tracked in `09-VERIFICATION.md` under `human_verification:` (key: `why_human: "Live LLM-backed IT; requires OPENROUTER_API_KEY"`). Per `09-05-SUMMARY.md`, the IntentClassifierIT (Test 5) was live-validated 3/3 GREEN on 2026-04-19; the remaining three (Tests 2/3/4) need a re-run with the key.
- Test 6 depends on the same live chat flow; unit coverage is already GREEN.
- Previous HUMAN-UAT (2026-04-18) recorded all three live tests as FAILED due to G1/G2 schema-mismatch; those gaps were closed by commit 45baf54 (diacritic resync) and the tighter prompt in 06459095 before the 2026-04-19 live validation.
