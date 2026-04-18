---
phase: 09-modular-rag-prompt-caching
plan: 05
status: complete — live-validated 2026-04-19
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
- Dropped Lombok `@RequiredArgsConstructor`; wrote an explicit constructor with `@Qualifier("intentChatClientMap")` on the `chatClientMap` parameter.
- **Why explicit constructor:** Lombok `@RequiredArgsConstructor` does NOT copy field-level `@Qualifier` onto the generated constructor parameter by default; the first live run showed Spring silently injected the default `chatClientMap`, traffic still passed through `StructuredOutputValidationAdvisor`, and `BeanOutputConverter` failed on `IntentDecision`. Added `lombok.config` with `lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier` (project-wide future-proofing) but the explicit constructor in `IntentClassifier` is the load-bearing fix.

### 3. `IntentClassifierWiringTest.java` (NEW)
- Reflection check: constructor param `chatClientMap` carries `@Qualifier("intentChatClientMap")` (not a field-level check — field is injected through the explicit constructor).
- Constructor smoke-test with a stub map.

### 4. `lombok.config` (NEW, project root)
- `lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier` — any future Lombok-generated constructor in this codebase will carry `@Qualifier` automatically.

## Byte-for-Byte Parity Assertions

- `chatClientMap` advisor ordering UNCHANGED (7 advisors, same slots: GuardIn+100 → Memory+200 → RAG+300 → CitationStash+310 → NoOpCache+500 → Validation+1000 → GuardOut-100).
- `validationAdvisor` bean UNCHANGED (still `outputType(LegalAnswerDraft.class)`, `maxRepeatAttempts(1)`, order +1000).
- `LegalQueryAugmenter` / `LegalAnswerDraft` / `CitationStashAdvisor` / `RetrievalAugmentationAdvisor` — UNCHANGED.
- No new Liquibase changeset, no new feature flag, no new `@ConfigurationProperties`.

## Verification

- `./gradlew test` — GREEN (full suite).
- `./gradlew test --tests "*IntentClassifierWiringTest*" --tests "*IntentClassifierTest*"` — GREEN.
- `./gradlew liveTest --tests "*IntentClassifierIT"` — **3/3 GREEN (2026-04-19)**: `legalQuestionClassifiedAsLegal`, `greetingClassifiedAsChitchat`, `offTopicQuestionClassifiedAsOffTopic`. Baseline pre-fix was 1/3 (LEGAL-only via D-02 silent fallback). G5 CLOSED.

## Out of Scope

- G7 (retrieval/grounding quality, 16/20 VN regression `fact=false`) — orthogonal; needs separate plan.
- CACHE-01 — still unassigned to any phase.
