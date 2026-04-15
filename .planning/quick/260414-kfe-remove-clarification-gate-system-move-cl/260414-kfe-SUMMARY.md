---
phase: quick
plan: 260414-kfe
subsystem: chat
tags: [simplification, clarification-removal, conversation-history, prompt-engineering]
dependency_graph:
  requires: []
  provides:
    - single-llm-call-per-message
    - conversation-history-context
    - inline-clarification-rules
  affects:
    - ChatThreadService
    - ChatService
    - ChatPromptFactory
    - ChatAnswerResponse
    - frontend-api-types
    - parameters-page
tech_stack:
  added: []
  patterns:
    - conversation-history-in-prompt
    - inline-clarification-via-system-prompt
key_files:
  created:
    - src/main/resources/db/changelog/014-drop-thread-fact-table.xml
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ScenarioAnswerComposer.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ResponseMode.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessageType.java
    - src/main/java/com/vn/traffic/chatbot/common/config/AppProperties.java
    - src/main/resources/default-parameter-set.yml
    - src/main/resources/application.yaml
    - src/main/resources/db/changelog/db.changelog-master.xml
    - frontend/types/api.ts
    - frontend/components/chat/message-bubble.tsx
    - frontend/e2e/chat.spec.ts
    - frontend/__tests__/message-bubble.test.tsx
    - frontend/app/(admin)/parameters/page.tsx
  deleted:
    - src/main/java/com/vn/traffic/chatbot/chat/service/LlmClarificationService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicy.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ThreadFact.java
    - src/main/java/com/vn/traffic/chatbot/chat/domain/ThreadFactStatus.java
    - src/main/java/com/vn/traffic/chatbot/chat/repo/ThreadFactRepository.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/PendingFactResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/RememberedFactResponse.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicyTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/FactMemoryServiceTest.java
decisions:
  - Conversation history capped at 10 messages to prevent token bloat (T-QT-03 mitigation)
  - CaseAnalysis config removed from AppProperties since clarification is now inline in system prompt
  - ScenarioAnswerComposer canFinalize simplified -- LIMITED_GROUNDING always eligible (no rememberedFacts gate)
metrics:
  duration: 24m
  completed: 2026-04-14
  tasks: 3
  files_modified: 18
  files_deleted: 10
  files_created: 1
---

# Quick Task 260414-kfe: Remove Clarification Gate System Summary

Collapsed the two-LLM-call clarification gate into a single RAG+LLM call per message, using conversation history from ChatMessage table and inline Vietnamese clarification rules in the system prompt.

## What Changed

### Backend (Task 1)

**Deleted 10 files:** LlmClarificationService, ClarificationPolicy, FactMemoryService, ThreadFact entity, ThreadFactStatus enum, ThreadFactRepository, PendingFactResponse DTO, RememberedFactResponse DTO, and their two test files.

**Simplified ChatThreadService:** Reduced from 6-step flow (with clarification gate, fact extraction, fact memory) to 4-step flow: save user message, load conversation history, call chatService.answer(), save assistant message. Both createThread() and postMessage() follow the same pattern.

**Added conversation history to ChatService:** New overloaded `answer(question, modelId, conversationHistory)` method passes history through to ChatPromptFactory. The existing 2-arg overload delegates with empty list for backward compatibility with PublicChatController single-turn flow.

**Updated ChatPromptFactory:** Added `List<ChatMessage> conversationHistory` parameter to buildPrompt(). History block (last 10 messages, role-tagged) is inserted before the user question. Added inline Vietnamese clarification rules (CLARIFICATION_RULES constant) between the configurable system prompt and the citation/JSON instructions.

**Simplified ChatAnswerResponse record:** Removed `pendingFacts` and `rememberedFacts` fields (18 fields reduced to 16). Updated all constructor call sites across 10+ files.

**Simplified enums:** Removed `CLARIFICATION_NEEDED` from ResponseMode, `CLARIFICATION` from ChatMessageType.

**Removed CaseAnalysis config:** Deleted CaseAnalysis inner class from AppProperties, removed `case-analysis` from application.yaml, removed caseAnalysis section from default-parameter-set.yml, removed clarificationIntro/clarificationNextStep messages.

**Liquibase migration:** Added 014-drop-thread-fact-table.xml with preCondition guard to drop the thread_fact table.

### Frontend (Task 2)

- Removed `CLARIFICATION_NEEDED` from ResponseMode union type
- Removed `PendingFactResponse` and `RememberedFactResponse` interface definitions
- Removed `pendingFacts`/`rememberedFacts` from ChatAnswerResponse interface
- Removed `CLARIFICATION` from ChatMessageType union
- Removed clarification rendering branch (amber box, pending facts list, [CLARIFICATION] prefix stripping) from message-bubble.tsx
- Removed E2E tests T-UI-05 and T-UI-06 (clarification-specific)
- Removed caseAnalysis and clarification message fields from parameters page form, schema, defaults, and YAML serialization

### Test Fixes (Task 3)

- Updated ChatServiceTest mocks: buildPrompt verify/when calls now match 4-arg signature with anyList() for conversation history
- Updated ChatAnswerResponse constructor calls in ChatControllerTest, CheckRunnerTest, ChatLogServiceTest, ChatThreadControllerTest (18-arg to 16-arg)
- Rewrote ChatThreadFlowIntegrationTest and ChatScenarioAnalysisIntegrationTest to use simplified ChatThreadService constructor (4 deps instead of 7)
- Updated ScenarioAnswerComposerTest for new 3-arg compose() signature

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed ScenarioAnswerComposerTest**
- **Found during:** Task 1 (compile-time grep)
- **Issue:** ScenarioAnswerComposerTest still referenced RememberedFactResponse and old compose() signature
- **Fix:** Rewrote test to use new 3-arg compose() without RememberedFactResponse
- **Commit:** 2a76c27

**2. [Rule 1 - Bug] Fixed ChatControllerTest, CheckRunnerTest, ChatLogServiceTest, ChatThreadControllerTest**
- **Found during:** Task 1 (compile-time grep for ChatAnswerResponse constructors)
- **Issue:** Multiple test files constructed ChatAnswerResponse with 18 args (old shape)
- **Fix:** Updated all to 16 args (removed pendingFacts/rememberedFacts positions)
- **Commit:** 2a76c27

**3. [Rule 1 - Bug] Fixed ChatServiceTest mock signature mismatch**
- **Found during:** Task 3 (test run)
- **Issue:** ChatServiceTest mocked buildPrompt with 3 args but actual call is now 4 args
- **Fix:** Added anyList() matcher for conversation history parameter in all when/verify calls
- **Commit:** 426936a

**4. [Rule 2 - Missing] Removed CaseAnalysis from AppProperties**
- **Found during:** Task 1 (plan mentioned removing caseAnalysis from YAML but not from AppProperties)
- **Issue:** AppProperties.Chat.CaseAnalysis inner class and application.yaml config would become dead code
- **Fix:** Removed CaseAnalysis class, removed case-analysis from application.yaml, removed test assertion
- **Commit:** 2a76c27

## Known Stubs

None -- all data paths are fully wired.

## Pre-existing Test Failures (Out of Scope)

- `message-bubble.test.tsx > AiBubble > renders scenario analysis as accordion` -- fails on ScenarioAccordion text assertion (pre-existing, confirmed by running against pre-change code)
- `app-sidebar.test.tsx` -- 3 pre-existing failures unrelated to this task

## Self-Check: PASSED
