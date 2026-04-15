# Phase 3 Discussion Log

**Date:** 2026-04-10
**Phase:** 3 — Multi-turn Case Analysis

## Areas Selected
- Thread model
- Clarifying-question policy
- Case-analysis structure
- Fact memory rules

## Q&A Audit Trail

### 1. Thread model
**Question:** For conversation continuity, which thread model do you want in Phase 3?

**Options presented:**
- Persistent thread — Each conversation has a thread ID and later messages continue that same case history.
- Short session — Continuity only lasts within a temporary session, with no durable thread identity.
- Branchable thread — Users can continue a thread but also fork into alternate scenario paths when facts change.
- Claude decide — Use the simplest thread model that best fits the current backend and Phase 3 scope.

**User selection:** Claude decide

**Captured outcome:** Persistent thread recommended and adopted for context capture as the simplest fit for CHAT-02 and later frontend continuity.

### 2. Clarifying-question behavior
**Question:** When facts are missing, how should clarifying questions behave?

**Options presented:**
- Block final answer — Ask for the missing fact first, and do not give a final scenario conclusion until the user replies.
- Provisional answer — Give a cautious provisional analysis plus the clarifying question in the same response.
- One-shot clarify — Ask at most one clarifying question, then answer with caveats even if details are still missing.
- Claude decide — Let implementation choose the behavior within the phase goals.

**User selection:** Block final answer

### 3. Scenario response structure
**Question:** For scenario-based answers, what structure should the final response follow?

**Options presented:**
- Facts→Rule→Outcome — Summarize facts, cite the rule, explain likely consequence, then give actions and sources.
- Conclusion first — Lead with the likely outcome, then back it up with facts, rule, actions, and sources.
- Checklist style — Use a practical step-by-step checklist style for users rather than a legal-analysis flow.
- Claude decide — Let implementation choose the best structure as long as it stays consistent.

**User selection:** Facts→Rule→Outcome

### 4. Fact memory rules
**Question:** How should prior-case facts be carried forward within a thread?

**Options presented:**
- Explicit facts only — Only persist concrete facts the user clearly stated; assumptions never become memory.
- Facts plus inferences — Persist both user-stated facts and high-confidence system inferences unless corrected later.
- Latest turn only — Use only the latest message as authoritative and avoid accumulating case memory over time.
- Claude decide — Let implementation choose the memory behavior within the phase scope.

**User selection:** Explicit facts only

### 5. Fact correction handling
**Question:** If the user later corrects an earlier case fact in the same thread, what should happen?

**Options presented:**
- Latest fact wins — The corrected fact replaces the earlier one in thread memory, and later analysis should use the correction.
- Keep both noted — Retain both the old and new fact explicitly as a contradiction for the next answer to discuss.
- Ask to confirm — Do not update memory until the system asks the user to confirm which version is true.
- Claude decide — Let implementation choose the correction rule.

**User selection:** Latest fact wins

## Notes
- Discussion stayed within Phase 3 scope.
- No backlog todos were matched into this phase.
- Decisions intentionally preserve Phase 2 grounding, citation, and disclaimer behavior.
