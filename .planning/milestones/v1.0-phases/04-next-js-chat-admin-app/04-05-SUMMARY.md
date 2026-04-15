---
phase: 04-next-js-chat-admin-app
plan: "05"
subsystem: frontend
tags: [nextjs, shadcn, tanstack-query, typescript, vitest, chat-ui, accordion, message-bubble]
dependency_graph:
  requires:
    - frontend/types/api.ts (from 04-03)
    - frontend/lib/api/chat.ts (from 04-03)
    - frontend/lib/query-keys.ts (from 04-03)
    - frontend/components/ui/accordion.tsx (from 04-03)
    - frontend/components/ui/avatar.tsx (from 04-03)
    - frontend/components/ui/badge.tsx (from 04-03)
    - frontend/components/ui/skeleton.tsx (from 04-03)
    - frontend/components/ui/textarea.tsx (from 04-03)
    - frontend/components/ui/button.tsx (from 04-03)
    - frontend/components/ui/scroll-area.tsx (from 04-03)
    - frontend/components/ui/alert.tsx (from 04-03)
    - frontend/app/(chat)/page.tsx (from 04-04, placeholder)
    - frontend/app/(chat)/threads/[id]/page.tsx (from 04-04, placeholder)
  provides:
    - frontend/components/chat/message-bubble.tsx
    - frontend/components/chat/scenario-accordion.tsx
    - frontend/components/chat/chat-input.tsx
    - frontend/hooks/use-chat.ts
    - frontend/app/(chat)/page.tsx (full implementation)
    - frontend/app/(chat)/threads/[id]/page.tsx (full implementation)
    - frontend/__tests__/message-bubble.test.tsx
  affects:
    - Plan 04-06 (admin screens will follow same component patterns)
tech_stack:
  added: []
  patterns:
    - "ResponseMode branching: SCENARIO_ANALYSIS/FINAL_ANALYSIS -> accordion, others -> text bubble"
    - "Optimistic UI: user message added to local state before mutation resolves"
    - "React.use(params) in client components for Next.js 16 async params"
    - "base-ui/react accordion: multiple prop + defaultValue array (not Radix type='multiple')"
    - "data-slot='skeleton' selector for shadcn v4 Skeleton in tests"
key_files:
  created:
    - frontend/components/chat/message-bubble.tsx
    - frontend/components/chat/scenario-accordion.tsx
    - frontend/components/chat/chat-input.tsx
    - frontend/hooks/use-chat.ts
    - frontend/__tests__/message-bubble.test.tsx
  modified:
    - frontend/app/(chat)/page.tsx (placeholder -> full new-chat flow)
    - frontend/app/(chat)/threads/[id]/page.tsx (placeholder -> full thread view)
decisions:
  - "Used base-ui/react accordion multiple + defaultValue props instead of Radix type='multiple' (base-ui API difference)"
  - "useCreateThread and usePostMessage in use-chat.ts are separate from use-threads.ts useCreateThread — chat hooks own the conversation UI state (LocalMessage), threads hook owns the sidebar list"
  - "Skeleton selector in tests uses data-slot='skeleton' not class*='skeleton' — shadcn v4 uses data-slot pattern"
  - "Thread page uses React.use(params) not await params — use() is correct for client components in Next.js 16"
metrics:
  duration: "~25 minutes"
  completed_date: "2026-04-11T03:03:22Z"
  tasks_completed: 3
  files_created: 5
  files_modified: 2
---

# Phase 04 Plan 05: Chat Interface — Message Bubbles, Accordion, and Thread Wiring Summary

**One-liner:** Full chat interface with user/AI message bubbles, scenario accordion with 5 Vietnamese sections, Enter-to-send chat input, and thread pages wired to Spring backend via TanStack Query mutations.

## Tasks Completed

| # | Name | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Message bubble, scenario accordion, and chat input components | `c7cbc5d` | components/chat/message-bubble.tsx, scenario-accordion.tsx, chat-input.tsx |
| 2 | Chat page wiring -- thread view and new-chat flow | `18af1b3` | hooks/use-chat.ts, app/(chat)/page.tsx, app/(chat)/threads/[id]/page.tsx |
| 3 | Message bubble Vitest unit test | `9d676f0` | __tests__/message-bubble.test.tsx |

## Verification Results

- `npx tsc --noEmit` -> zero errors (exit 0)
- `npx vitest run message-bubble` -> 6 passed (exit 0)
- `npx vitest run` -> 11 passed, 9 todo (Wave 0 stubs), 0 failed (exit 0)
- All 7 key files confirmed present on disk
- All 3 task commits confirmed in git log

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Skeleton test selector wrong for shadcn v4**
- **Found during:** Task 3 test run
- **Issue:** Plan specified `container.querySelectorAll('[class*="skeleton"]')` but shadcn v4 Skeleton uses `data-slot="skeleton"` not a class containing "skeleton". Result: 0 skeletons found, test failed.
- **Fix:** Changed selector to `container.querySelectorAll('[data-slot="skeleton"]')` which matches the shadcn v4 `data-slot` pattern
- **Files modified:** `frontend/__tests__/message-bubble.test.tsx`
- **Commit:** `9d676f0`

**2. [Rule 3 - Blocking] node_modules not present in worktree**
- **Found during:** Task 1 pre-verification
- **Issue:** Worktree does not share `node_modules/` from main checkout
- **Fix:** Ran `npm install --prefer-offline` (733 packages, ~1 min)
- **Files modified:** None (runtime only)

**3. [Observation] base-ui/react accordion uses `multiple` not `type="multiple"`**
- **Found during:** Task 1 implementation
- **Issue:** Plan code used `<Accordion type="multiple">` which is the Radix UI API. This shadcn v4 build wraps `@base-ui/react` which uses `multiple={boolean}` prop instead.
- **Fix:** Used `<Accordion multiple defaultValue={['sources']}>` per base-ui API
- **Files modified:** `frontend/components/chat/scenario-accordion.tsx`
- **Commit:** `c7cbc5d`

## Known Stubs

None. All components in this plan are fully implemented with real data sources wired.

## Threat Surface Scan

Plan threat register fully mitigated:

| Flag | File | Description |
|------|------|-------------|
| T-04-12 mitigated | components/chat/message-bubble.tsx | All AI response text rendered via React JSX escaping only; no dangerouslySetInnerHTML used |
| T-04-13 mitigated | components/chat/chat-input.tsx | Send button disabled while isPending=true; prevents duplicate submissions |

No new threat surface introduced beyond the plan's register.

## Self-Check: PASSED

- `frontend/components/chat/message-bubble.tsx` — FOUND
- `frontend/components/chat/scenario-accordion.tsx` — FOUND
- `frontend/components/chat/chat-input.tsx` — FOUND
- `frontend/hooks/use-chat.ts` — FOUND
- `frontend/app/(chat)/page.tsx` — FOUND
- `frontend/app/(chat)/threads/[id]/page.tsx` — FOUND
- `frontend/__tests__/message-bubble.test.tsx` — FOUND
- Commits `c7cbc5d`, `18af1b3`, `9d676f0` — FOUND in git log
