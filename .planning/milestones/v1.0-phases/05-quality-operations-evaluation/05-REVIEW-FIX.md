---
phase: 05-quality-operations-evaluation
fixed_at: 2026-04-13T10:30:00Z
review_path: .planning/phases/05-quality-operations-evaluation/05-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 5: Code Review Fix Report

**Fixed at:** 2026-04-13T10:30:00Z
**Source review:** .planning/phases/05-quality-operations-evaluation/05-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (WR-01 through WR-05; CR-* none; Info excluded by fix_scope)
- Fixed: 5
- Skipped: 0

## Fixed Issues

### WR-01 & WR-02: `CheckRunner.runAll` long-running transaction + no FAILED status on error

**Files modified:** `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java`
**Commit:** `39badce`
**Applied fix:** Removed `@Transactional` from `runAll` (keeping only `@Async`). Extracted three short-lived transactional helpers: `loadRun` (readOnly), `loadActiveDefs` (readOnly), and `persistResults` / `persistRunStatus` (write). Wrapped the entire body of `runAll` in a `try/catch(Exception)` block that sets the run status to `FAILED` and saves via `checkRunRepository` on any unhandled exception, preventing runs from being permanently stuck in `RUNNING`.

---

### WR-03: SQL injection risk via unparameterized LIKE wildcards in `ChatLogService.findFiltered`

**Files modified:** `src/main/java/com/vn/traffic/chatbot/chatlog/service/ChatLogService.java`
**Commit:** `2196565`
**Applied fix:** Replaced the raw string concatenation in the LIKE predicate with wildcard escaping (`!`, `%`, `_` characters are escaped with `!` as the escape character) and `cb.literal(...)` with an explicit escape character argument, preventing user-supplied `%` and `_` characters from acting as unintended SQL wildcards.

---

### WR-04: `ChatLogServiceTest` calls 7-argument `save` but method signature is 8-argument

**Files modified:** `src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogServiceTest.java`
**Commit:** `9d6a50f`
**Applied fix:** Added the missing 8th argument (`pipelineLog = null`) to both `chatLogService.save(...)` calls: the one in `testLogPersistedAfterAnswer` (line 44) and the one in `testLogFailureSwallowed` (line 74). Tests now match the actual `ChatLogService.save` signature.

---

### WR-05: `app-sidebar.tsx` prefix match highlights two nav items on `/checks/runs`

**Files modified:** `frontend/components/layout/app-sidebar.tsx`
**Commit:** `2dfdb40`
**Applied fix:** Replaced `pathname.startsWith(item.href)` with `pathname === item.href || pathname.startsWith(item.href + '/')`. This ensures `/checks/runs` activates only the "Lịch sử chạy" item and not the parent "Kiểm tra chất lượng" item, while still correctly highlighting parent items for deeper child routes.

---

_Fixed: 2026-04-13T10:30:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
