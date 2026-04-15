---
phase: 06-audit-real-data-validation-and-stabilization
plan: 06
subsystem: frontend-e2e
tags: [playwright, e2e-tests, chat-ui, admin-ui, ui-validation]
dependency_graph:
  requires: [06-02]
  provides: [playwright-chat-spec, playwright-admin-spec, playwright-results]
  affects: [06-05]
tech_stack:
  added: []
  patterns: [playwright-e2e, selector-class-based, blocked-infrastructure]
key_files:
  created:
    - frontend/e2e/chat.spec.ts
    - frontend/e2e/admin.spec.ts
    - .planning/phases/06-audit-real-data-validation-and-stabilization/playwright_results.md
  modified: []
decisions:
  - "Corrected selector from [data-from=assistant] to .is-assistant — Message component uses CSS class not data attribute"
  - "Corrected citation selector from 'Nguồn' to 'Nguồn tham khảo' — actual CitationList section header text"
  - "All 15 tests BLOCKED (not FAILED) due to missing node_modules and backend/frontend not running in worktree context"
  - "Selector cross-validation performed against actual component source before commit"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-13"
  tasks_completed: 1
  files_modified: 3
---

# Phase 06 Plan 06: Playwright UI E2E Tests Summary

**One-liner:** 15 Playwright E2E tests written (10 chat + 5 admin) with selectors validated against actual message-bubble.tsx and Message component — BLOCKED from executing due to missing node_modules and backend/frontend not running in worktree

## Task Results

### Task 1: Write Playwright E2E tests — chat UI and admin UI

**Status:** COMPLETE

Both spec files written with correct selectors derived from reading the actual source code:

**frontend/e2e/chat.spec.ts** — 10 tests (T-UI-01 through T-UI-10):

| Test | Category | Key Assertion |
|------|----------|---------------|
| T-UI-01 | Grounded | `bubble.toContainText('tham khảo')` — disclaimer text present |
| T-UI-02 | Grounded | `bubble.toContainText('Nguồn tham khảo')` — citation section header |
| T-UI-03 | Grounded | `bubble.toContainText('Căn cứ pháp lý')` — legal basis section |
| T-UI-04 | Grounded | Both 'Căn cứ pháp lý' AND 'tham khảo' present |
| T-UI-05 | Clarification | `bubble.toContainText('Cần làm rõ thêm')` — amber box |
| T-UI-06 | Clarification | `bubble.toContainText('phương tiện')` — pending fact prompt |
| T-UI-07 | Refused | NOT 'Căn cứ pháp lý', NOT 'Nguồn tham khảo' |
| T-UI-08 | Hallucination | NOT 'Điều 99b' — hallucination probe |
| T-UI-09 | Multi-turn | New thread button visible, thread appears in sidebar |
| T-UI-10 | Multi-turn | Two .is-assistant bubbles after second message |

**frontend/e2e/admin.spec.ts** — 5 tests (T-UI-11 through T-UI-15):

| Test | Page | Key Assertion |
|------|------|---------------|
| T-UI-11 | /sources | Decree names visible (Nghị định, Luật Giao thông) |
| T-UI-12 | /sources | APPROVED badge visible |
| T-UI-13 | /parameters | 'Bộ tham số AI' and ACTIVE status present |
| T-UI-14 | /chat-logs | At least one table row visible |
| T-UI-15 | /checks | Check definitions text visible |

**Verification:**
```
grep -c "^test(" frontend/e2e/chat.spec.ts  → 10 ✓
grep -c "^test(" frontend/e2e/admin.spec.ts → 5  ✓
```

### Task 2: Run Playwright tests against live system

**Status:** BLOCKED (checkpoint:human-verify — infrastructure not available)

Per the context note provided with this plan: "If the backend/frontend are not running, the tests will fail with connection errors. That is expected per the plan — document it in playwright_results.md with 'Backend/frontend not running' as the failure category, note all tests as SKIP/BLOCKED (not FAIL due to code issues), and create the SUMMARY with a note about re-execution requirements."

**Block reason:**
1. `frontend/node_modules` absent in this worktree — Playwright npm module not installed
2. Backend (Spring Boot) not running at http://localhost:8089
3. Frontend (Next.js) not running at http://localhost:3000

**Result:** All 15 tests recorded as BLOCKED in `playwright_results.md`. No FAIL classifications — no code-level test failures detected.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | 8ac2284 | feat(06-06): write Playwright E2E test suite — 10 chat + 5 admin tests |

## Deviations from Plan

### Selector Corrections Applied

**1. [Rule 1 - Bug] Corrected data-from selector to CSS class selector**
- **Found during:** Task 1, reading message.tsx
- **Issue:** Plan template used `[data-from="assistant"]` selector. The `Message` component (components/ai-elements/message.tsx line 37-46) does NOT render a `data-from` attribute — it applies CSS classes: `is-user` for user messages, `is-assistant` for assistant messages.
- **Fix:** Changed all selectors from `page.locator('[data-from="assistant"]')` to `page.locator('.is-assistant')`
- **Files modified:** frontend/e2e/chat.spec.ts
- **Commit:** 8ac2284

**2. [Rule 1 - Bug] Corrected citation text selector to match actual component output**
- **Found during:** Task 1, reading message-bubble.tsx CitationList component
- **Issue:** Plan template used `'Nguồn'` as assertion text. The `CitationList` component renders `<p>Nguồn tham khảo</p>` as the section header (message-bubble.tsx line 46).
- **Fix:** Changed assertion to `toContainText('Nguồn tham khảo')`
- **Files modified:** frontend/e2e/chat.spec.ts (T-UI-02)
- **Commit:** 8ac2284

**3. [Rule 1 - Bug] Corrected "New Chat" button selector to match actual button text**
- **Found during:** Task 1, reading thread-list.tsx
- **Issue:** Plan template used `/new|mới|tạo/i` regex. ThreadList renders a button with exact text "Cuộc hội thoại mới" (thread-list.tsx line 26).
- **Fix:** Changed to `page.getByRole('button', { name: /Cuộc hội thoại mới/i })`
- **Files modified:** frontend/e2e/chat.spec.ts (T-UI-09)
- **Commit:** 8ac2284

## Known Stubs

None — spec files contain real assertions against actual component behavior. No placeholder or trivially-passing tests.

## Re-execution Instructions

To run these tests in a full environment:

```bash
# Start backend
./gradlew bootRun  # or: java -jar build/libs/*.jar

# Install frontend deps and start
cd frontend
pnpm install
pnpm dev

# Run tests
npx playwright test --reporter=html

# View report
npx playwright show-report
```

Expected output: "15 passed" when all infrastructure is running and real ingested data is present (43 chunks from Plan 02).

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced. Test files only.

Note: per threat model T-06-06-01, `playwright-report/` should not be committed to git (verify .gitignore). Test files themselves do not capture sensitive data.

## Self-Check

- frontend/e2e/chat.spec.ts: EXISTS ✓
- frontend/e2e/admin.spec.ts: EXISTS ✓
- playwright_results.md: EXISTS ✓
- commit 8ac2284: EXISTS ✓
- grep -c "^test(" chat.spec.ts = 10 ✓
- grep -c "^test(" admin.spec.ts = 5 ✓
- T-UI-05 contains 'Cần làm rõ thêm' assertion ✓
- T-UI-07 contains not.toContainText('Căn cứ pháp lý') ✓
- T-UI-08 contains not.toContainText('Điều 99b') ✓

## Self-Check: PASSED
