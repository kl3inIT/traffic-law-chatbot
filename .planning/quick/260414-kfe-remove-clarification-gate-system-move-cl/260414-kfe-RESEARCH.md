# Quick Task: Remove Clarification Gate System - Research

**Researched:** 2026-04-14
**Domain:** Chat flow simplification, system prompt design
**Confidence:** HIGH

## Summary

The current chat thread flow uses a two-LLM-call architecture: first `LlmClarificationService` makes a separate LLM call to decide whether to ask a clarifying question, then (if no clarification needed) `ChatService.answer()` makes the actual RAG-grounded answer call. This adds latency (a full LLM round-trip) and complexity (ThreadFact entity tracking, FactMemoryService regex extraction, ClarificationPolicy decision logic).

The task is to collapse this into a single LLM call by moving clarification instructions into the system prompt. The LLM decides inline whether to answer directly or ask for clarification. Conversation history replaces extracted facts as context.

**Primary recommendation:** Remove `LlmClarificationService`, `ClarificationPolicy`, `FactMemoryService`, and `ThreadFact` entity. Pass conversation history (prior ChatMessages) as context to the main answer call. Add clarification instructions to the system prompt so the LLM handles everything in one shot.

## Current Architecture (What to Remove)

### Components to delete
| Component | File | Role |
|-----------|------|------|
| `LlmClarificationService` | `chat/service/LlmClarificationService.java` | Separate LLM call to decide clarification |
| `ClarificationPolicy` | `chat/service/ClarificationPolicy.java` | Hardcoded fact-checking rules (now mostly unused but still referenced) |
| `FactMemoryService` | `chat/service/FactMemoryService.java` | Regex-based fact extraction from user messages |
| `ThreadFact` | `chat/domain/ThreadFact.java` | JPA entity for extracted facts |
| `ThreadFactStatus` | `chat/domain/ThreadFactStatus.java` | Enum for fact lifecycle |
| `ThreadFactRepository` | `chat/repo/ThreadFactRepository.java` | JPA repository |
| `PendingFactResponse` | `chat/api/dto/PendingFactResponse.java` | DTO for clarification questions |
| `RememberedFactResponse` | `chat/api/dto/RememberedFactResponse.java` | DTO for remembered facts |
| `ClarificationPolicyTest` | test file | Unit tests for deleted class |
| `FactMemoryServiceTest` | test file | Unit tests for deleted class |

### Enums/fields to simplify
| Item | Change |
|------|--------|
| `ResponseMode.CLARIFICATION_NEEDED` | Remove enum value |
| `ChatMessageType.CLARIFICATION` | Remove enum value |
| `ChatAnswerResponse.pendingFacts` | Remove field |
| `ChatAnswerResponse.rememberedFacts` | Remove field |
| `default-parameter-set.yml` caseAnalysis section | Remove entirely |
| `default-parameter-set.yml` clarification messages | Remove |

### Database migration needed
- `thread_fact` table exists (Liquibase `004-chat-thread-schema.xml`). Add a new changelog to DROP the table. [VERIFIED: codebase grep]
- No data migration needed -- thread facts are transient session data with no long-term value.

## Architecture Pattern: Inline Clarification via System Prompt

### How it works

Instead of a separate gate, the system prompt instructs the LLM:

1. **If the question is clear enough** -- answer directly using retrieved sources
2. **If truly ambiguous** -- ask ONE concise clarifying question, then stop (do not guess)

The key insight from production RAG chatbots is: **bias toward answering**. Most questions can be answered with a range-based response ("for motorcycles the fine is X, for cars it is Y") rather than blocking the user with a clarification question. [ASSUMED]

### System prompt addition (Vietnamese, matches existing style)

```
Quy tắc hỏi lại:
- Ưu tiên trả lời trực tiếp. Nếu câu hỏi có thể trả lời được dù thiếu chi tiết phụ, hãy trả lời và nêu rõ các trường hợp khác nhau.
- Chỉ hỏi lại khi thông tin thiếu thực sự khiến không thể xác định được quy định áp dụng (ví dụ: câu hỏi quá chung chung hoặc mơ hồ đến mức không liên quan đến bất kỳ điều luật nào).
- Nếu cần hỏi lại, chỉ hỏi TỐI ĐA 1 câu ngắn gọn, cụ thể, bằng tiếng Việt.
- Không bao giờ hỏi lại điều đã nêu rõ trong lịch sử hội thoại.
```

This goes into `default-parameter-set.yml` systemPrompt and/or `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK`. [ASSUMED]

### Conversation history as context

**Current:** `FactMemoryService.buildThreadAwareQuestion()` appends regex-extracted facts to the question string.

**New:** Pass the last N messages from `ChatMessageRepository.findByThreadIdOrderByCreatedAtAsc()` as conversation context. This is simpler and more accurate because:
- No regex extraction needed -- the LLM reads the actual conversation
- No fact key/value mismatch issues
- Works for any language/topic without pattern maintenance

**Implementation pattern:**

```java
// In ChatThreadService.postMessage():
List<ChatMessage> history = chatMessageRepository
    .findByThreadIdOrderByCreatedAtAsc(threadId);
String conversationContext = history.stream()
    .map(m -> m.getRole().name() + ": " + m.getContent())
    .collect(Collectors.joining("\n"));

// Pass to ChatService or ChatPromptFactory
String fullPrompt = conversationContext + "\nUSER: " + question;
```

Alternatively, use Spring AI's message list to pass proper role-tagged messages. The `ChatClient.prompt().messages(...)` API supports `UserMessage` and `AssistantMessage` objects. [VERIFIED: Spring AI usage in existing codebase ChatService]

## ChatThreadService Simplification

### Current flow (per message)
1. Save user message
2. `factMemoryService.rememberExplicitFacts()` -- regex extraction
3. `factMemoryService.getActiveFacts()` -- query ThreadFact table
4. `llmClarificationService.decide()` -- **separate LLM call**
5. If clarification needed: return clarification response (no RAG)
6. Else: `chatService.answer()` -- RAG + LLM call

### New flow (per message)
1. Save user message
2. Load conversation history from ChatMessage table
3. `chatService.answer(question, modelId, conversationHistory)` -- single RAG + LLM call
4. Save assistant message
5. Return response

### Impact on ChatService
- `ChatService.answer()` gains an optional `List<ChatMessage> history` parameter
- `ChatPromptFactory.buildPrompt()` gains conversation history formatting
- The LLM sees both retrieved citations AND conversation context
- No structural change to retrieval, citation mapping, or grounding logic

## Common Pitfalls

### Pitfall 1: LLM over-clarifying
**What goes wrong:** Without careful prompt tuning, LLMs default to asking clarifying questions rather than answering -- especially for legal domains where they are trained to be cautious.
**How to avoid:** The system prompt must explicitly say "prefer answering over asking." Include the instruction "If you can provide a useful answer even with incomplete info, do so and mention the assumptions." Test with the existing integration test scenarios. [ASSUMED]

### Pitfall 2: Conversation history token bloat
**What goes wrong:** Long threads fill up the context window, leaving insufficient room for retrieved citations.
**How to avoid:** Limit conversation history to last 10 messages (5 turns). This is a reasonable default for legal Q&A. The current system prompt + citations already use significant tokens. [ASSUMED]

### Pitfall 3: Breaking the frontend
**What goes wrong:** The frontend `message-bubble.tsx` renders `pendingFacts` and checks `CLARIFICATION_NEEDED` response mode. Removing these from the API breaks the UI.
**How to avoid:** Keep `pendingFacts` as an empty array in the response DTO (or remove and update frontend simultaneously). The frontend already handles empty arrays gracefully. The `CLARIFICATION_NEEDED` response mode check in the frontend becomes dead code that should be cleaned up. [VERIFIED: frontend/components/chat/message-bubble.tsx]

### Pitfall 4: Existing E2E tests reference clarification
**What goes wrong:** `chat.spec.ts` and integration tests reference clarification behavior.
**How to avoid:** Update/remove clarification-specific test scenarios. The E2E test `T-UI-05` and `T-UI-06` test clarification behavior and will need removal or rewrite. [VERIFIED: frontend/e2e/chat.spec.ts]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Conversation history formatting | Custom string concatenation | Spring AI `Message` objects with `ChatClient.prompt().messages()` |
| Fact extraction from text | Regex patterns (`FactMemoryService`) | Let the LLM understand context from conversation history |

## What to Keep

- **ChatThread entity** -- still needed for grouping messages into conversations
- **ChatMessage entity** -- still needed, it IS the conversation history
- **ChatMessageRepository** -- still needed for loading history
- **ScenarioAnswerComposer / ChatThreadMapper** -- still useful for scenario analysis formatting, but remove fact-related mapping

## Parameter Set Changes

Remove from `default-parameter-set.yml`:
```yaml
# DELETE these sections:
caseAnalysis:
  maxClarifications: 2
  requiredFacts: [...]

messages:
  clarificationIntro: ...
  clarificationNextStep: ...
```

Add to `systemPrompt` value the clarification-inline instructions (shown above).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Biasing toward answering over clarifying is better UX for legal chatbots | Architecture Pattern | Users might get less precise answers; mitigated by showing multiple scenarios |
| A2 | Last 10 messages is sufficient conversation context | Pitfalls | Long analysis threads might lose early context; can increase limit later |
| A3 | System prompt clarification instructions integrate cleanly with existing JSON output format | Architecture Pattern | LLM might mix clarification text with JSON; need to test |

## Sources

### Primary (HIGH confidence)
- Codebase analysis: `ChatThreadService.java`, `LlmClarificationService.java`, `ClarificationPolicy.java`, `FactMemoryService.java`, `ChatPromptFactory.java`, `ChatService.java`
- Frontend: `message-bubble.tsx`, `types/api.ts`
- Config: `default-parameter-set.yml`, `004-chat-thread-schema.xml`

### Secondary (MEDIUM confidence)
- Spring AI ChatClient message API -- verified from existing codebase usage patterns

## Metadata

**Confidence breakdown:**
- Architecture change: HIGH -- straightforward removal of a clearly defined subsystem
- System prompt design: MEDIUM -- prompt tuning may need iteration
- Conversation history approach: HIGH -- standard pattern, simpler than fact extraction

**Research date:** 2026-04-14
**Valid until:** 2026-05-14
