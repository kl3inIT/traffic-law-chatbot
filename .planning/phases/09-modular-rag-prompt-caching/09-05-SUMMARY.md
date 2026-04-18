---
phase: 09-modular-rag-prompt-caching
plan: 05
status: code_complete — awaiting live IntentClassifierIT re-run
completed_at: 2026-04-19
---

# Plan 09-05 Summary — G5 Close-Out (IntentClassifier Dedicated ChatClient Wiring)

## Root Cause (Plan 09-04 investigation)

`ChatClientConfig.chatClientMap` binds `validationAdvisor` (= `StructuredOutputValidationAdvisor` with `outputType(LegalAnswerDraft.class)`) to every entry. `IntentClassifier.classify(...).entity(IntentDecision.class)` routed through that chain → `BeanOutputConverter` parse failure → silent fallback to `IntentDecision(LEGAL, 0.0)` per D-02.

## Fix

### 1. `ChatClientConfig.java`
- Extracted `buildChatModel(entry, modelProperties)` private helper (OpenAiApi + OpenAiChatOptions + OpenAiChatModel build — previously inlined in the `chatClientMap` loop).
- Refactored `chatClientMap` to call the helper — advisor chain + order UNCHANGED (ARCH-01/ARCH-05 byte-for-byte preserved).
- Added `@Bean("intentChatClientMap")` built from the SAME `OpenAiChatModel` instances but with NO `defaultAdvisors(...)` — plain `ChatClient.builder(chatModel).build()`.

### 2. `IntentClassifier.java`
- Added `@Qualifier("intentChatClientMap")` on the injected `chatClientMap` field (2-line change: import + annotation).

### 3. `IntentClassifierWiringTest.java` (NEW)
- Reflection check: `chatClientMap` field carries `@Qualifier("intentChatClientMap")`.
- Constructor smoke-test with a stub map.

## Byte-for-Byte Parity Assertions

- `chatClientMap` advisor ordering UNCHANGED (7 advisors, same slots: GuardIn+100 → Memory+200 → RAG+300 → CitationStash+310 → NoOpCache+500 → Validation+1000 → GuardOut-100).
- `validationAdvisor` bean UNCHANGED (still `outputType(LegalAnswerDraft.class)`, `maxRepeatAttempts(1)`, order +1000).
- `LegalQueryAugmenter` / `LegalAnswerDraft` / `CitationStashAdvisor` / `RetrievalAugmentationAdvisor` — UNCHANGED.
- No new Liquibase changeset, no new feature flag, no new `@ConfigurationProperties`.

## Verification

- `./gradlew test` — GREEN (full suite).
- `./gradlew test --tests "*IntentClassifierWiringTest*" --tests "*IntentClassifierTest*"` — GREEN.

## Pending Live Verification

`./gradlew liveTest --tests "*IntentClassifierIT"` — should now return distinct LEGAL/CHITCHAT/OFF_TOPIC classifications on canonical fixtures (baseline was LEGAL-only due to misroute). Requires `OPENROUTER_API_KEY`.

## Out of Scope

- G7 (retrieval/grounding quality, 16/20 VN regression `fact=false`) — orthogonal; needs separate plan.
- CACHE-01 — still unassigned to any phase.
