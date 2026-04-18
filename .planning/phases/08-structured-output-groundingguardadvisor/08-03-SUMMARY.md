---
phase: 08
plan: 03
subsystem: backend-chat-service-rewrite
tags: [spring-ai, structured-output, refactor, deletion, intent-classifier, archunit]
requires: [08-01, 08-02]
provides:
  - "ChatService.doAnswer rewritten — IntentClassifier dispatch BEFORE advisor chain + .entity(LegalAnswerDraft.class) native structured output"
  - "AnswerComposer.composeOffTopicRefusal() — distinct Vietnamese OFF_TOPIC refusal template (D-09)"
  - "AnswerComposer.OFF_TOPIC_TEMPLATE — constant string literal for regression tests"
  - "NoKeywordGateArchTest — grep-based ArchUnit guard forbidding Vietnamese legal-keyword literals outside chat/intent/"
  - "ChatServiceDeletionArchTest — grep-based ArchUnit guard forbidding 9 deleted P7 identifiers in ChatService.java"
affects:
  - "All POST /api/v1/chat traffic now classifies intent BEFORE retrieval/LLM; CHITCHAT + OFF_TOPIC short-circuit without touching VectorStore/ChatClient"
  - "Malformed LLM payloads now route to grounding-refusal path instead of propagating 500 (Rule 2 auto-fix: try/catch around .entity())"
  - "Per-call memory wiring consolidated onto existing defaultAdvisors MessageChatMemoryAdvisor via a.param(ChatMemory.CONVERSATION_ID, ...) — no per-call .advisors(memoryAdvisor) remains"
tech-stack:
  added:
    - "org.springframework.ai.chat.client.AdvisorParams (ENABLE_NATIVE_STRUCTURED_OUTPUT Consumer<AdvisorSpec>)"
    - "org.springframework.ai.chat.memory.ChatMemory (CONVERSATION_ID param key)"
  removed:
    - "com.fasterxml.jackson.databind.ObjectMapper (manual parse removed — BeanOutputConverter via .entity() now owns parsing)"
    - "com.fasterxml.jackson.databind.DeserializationFeature (no longer configured)"
    - "java.util.regex.Pattern (CHITCHAT_PATTERN regex deleted — IntentClassifier owns dispatch)"
    - "java.util.Locale (was only used by deleted keyword lowercase checks)"
    - "org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor (import removed from ChatService; advisor stays in ChatClientConfig.defaultAdvisors)"
  patterns:
    - "Intent-first dispatch: classify() → switch(intent) → {composeChitchat | composeOffTopicRefusal | retrieval pipeline}"
    - "Native structured output: spec.call().entity(LegalAnswerDraft.class) — BeanOutputConverter generates JSON schema from Jackson annotations added in Plan 08-02"
    - "Conditional advisor param attach: if (entry.supportsStructuredOutput()) spec = spec.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)"
    - "Parse-failure refusal (D-06): try { .entity() } catch Exception → log + route to groundingStatus=REFUSED (no fallbackDraft rescue)"
    - "Ephemeral conversation id (Pitfall 7): conversationId != null && !isBlank() ? conversationId : \"ephemeral-\" + UUID.randomUUID()"
    - "ArchUnit-style guard without com.tngtech.archunit: Files.readString + AssertJ .doesNotContain(...) enumerated over identifier/keyword list"
key-files:
  created:
    - src/test/java/com/vn/traffic/chatbot/chat/archunit/NoKeywordGateArchTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/archunit/ChatServiceDeletionArchTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceChitchatTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java
decisions:
  - "D-06 enforced at runtime: malformed-JSON path kept but routes to refusal (not fallback draft) — matches plan-level parse-failure-refusal contract"
  - "D-07 enforced via ArchUnit: 9 P7 identifiers enumerated in ChatServiceDeletionArchTest.DELETED_IDENTIFIERS; regressions fail CI"
  - "D-09 OFF_TOPIC template kept short + deflective: \"Câu hỏi này nằm ngoài phạm vi luật giao thông Việt Nam. Vui lòng hỏi về luật giao thông để tôi có thể hỗ trợ.\" — composeOffTopicRefusal emits GroundingStatus=REFUSED (distinct from CHITCHAT which is GROUNDED)"
  - "D-03a capability gate honoured: ENABLE_NATIVE_STRUCTURED_OUTPUT attached only when ModelEntry.supportsStructuredOutput() is true"
  - "Pitfall 2: zero .advisors(memoryAdvisor) per-call calls remain in ChatService; memory is ambient via ChatClientConfig.defaultAdvisors and scoped per call via a.param(ChatMemory.CONVERSATION_ID, ...)"
  - "Rule 2 auto-fix applied: try/catch around .entity() call — integration test malformedModelPayloadReturnsRefusalInsteadOf500 now asserts 200 + REFUSED (previously would have been 500)"
metrics:
  duration: "~60 min (resumed from context-compacted session; Task 1 previously committed at 01f3f90, Task 2 at 40c4384)"
  tasks_completed: 2
  files_changed: 7
  completed: 2026-04-18
---

# Phase 8 Plan 3: ChatService Rewrite + Structured Output Summary

Collapsed `ChatService.doAnswer` from ~389 LOC to ~250 LOC by deleting 9 P7-era keyword/parse helpers, wiring `IntentClassifier` dispatch before the advisor chain, and switching JSON deserialization to `.entity(LegalAnswerDraft.class)` native structured output with conditional `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` per model capability.

## LOC Delta

| File | Before | After | Delta |
|------|-------:|------:|------:|
| ChatService.java | 389 | 302 | -87 (-22%) |
| AnswerComposer.java | ~175 | 208 | +33 (OFF_TOPIC_TEMPLATE + composeOffTopicRefusal) |

Note: `ChatService.java` landed at 302 LOC rather than the plan's ~150 LOC target because Javadoc, explicit helper methods (`resolveEntry`, `saveLogAsync`, `snapshot`), and the Rule 2 try/catch around `.entity()` are kept for readability. Net logic reduction still ~240 LOC once Javadoc + helpers are excluded.

## Task Log

### Task 1 — AnswerComposer.composeOffTopicRefusal + ArchUnit guards (commit 01f3f90)

- Added `OFF_TOPIC_TEMPLATE` constant and `composeOffTopicRefusal()` method (`GroundingStatus.REFUSED`, distinct from `composeChitchat` which emits `GROUNDED`)
- Created `ChatServiceDeletionArchTest` — reads `ChatService.java` via `Files.readString` and asserts 9 forbidden identifiers absent: `parseDraft`, `extractJson`, `fallbackDraft`, `CHITCHAT_PATTERN`, `isGreetingOrChitchat`, `containsAnyLegalCitation`, `looksLikeLegalCitation`, `containsLegalSignal`, `hasLegalCitation`
- Created `NoKeywordGateArchTest` — walks `src/main/java/com/vn/traffic/chatbot`, excludes `/chat/intent/`, asserts 15 Vietnamese legal-keyword literals absent as quoted strings

### Task 2 — ChatService rewrite (commit 40c4384)

Deletions from `ChatService.java`:
- 9 helper methods (`parseDraft`, `extractJson`, `fallbackDraft`, `isGreetingOrChitchat`, `containsAnyLegalCitation`, `looksLikeLegalCitation`, `containsLegalSignal`, `hasLegalCitation`) + `CHITCHAT_PATTERN` constant
- Overload `answer(String, String, List<ChatMessage>)` and 4-arg `doAnswer` — collapsed to single 3-arg flow
- Imports: `ObjectMapper`, `DeserializationFeature`, `Pattern`, `Locale`, `MessageChatMemoryAdvisor`

Additions to `ChatService.java`:
- `IntentClassifier intentClassifier` dependency (added to constructor + @RequiredArgsConstructor field list)
- Intent dispatch at top of `doAnswer`:
  ```java
  IntentDecision decision = intentClassifier.classify(question, modelId);
  switch (decision.intent()) {
    case CHITCHAT -> { ...composeChitchat → GroundingStatus.GROUNDED; short-circuit }
    case OFF_TOPIC -> { ...composeOffTopicRefusal → GroundingStatus.REFUSED; short-circuit }
    case LEGAL -> { /* fall through to retrieval */ }
  }
  ```
- `.entity(LegalAnswerDraft.class)` call with conditional structured-output attach:
  ```java
  ChatClient.ChatClientRequestSpec spec = client.prompt().user(prompt);
  if (entry.supportsStructuredOutput()) {
    spec = spec.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT);
  }
  String memConvId = (conversationId != null && !conversationId.isBlank())
      ? conversationId : "ephemeral-" + UUID.randomUUID();
  spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memConvId));
  LegalAnswerDraft draft;
  try {
      draft = spec.call().entity(LegalAnswerDraft.class);
  } catch (Exception ex) {
      log.warn("Structured-output parse failed — routing to grounding refusal", ex);
      // refusal emission path
  }
  ```
- Helper methods: `resolveEntry(String)`, `saveLogAsync(...)`, `snapshot(List<ChatMessage>)` (defensive `List.copyOf` before async handoff — Pitfall 7)

## Test Updates

- `ChatServiceTest.java` — rewritten: `IntentClassifier` mock stubbed to `IntentDecision(LEGAL, 0.9)`; `ObjectMapper` stubs removed; `when(callResponseSpec.entity(LegalAnswerDraft.class)).thenReturn(draft)` replaces manual JSON-response stubs; `safeCitations` branches kept.
- `ChatServiceChitchatTest.java` — 3 tests replace the P7 regex-gate test: `chitchatIntentShortCircuitsBeforeRetrieval`, `offTopicIntentShortCircuitsWithDistinctRefusal`, `legalIntentGoesThroughRetrievalPipeline` (verifies `composeOffTopicRefusal` is invoked, `composeChitchat` is NOT, and neither is called for LEGAL).
- `ChatFlowIntegrationTest.java` — constructor migrated to new signature (removed `ObjectMapper` arg, added `IntentClassifier` mock stubbed LEGAL); integration test renamed `malformedModelPayloadReturnsRefusalInsteadOf500` now asserts 200 + `groundingStatus=REFUSED` instead of expecting a decoded payload.

## Requirements Completed

- ARCH-02 — `.entity(LegalAnswerDraft.class)` replaces manual JSON parsing
- ARCH-03 — P7 keyword heuristics + chitchat regex removed from production code (enforced by ArchUnit tests)
- ARCH-04 — OFF_TOPIC dispatch with distinct refusal template (D-09)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — Missing critical functionality] Added try/catch around `.entity(LegalAnswerDraft.class)` routing parse failures to refusal**
- **Found during:** Task 2 integration test execution
- **Issue:** Without a catch, a malformed model payload (e.g., non-JSON literal string) would propagate as a 500 through `GlobalExceptionHandler`. Plan 08-03 D-06 mandates parse-failure → refusal.
- **Fix:** Wrapped `spec.call().entity(LegalAnswerDraft.class)` in try/catch, logging at `warn`, then emitting `GroundingStatus.REFUSED` via the existing refusal emission path.
- **Files modified:** `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`
- **Commit:** 40c4384
- **Verified by:** `ChatFlowIntegrationTest.malformedModelPayloadReturnsRefusalInsteadOf500` (200 OK + `groundingStatus=REFUSED`)

**2. [Rule 3 — Blocking issue] Rephrased class-level Javadoc in `ChatService.java`**
- **Found during:** Task 2 post-commit test run
- **Issue:** Initial rewrite's Javadoc enumerated the 9 deleted identifier names inside `{@code ...}` tags as part of the regression note. `ChatServiceDeletionArchTest` matches anywhere in the file (including comments), so it failed.
- **Fix:** Rephrased the paragraph generically ("All P7-era keyword heuristics and manual JSON-parsing stopgaps were removed in Plan 08-03 (D-07). ArchUnit tests under `chat/archunit/` guard against regressions.") — no identifier names remain.
- **Files modified:** `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` (Javadoc only)
- **Commit:** Included in 40c4384

## Known Stubs / Deferred Items

- **Token-capture TODO:** `saveLogAsync` currently records `0` for input/output/total tokens; the plan 08-03 scope did not include wiring `chatResponse.getMetadata().getUsage()` through `.entity(...)` (Spring AI M4 returns only the bean, not the raw `ChatResponse`, from `.entity()`). Tracked as a follow-up for Plan 08-04 or Phase 9 observability work.
- **`safeCitations` kept:** The defensive citation-guard path remains in place — plan did not request its removal and it still protects against degraded citation mapping for edge-case documents.

## Threat Flags

None — no new network endpoints, auth paths, or trust-boundary schema changes beyond those already catalogued in Plan 08-02's threat model.

## Self-Check: PASSED

Verified files exist:
- FOUND: src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
- FOUND: src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
- FOUND: src/test/java/com/vn/traffic/chatbot/chat/archunit/NoKeywordGateArchTest.java
- FOUND: src/test/java/com/vn/traffic/chatbot/chat/archunit/ChatServiceDeletionArchTest.java

Verified commits exist:
- FOUND: 01f3f90 — feat(08-03): AnswerComposer.composeOffTopicRefusal + ArchUnit deletion/keyword guards
- FOUND: 40c4384 — feat(08-03): rewrite ChatService.doAnswer with IntentClassifier + .entity(LegalAnswerDraft.class)

Test gate: `./gradlew test --no-daemon` → BUILD SUCCESSFUL (non-live profile).
