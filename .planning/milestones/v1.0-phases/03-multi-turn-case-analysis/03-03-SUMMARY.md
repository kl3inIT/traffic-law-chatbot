---
phase: 03-multi-turn-case-analysis
plan: 03
subsystem: chat
status: complete
tags: [phase-3, scenario-analysis, grounding, composition, uat-fixes]
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/chat/service/ScenarioAnswerComposer.java
    - src/test/java/com/vn/traffic/chatbot/chat/ChatScenarioAnalysisIntegrationTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ScenarioAnalysisResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ResponseMode.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/LegalAnswerDraft.java
    - src/main/resources/application.yaml
    - src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/api/ChatContractSerializationTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/api/ChatThreadControllerTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/FactMemoryServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ScenarioAnswerComposerTest.java
decisions:
  - ScenarioAnswerComposer produces Facts→Rule→Outcome→Actions structure, not the Phase 2 conclusion-first answer format.
  - Retrieval question for post-clarification turns reconstructed from the original legal question + collected facts (not the short follow-up message) to ensure adequate vector search signal.
  - FINAL_ANALYSIS is allowed when rememberedFacts is non-empty even under LIMITED_GROUNDING — preserves caution for single-turn weak retrieval while enabling finalization after fact collection.
  - LegalAnswerDraft.scenarioRule and scenarioOutcome changed to List<String> to accept model array output; joined with newlines before use.
  - ChatService.parseDraft() now strips markdown code fences and extracts JSON by {/} boundaries before deserializing.
metrics:
  completed_date: 2026-04-10
  task_commits:
    - f400be6
    - fb90fc5
    - 86fb1b4
    - d0ff8e1
    - 225301e
---

# Phase 03 Plan 03: Grounded Scenario Answer Composition Summary

Phase 3 is complete. The scenario answer composer delivers grounded multi-turn final-analysis responses with Facts→Rule→Outcome structure. All UAT gaps were diagnosed and fixed. Phase 3 shipped via PR #4.

## Task Outcomes

### Task 1 — Complete: Scenario answer composer
- Created `ScenarioAnswerComposer` with `compose(GroundingStatus, ThreadFact[], LegalAnswerDraft, citations, sources)`:
  - Facts section: lists active rememberedFacts as bullet points
  - Rule section: joins `draft.scenarioRule()` lines from retrieved legal text
  - Outcome section: joins `draft.scenarioOutcome()` lines
  - Actions section: maps `draft.nextSteps()` as recommended actions
  - Preserves Phase 2 disclaimer, citation, and grounding behavior throughout
- Updated `ResponseMode` with `FINAL_ANALYSIS` handling in `ChatThreadService`
- Updated `ScenarioAnalysisResponse` with `rule`, `outcome`, `actions` fields
- Created `ChatScenarioAnalysisIntegrationTest` covering thread → clarification → fact-collection → final-analysis flow
- Locked serialization of `FINAL_ANALYSIS` responseMode and `scenarioAnalysis` fields in `ChatContractSerializationTest`
- Commit: `f400be6` — `feat(03-03): compose grounded final scenario answers`
- Commit: `fb90fc5` — `test(03-03): lock thread-aware scenario response serialization`

### Task 2 — Complete: UAT gap fixes (3 issues diagnosed and resolved)

**UAT-5: Fact correction fails when user uses negation phrase (major)**
- Root cause: `FactMemoryService.addMatch()` used `while(matcher.find())` with last-match-wins; "không phải xe máy, tôi đi ô tô" matched `xe máy` after `ô tô` so xe máy overwrote the correct value.
- Fix: Added negation guard in `extractExplicitFacts()` — matches preceded by `không phải`/`không phải là`/`chứ không phải` within a 30-char lookback are skipped.
- Commit: `d0ff8e1` — `fix(03-uat): negation guard for fact correction + JSON extraction for scenario parse`

**UAT-7: FINAL_ANALYSIS returns fallback placeholder content (major)**
- Root cause 1: `ChatThreadService.postMessage()` sent the short follow-up turn to vector search, yielding LIMITED_GROUNDING from weak semantic signal. Fix: `buildRetrievalQuestion()` now reconstructs from the first USER message + collected facts.
- Root cause 2: `ScenarioAnswerComposer` blocked FINAL_ANALYSIS on LIMITED_GROUNDING unconditionally. Fix: allow finalization when `rememberedFacts` is non-empty.
- Root cause 3: Model returned `scenarioRule`/`scenarioOutcome` as JSON arrays; `LegalAnswerDraft` declared them as `String`. Fix: changed to `List<String>`; `ScenarioAnswerComposer` joins via `joinLines()`.
- Root cause 4: Model wrapped JSON in markdown code fences; `parseDraft()` failed to deserialize. Fix: `extractJson()` strips fences and finds `{}`  boundaries before Jackson parse; `ACCEPT_SINGLE_VALUE_AS_ARRAY` enabled.
- Commits: `86fb1b4` (retrieval + LIMITED_GROUNDING fix), `d0ff8e1` (JSON extraction + negation guard), `225301e` (List<String> deserialization)

**Post-fix UAT result:** 6 passed, 2 fixed (tests 5 and 7), 0 issues, 0 pending.

## Deviations from Plan

### Auto-fixed Issues
1. **Post-clarification retrieval used wrong query** — short follow-up messages yielded weak semantic signal from vector search. Fixed by reconstructing retrieval query from original legal question + thread facts.
2. **Model JSON output format mismatch** — model wrapped responses in markdown fences and returned arrays instead of strings for rule/outcome fields. Fixed by lenient parse and List<String> type change.

## Verification

Passed:
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.ChatScenarioAnalysisIntegrationTest"`
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.ScenarioAnswerComposerTest"`
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.FactMemoryServiceTest"`
- Full suite: 108 tests, 0 failures
- Live UAT: FINAL_ANALYSIS with real legal text (Điều 7 khoản 4 Nghị định 168/2024/NĐ-CP)

## Known Stubs

None.

## Self-Check: PASSED
- `ScenarioAnswerComposer` present with Facts→Rule→Outcome→Actions structure
- Negation guard in `FactMemoryService` confirmed
- `LegalAnswerDraft.scenarioRule` and `scenarioOutcome` are `List<String>`
- `ChatService.parseDraft()` strips markdown fences
- 108 tests pass, phase shipped via PR #4
