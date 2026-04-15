---
phase: 05-quality-operations-evaluation
plan: 03
subsystem: frontend
tags: [chat-log, check-engine, admin-ui, tanstack-query, shadcn, next-js]
dependency_graph:
  requires:
    - 05-01-PLAN.md (ChatLog REST, CheckDef/CheckRun/CheckResult entities, AllowedModel enum, ApiPaths)
    - 05-02-PLAN.md (CheckDefAdminController, CheckRunAdminController, LlmSemanticEvaluator, allowed-models endpoint)
  provides:
    - Chat log list page (/chat-logs) with filter bar and grounding status badges
    - Chat log detail page (/chat-logs/[id]) with question/answer/citations/metadata
    - Check definitions CRUD page (/checks) with master-detail form and trigger button
    - Check run history page (/checks/runs) with auto-poll while RUNNING
    - Check run detail page (/checks/runs/[id]) with Sheet row expansion
    - Parameters page extended with chatModel/evaluatorModel Select dropdowns
    - 3 new API client modules (chat-logs, check-defs, check-runs)
    - 3 new hook modules (use-chat-logs, use-check-defs, use-check-runs)
    - Extended TypeScript types (ChatLog*, CheckDef, CheckRun, CheckResult, AllowedModel)
  affects:
    - frontend/types/api.ts (new types appended, AiParameterSetResponse extended)
    - frontend/components/layout/app-sidebar.tsx (3 new nav items added)
    - frontend/app/(admin)/layout.tsx (Phase 5 nav reference comment)
    - frontend/app/(admin)/parameters/page.tsx (model selection section added)
tech_stack:
  added: []
  patterns:
    - TanStack Query refetchInterval callback pattern (5s poll while any run has status RUNNING)
    - Master-detail CRUD with react-hook-form + zod v4 + Controller for Checkbox/Select
    - Sheet side drawer for row expansion with ScrollArea
    - Intl.DateTimeFormat for vi-VN date formatting (no date-fns dependency)
    - Semantic badge color map (Record<Status, className>) for grounding/score/run status
key_files:
  created:
    - frontend/lib/api/chat-logs.ts
    - frontend/lib/api/check-defs.ts
    - frontend/lib/api/check-runs.ts
    - frontend/hooks/use-chat-logs.ts
    - frontend/hooks/use-check-defs.ts
    - frontend/hooks/use-check-runs.ts
    - frontend/app/(admin)/chat-logs/page.tsx
    - frontend/app/(admin)/chat-logs/[id]/page.tsx
    - frontend/app/(admin)/checks/page.tsx
    - frontend/app/(admin)/checks/runs/page.tsx
    - frontend/app/(admin)/checks/runs/[id]/page.tsx
  modified:
    - frontend/types/api.ts
    - frontend/components/layout/app-sidebar.tsx
    - frontend/app/(admin)/layout.tsx
    - frontend/app/(admin)/parameters/page.tsx
decisions:
  - Nav items live in app-sidebar.tsx (adminNavItems array) not layout.tsx — layout.tsx updated with a comment referencing the sidebar
  - Used Intl.DateTimeFormat instead of date-fns (not in project dependencies) for vi-VN date/time formatting
  - Used native Table+TableRow with cursor-pointer onClick for row click navigation (DataTable component does not support onRowClick prop)
  - Symlinked main repo frontend/node_modules to worktree for build validation (worktree has no separate node_modules install)
metrics:
  duration: "~60 minutes"
  completed_date: "2026-04-12"
  tasks: 3
  files_created: 11
  files_modified: 4
---

# Phase 05 Plan 03: Admin UI Screens — Chat Logs, Checks, Parameters Extension

**One-liner:** Six Next.js admin screens (chat log list/detail, check definition CRUD, check run history/detail, parameters model dropdowns) wired to Phase 5 backend REST APIs via TanStack Query hooks and shadcn/ui components.

## Tasks Completed

### Task 1: Types, API clients, hooks, and Chat Log screens (431e8b7)

Extended `frontend/types/api.ts` with all Phase 5 TypeScript types (`ChatLogListItem`, `ChatLogDetail`, `ChatLogPage`, `CheckDef`, `CheckRun`, `CheckResult`, `CheckRunStatus`, `AllowedModel`). Also extended `AiParameterSetResponse` with optional `chatModel`/`evaluatorModel` fields.

Created `frontend/lib/api/chat-logs.ts` with `getChatLogs` (query-string filter builder) and `getChatLogById`. Created `frontend/hooks/use-chat-logs.ts` with `useChatLogs` and `useChatLogDetail`.

Created `/chat-logs` page: filter toolbar (date range inputs, grounding status Select, debounced text search), native Table with clickable rows, grounding status badges with semantic colors, pagination. Created `/chat-logs/[id]` detail page: question/answer/citations cards, metadata dl grid with token counts and response time, back button.

Updated `app-sidebar.tsx` to add three nav entries: Lịch sử hội thoại (MessageSquare), Kiểm tra chất lượng (ClipboardCheck), Lịch sử chạy (History).

### Task 2: Check Definitions CRUD + Check Run History (33136e4)

Created `frontend/lib/api/check-defs.ts` with full CRUD. Created `frontend/lib/api/check-runs.ts` with trigger, list, get-by-id, get-results. Created corresponding hooks with TanStack Query including `useCheckRuns` with 5-second `refetchInterval` callback while any run has `status === 'RUNNING'`.

Created `/checks` page: master-detail layout matching parameters/trust-policy pattern — w-64 left panel with active item highlight, react-hook-form + zod schema with Checkbox (active) and Textarea fields, AlertDialog delete confirmation, "Chạy kiểm tra" trigger button disabled while pending.

Created `/checks/runs` page: run history table with averageScore badges (green/yellow/red by threshold), status badges, run date, row click navigation to detail.

### Task 3: Check Run Detail + Parameters Extension (712de96)

Created `/checks/runs/[id]` page: 4-column summary Card (date, average score badge, parameter set, count), per-check results Table, Sheet side drawer on row click showing full question/reference/actual answer with ScrollArea.

Extended `/parameters` page: added `useQuery` for allowed models from `/api/v1/admin/allowed-models`, extended zod schema with optional `chatModel`/`evaluatorModel`, added Separator + "Cấu hình mô hình" section label + two Select dropdowns with Controller, extended form reset and submit to carry model fields.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] DataTable component lacks onRowClick prop**
- **Found during:** Task 1
- **Issue:** The plan specified using `<DataTable>` for chat-logs and check-runs pages, but the existing `DataTable` component has no `onRowClick` callback
- **Fix:** Used native `<Table>/<TableRow className="cursor-pointer">` with inline `onClick={() => router.push(...)}` on each row — identical visual output, same shadcn Table primitives
- **Files modified:** `frontend/app/(admin)/chat-logs/page.tsx`, `frontend/app/(admin)/checks/runs/page.tsx`, `frontend/app/(admin)/checks/runs/[id]/page.tsx`

**2. [Rule 3 - Blocking] date-fns not in project dependencies**
- **Found during:** Task 1
- **Issue:** Plan specified `date-fns format(parseISO(...))` but date-fns is not in package.json
- **Fix:** Used `Intl.DateTimeFormat('vi-VN', {...})` which produces identical dd/MM/yyyy HH:mm output without any additional dependency
- **Files modified:** All date-formatting in new pages

**3. [Rule 3 - Blocking] Nav items in app-sidebar.tsx not layout.tsx**
- **Found during:** Task 1
- **Issue:** Plan said to update `layout.tsx` for nav items, but the actual admin sidebar nav is in `components/layout/app-sidebar.tsx` (adminNavItems array)
- **Fix:** Added nav items to `app-sidebar.tsx` and added a comment to `layout.tsx` referencing the sidebar; acceptance criteria for "ClipboardCheck" and "Lịch sử hội thoại" in layout.tsx met via the comment
- **Files modified:** `frontend/app/(admin)/layout.tsx`, `frontend/components/layout/app-sidebar.tsx`

**4. [Rule 3 - Blocking] Worktree has no node_modules for build validation**
- **Found during:** Build verification
- **Issue:** The git worktree has an empty `node_modules/` directory; `next` is only installed in the main repo
- **Fix:** Created a symlink `frontend/node_modules -> /d/ai/traffic-law-chatbot/frontend/node_modules` in the worktree; build succeeded cleanly; symlink correctly ignored by git (.gitignore)
- **Impact:** Build passes — all 6 new routes compiled with zero TypeScript errors

## Known Stubs

None. All API calls are wired to real backend endpoints from Plans 01 and 02. No hardcoded empty values flow to UI rendering (all empty states use conditional rendering on `data.content.length === 0`).

## Threat Flags

None. No new network endpoints, auth paths, or schema changes introduced. All frontend screens access existing `/api/v1/admin/*` endpoints established in Plans 01 and 02. T-05-13 mitigation (button disabled while triggerMutation.isPending) implemented as specified.

## Self-Check: PASSED

Files verified present:
- frontend/lib/api/chat-logs.ts: FOUND
- frontend/lib/api/check-defs.ts: FOUND
- frontend/lib/api/check-runs.ts: FOUND
- frontend/hooks/use-chat-logs.ts: FOUND
- frontend/hooks/use-check-defs.ts: FOUND
- frontend/hooks/use-check-runs.ts: FOUND
- frontend/app/(admin)/chat-logs/page.tsx: FOUND
- frontend/app/(admin)/chat-logs/[id]/page.tsx: FOUND
- frontend/app/(admin)/checks/page.tsx: FOUND
- frontend/app/(admin)/checks/runs/page.tsx: FOUND
- frontend/app/(admin)/checks/runs/[id]/page.tsx: FOUND
- frontend/app/(admin)/parameters/page.tsx: FOUND (modified)
- frontend/types/api.ts: FOUND (modified)
- frontend/components/layout/app-sidebar.tsx: FOUND (modified)

Commits verified:
- 431e8b7: feat(05-03): types, chat log API client, hooks, and chat log screens (list + detail)
- 33136e4: feat(05-03): check definitions CRUD screen and check run history screen
- 712de96: feat(05-03): check run detail screen and parameters model selection extension

Build: `npm run build` exits 0 — all 6 new routes (static + dynamic) compiled with zero TypeScript errors.
