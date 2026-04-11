---
phase: 04-next-js-chat-admin-app
plan: "03"
subsystem: frontend
tags: [nextjs, shadcn, tanstack-query, typescript, vitest, scaffold]
dependency_graph:
  requires: []
  provides:
    - frontend/types/api.ts
    - frontend/lib/api/client.ts
    - frontend/lib/api/chat.ts
    - frontend/lib/api/sources.ts
    - frontend/lib/api/chunks.ts
    - frontend/lib/api/parameters.ts
    - frontend/lib/query-keys.ts
    - frontend/components/layout/providers.tsx
    - frontend/vitest.config.ts
  affects:
    - All subsequent frontend plans (04-04 through 04-06)
tech_stack:
  added:
    - next@16.2.3 (App Router)
    - "@tanstack/react-query@^5.97.0"
    - "@tanstack/react-table@^8.21.3"
    - react-hook-form@^7.72.1
    - zod@^4.3.6
    - shadcn/ui (sidebar, accordion, badge, button, card, dialog, etc.)
    - vitest@^4.1.4
    - "@testing-library/react@^16.3.2"
    - "@playwright/test@^1.59.1"
  patterns:
    - App Router (no src/ dir, @/* import alias)
    - QueryClientProvider at root via Providers component
    - Centralized queryKeys factory
    - Typed fetch wrapper using NEXT_PUBLIC_API_BASE_URL
key_files:
  created:
    - frontend/package.json
    - frontend/tsconfig.json
    - frontend/next.config.ts
    - frontend/app/layout.tsx
    - frontend/app/globals.css
    - frontend/components.json
    - frontend/lib/utils.ts
    - frontend/lib/api/client.ts
    - frontend/lib/api/chat.ts
    - frontend/lib/api/sources.ts
    - frontend/lib/api/chunks.ts
    - frontend/lib/api/parameters.ts
    - frontend/lib/query-keys.ts
    - frontend/types/api.ts
    - frontend/components/layout/providers.tsx
    - frontend/vitest.config.ts
    - frontend/vitest.setup.ts
    - frontend/__tests__/smoke.test.tsx
    - frontend/__tests__/stubs/sources.test.tsx
    - frontend/__tests__/stubs/chat.test.tsx
    - frontend/__tests__/stubs/param.test.tsx
    - frontend/__tests__/stubs/vector.test.tsx
  modified:
    - frontend/.env.local (created, gitignored)
decisions:
  - "Used Inter font with vietnamese subset for lang=vi layout"
  - "shadcn/ui v4 uses @base-ui/react (not Radix) with this Next.js 16 version"
  - "Removed embedded .git from create-next-app before staging to avoid submodule tracking"
  - "Vitest @ alias resolves to frontend/ root (not src/) matching tsconfig paths"
metrics:
  duration: "~35 minutes"
  completed_date: "2026-04-11T02:38:05Z"
  tasks_completed: 3
  files_created: 43
---

# Phase 04 Plan 03: Next.js Scaffold & Foundation Summary

**One-liner:** Next.js 16 App Router frontend scaffolded with shadcn/ui, typed Spring DTO layer, TanStack Query provider, and Vitest infrastructure with Wave 0 stubs.

## Tasks Completed

| # | Name | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Scaffold Next.js 16, install deps, shadcn/ui | `7011d19` | package.json, components.json, layout.tsx, components/ui/* |
| 2 | TypeScript types, API client, query keys, providers | `b54de5f` | types/api.ts, lib/api/*, lib/query-keys.ts, components/layout/providers.tsx |
| 3 | Vitest infra, Wave 0 stubs, smoke test | `b35360d` | vitest.config.ts, vitest.setup.ts, __tests__/* |

## Verification Results

- `npx next --version` → Next.js v16.2.3
- `frontend/components.json` exists (shadcn initialized)
- `frontend/.env.local` contains `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` (gitignored via `.env*`)
- `npx tsc --noEmit` → zero errors
- `npx vitest run` → 2 passed (smoke), 9 todo (Wave 0 stubs), exit 0

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Removed embedded .git from create-next-app output**
- **Found during:** Task 1 commit
- **Issue:** `create-next-app` initializes its own `.git` directory, causing git to treat `frontend/` as a submodule
- **Fix:** Removed `frontend/.git`, force-removed the submodule cache entry, re-added all frontend files as regular tracked files
- **Files modified:** None (git index only)
- **Commit:** `7011d19`

**2. [Observation] shadcn/ui v4 uses @base-ui/react instead of Radix**
- **Found during:** Task 1
- **Issue:** This Next.js 16 + shadcn v4 combination pulls `@base-ui/react` as the primitive layer instead of `@radix-ui/*`
- **Fix:** No action needed — components work identically from the consumer side; documented for future plans
- **Impact:** Subsequent plans importing shadcn components will work normally

**3. [Rule 2 - Security] Verified .env.local gitignore coverage**
- **Found during:** Post-task threat scan (T-04-08)
- **Issue:** Confirmed `.env*` pattern in frontend/.gitignore covers `.env.local`
- **Fix:** No action needed — already mitigated by create-next-app default

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| Wave 0 todo stubs | `__tests__/stubs/sources.test.tsx` | Real tests added by Plan 04 (source admin screen) |
| Wave 0 todo stubs | `__tests__/stubs/chat.test.tsx` | Real tests added by Plan 04 (chat screen) |
| Wave 0 todo stubs | `__tests__/stubs/param.test.tsx` | Real tests added by Plan 05 (parameter sets) |
| Wave 0 todo stubs | `__tests__/stubs/vector.test.tsx` | Real tests added by Plan 06 (vector/index admin) |

These stubs are intentional Wave 0 placeholders — they register test paths as known so VALIDATION.md tracking works. Each stub will be replaced with real assertions by the plan that builds the corresponding screen.

## Threat Surface Scan

No new threat surface beyond what was already modeled in the plan's threat register.

| Flag | File | Description |
|------|------|-------------|
| T-04-08 mitigated | frontend/.gitignore | `.env*` pattern covers `.env.local`; only contains localhost URL |
| T-04-09 mitigated | frontend/lib/api/client.ts | Base URL from `NEXT_PUBLIC_API_BASE_URL` env var, not hardcoded |

## Self-Check: PASSED

- `frontend/types/api.ts` — FOUND
- `frontend/lib/api/client.ts` — FOUND
- `frontend/lib/api/chat.ts` — FOUND
- `frontend/lib/api/sources.ts` — FOUND
- `frontend/lib/api/chunks.ts` — FOUND
- `frontend/lib/api/parameters.ts` — FOUND
- `frontend/lib/query-keys.ts` — FOUND
- `frontend/components/layout/providers.tsx` — FOUND
- `frontend/vitest.config.ts` — FOUND
- `frontend/vitest.setup.ts` — FOUND
- `frontend/__tests__/smoke.test.tsx` — FOUND
- `frontend/components.json` — FOUND
- Commits `7011d19`, `b54de5f`, `b35360d` — FOUND in git log
