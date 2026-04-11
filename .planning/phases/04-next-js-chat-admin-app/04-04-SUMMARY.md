---
phase: 04-next-js-chat-admin-app
plan: "04"
subsystem: frontend
tags: [nextjs, shadcn, sidebar, tanstack-query, vitest, routing]
dependency_graph:
  requires:
    - frontend/components/layout/providers.tsx (from 04-03)
    - frontend/lib/api/chat.ts (from 04-03)
    - frontend/lib/query-keys.ts (from 04-03)
    - frontend/types/api.ts (from 04-03)
    - frontend/components/ui/sidebar.tsx (from 04-03)
    - frontend/components/ui/scroll-area.tsx (from 04-03)
    - frontend/components/ui/skeleton.tsx (from 04-03)
    - frontend/components/ui/button.tsx (from 04-03)
  provides:
    - frontend/app/layout.tsx (updated)
    - frontend/components/layout/app-sidebar.tsx
    - frontend/components/chat/thread-list.tsx
    - frontend/hooks/use-threads.ts
    - frontend/app/(chat)/page.tsx
    - frontend/app/(chat)/layout.tsx
    - frontend/app/(chat)/threads/[id]/page.tsx
    - frontend/app/(admin)/sources/page.tsx
    - frontend/app/(admin)/index/page.tsx
    - frontend/app/(admin)/parameters/page.tsx
    - frontend/__tests__/app-sidebar.test.tsx
  affects:
    - Plan 04-05 (chat thread view uses (chat)/threads/[id]/page.tsx)
    - Plan 04-06 (admin screens build on top of placeholder pages)
tech_stack:
  added: []
  patterns:
    - "SidebarProvider defaultOpen={true} (no cookie read, avoids Next.js 16 blocking route warning)"
    - "base-ui/react render prop pattern instead of asChild for Link inside SidebarMenuButton"
    - "Route groups (chat) and (admin) with no URL prefix"
    - "async params in page components (Next.js 16 requirement)"
    - "vi.mock hoisting for shadcn Sidebar and next/navigation in Vitest"
key_files:
  created:
    - frontend/components/layout/app-sidebar.tsx
    - frontend/components/chat/thread-list.tsx
    - frontend/hooks/use-threads.ts
    - frontend/app/(chat)/layout.tsx
    - frontend/app/(chat)/page.tsx
    - frontend/app/(chat)/threads/[id]/page.tsx
    - frontend/app/(admin)/sources/page.tsx
    - frontend/app/(admin)/index/page.tsx
    - frontend/app/(admin)/parameters/page.tsx
    - frontend/__tests__/app-sidebar.test.tsx
  modified:
    - frontend/app/layout.tsx (added SidebarProvider, Providers, AppSidebar)
  deleted:
    - frontend/app/page.tsx (replaced by (chat)/page.tsx which owns / route)
decisions:
  - "Used render prop pattern instead of asChild for SidebarMenuButton + Link — shadcn v4 with base-ui/react does not support asChild prop on SidebarMenuButton"
  - "Deleted root app/page.tsx to avoid route conflict with (chat)/page.tsx (both match /)"
  - "Used defaultOpen={true} static default on SidebarProvider — avoids cookie read and blocking route warning in Next.js 16"
  - "Moved vi.mock calls before import in test file to leverage Vitest hoisting correctly"
metrics:
  duration: "~20 minutes"
  completed_date: "2026-04-11T02:54:00Z"
  tasks_completed: 3
  files_created: 10
---

# Phase 04 Plan 04: Sidebar Shell Layout & Navigation Summary

**One-liner:** shadcn Sidebar shell with 4 Vietnamese nav items, TanStack Query thread list, route groups (chat)/(admin), and 3 passing Vitest render tests for AppSidebar.

## Tasks Completed

| # | Name | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Root layout with Providers + SidebarProvider, and AppSidebar navigation | `b9fa1b7` | app/layout.tsx, components/layout/app-sidebar.tsx |
| 2 | Thread list component with TanStack Query hook | `b0fe82f` | components/chat/thread-list.tsx, hooks/use-threads.ts |
| 3 | Route group layouts, placeholder pages, and AppSidebar render test | `f901fc7` | app/(chat)/*, app/(admin)/*, __tests__/app-sidebar.test.tsx |

## Verification Results

- `node_modules/.bin/tsc --noEmit` → zero errors (run after each task)
- `node_modules/.bin/vitest run app-sidebar` → 3 passed (1 test file), exit 0
- AppSidebar renders: "Tro chuyen", "Quan ly nguon", "Chi muc kien thuc", "Bo tham so AI"
- adminNavItems.length === 3 confirmed in test assertion

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SidebarMenuButton asChild not supported in base-ui/react version**
- **Found during:** Task 1 TypeScript verification
- **Issue:** `SidebarMenuButton` in this shadcn v4 build uses `@base-ui/react/use-render` with a `render` prop API instead of the Radix-style `asChild` prop. Passing `asChild` caused TS2322 type error.
- **Fix:** Changed `<SidebarMenuButton asChild><Link ...>` to `<SidebarMenuButton render={<Link href={item.href} />}>` with children inside the button element
- **Files modified:** `frontend/components/layout/app-sidebar.tsx`
- **Commit:** `b9fa1b7`

**2. [Rule 1 - Bug] Route conflict between app/page.tsx and app/(chat)/page.tsx**
- **Found during:** Task 3 — both files resolve to the `/` URL in Next.js App Router
- **Issue:** `app/page.tsx` (create-next-app scaffold) and `app/(chat)/page.tsx` both own the root `/` route; Next.js would error or use only one
- **Fix:** Deleted `app/page.tsx` so the route group page owns `/` cleanly per the plan's architecture
- **Files modified:** `frontend/app/page.tsx` (deleted)
- **Commit:** `f901fc7`

**3. [Rule 3 - Blocking] npm install required in worktree**
- **Found during:** Task 1 verification
- **Issue:** Worktree does not share `node_modules/` from the main checkout; `tsc` and `vitest` were unavailable
- **Fix:** Ran `npm install --prefer-offline` in the worktree's `frontend/` directory (733 packages, ~52s)
- **Files modified:** None (runtime only)

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| Thread page placeholder | `app/(chat)/threads/[id]/page.tsx` | Full chat thread view implemented in Plan 05 |
| Admin sources placeholder | `app/(admin)/sources/page.tsx` | Sources DataTable implemented in Plan 04-06 |
| Admin index placeholder | `app/(admin)/index/page.tsx` | Index admin screen implemented in Plan 04-06 |
| Admin parameters placeholder | `app/(admin)/parameters/page.tsx` | Parameter sets admin implemented in Plan 04-06 |

These stubs render page headings and placeholder text. They are intentional — each will be replaced by the screen-specific plan that builds that admin area.

## Threat Surface Scan

No new threat surface beyond the plan's threat register.

| Flag | File | Description |
|------|------|-------------|
| T-04-10 accepted | `app/(chat)/threads/[id]/page.tsx` | Thread ID from URL rendered directly; UUID validated by backend; no XSS risk |
| T-04-11 accepted | `components/chat/thread-list.tsx` | Thread titles visible to all users; auth deferred per v1 scope decision |

## Self-Check: PASSED

- `frontend/components/layout/app-sidebar.tsx` — FOUND
- `frontend/components/chat/thread-list.tsx` — FOUND
- `frontend/hooks/use-threads.ts` — FOUND
- `frontend/app/(chat)/layout.tsx` — FOUND
- `frontend/app/(chat)/page.tsx` — FOUND
- `frontend/app/(chat)/threads/[id]/page.tsx` — FOUND
- `frontend/app/(admin)/sources/page.tsx` — FOUND
- `frontend/app/(admin)/index/page.tsx` — FOUND
- `frontend/app/(admin)/parameters/page.tsx` — FOUND
- `frontend/__tests__/app-sidebar.test.tsx` — FOUND
- Commits `b9fa1b7`, `b0fe82f`, `f901fc7` — FOUND in git log
