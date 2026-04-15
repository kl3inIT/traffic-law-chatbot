---
phase: 03-multi-turn-case-analysis
plan: 02
subsystem: chat
status: complete
tags: [phase-3, fact-memory, clarification, policy]
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicy.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ResponseMode.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java
    - src/main/resources/application.yaml
    - src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicyTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/FactMemoryServiceTest.java
decisions:
  - Only explicit user-stated facts are persisted — model inferences are never written to ThreadFact.
  - Fact correction uses SUPERSEDED status on the prior ThreadFact; only ACTIVE facts are exposed.
  - Clarification is a blocking state (CLARIFICATION_NEEDED responseMode); the thread cannot advance to FINAL_ANALYSIS until material facts are collected or the cap is reached.
  - Clarification loop cap configured in application.yaml under app.chat.clarification.max-rounds.
metrics:
  completed_date: 2026-04-10
  task_commits:
    - 8de22d2
    - 9decd5e
---

# Phase 03 Plan 02: Fact Memory & Clarification Policy Summary

The explicit-fact memory layer and clarification policy are implemented. The chatbot safely carries case facts across turns, corrects superseded facts, and blocks final conclusions until material information is provided.

## Task Outcomes

### Task 1 — Complete: Explicit fact memory service
- Created `FactMemoryService` under `com.vn.traffic.chatbot.chat.service`:
  - `extractExplicitFacts(String message)` — regex-based extraction of vehicleType, violationType, location, speed from user turn text
  - `addMatch(ThreadFact)` — persists new fact; supersedes prior ACTIVE fact of same type
  - `getActiveFacts(UUID threadId)` — returns current ACTIVE facts for thread context
- Extended `ThreadFactRepository` with query for active facts by type and thread
- Filled `FactMemoryServiceTest` with coverage for: fact extraction, fact persistence, correction-wins (latest supersedes), missing-fact (null) handling
- Commit: `8de22d2` — `feat(03-02): add explicit fact memory service`

### Task 2 — Complete: Clarification policy for thread analysis
- Created `ClarificationPolicy` with:
  - `missingMaterialFacts(List<ThreadFact>)` — identifies which of {vehicleType, violationType} are absent
  - `shouldClarify(facts, round)` — returns true when material facts are missing and round < max-rounds cap
  - `buildClarifyingQuestion(pendingFacts)` — generates Vietnamese clarifying prompt text
- Extended `ResponseMode` with `CLARIFICATION_NEEDED` and `FINAL_ANALYSIS` states
- Updated `ChatThreadService` to route turns through `ClarificationPolicy` before proceeding to scenario analysis
- Updated `ChatThreadMapper` to map pending/remembered facts into `ChatThreadResponse`
- Added `app.chat.clarification.max-rounds: 3` to `application.yaml`
- Extended `ChatThreadFlowIntegrationTest` with clarification-needed, fact-collection, and cap-reached scenarios
- Filled `ClarificationPolicyTest` with missing-fact, sufficient-fact, and loop-cap assertions
- Commit: `9decd5e` — `feat(03-02): add clarification policy for thread analysis`

## Deviations from Plan

None recorded.

## Verification

Passed:
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.FactMemoryServiceTest"`
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.ClarificationPolicyTest"`
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.ChatThreadFlowIntegrationTest"`
- `./gradlew compileJava`
- CLARIFICATION_NEEDED returns non-empty pendingFacts and null scenarioAnalysis

## Known Stubs

None — all plan must-haves delivered. Scenario composition wired in Plan 03.

## Self-Check: PASSED
- `FactMemoryService` present with explicit-only extraction and supersede logic
- `ClarificationPolicy` present with missing-fact detection and loop cap
- `ResponseMode` contains CLARIFICATION_NEEDED and FINAL_ANALYSIS
- All three test classes pass
