# Phase 3 Research: Multi-turn Case Analysis

**Phase:** 3
**Date:** 2026-04-10
**Revision:** 2 — architecture realignment

## Executive Decision

Implement Phase 3 by extending the shipped `chat` module and `/api/v1/chat` contract instead of creating a parallel `caseanalysis` package tree or second public API family.

This revision keeps the good fixes from the prior revision:
- smaller 4-plan split
- explicit resolution of open questions
- Nyquist-complete automated verification for each task
- stronger validation sampling

But it restores the locked architecture and product decisions from `03-CONTEXT.md`.

## Locked Decisions Carried Forward Verbatim

1. **Persistent threads** — thread state must persist across requests so conversation continuity survives beyond a single prompt/response.
2. **Clarify before conclude** — missing material facts should trigger clarification instead of premature legal conclusions.
3. **Facts → Rule → Outcome with actions and sources** — final scenario responses must use this structure.
4. **Explicit-user-facts-only memory** — stored thread facts must come only from the user’s own statements.
5. **Latest corrected fact wins** — later explicit corrections overwrite prior remembered facts for the same thread.
6. **Extend existing chat module** — use `src/main/java/com/vn/traffic/chatbot/chat/...`, not a parallel package tree.
7. **Preserve Phase 2 grounding/citation/disclaimer behavior** — retrieval gating and refusal semantics stay intact.
8. **Prefer extending existing contract** — evolve `/api/v1/chat` request/response in a backward-compatible way instead of adding `/api/v1/case-analysis`.

## Open Questions Resolved

### Q1. Should Phase 3 use a new public endpoint?
**Answer:** No.

Use the existing `/api/v1/chat` public endpoint and extend `ChatQuestionRequest` with optional thread/scenario fields if needed. This matches D-01 and D-02, avoids fragmented clients, and preserves the Phase 2 product surface.

### Q2. Should scenario analysis live in a new top-level backend module?
**Answer:** No.

Keep orchestration within the existing `chat` module. Introduce internal chat subpackages only when they clearly support the existing public chat flow, e.g. `chat.thread`, `chat.memory`, or `chat.scenario`, but do not create a separate product stack.

### Q3. How should thread continuity be persisted?
**Answer:** Persist thread identifiers, user turns, and normalized explicit fact state in backend storage owned by the chat module.

The persistence shape can be relational tables plus repositories because Phase 3 needs durable continuity, corrected-fact replacement, and deterministic testability. Pure ephemeral in-memory memory would violate D-04.

### Q4. What should memory store?
**Answer:** Only explicit user facts plus enough metadata to resolve latest-value replacement.

Do not store inferred legal conclusions, model assumptions, or synthesized facts as memory state. This directly enforces D-07 and D-08.

### Q5. When should the system clarify?
**Answer:** Before final scenario composition whenever critical facts required for a grounded outcome are missing, contradictory, or underspecified.

Clarification should produce a handled chat response that still preserves Phase 2 disclaimer/citation behavior and avoids overclaiming.

### Q6. Should final structured scenario output replace the Phase 2 response contract?
**Answer:** No.

Extend the existing `ChatAnswerResponse` contract with additional optional scenario fields or structured sections while keeping existing fields valid for Phase 2 clients. The endpoint should remain compatible with older callers that only use `groundingStatus`, `answer`, `disclaimer`, `citations`, and `sources`.

## Architecture Alignment Rules

### Use the existing chat surface
- Public controller remains `chat/api/PublicChatController`
- Public path remains `ApiPaths.CHAT = "/api/v1/chat"`
- Service orchestration remains centered on `chat/service/ChatService`

### Preserve Phase 2 internals that must still be used
- Retrieval via `RetrievalPolicy.buildRequest(...)`
- Citation derivation via chat citation mapping and retrieved metadata
- Disclaimer and refusal composition via the existing answer-composition policy path
- Approved+trusted+active retrieval filter remains untouched

### Allowed additions inside the chat module
These are acceptable if they support the existing chat flow:
- thread persistence entities/repositories
- fact extraction and replacement logic
- clarification planning logic
- scenario-specific DTO fragments nested under `chat/api/dto`
- orchestration helpers/services called by `ChatService`

### Not allowed in this phase plan
- a new public `/api/v1/case-analysis` endpoint
- a parallel `src/main/java/com/vn/traffic/chatbot/caseanalysis/...` tree
- a separate DTO family that bypasses `ChatAnswerResponse` without explicit user approval
- any design that weakens the Phase 2 retrieval/citation/disclaimer gate

## Recommended Contract Direction

### Request contract
Keep `question` as-is for backward compatibility and add optional fields such as:
- `threadId`
- `mode` or `analysisMode` only if truly needed for orchestration
- `facts` only if the backend needs structured explicit overrides later; not required for the first plan

Phase 3 should continue accepting requests that only contain `question`.

### Response contract
Keep the existing Phase 2 fields and add optional fields such as:
- `threadId`
- `clarificationNeeded`
- `clarificationQuestions`
- `rememberedFacts`
- `analysis` object or parallel scenario section fields aligned to Facts / Rule / Outcome / Actions / Sources

The exact shape should optimize backward compatibility and testability, not create a second public response family.

## Testing Implications

The strongest regression net for this phase is:
1. serialization/contract tests for backward-compatible DTO evolution
2. service tests for fact memory and latest-correction replacement
3. orchestration tests for clarification-needed branching
4. controller/integration regressions proving `/api/v1/chat` still works for both Phase 2 and Phase 3 scenarios

## Decision Coverage Matrix

| Decision | Plan | Task | Coverage | Notes |
|----------|------|------|----------|-------|
| D-01 extend existing chat module | 03-01..03-04 | all | Full | No parallel package tree |
| D-02 preserve `/api/v1/chat` surface | 03-01, 03-04 | tasks 1-2 | Full | DTO/controller extension only |
| D-03 preserve Phase 2 grounding behavior | 03-03, 03-04 | tasks 1-2 | Full | Retrieval/citation/disclaimer regressions retained |
| D-04 persistent threads | 03-01, 03-02 | tasks 1-2 | Full | persistence foundation + continuity logic |
| D-05 clarify before conclude | 03-03 | tasks 1-2 | Full | clarification branch before final composition |
| D-06 Facts→Rule→Outcome with actions and sources | 03-04 | tasks 1-2 | Full | final composition in existing response flow |
| D-07 explicit-user-facts-only memory | 03-02 | tasks 1-2 | Full | fact extraction + persistence rules |
| D-08 latest corrected fact wins | 03-02 | tasks 1-2 | Full | replacement semantics tested |

## Bottom Line

Phase 3 should be planned as backend thread continuity, explicit fact memory, clarification branching, and scenario composition on top of the shipped Phase 2 chat backend. The existing `chat` module and `/api/v1/chat` contract are the correct architectural home for this phase.
