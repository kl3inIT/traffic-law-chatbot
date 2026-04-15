---
phase: 04-next-js-chat-admin-app
plan: 07
type: summary
---

# Phase 04-07 Summary — AI Parameter Sets CRUD + Playwright E2E

## What Was Built

### Task 1: Parameter Sets Components
- `frontend/hooks/use-parameters.ts` — TanStack Query hooks: `useParameterSets`, `useCreateParameterSet`, `useUpdateParameterSet`, `useDeleteParameterSet`, `useActivateParameterSet`, `useCopyParameterSet`
- `frontend/components/admin/parameters/parameter-dialog.tsx` — Create/edit dialog with `react-hook-form` + Zod validation; YAML textarea `font-mono min-h-[320px]`
- `frontend/components/admin/parameters/parameters-table.tsx` — `ParameterActionsMenu` with Chỉnh sửa, Sao chép (→ backend `POST /{id}/copy`), Kích hoạt, Xóa with AlertDialog confirm
- `frontend/components/admin/parameters/columns.tsx` — Factory `createParameterColumns(onEdit)` imports `ParameterActionsMenu` directly

### Task 2: Page Wiring + Playwright E2E
- `frontend/app/(admin)/parameters/page.tsx` — Full wiring; no `handleCopy` override (copy entirely in `ParameterActionsMenu`)
- `frontend/playwright.config.ts` — Playwright config with `baseURL: localhost:3000`
- `frontend/e2e/smoke.spec.ts` — 3 smoke tests: homepage, sources page, parameters page

## Key Technical Decisions

| Decision | Reason |
|---|---|
| Copy calls `POST /{id}/copy` directly | Backend creates copy with " (bản sao)" suffix — no dialog-based fake create |
| `createParameterColumns(onEdit)` factory | Same pattern as `sources/columns.tsx`; co-locates column definition and cell renderer |
| Activate/Delete hidden for active set | Backend rejects delete of active set; UI prevents the action proactively |

## Post-07 Fixes (same branch)

After 04-07 was committed, several bugs and UX issues were addressed:

### Backend
- `ChatMessage` + `ChatMessageResponse`: added `structured_response` JSON column (Liquibase migration 006) to persist full `ChatAnswerResponse` for structured history rendering
- `ChatThreadService`: saves `structuredResponse` on assistant messages; history query includes it
- `FactMemoryService`: expanded `VIOLATION_PATTERN` with 20+ violations; added `VIOLATION_BROAD_PATTERN` + `VIOLATION_KEYWORD_PATTERN` fallback to fix clarification loop for inputs like "không có gương"

### Frontend — Sources Page
- `DataTable`: added row selection with direct index-based mapping fix (TanStack Table `getFilteredSelectedRowModel` not available; `Object.entries(rowSelection)` → `data[index]` is reliable)
- `sources/page.tsx`: pagination (`page` state, `key={page}` reset), bulk toolbar (Phê duyệt / Kích hoạt / Từ chối) always visible, disabled when no eligible rows selected
- `sources/columns.tsx`: checkbox column at index 0
- `sources/sources-table.tsx`: reduced to `...` dropdown with only "Nhập lại" item + confirm dialog
- `lib/api/sources.ts` + `hooks/use-sources.ts`: `fetchSources(page, size)`, `fetchAllSources`, `useBulkApproveSource`, `useBulkRejectSource`, `useBulkActivateSource`

### Frontend — Index Page
- `index/page.tsx`: removed "Nguồn đang index" tab; only `IndexCards` + `IndexChunkTable`
- `index/chunk-detail-sheet.tsx`: removed entire embedding/vector section
- `index/index-chunk-table.tsx`: replaced `Select` with Combobox (Popover + Command) for source filter; uses `useAllSources` (size=500)

### Frontend — Chat
- `threads/[id]/page.tsx`: renders `<AiBubble response={msg.structuredResponse} />` for historical assistant messages when `structuredResponse` exists
- `message-bubble.tsx`: strips `[CLARIFICATION]` prefix from clarification responses

## Verification Status
- TypeScript compile: passes (`npx tsc --noEmit`)
- Playwright smoke tests: configured; requires running services
- Backend restart required for Liquibase migration (006) and service changes to take effect
