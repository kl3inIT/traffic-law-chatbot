---
phase: quick
plan: 260414-kfe
type: execute
wave: 1
depends_on: []
files_modified:
  # Backend — delete
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
  # Backend — modify
  - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java
  - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
  - src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java
  - src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java
  - src/main/java/com/vn/traffic/chatbot/chat/service/ScenarioAnswerComposer.java
  - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
  - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java
  - src/main/java/com/vn/traffic/chatbot/chat/domain/ResponseMode.java
  - src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessageType.java
  - src/main/resources/default-parameter-set.yml
  - src/main/resources/db/changelog/db.changelog-master.xml
  # Backend — new
  - src/main/resources/db/changelog/014-drop-thread-fact-table.xml
  # Backend tests — modify
  - src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/ChatScenarioAnalysisIntegrationTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/api/ChatContractSerializationTest.java
  # Frontend — modify
  - frontend/types/api.ts
  - frontend/components/chat/message-bubble.tsx
  - frontend/e2e/chat.spec.ts
  - frontend/__tests__/message-bubble.test.tsx
autonomous: true
requirements: []

must_haves:
  truths:
    - "Thread message flow uses a single LLM call (no separate clarification gate call)"
    - "Conversation history from ChatMessage table is passed as context to ChatService.answer()"
    - "System prompt includes inline clarification instructions in Vietnamese"
    - "ThreadFact table is dropped via Liquibase migration"
    - "All deleted Java classes have zero remaining import references"
    - "Frontend renders assistant messages without pendingFacts/rememberedFacts logic"
    - "Backend compiles and all remaining tests pass"
  artifacts:
    - path: "src/main/resources/db/changelog/014-drop-thread-fact-table.xml"
      provides: "Liquibase migration to drop thread_fact table and its indexes"
    - path: "src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java"
      provides: "Simplified thread flow: save user msg -> load history -> chatService.answer() -> save assistant msg"
  key_links:
    - from: "ChatThreadService.java"
      to: "ChatService.answer()"
      via: "passes conversation history as List<ChatMessage>"
      pattern: "chatService\\.answer.*history"
    - from: "ChatPromptFactory.java"
      to: "system prompt"
      via: "includes inline clarification rules in Vietnamese"
      pattern: "Quy tắc hỏi lại"
---

<objective>
Remove the two-LLM-call clarification gate system and collapse into a single RAG+LLM call per message. The LLM decides inline (via system prompt instructions) whether to answer directly or ask a clarifying question. Conversation history from ChatMessage table replaces regex-extracted ThreadFacts as context.

Purpose: Eliminates an entire LLM round-trip per message, removes ~10 Java classes/entities, and simplifies the thread flow from 6 steps to 4 steps.
Output: Simplified ChatThreadService, updated system prompt, dropped thread_fact table, cleaned frontend.
</objective>

<execution_context>
@.planning/quick/260414-kfe-remove-clarification-gate-system-move-cl/260414-kfe-RESEARCH.md
</execution_context>

<context>
@.planning/STATE.md
@src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java
@src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
@src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java
@src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java
@src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
@src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java
@src/main/java/com/vn/traffic/chatbot/chat/domain/ResponseMode.java
@src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessageType.java
@src/main/resources/default-parameter-set.yml
@frontend/types/api.ts
@frontend/components/chat/message-bubble.tsx
</context>

<tasks>

<task type="auto">
  <name>Task 1: Backend — delete clarification subsystem, simplify ChatThreadService, add conversation history to ChatService</name>
  <files>
    src/main/java/com/vn/traffic/chatbot/chat/service/LlmClarificationService.java
    src/main/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicy.java
    src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java
    src/main/java/com/vn/traffic/chatbot/chat/domain/ThreadFact.java
    src/main/java/com/vn/traffic/chatbot/chat/domain/ThreadFactStatus.java
    src/main/java/com/vn/traffic/chatbot/chat/repo/ThreadFactRepository.java
    src/main/java/com/vn/traffic/chatbot/chat/api/dto/PendingFactResponse.java
    src/main/java/com/vn/traffic/chatbot/chat/api/dto/RememberedFactResponse.java
    src/test/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicyTest.java
    src/test/java/com/vn/traffic/chatbot/chat/service/FactMemoryServiceTest.java
    src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java
    src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java
    src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java
    src/main/java/com/vn/traffic/chatbot/chat/service/ScenarioAnswerComposer.java
    src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
    src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java
    src/main/java/com/vn/traffic/chatbot/chat/domain/ResponseMode.java
    src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessageType.java
    src/main/resources/default-parameter-set.yml
    src/main/resources/db/changelog/014-drop-thread-fact-table.xml
    src/main/resources/db/changelog/db.changelog-master.xml
    src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java
    src/test/java/com/vn/traffic/chatbot/chat/ChatScenarioAnalysisIntegrationTest.java
    src/test/java/com/vn/traffic/chatbot/chat/api/ChatContractSerializationTest.java
    src/test/java/com/vn/traffic/chatbot/common/config/AppPropertiesTest.java
  </files>
  <action>
**Step 1 — Delete files** (10 files):
Delete these Java files entirely:
- `LlmClarificationService.java`, `ClarificationPolicy.java`, `FactMemoryService.java`
- `ThreadFact.java`, `ThreadFactStatus.java`, `ThreadFactRepository.java`
- `PendingFactResponse.java`, `RememberedFactResponse.java`
- `ClarificationPolicyTest.java`, `FactMemoryServiceTest.java`

**Step 2 — Simplify enums:**
- `ResponseMode.java`: Remove `CLARIFICATION_NEEDED`. Keep `STANDARD`, `SCENARIO_ANALYSIS`, `FINAL_ANALYSIS`, `REFUSED`.
- `ChatMessageType.java`: Remove `CLARIFICATION`. Keep `QUESTION`, `ANSWER`.

**Step 3 — Remove pendingFacts/rememberedFacts from ChatAnswerResponse:**
Remove the `pendingFacts` and `rememberedFacts` fields from the record. Update every call site that constructs a `ChatAnswerResponse` (AnswerComposer, ChatThreadService clarificationResponse, ChatThreadMapper). Each constructor call must drop those two positional arguments. Since this is a record, carefully count field positions when editing constructors.

**Step 4 — Rewrite ChatThreadService:**
Remove all imports/fields for: `ThreadFactRepository`, `FactMemoryService`, `LlmClarificationService`, `ThreadFact`, `ThreadFactStatus`, `PendingFactResponse`.

New `createThread()` flow:
1. Create and save ChatThread
2. `appendUserMessage(thread, question)`
3. Load history: `chatMessageRepository.findByThreadIdOrderByCreatedAtAsc(thread.getId())` (will contain just the user message)
4. Call `chatService.answer(question, null, history)` — pass conversation history
5. `appendAssistantMessage(thread, answer)`
6. Return `chatThreadMapper.attachScenarioContext(answer, thread.getId(), answer.sources())`

New `postMessage()` flow:
1. Find thread or throw
2. `appendUserMessage(thread, question)`
3. Load history: `chatMessageRepository.findByThreadIdOrderByCreatedAtAsc(threadId)`
4. Call `chatService.answer(question, null, history)` — pass conversation history
5. `appendAssistantMessage(thread, answer)`
6. Return `chatThreadMapper.attachScenarioContext(answer, thread.getId(), answer.sources())`

Remove methods: `toFactMap()`, `countClarificationMessages()`, `buildRetrievalQuestion()`, `clarificationResponse()`.

**Step 5 — Add conversation history to ChatService.answer():**
Add overloaded method or modify existing signature:
```java
public ChatAnswerResponse answer(String question, String modelId, List<ChatMessage> conversationHistory)
```
Pass the history to `chatPromptFactory.buildPrompt()` (add parameter). The existing `answer(question, modelId)` can delegate with `List.of()` for backward compatibility with `PublicChatController` (single-turn, no thread).

**Step 6 — Update ChatPromptFactory.buildPrompt():**
Add `List<ChatMessage> conversationHistory` parameter. Before the user question line, insert conversation history formatted as role-tagged lines. Limit to last 10 messages to prevent token bloat:
```java
List<ChatMessage> recent = conversationHistory.size() > 10
    ? conversationHistory.subList(conversationHistory.size() - 10, conversationHistory.size())
    : conversationHistory;
String historyBlock = recent.stream()
    .map(m -> m.getRole().name() + ": " + m.getContent())
    .collect(Collectors.joining("\n"));
```
If history is non-empty, insert before the question line: `"Lịch sử hội thoại:\n" + historyBlock`

Also add inline clarification instructions to the system prompt section. Append these Vietnamese instructions AFTER the configurable `systemPrompt` value but BEFORE the citation/JSON instructions:
```
Quy tắc hỏi lại:
- Ưu tiên trả lời trực tiếp. Nếu câu hỏi có thể trả lời được dù thiếu chi tiết phụ, hãy trả lời và nêu rõ các trường hợp khác nhau.
- Chỉ hỏi lại khi thông tin thiếu thực sự khiến không thể xác định được quy định áp dụng (ví dụ: câu hỏi quá chung chung hoặc mơ hồ đến mức không liên quan đến bất kỳ điều luật nào).
- Nếu cần hỏi lại, chỉ hỏi TỐI ĐA 1 câu ngắn gọn, cụ thể, bằng tiếng Việt.
- Không bao giờ hỏi lại điều đã nêu rõ trong lịch sử hội thoại.
```

**Step 7 — Simplify ChatThreadMapper:**
- Remove `attachThreadContext()` method (only used for clarification responses).
- Remove `mapFacts()` method and all ThreadFact/RememberedFactResponse imports.
- Update `attachScenarioContext()` signature: remove `List<ThreadFact> facts` parameter. Pass `List.of()` for `rememberedFacts` in ScenarioAnswerComposer.compose() call (the composer still accepts the parameter but will always get empty list — this avoids touching the composer's interface).

**Step 8 — Update ScenarioAnswerComposer:**
Remove `RememberedFactResponse` from the `compose()` method signature. The `buildFacts()` method no longer receives remembered facts — just use `draft.scenarioFacts()`. Remove the `RememberedFactResponse` import.

**Step 9 — Update AnswerComposer:**
Remove the two `List.of()` positional args that were for `pendingFacts` and `rememberedFacts` in all `ChatAnswerResponse` constructor calls.

**Step 10 — Update default-parameter-set.yml:**
Remove the `caseAnalysis` section entirely (lines with maxClarifications, requiredFacts, vehicleType, violationType).
Remove `messages.clarificationIntro` and `messages.clarificationNextStep` keys.
Keep all other messages (disclaimer, refusal, limitedNotice, refusalNextStep1-3).
Remove the line from systemPrompt: `- Khi câu hỏi đề cập đến mức phạt, luôn hỏi rõ loại phương tiện và hành vi cụ thể nếu chưa có` (this behavior is now handled by the inline clarification rules in ChatPromptFactory).

**Step 11 — Liquibase migration:**
Create `014-drop-thread-fact-table.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.27.xsd">
    <changeSet id="014-drop-thread-fact-table" author="claude">
        <dropTable tableName="thread_fact"/>
    </changeSet>
</databaseChangeLog>
```
Add include to `db.changelog-master.xml`:
```xml
<include file="db/changelog/014-drop-thread-fact-table.xml" relativeToChangelogFile="false"/>
```

**Step 12 — Fix remaining tests:**
- `ChatThreadFlowIntegrationTest.java`: Remove any clarification-specific test methods (those testing CLARIFICATION_NEEDED responses, fact extraction). Update remaining test flows to not expect `pendingFacts`/`rememberedFacts` in responses. If tests mock `LlmClarificationService` or `FactMemoryService`, remove those mocks.
- `ChatScenarioAnalysisIntegrationTest.java`: Remove `rememberedFacts` assertions and any ThreadFact setup. Update `ScenarioAnswerComposer` mock calls to not pass rememberedFacts.
- `ChatContractSerializationTest.java`: Remove `pendingFacts` and `rememberedFacts` from serialization assertions/JSON fixtures. Update the ChatAnswerResponse constructor calls.
- `AppPropertiesTest.java`: If it references caseAnalysis or clarification config keys, remove those assertions.

Do a project-wide grep for `ThreadFact`, `FactMemory`, `LlmClarification`, `ClarificationPolicy`, `PendingFactResponse`, `RememberedFactResponse`, `CLARIFICATION_NEEDED`, `CLARIFICATION` (as enum value) to ensure zero remaining references after all edits.
  </action>
  <verify>
    <automated>cd D:/ai/traffic-law-chatbot && ./gradlew compileJava compileTestJava 2>&1 | tail -5</automated>
  </verify>
  <done>
    - All 10 deleted files are gone
    - ChatThreadService has no clarification/fact imports; createThread() and postMessage() follow the simplified 4-step flow
    - ChatService.answer() accepts optional conversation history; ChatPromptFactory includes history and inline clarification rules
    - ChatAnswerResponse record has no pendingFacts/rememberedFacts fields
    - ResponseMode has no CLARIFICATION_NEEDED; ChatMessageType has no CLARIFICATION
    - 014 migration registered in master changelog
    - Backend compiles with zero errors
  </done>
</task>

<task type="auto">
  <name>Task 2: Frontend — remove clarification types and rendering, update E2E tests</name>
  <files>
    frontend/types/api.ts
    frontend/components/chat/message-bubble.tsx
    frontend/e2e/chat.spec.ts
    frontend/__tests__/message-bubble.test.tsx
  </files>
  <action>
**frontend/types/api.ts:**
- Remove `'CLARIFICATION_NEEDED'` from `ResponseMode` union type
- Remove `pendingFacts` and `rememberedFacts` fields from `ChatAnswerResponse` interface
- Remove `PendingFactResponse` and `RememberedFactResponse` interface definitions entirely
- Remove `'CLARIFICATION'` from `ChatMessageType` union type

**frontend/components/chat/message-bubble.tsx:**
- Remove `const isClarification = response.responseMode === 'CLARIFICATION_NEEDED'` and all code branching on it
- Remove the `pendingFacts` rendering block (the `.map()` over `response.pendingFacts`)
- Remove the `[CLARIFICATION]` prefix stripping from `response.answer` (the `.replace(/^\[CLARIFICATION\]\s*/i, '')` call)
- Keep all other rendering logic (disclaimer, citations, legal basis, etc.) intact

**frontend/__tests__/message-bubble.test.tsx:**
- Remove any test cases that create fixtures with `responseMode: 'CLARIFICATION_NEEDED'`
- Remove any assertions about `pendingFacts` rendering
- Update ChatAnswerResponse fixtures: remove `pendingFacts` and `rememberedFacts` properties

**frontend/e2e/chat.spec.ts:**
- Remove test `T-UI-05` ("clarification needed -- amber box renders when vehicleType missing")
- Remove test `T-UI-06` ("clarification needed -- pending fact prompt is visible")
- In `T-UI-10` (multi-turn test), remove any expectation that the first response is a clarification. The test should expect a direct answer. If the test sends a vague message expecting clarification, update it to send a clear question and expect a grounded answer.
- Keep all other tests intact (T-UI-01 through T-UI-04, T-UI-07 through T-UI-09)

Also check `frontend/app/(admin)/parameters/page.tsx` for any references to `caseAnalysis`, `clarification`, `pendingFacts`, or `requiredFacts` — remove if found (this page renders YAML preview and may show the deleted config sections).
  </action>
  <verify>
    <automated>cd D:/ai/traffic-law-chatbot/frontend && npx tsc --noEmit 2>&1 | tail -10</automated>
  </verify>
  <done>
    - Frontend TypeScript compiles with no errors
    - No references to PendingFactResponse, RememberedFactResponse, CLARIFICATION_NEEDED, or CLARIFICATION enum value remain in frontend code
    - E2E tests T-UI-05 and T-UI-06 are removed
    - message-bubble.tsx has no clarification rendering branch
  </done>
</task>

<task type="auto">
  <name>Task 3: Run backend tests and verify full build</name>
  <files></files>
  <action>
Run the full backend test suite to catch any remaining references or broken integration tests. Fix any compilation or test failures that surface from the removals in Task 1.

Then run the frontend unit tests to verify message-bubble tests pass after Task 2 changes.

Do NOT run E2E tests (they require a running backend + frontend server). The E2E spec file correctness is verified by TypeScript compilation in Task 2.

If any test fails due to leftover references to deleted classes/fields, fix the test file. Common fixes:
- Remove mock declarations for deleted services
- Remove assertions on deleted response fields
- Update ChatAnswerResponse constructor calls in test fixtures to match the new record shape (fewer fields)
  </action>
  <verify>
    <automated>cd D:/ai/traffic-law-chatbot && ./gradlew test 2>&1 | tail -20</automated>
  </verify>
  <done>
    - `./gradlew test` passes (all backend tests green)
    - Frontend unit tests pass
    - Zero references to deleted classes remain in the codebase
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| user->ChatThreadService | User question text enters the system |
| ChatPromptFactory->LLM | Conversation history injected into prompt |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-QT-01 | I (Information Disclosure) | ChatPromptFactory history block | accept | Conversation history is same-thread only; user already owns all messages in their thread. No cross-user data leakage risk. |
| T-QT-02 | T (Tampering) | Conversation history in prompt | mitigate | Limit to last 10 messages to prevent prompt injection via excessively long history. History loaded from DB (server-authoritative), not from client payload. |
| T-QT-03 | D (Denial of Service) | Token bloat from history | mitigate | Hard cap at 10 messages in ChatPromptFactory prevents unbounded token usage. |
</threat_model>

<verification>
1. `./gradlew compileJava compileTestJava` — zero errors
2. `./gradlew test` — all tests pass
3. `cd frontend && npx tsc --noEmit` — zero errors
4. `grep -r "ThreadFact\|FactMemory\|LlmClarification\|ClarificationPolicy\|PendingFactResponse\|RememberedFactResponse" src/ --include="*.java" | wc -l` — returns 0
5. `grep -r "CLARIFICATION_NEEDED\|pendingFacts\|rememberedFacts" frontend/types/ frontend/components/ | wc -l` — returns 0
</verification>

<success_criteria>
- Thread message flow uses exactly ONE LLM call (no clarification gate)
- Conversation history (last 10 messages) is injected into the prompt for multi-turn context
- System prompt contains Vietnamese inline clarification rules
- thread_fact table is dropped via Liquibase 014 migration
- 10 Java files deleted, ~8 files simplified
- Frontend renders no clarification-specific UI
- Full backend test suite passes; frontend TypeScript compiles
</success_criteria>

<output>
After completion, create `.planning/quick/260414-kfe-remove-clarification-gate-system-move-cl/260414-kfe-SUMMARY.md`
</output>
