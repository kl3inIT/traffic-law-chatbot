---
phase: 02-grounded-legal-q-a-core
plan: 04
subsystem: api
tags: [spring, chat, retrieval, grounding, citations, testing]
requires:
  - phase: 02-grounded-legal-q-a-core
    provides: public grounded chat endpoint, answer composer, citation contract, retrieval-backed chat orchestration
provides:
  - retrieval-readiness counts for approved trusted active eligibility diagnosis
  - non-500 handling for empty or null eligible retrieval outcomes
  - refusal responses with disclaimer plus actionable next steps
  - focused regression coverage for grounded legal-basis penalty procedure questions
affects: [phase-02-gap-closure, phase-03-multi-turn-case-analysis, chat, retrieval, testing]
tech-stack:
  added: []
  patterns: [handled weak-grounding refusal path, code-backed retrieval readiness diagnostics, regression-first chat slice verification]
key-files:
  created: [.planning/phases/02-grounded-legal-q-a-core/02-04-SUMMARY.md]
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerCompositionPolicy.java
    - src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java
    - src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/api/ChatContractSerializationTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java
key-decisions:
  - "Keep the approved+trusted+active retrieval gate unchanged and expose readiness through explicit counts rather than logs."
  - "Treat zero-result and null-result retrieval outcomes as handled refusal flows instead of surfacing server errors."
  - "Make refusal guidance actionable with stable next-step recommendations while preserving disclaimer and conditional grounded sections."
patterns-established:
  - "Weak-grounding handling: empty or malformed retrieval context returns a structured REFUSED response with no model call."
  - "Readiness diagnostics: reuse retrieval-filter semantics in inspection queries so UAT diagnosis matches production gating."
requirements-completed: [CHAT-01, CHAT-03, CHAT-04, LEGAL-01, LEGAL-02, LEGAL-03, LEGAL-04]
duration: 31 min
completed: 2026-04-10
---

# Phase 2 Plan 04: Gap Closure Summary

**Grounded chat now exposes approved/trusted/active retrieval readiness, avoids 500s on empty retrieval paths, and returns refusal guidance with actionable Vietnamese next steps.**

## Performance

- **Duration:** 31 min
- **Started:** 2026-04-10T02:35:00Z
- **Completed:** 2026-04-10T03:06:24Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- Added code-backed retrieval-readiness counts, including the combined approved+trusted+active eligible chunk count used to explain Phase 2 UAT failures.
- Hardened `ChatService` so empty and null retrieval results follow a handled refusal path instead of becoming HTTP 500 failures.
- Upgraded refusal responses to include both the required disclaimer and concrete `nextSteps`, with regression coverage across service, controller, serialization, and integration tests.

## Task Commits

Each task was committed atomically:

1. **Task 1: Make retrieval readiness diagnosable and non-silent for the approved/trusted/active path** - `f9927b3` (fix)
2. **Task 2: Ensure refusal responses include disclaimer and actionable recommended next steps** - `aa2fa94` (fix)
3. **Task 3: Re-run focused Phase 2 regression and live-UAT proof commands** - no code changes required after verification; covered by the focused regression run

## Files Created/Modified
- `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java` - added approved, trusted, active, and combined eligible retrieval-readiness counts.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` - treated empty/null retrieval results as handled refusals and triggered readiness diagnostics on refusal paths.
- `src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java` - populated refusal answers with actionable next-step guidance.
- `src/main/java/com/vn/traffic/chatbot/chat/service/AnswerCompositionPolicy.java` - centralized stable refusal next-step strings.
- `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java` - added regression coverage for readiness diagnostics and non-empty refusal next steps.
- `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java` - asserted HTTP 200, visible citations, legal basis, penalties, and procedure steps for a live-style question.
- `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java` - verified handled weak-grounding requests stay HTTP 200 and include refusal next steps.
- `src/test/java/com/vn/traffic/chatbot/chat/api/ChatContractSerializationTest.java` - locked refusal payload serialization for disclaimer plus `nextSteps`.

## Decisions Made
- Preserved the approved+trusted+active retrieval gate exactly as defined in `RetrievalPolicy.RETRIEVAL_FILTER`; only observability and handling around that gate changed.
- Kept refusal handling in the existing response contract instead of introducing a new exception-mapping API shape, because the current Phase 2 endpoint already supports structured `REFUSED` responses.
- Used deterministic refusal next-step strings in policy/composer code so controller and serialization tests can lock the payload shape against regression.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Corrected the isolated worktree onto the intended Phase 2 branch before implementation**
- **Found during:** Task 1 startup
- **Issue:** The spawned worktree branch was based on `main`, so the plan-target chat files were missing even though the local `gsd/phase-02-grounded-legal-q-a-core` branch contained them.
- **Fix:** Fast-forwarded the isolated agent branch to the local Phase 2 branch tip before executing the gap plan.
- **Files modified:** worktree branch state only; no source files changed by this fix
- **Verification:** Confirmed the Phase 2 chat source and test files became present, then completed all plan verification commands successfully.
- **Committed in:** not a separate repository commit; prerequisite workspace correction before task commits

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to execute the actual 02-04 scope in the isolated worktree. No product scope creep.

## Issues Encountered
- The integration regression initially expected bracketed inline labels (`[Nguồn 1]`), but the locked `CitationMapper` contract emits `Nguồn 1`. The assertion was corrected to the actual contract without changing production citation formatting.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Phase 2 gap-closure regressions are covered and green, so Phase 3 can build on a stable single-turn grounded chat baseline.
- Retrieval readiness is now diagnosable when future multi-turn or live UAT flows report missing grounded answers.

## Self-Check: PASSED
- Found summary file: `.planning/phases/02-grounded-legal-q-a-core/02-04-SUMMARY.md`
- Found task commit: `f9927b3`
- Found task commit: `aa2fa94`
