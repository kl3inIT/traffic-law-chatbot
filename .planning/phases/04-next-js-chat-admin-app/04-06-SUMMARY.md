---
phase: 04-next-js-chat-admin-app
plan: "06"
subsystem: frontend + backend
tags: [nextjs, tanstack-table, tanstack-query, shadcn, admin, sources, index]
dependency_graph:
  requires:
    - frontend/lib/api/sources.ts (from 04-03)
    - frontend/lib/api/chunks.ts (from 04-03)
    - frontend/lib/query-keys.ts (from 04-03)
    - frontend/types/api.ts (from 04-03)
    - frontend/components/ui/table.tsx (from 04-03)
    - frontend/components/ui/badge.tsx (from 04-03)
    - frontend/components/ui/dropdown-menu.tsx (from 04-03)
    - frontend/components/ui/alert-dialog.tsx (from 04-03)
    - frontend/components/ui/card.tsx (from 04-03)
    - frontend/components/ui/tooltip.tsx (from 04-03)
    - frontend/components/ui/skeleton.tsx (from 04-03)
    - frontend/app/(admin)/sources/page.tsx placeholder (from 04-04)
    - frontend/app/(admin)/index/page.tsx placeholder (from 04-04)
    - src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java (existing)
    - src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java (existing)
  provides:
    - frontend/components/admin/data-table.tsx
    - frontend/components/admin/sources/sources-table.tsx
    - frontend/components/admin/sources/columns.tsx
    - frontend/hooks/use-sources.ts
    - frontend/app/(admin)/sources/page.tsx (replaced placeholder)
    - frontend/components/admin/index/index-cards.tsx
    - frontend/hooks/use-index.ts
    - frontend/app/(admin)/index/page.tsx (replaced placeholder)
    - POST /api/v1/admin/sources/{sourceId}/reingest endpoint
  affects:
    - Plan 04-07 (parameter sets admin builds on same admin layout pattern)
tech_stack:
  added:
    - "@tanstack/react-table@^8.21.3 (useReactTable, getCoreRowModel, getSortedRowModel)"
  patterns:
    - Reusable DataTable<TData, TValue> generic wrapper
    - Context-sensitive action menus per source lifecycle status
    - AlertDialog confirmation for destructive mutations
    - TanStack Query invalidate-on-success pattern for all source mutations
    - base-ui render prop pattern for DropdownMenuTrigger and TooltipTrigger
key_files:
  created:
    - frontend/components/admin/data-table.tsx
    - frontend/components/admin/sources/columns.tsx
    - frontend/components/admin/sources/sources-table.tsx
    - frontend/hooks/use-sources.ts
    - frontend/components/admin/index/index-cards.tsx
    - frontend/hooks/use-index.ts
  modified:
    - frontend/app/(admin)/sources/page.tsx (replaced placeholder with live DataTable)
    - frontend/app/(admin)/index/page.tsx (replaced placeholder with IndexCards)
    - src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java (added reingest handler)
    - src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java (added reingest method)
decisions:
  - "Used base-ui render prop pattern for DropdownMenuTrigger and TooltipTrigger to avoid nested button elements"
  - "reingest() resets approvalState=PENDING, status=DRAFT, trustedState=UNTRUSTED and deactivates chunks — clean slate for re-approval flow"
  - "columns.tsx imports SourceActionsMenu directly from sources-table.tsx — no split cell injection per plan constraint"
  - "DropdownMenuTrigger uses render=<Button .../> pattern — shadcn v4 base-ui MenuTrigger renders its own button element"
metrics:
  duration: "~25 minutes"
  completed_date: "2026-04-11T03:13:32Z"
  tasks_completed: 2
  files_created: 6
  files_modified: 4
---

# Phase 04 Plan 06: Source Management DataTable & Knowledge Index Cards Summary

**One-liner:** Source management DataTable with full lifecycle actions (approve/reject/activate/deactivate/reingest) and AlertDialog confirmation, plus knowledge index readiness and summary cards backed by TanStack Query.

## Tasks Completed

| # | Name | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Backend reingest endpoint + DataTable + source columns + sources table | `58e944b` | SourceAdminController.java, SourceService.java, data-table.tsx, columns.tsx, sources-table.tsx, use-sources.ts, sources/page.tsx |
| 2 | Knowledge index inspection cards | `72db6a9` | index-cards.tsx, use-index.ts, index/page.tsx |

## Verification Results

- `npx tsc --noEmit` → zero errors (run after each task)
- `SourceAdminController.java` contains `@PostMapping("/{sourceId}/reingest")` handler (grep count: 3)
- `SourceService.java` contains `reingest()` method
- `data-table.tsx` contains `useReactTable`, `getCoreRowModel`, `getSortedRowModel`
- `columns.tsx` contains 5 column definitions: title, sourceType, status (Badge), createdAt, actions
- `columns.tsx` imports `SourceActionsMenu` directly from `sources-table.tsx`
- `sources-table.tsx` contains context-sensitive actions per status (PENDING, APPROVED, ACTIVE, REJECTED)
- `sources-table.tsx` contains `AlertDialog` for destructive confirmation
- `use-sources.ts` exports: `useSources`, `useApproveSource`, `useRejectSource`, `useActivateSource`, `useDeactivateSource`, `useReingestSource`
- Sources page contains `DataTable` with `columns` and `data?.content` plus empty state text
- `use-index.ts` exports `useChunkReadiness` and `useIndexSummary`
- Index cards contain two Cards: "San sang truy xuat" and "Tom tat chi muc"
- Readiness card: approvedCount, trustedCount, activeCount, eligibleCount
- Summary card: totalChunks, activeChunks, inactiveChunks
- Refresh button uses `Tooltip` with `RefreshCw` icon and animate-spin loading state

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] DropdownMenuTrigger nested button pattern**
- **Found during:** Task 1 — reviewing the base-ui DropdownMenu implementation
- **Issue:** The plan's template used `<DropdownMenuTrigger asChild><Button .../></DropdownMenuTrigger>` which would create nested `<button>` elements since `MenuPrimitive.Trigger` already renders a `<button>`. The `asChild` prop is not supported in this base-ui v1 implementation.
- **Fix:** Changed to `<DropdownMenuTrigger render={<Button variant="ghost" size="icon" />}>` with the icon as children — matches the base-ui `render` prop pattern used consistently across this shadcn v4 build (same pattern as SidebarMenuButton fix in Plan 04-04)
- **Files modified:** `frontend/components/admin/sources/sources-table.tsx`
- **Commit:** `58e944b`

**2. [Rule 1 - Bug] TooltipTrigger nested button pattern**
- **Found during:** Task 2 — same base-ui pattern applies to TooltipTrigger
- **Issue:** Same nested button issue would occur with `<TooltipTrigger asChild><Button .../>` pattern
- **Fix:** Used `<TooltipTrigger render={<Button variant="ghost" size="icon" onClick={...} disabled={...} />}>` pattern
- **Files modified:** `frontend/components/admin/index/index-cards.tsx`
- **Commit:** `72db6a9`

**3. [Rule 2 - Missing] reingest endpoint missing from backend**
- **Found during:** Task 1 — confirmed `SOURCE_REINGEST` path constant exists in `ApiPaths.java` but no handler in `SourceAdminController`
- **Issue:** `reingestSource()` in `frontend/lib/api/sources.ts` calls `POST /api/v1/admin/sources/{id}/reingest` but the endpoint did not exist
- **Fix:** Added `reingest()` to `SourceService` (resets to PENDING/DRAFT/UNTRUSTED, deactivates chunks, records approval event) and `@PostMapping("/{sourceId}/reingest")` to `SourceAdminController`
- **Files modified:** `SourceAdminController.java`, `SourceService.java`
- **Commit:** `58e944b`

## Known Stubs

None — all admin screens implemented with live data wiring via TanStack Query.

## Threat Surface Scan

| Flag | File | Description |
|------|------|-------------|
| T-04-14 mitigated | sources-table.tsx | Destructive actions (reject, deactivate) require AlertDialog confirmation before API call — implemented per threat register |
| T-04-15 accepted | SourceAdminController.java | New reingest endpoint is open with no auth — consistent with accepted v1 decision for all admin endpoints |

## Self-Check: PASSED

- `frontend/components/admin/data-table.tsx` — FOUND
- `frontend/components/admin/sources/columns.tsx` — FOUND
- `frontend/components/admin/sources/sources-table.tsx` — FOUND
- `frontend/hooks/use-sources.ts` — FOUND
- `frontend/app/(admin)/sources/page.tsx` — FOUND (updated)
- `frontend/components/admin/index/index-cards.tsx` — FOUND
- `frontend/hooks/use-index.ts` — FOUND
- `frontend/app/(admin)/index/page.tsx` — FOUND (updated)
- `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java` — reingest handler FOUND
- `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java` — reingest method FOUND
- Commits `58e944b`, `72db6a9` — FOUND in git log
