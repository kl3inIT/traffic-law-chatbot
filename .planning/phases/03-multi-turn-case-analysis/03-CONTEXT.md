# Phase 3: Multi-turn Case Analysis - Context

**Gathered:** 2026-04-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Add multi-turn conversation continuity on top of the grounded Phase 2 chat flow so the system can carry forward case facts within a thread, detect when key facts are missing, ask clarifying follow-up questions before giving a final scenario conclusion, and return consistent structured scenario analysis for realistic Vietnam traffic-law cases.

This phase extends backend chat behavior and contracts. It does not add Phase 4 UI concerns, new admin capabilities, or broader legal-domain expansion.

</domain>

<decisions>
## Implementation Decisions

### Thread continuity
- **D-01:** Phase 3 should use a persistent thread model rather than a temporary session-only memory model.
- **D-02:** Conversation continuity should be anchored to a thread identity so later messages continue the same case history.

### Clarifying-question behavior
- **D-03:** When material facts are missing, the system should ask a clarifying question first and block a final scenario conclusion until the user responds.
- **D-04:** Clarifying-question behavior should favor safe incompleteness over provisional legal conclusions.

### Scenario answer structure
- **D-05:** Final scenario-based answers should follow a consistent Facts → Rule → Outcome flow.
- **D-06:** The response should still include practical actions and sources, preserving the Phase 2 grounding and citation expectations.

### Thread fact memory
- **D-07:** Only explicit facts clearly stated by the user should persist in thread memory.
- **D-08:** System assumptions or inferences must not be promoted into remembered case facts.
- **D-09:** If the user corrects an earlier fact in the same thread, the latest user-stated fact wins and should replace the prior version in subsequent analysis.

### Claude's Discretion
- Exact REST contract shape for thread creation, thread continuation, and per-message payloads.
- Internal representation of remembered facts, contradiction handling, and missing-fact tracking.
- How many clarifying questions may be asked across a case before refusal or fallback behavior applies.
- Whether clarifying prompts are represented as a dedicated response mode, status field, or structured answer subtype.
- The precise wording and formatting used for Facts, Rule, Outcome, action guidance, and source sections.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase definition
- `.planning/ROADMAP.md` — Defines Phase 3 goal, requirements, and success criteria for multi-turn continuity, clarifying questions, and case analysis.
- `.planning/REQUIREMENTS.md` — Defines CHAT-02, CASE-01, CASE-02, CASE-03, and CASE-04.
- `.planning/PROJECT.md` — Defines Vietnamese-first guidance, trusted-source constraints, and the REST-first backend direction.
- `.planning/STATE.md` — Confirms Phase 3 is now the active priority after Phase 2 shipping.

### Prior locked context
- `.planning/phases/02-grounded-legal-q-a-core/02-CONTEXT.md` — Carries forward grounding, citation visibility, disclaimer behavior, and the explicit deferral of multi-turn continuity and clarifying questions into Phase 3.

### Existing backend foundations
- `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java` — Defines the current single-turn public chat endpoint that Phase 3 will extend rather than replace conceptually.
- `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatQuestionRequest.java` — Shows the current single-question request contract with no thread identity yet.
- `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java` — Shows the current grounded-answer response contract that Phase 3 must extend for scenario and clarifying-question behavior.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` — Defines the current single-turn retrieval, grounding decision, prompt, and answer-composition flow that Phase 3 builds on.
- `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java` — Preserves the approved + trusted + active retrieval gate that must remain intact for scenario analysis too.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java` — Existing REST entry point for public chat can anchor Phase 3 thread-aware endpoints or request extensions.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` — Existing grounded-answer orchestration is the main reuse point for adding thread context, missing-fact detection, and clarifying-question branching.
- `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java` — Existing structured answer contract already separates grounding status, conclusion, disclaimer, sections, citations, and sources.
- `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatQuestionRequest.java` — Existing request DTO is the obvious place to extend or evolve for thread continuation inputs.

### Established Patterns
- The backend is REST-first and currently exposes chat through a simple controller/service/DTO split.
- Grounding safety already happens before answer generation in `ChatService`, and Phase 3 must preserve that fail-safe behavior.
- Citations and sources are already first-class response fields, so case analysis should stay source-backed rather than inventing a separate uncited mode.
- No thread, conversation-log, message-history, or case-fact persistence model exists yet in the current backend.

### Integration Points
- Phase 3 should extend the `chat` module under `src/main/java/com/vn/traffic/chatbot/chat/` rather than introducing a parallel chatbot stack.
- Thread continuity will need a new persistence and API contract layer that still feeds into the existing retrieval + answer composition path.
- Clarifying-question handling should integrate with the current grounding/status flow so ambiguous scenarios fail safely instead of bypassing safety rules.
- Later Phase 4 frontend work will depend on whatever thread/message contract Phase 3 establishes, so backend responses should be explicit enough for UI state handling.

</code_context>

<specifics>
## Specific Ideas

- The user wants all four main gray areas handled in this phase: thread model, clarifying-question policy, scenario answer structure, and fact memory rules.
- Persistent threads are preferred as the simplest product fit for true conversation continuity without branching into alternate-path UX yet.
- The system should behave conservatively when facts are missing: ask first, conclude later.
- Case memory should stay grounded in user-stated facts only, with later user corrections replacing earlier remembered facts.

</specifics>

<deferred>
## Deferred Ideas

- Branchable or forked scenario threads are out of scope for Phase 3 and belong in a future phase if needed.
- Temporary session-only continuity was considered but rejected in favor of durable thread continuity.
- Full chat/admin UI workflow details remain in Phase 4.

</deferred>

---

*Phase: 03-multi-turn-case-analysis*
*Context gathered: 2026-04-10*
