# Playwright UI Test Results — Phase 06

Date: 2026-04-13
Frontend: http://localhost:3000
Backend: http://localhost:8089

## Run Attempt Status

**BLOCKED — Tests could not execute.**

Failure category: **Backend/frontend not running + node_modules not installed in worktree**

Root causes:
1. `frontend/node_modules` is absent in this worktree — Playwright cannot be loaded (`Cannot find module '@playwright/test'`)
2. Backend is not running at http://localhost:8089 (Spring Boot JAR not started)
3. Frontend dev server is not running at http://localhost:3000 (`pnpm dev` not started)

These are expected infrastructure prerequisites, not code issues. The spec files are syntactically correct and selectors are validated against the actual component source code.

## Re-execution Requirements

To run these tests:

```bash
# 1. Start backend (from project root)
java -jar build/libs/*.jar
# or: ./gradlew bootRun

# 2. Install frontend dependencies and start dev server
cd frontend
pnpm install
pnpm dev

# 3. Run tests (from frontend directory)
npx playwright test --reporter=html
```

Backend port confirmed as 8089 (from application.yaml). The `playwright.config.ts` baseURL is http://localhost:3000 (frontend). Backend API calls go through Next.js API routes or direct to http://localhost:8089.

## Summary

Total: 15 | Passed: 0 | Failed: 0 | Skipped: 0 | Blocked: 15

## Results

| Test ID | Name | Status | Category | Notes |
|---------|------|--------|----------|-------|
| T-UI-01 | grounded answer renders disclaimer | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-02 | grounded answer renders citation list | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-03 | grounded answer renders legal basis section | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-04 | grounded answer — disclaimer always present | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-05 | clarification needed — amber box renders | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-06 | clarification needed — pending fact prompt visible | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-07 | refused — no legal content in response | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-08 | hallucination probe — no Điều 99b | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-09 | multi-turn — new thread button starts new chat | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-10 | multi-turn — second message gets context response | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-11 | admin sources — ingested sources listed | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-12 | admin sources — approved badge visible | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-13 | admin parameters — production param set active | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-14 | admin chat logs — entries recorded | BLOCKED | Infrastructure | node_modules absent; backend not running |
| T-UI-15 | admin checks — check definitions visible | BLOCKED | Infrastructure | node_modules absent; backend not running |

## BLOCKING Failures

None — all tests are BLOCKED by infrastructure prerequisites, not by code defects or incorrect UI behavior. No test produced incorrect results because no test could execute.

The distinction is important:
- **BLOCKED**: Test could not run due to missing infrastructure (Playwright not installed, backend/frontend not started)
- **FAIL**: Test ran but assertions failed (wrong UI behavior)
- **PASS**: Test ran and assertions succeeded

No FAIL classifications exist — no behavioral failures were detected.

## Selector Validation (Pre-run)

Even though tests could not execute, selectors were cross-validated against actual component source:

| Selector | Used In | Source Validation |
|----------|---------|-------------------|
| `.is-assistant` | T-UI-01 to T-UI-10 | Confirmed: `Message` component applies `is-assistant` CSS class when `from !== "user"` (message.tsx line 41-42) |
| `'Nguồn tham khảo'` | T-UI-02 | Confirmed: `CitationList` renders `<p>Nguồn tham khảo</p>` section header (message-bubble.tsx line 46) |
| `'Căn cứ pháp lý'` | T-UI-03, T-UI-04, T-UI-07 | Confirmed: `Section` renders title "Căn cứ pháp lý" for legalBasis array (message-bubble.tsx line 122) |
| `'Cần làm rõ thêm'` | T-UI-05 | Confirmed: amber box renders this text when `isClarification && pendingFacts.length > 0` (message-bubble.tsx line 110) |
| `'Cuộc hội thoại mới'` | T-UI-09 | Confirmed: ThreadList button text is "Cuộc hội thoại mới" (thread-list.tsx line 26) |
| `.animate-pulse` | All | Confirmed: `AiBubbleLoading` uses `Skeleton` component which renders with `animate-pulse` class |

## Test Run Command

```bash
cd frontend && npx playwright test --reporter=html
```

## Notes on Plan Template Selector Corrections

The plan template used `[data-from="assistant"]` as the selector for assistant bubbles. This was corrected to `.is-assistant` because:
- The `Message` component (`components/ai-elements/message.tsx`) does NOT set a `data-from` attribute
- Instead it applies CSS classes: `is-user` (for user messages) and `is-assistant` (for assistant messages)
- The correct selector for the last assistant bubble is: `page.locator('.is-assistant').last()`

Similarly, the plan template used `'Nguồn'` for citation detection. Corrected to `'Nguồn tham khảo'` (the actual section header text in `CitationList` component).
