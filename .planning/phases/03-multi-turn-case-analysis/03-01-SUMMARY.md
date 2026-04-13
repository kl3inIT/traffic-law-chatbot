---
phase: 03-multi-turn-case-analysis
plan: 01
subsystem: chat
status: complete
tags: [phase-3, multi-turn, thread, schema, rest]
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ChatThread.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessage.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessageRole.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessageType.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ResponseMode.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ThreadFact.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ThreadFactStatus.java
    - src/main/java/com/vn/traffic/chatbot/chat/repo/ChatThreadRepository.java
    - src/main/java/com/vn/traffic/chatbot/chat/repo/ChatMessageRepository.java
    - src/main/java/com/vn/traffic/chatbot/chat/repo/ThreadFactRepository.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/CreateChatThreadRequest.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatThreadMessageRequest.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatThreadResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/PendingFactResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/RememberedFactResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ScenarioAnalysisResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java
    - src/main/resources/db/changelog/004-chat-thread-schema.xml
    - src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/api/ChatThreadControllerTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicyTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/FactMemoryServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ScenarioAnswerComposerTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatQuestionRequest.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
    - src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java
    - src/main/java/com/vn/traffic/chatbot/common/error/ErrorCode.java
    - src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java
    - src/main/resources/db/changelog/db.changelog-master.xml
decisions:
  - Thread and message entities use UUID primary keys for opaque external identifiers per D-01.
  - ThreadFact is the only persistence boundary for case facts — no implicit state from message text.
  - Phase 2 ChatAnswerResponse extended (not replaced) with thread fields so single-turn callers are unaffected.
  - Wave 0 test skeletons created first, then filled, ensuring regression targets existed before implementation.
metrics:
  completed_date: 2026-04-10
  task_commits:
    - d8999ba
    - 3b96586
    - 9408d98
---

# Phase 03 Plan 01: Thread-Aware Contract & Persistence Foundation Summary

The durable thread schema, repositories, thread-aware REST DTOs, and endpoints are in place. Multi-turn case analysis extends the shipped Phase 2 chat module without replacing its single-turn path.

## Task Outcomes

### Task 1 — Complete: Wave 0 regression skeletons
- Created failing placeholder tests for all Phase 3 capabilities: thread flow, thread controller, clarification policy, fact memory, scenario composer
- Commit: `d8999ba` — `test(03-01): add Wave 0 phase 3 regression skeletons`

### Task 2 — Complete: Durable thread schema and thread-aware contracts
- Created `ChatThread`, `ChatMessage`, `ThreadFact`, `ResponseMode`, and supporting enums/status types under `com.vn.traffic.chatbot.chat.domain`
- Created `ChatThreadRepository`, `ChatMessageRepository`, `ThreadFactRepository`
- Added Liquibase migration `004-chat-thread-schema.xml` with UUID-backed thread/message/fact tables
- Extended `ChatAnswerResponse` with `threadId`, `responseMode`, `pendingFacts`, `rememberedFacts`, `scenarioAnalysis`
- Extended `ChatQuestionRequest` with optional `threadId`
- Added `ApiPaths.CHAT_THREADS` and `ApiPaths.CHAT_THREAD_MESSAGES` path constants
- Added `CHAT_THREAD_NOT_FOUND` to `ErrorCode`
- Locked serialization of new thread fields in `ChatContractSerializationTest`
- Commit: `3b96586` — `feat(03-01): add durable thread schema and thread-aware contracts`

### Task 3 — Complete: Thread create and continue endpoints
- Extended `PublicChatController` with `POST /api/v1/chat/threads` and `POST /api/v1/chat/threads/{threadId}/messages`
- Created `ChatThreadService` (create thread, append message) and `ChatThreadMapper`
- Added `CHAT_THREAD_NOT_FOUND` handling to `GlobalExceptionHandler` → 404 ProblemDetail
- Filled `ChatThreadFlowIntegrationTest` and `ChatThreadControllerTest` with full integration coverage
- Commit: `9408d98` — `feat(03-01): wire thread create and continue endpoints`

## Deviations from Plan

None recorded.

## Verification

Passed:
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.ChatThreadFlowIntegrationTest"`
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.api.ChatThreadControllerTest"`
- `./gradlew compileJava`
- Thread create returns UUID threadId; unknown threadId returns 404 ProblemDetail

## Known Stubs

Wave 0 test skeletons for ClarificationPolicy, FactMemory, and ScenarioComposer filled in Plans 02 and 03.

## Self-Check: PASSED
- `ChatThread`, `ChatMessage`, `ThreadFact` entities present
- `004-chat-thread-schema.xml` migration registered
- `PublicChatController` handles thread create and continue endpoints
- `ChatThreadControllerTest` and `ChatThreadFlowIntegrationTest` pass
