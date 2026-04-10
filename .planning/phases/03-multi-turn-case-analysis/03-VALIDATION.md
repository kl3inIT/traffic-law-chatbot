# Phase 3 Validation

**Phase:** 3
**Focus:** Multi-turn case analysis on top of the shipped chat backend
**Date:** 2026-04-10

## Validation Targets

Phase 3 is valid only if all of the following stay true:

1. The public endpoint is still `/api/v1/chat`.
2. The implementation lives under `src/main/java/com/vn/traffic/chatbot/chat/...`.
3. Phase 2 refusal, disclaimer, citation, and approved+trusted+active retrieval behavior still pass regression tests.
4. Persistent thread continuity works across multiple requests in the same thread.
5. Only explicit user facts are remembered.
6. A later user correction overwrites the earlier remembered fact.
7. Missing material facts trigger clarification-needed output before a final scenario conclusion.
8. Final scenario output follows Facts / Rule / Outcome / Actions / Sources while still returning the existing chat response envelope.

## Required Automated Checks

### Contract and controller checks
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.api.ChatContractSerializationTest"`
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.api.ChatControllerTest"`

### Chat service and orchestration checks
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.ChatServiceTest"`
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.ChatFlowIntegrationTest"`

### New Phase 3-specific checks to add during execution
- thread persistence repository/service regression
- explicit fact extraction and latest-correction replacement regression
- clarification-needed orchestration regression
- scenario composition regression proving Facts / Rule / Outcome / Actions / Sources sections

## Phase 3 Sampling Guidance

Use at least these scenario samples in tests or validation fixtures:

### Sample A — direct Phase 2 legal Q&A regression
A single-turn legal question that should still return the Phase 2 grounded answer shape with citations and disclaimer, without requiring thread features.

### Sample B — thread continuity
Turn 1 provides scenario facts.
Turn 2 asks a follow-up that depends on those facts.
Expected: remembered explicit facts are reused inside the same thread.

### Sample C — latest corrected fact wins
Turn 1 states fact A.
Turn 2 corrects fact A.
Turn 3 asks for outcome.
Expected: final analysis uses only the corrected fact.

### Sample D — clarification before conclusion
Scenario omits a material fact such as vehicle type, license status, alcohol test result, injury severity, or document status.
Expected: response indicates clarification is needed and asks targeted follow-up questions instead of giving a confident outcome.

### Sample E — final structured scenario output
Scenario includes enough explicit facts for a grounded answer.
Expected: final response keeps existing chat fields and includes structured scenario sections for Facts, Rule, Outcome, Actions, and Sources.

## Failure Conditions

Phase 3 must be rejected if any of these appear:

- new `caseanalysis` top-level module or package tree
- new `/api/v1/case-analysis` public endpoint in Phase 3
- retrieval filter duplicated or altered outside `RetrievalPolicy`
- model-generated facts written into thread memory
- missing corrected-fact replacement semantics
- final scenario answers emitted without citations/sources when the response claims grounding
- Phase 2 direct Q&A regression fails because of thread/scenario changes

## Review Questions

Before accepting execution output, ask:
- Did the implementation stay in `chat/...`?
- Did `/api/v1/chat` remain the public surface?
- Did Phase 2 tests still pass?
- Can the same thread continue across requests?
- Are remembered facts strictly user-stated?
- Does clarification happen before conclusion when facts are missing?
- Does final analysis follow Facts / Rule / Outcome / Actions / Sources?
