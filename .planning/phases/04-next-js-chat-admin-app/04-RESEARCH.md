# Phase 4: Next.js Chat & Admin App — Research

**Researched:** 2026-04-11
**Domain:** Next.js 16 App Router, shadcn/ui, TanStack Query v5, Spring REST frontend integration
**Confidence:** HIGH (core stack), MEDIUM (Next.js 16-specific patterns), HIGH (backend contracts)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** shadcn/ui + Tailwind CSS for all UI components — sidebar shell, admin tables, forms, dialogs, badges.
- **D-02:** TanStack Query (`@tanstack/react-query`) for all Spring REST API calls — handles caching, loading/error states, and refetch.
- **D-03:** No Vercel AI SDK — the Spring backend owns all AI orchestration and returns complete JSON responses. No streaming proxy layer is needed.
- **D-04:** shadcn/ui `Sidebar` component (with `SidebarProvider`) is the foundation for the sidebar shell (ADMIN-06).
- **D-05:** shadcn/ui `DataTable` (powered by TanStack Table v8) is the foundation for all admin list screens.
- **D-06:** Thread list panel on the left + active thread main area on the right. User can switch threads and create new ones ("+ New" button). Standard Claude/ChatGPT pattern.
- **D-07:** Structured scenario answers (Facts / Rule / Outcome / Actions / Sources) are rendered as collapsible accordion sections using shadcn/ui `Accordion`. Regular Q&A answers render as plain text bubbles.
- **D-08:** Clarifying questions from the system appear inline as normal AI message bubbles — no special highlighted card. User replies in the same input box.
- **D-09:** Thread list lives in the chat section of the sidebar, not a separate panel.
- **D-10:** All three admin capability areas are fully functional in Phase 4.
- **D-11 through D-15:** AI parameter sets backend design (entity, CRUD REST API, YAML structure, seeded defaults).
- **D-16:** Next.js app lives in `frontend/` subfolder alongside `src/`. Same git repo.
- **D-17:** App Router with route groups `(chat)/` and `(admin)/`.
- **D-18:** All API calls go directly from Next.js client components to the Spring REST backend via TanStack Query. No Next.js API routes as proxy.

### Claude's Discretion
- Exact shadcn/ui component variants (sizes, colors, icon choices) for chat bubbles and status badges.
- TanStack Query stale times, retry config, and cache invalidation strategy.
- Whether thread titles are derived from the first message or kept as timestamps.
- Exact YAML schema field names in the parameter set.
- How the parameter set YAML editor is presented (plain textarea vs code editor).
- Error boundary and loading skeleton treatment.

### Deferred Ideas (OUT OF SCOPE)
- Chat log review and answer check workflows (Phase 5).
- Streaming responses from the Spring backend — not in v1 scope.
- User authentication / role separation — explicitly out of v1 scope.
- Mobile-specific optimizations beyond what shadcn/ui handles by default.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PLAT-02 | Frontend is implemented in Next.js and supports the v1 chat and admin workflows | Next.js 16 App Router setup, TanStack Query integration, API path mapping |
| ADMIN-01 | Admin can view and manage ingested sources through an admin interface | Source API contracts verified in codebase; DataTable + DropdownMenu pattern researched |
| ADMIN-02 | Admin can inspect vector-store content or indexed documents | Chunk readiness and index summary endpoints verified in codebase |
| ADMIN-03 | Admin can create, edit, copy, and activate AI parameter sets | New backend work (entity + CRUD) + admin UI; YAML editor + react-hook-form pattern researched |
| ADMIN-06 | Admin can use a sidebar-style interface combining chat and admin screens | shadcn/ui Sidebar component pattern; cookie persistence pitfall with Next.js 16 documented |
</phase_requirements>

---

## Summary

Phase 4 builds the entire Next.js frontend from scratch in a `frontend/` subfolder. The stack is completely determined: Next.js 16 (App Router), shadcn/ui 4.x, TanStack Query v5, TanStack Table v8, and react-hook-form + Zod for forms. The Spring REST backend built in Phases 1–3 provides all API contracts; the frontend consumes them directly from client components with no proxy layer.

The phase also includes backend work that must land before the admin UI can function: the `AiParameterSet` entity, its CRUD REST API, and a targeted refactoring of hardcoded constants in `AnswerCompositionPolicy`, `ClarificationPolicy`, and `ChatPromptFactory`. These backend tasks are strictly gating — the admin Parameter Sets screen cannot be built before the `/api/v1/admin/parameter-sets` endpoint exists.

Next.js 16 ships with Turbopack as default bundler and introduces `proxy.ts` replacing `middleware.ts`. The most significant gotcha for this phase is that `SidebarProvider`'s cookie-based sidebar state restoration triggers a "Blocking Route" warning in Next.js 16 when Cache Components are enabled. The mitigation is either to wrap in `<Suspense>` or to disable `cacheComponents` in `next.config.ts` (reasonable for this app since SSR caching is not a priority). Async params and async cookies are now mandatory — `await params`, `await searchParams`, `await cookies()` required throughout.

**Primary recommendation:** Stand up the `frontend/` directory with `create-next-app` using Next.js 16 defaults, then `npx shadcn@latest init`, then add all shadcn components before building screens. Backend parameter set work must land in Wave 1 before any Wave 2 admin UI work begins.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| next | 16.2.3 | App framework, App Router, Turbopack | Latest stable; React 19.2, proxy.ts, async APIs |
| react | 19.2.5 | UI primitives, Server Components | Required by Next.js 16 |
| typescript | 5.9.3 | Type safety | Required minimum TS 5.1 in Next.js 16 |
| tailwindcss | 4.2.2 | Utility CSS | Locked in D-01; included in create-next-app defaults |
| shadcn (CLI) | 4.2.0 | Component scaffolding | Official CLI for shadcn/ui 4.x |
| @radix-ui/* | (via shadcn) | Accessible headless primitives | Backing all shadcn components |
| lucide-react | 1.8.0 | Icon library | shadcn/ui default icon set |
| class-variance-authority | 0.7.1 | Component variant API | Used internally by shadcn/ui |
| clsx | 2.1.1 | Conditional class merging | Standard with tailwind-merge |
| tailwind-merge | 3.5.0 | Tailwind class conflict resolution | Part of shadcn/ui `cn()` utility |

### Data Fetching & State
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @tanstack/react-query | 5.97.0 | Server state management, caching | All Spring REST calls (locked D-02) |
| @tanstack/react-table | 8.21.3 | Headless table logic | All DataTable screens (locked D-05) |

### Forms & Validation
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| react-hook-form | 7.72.1 | Form state management | Parameter set create/edit form |
| @hookform/resolvers | 5.2.2 | Zod adapter for react-hook-form | Connects Zod schemas to form validation |
| zod | 4.3.6 | Schema validation | Form field validation |

### Dev Tooling (from reference project + current best practices)
| Tool | Purpose | Config |
|------|---------|--------|
| Husky | Git hooks | `frontend/.husky/` |
| lint-staged | Run linters on staged files | `frontend/package.json` |
| ESLint (flat config) | Linting | `eslint.config.ts` (Next.js 16 uses flat config by default) |
| Prettier | Formatting | `.prettierrc` |
| Jest / Testing Library | Unit tests | `jest.config.ts` |

**Version verification:** All versions above confirmed against npm registry on 2026-04-11. [VERIFIED: npm registry]

**Installation (after `create-next-app`):**
```bash
cd frontend

# TanStack Query + Table
npm install @tanstack/react-query @tanstack/react-table

# Forms
npm install react-hook-form @hookform/resolvers zod

# shadcn init (interactive)
npx shadcn@latest init

# Add shadcn components (run individually)
npx shadcn@latest add sidebar
npx shadcn@latest add accordion avatar badge button textarea input label select
npx shadcn@latest add dialog alert-dialog alert card form tabs tooltip
npx shadcn@latest add dropdown-menu scroll-area skeleton separator table

# Dev tooling
npm install --save-dev husky lint-staged prettier
```

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| TanStack Query | SWR | TanStack Query has richer mutation + cache invalidation API; locked D-02 |
| react-hook-form | Formik | react-hook-form has better shadcn/ui integration via `<Form>` component |
| Tailwind v4 | Tailwind v3 | v4 (current) is CSS-first; shadcn 4.x requires it |

---

## Architecture Patterns

### Recommended Project Structure
```
frontend/
├── app/
│   ├── layout.tsx                   ← root layout: SidebarProvider, QueryClientProvider
│   ├── globals.css                  ← shadcn CSS variables, base styles
│   ├── (chat)/
│   │   ├── page.tsx                 ← chat landing (empty state or redirect to latest thread)
│   │   └── threads/
│   │       └── [id]/
│   │           └── page.tsx         ← active thread view
│   └── (admin)/
│       ├── sources/page.tsx
│       ├── index/page.tsx
│       └── parameters/page.tsx
├── components/
│   ├── ui/                          ← shadcn-generated components (never edit directly)
│   ├── layout/
│   │   ├── app-sidebar.tsx          ← sidebar nav items, thread list
│   │   └── providers.tsx            ← 'use client' QueryClientProvider wrapper
│   ├── chat/
│   │   ├── thread-list.tsx
│   │   ├── message-bubble.tsx
│   │   ├── scenario-accordion.tsx
│   │   └── chat-input.tsx
│   └── admin/
│       ├── sources/
│       │   ├── sources-table.tsx
│       │   └── columns.tsx
│       ├── index/
│       │   └── index-cards.tsx
│       └── parameters/
│           ├── parameters-table.tsx
│           ├── columns.tsx
│           └── parameter-dialog.tsx
├── lib/
│   ├── api/
│   │   ├── client.ts                ← fetch wrapper with base URL
│   │   ├── chat.ts                  ← chat endpoint functions
│   │   ├── sources.ts               ← source admin endpoint functions
│   │   ├── chunks.ts                ← chunk/index endpoint functions
│   │   └── parameters.ts            ← parameter sets endpoint functions
│   ├── query-keys.ts                ← centralized TanStack Query key factory
│   └── utils.ts                     ← shadcn cn() utility
├── hooks/
│   ├── use-threads.ts               ← TanStack Query hooks for chat
│   ├── use-sources.ts
│   ├── use-index.ts
│   └── use-parameters.ts
├── types/
│   └── api.ts                       ← TypeScript types mirroring Spring DTOs
├── next.config.ts
├── tsconfig.json
├── components.json                   ← shadcn config (generated by init)
└── package.json
```

### Pattern 1: Root Layout with Providers
**What:** SidebarProvider and QueryClientProvider both wrap children in the root layout. QueryClientProvider must be a 'use client' wrapper component.
**When to use:** Always — these are app-global.

```typescript
// Source: shadcn/ui Sidebar docs + TanStack Query Next.js App Router pattern
// frontend/components/layout/providers.tsx
'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () => new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 30 * 1000,       // 30s — admin data can be slightly stale
          retry: 1,                    // 1 retry on network error
          retryOnMount: false,
        },
        mutations: {
          retry: 0,                    // never retry mutations
        },
      },
    })
  );
  return (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
}
```

```typescript
// frontend/app/layout.tsx
import { SidebarProvider } from '@/components/ui/sidebar';
import { AppSidebar } from '@/components/layout/app-sidebar';
import { Providers } from '@/components/layout/providers';

export default function RootLayout({ children }: { children: React.ReactNode }) {
  // NOTE: Do NOT read cookies() here for sidebar state with Next.js 16 + cacheComponents.
  // See Pitfall 1 below. Use client-side default (open=true).
  return (
    <html lang="vi">
      <body>
        <Providers>
          <SidebarProvider defaultOpen={true}>
            <AppSidebar />
            <main>{children}</main>
          </SidebarProvider>
        </Providers>
      </body>
    </html>
  );
}
```

### Pattern 2: TanStack Query Data Fetching
**What:** All Spring REST calls are made from `'use client'` components via TanStack Query hooks. Data fetching functions live in `lib/api/`.
**When to use:** Every interaction with the Spring backend.

```typescript
// Source: TanStack Query v5 docs + Next.js App Router guidance
// lib/api/sources.ts
const BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export async function fetchSources(): Promise<SourceSummaryResponse[]> {
  const res = await fetch(`${BASE}/api/v1/admin/sources`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

// hooks/use-sources.ts
'use client';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchSources, approveSource } from '@/lib/api/sources';

export function useSources() {
  return useQuery({
    queryKey: ['admin', 'sources'],
    queryFn: fetchSources,
  });
}

export function useApproveSource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sourceId: string) => approveSource(sourceId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'sources'] }),
  });
}
```

### Pattern 3: DataTable with shadcn + TanStack Table
**What:** Columns defined in a `columns.tsx` client component; DataTable wrapper in `data-table.tsx`; page fetches data and passes it as props.
**When to use:** Sources table, Parameters table.

```typescript
// Source: shadcn/ui DataTable docs
// 'use client' — columns must be in a client component
import { ColumnDef } from '@tanstack/react-table';

export const columns: ColumnDef<SourceSummaryResponse>[] = [
  { accessorKey: 'title', header: 'Tiêu đề' },
  { accessorKey: 'sourceType', header: 'Loại nguồn' },
  {
    accessorKey: 'status',
    header: 'Trạng thái',
    cell: ({ row }) => <StatusBadge status={row.original.status} />,
  },
  {
    id: 'actions',
    cell: ({ row }) => <SourceActionsMenu source={row.original} />,
  },
];
```

### Pattern 4: shadcn Form with Zod Validation
**What:** shadcn `<Form>` wraps react-hook-form + zod resolver. Used for parameter set create/edit.
**When to use:** Any form in the admin UI (currently only parameter set dialog).

```typescript
// Source: shadcn/ui Form docs
'use client';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const parameterSetSchema = z.object({
  name: z.string().min(1, 'Tên bộ tham số là bắt buộc'),
  content: z.string().min(1, 'Nội dung YAML là bắt buộc'),
});

type ParameterSetFormValues = z.infer<typeof parameterSetSchema>;

export function ParameterSetDialog() {
  const form = useForm<ParameterSetFormValues>({
    resolver: zodResolver(parameterSetSchema),
    defaultValues: { name: '', content: '' },
  });
  // ...
}
```

### Pattern 5: Chat Message Rendering
**What:** `ChatAnswerResponse.responseMode` determines rendering branch. `STANDARD` and `CLARIFICATION_NEEDED` render as plain text bubble; `SCENARIO_ANALYSIS` and `FINAL_ANALYSIS` render as shadcn Accordion.

```typescript
// Response mode enum (mirrors Spring backend)
type ResponseMode =
  | 'STANDARD'
  | 'CLARIFICATION_NEEDED'
  | 'SCENARIO_ANALYSIS'
  | 'FINAL_ANALYSIS'
  | 'REFUSED';

function MessageContent({ response }: { response: ChatAnswerResponse }) {
  if (response.responseMode === 'SCENARIO_ANALYSIS' ||
      response.responseMode === 'FINAL_ANALYSIS') {
    return <ScenarioAccordion analysis={response.scenarioAnalysis} citations={response.citations} />;
  }
  return <PlainTextBubble text={response.answer} disclaimer={response.disclaimer} />;
}
```

### Anti-Patterns to Avoid
- **Server Components for TanStack Query hooks:** `useQuery` requires `'use client'`. Never call TanStack Query hooks in Server Components.
- **Async params without await in Next.js 16:** `params` and `searchParams` are now Promises. Always `const { id } = await params` in page components.
- **Putting QueryClientProvider in page-level components:** It must be at root layout level via a 'use client' wrapper to avoid creating multiple QueryClient instances.
- **Cookie-based sidebar state with cacheComponents enabled:** See Pitfall 1.
- **Hardcoding `http://localhost:8080` in fetch calls:** Use `NEXT_PUBLIC_API_BASE_URL` env var so it works in all environments.
- **Editing files inside `components/ui/`:** shadcn-generated files can be regenerated; business logic goes in `components/chat/`, `components/admin/`, etc.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Accessible sidebar with keyboard nav | Custom sidebar | `shadcn add sidebar` | Handles focus trap, ARIA, mobile Sheet, cookie persistence, collapsible state |
| Table sorting/filtering | Custom table | `@tanstack/react-table` + shadcn Table | Handles complex sort, filter, virtualization, TypeScript generics |
| Form validation + error display | Custom validation | react-hook-form + zod + shadcn Form | Handles async validation, field error mapping, submission state |
| Confirmation dialogs | Custom modal | shadcn AlertDialog | Accessible focus management, ARIA dialog role |
| Loading skeletons | CSS spinners | shadcn Skeleton | Consistent with design system, matches element dimensions |
| Dropdown action menus | Custom dropdown | shadcn DropdownMenu | Keyboard navigation, portal rendering, z-index management |
| Tooltip on icon buttons | title attribute | shadcn Tooltip | Works with keyboard focus, delay handling, accessible |
| API error display | console.error | shadcn Alert | Consistent visual treatment, accessible role="alert" |

**Key insight:** Every UI primitive above has invisible complexity (ARIA, focus, keyboard, portal z-index) that takes days to get right. shadcn/ui + Radix solves all of this.

---

## Common Pitfalls

### Pitfall 1: SidebarProvider Cookie Blocking in Next.js 16
**What goes wrong:** Using `await cookies()` in root `layout.tsx` to restore sidebar open state causes "Data that blocks navigation was accessed outside of `<Suspense>`" warning in Next.js 16 with Cache Components enabled. [VERIFIED: github.com/shadcn-ui/ui/issues/9189]
**Why it happens:** Next.js 16 Cache Components treats synchronous cookie reads as blocking the render pipeline.
**How to avoid:** Do not read the sidebar cookie in `layout.tsx`. Use `defaultOpen={true}` (or false) as a static default. If sidebar persistence across reloads is required, use a `<Suspense>` boundary around the SidebarProvider subtree, or disable `cacheComponents` in `next.config.ts`.
**Warning signs:** "Blocking Route" errors in dev console when navigating.

### Pitfall 2: Async Params Breaking in Next.js 16
**What goes wrong:** `params.id` accessed synchronously throws in Next.js 16. Pages in `(chat)/threads/[id]/page.tsx` that do `params.id` without `await` will error.
**Why it happens:** Next.js 16 makes `params` and `searchParams` async Promises (was sync in v14). [VERIFIED: nextjs.org/blog/next-16]
**How to avoid:** Always `const { id } = await params` in all page components.
**Warning signs:** TypeScript error "params is Promise<...>" or runtime error in dev.

### Pitfall 3: CORS Between Frontend and Spring Backend
**What goes wrong:** Browser blocks fetch calls from `localhost:3000` to Spring at `localhost:8080` unless CORS is configured.
**Why it happens:** Same-origin policy.
**How to avoid:** Two approaches:
1. **Spring-side CORS** (recommended for production): Add `@CrossOrigin` or a Spring `WebMvcConfigurer` CORS filter for `http://localhost:3000` in dev.
2. **next.config.ts rewrites** (dev convenience): Proxy `/api/**` to Spring in development.
**Warning signs:** "Access-Control-Allow-Origin" errors in browser DevTools.

### Pitfall 4: ChatAnswerResponse Field Nullability
**What goes wrong:** `scenarioAnalysis` is `null` for non-scenario responses. Rendering code that tries to access `response.scenarioAnalysis.facts` throws.
**Why it happens:** `ChatAnswerResponse.scenarioAnalysis` is only populated when `responseMode` is `SCENARIO_ANALYSIS` or `FINAL_ANALYSIS`.
**How to avoid:** Always branch on `responseMode` before accessing `scenarioAnalysis`. TypeScript type the field as `ScenarioAnalysisResponse | null`.
**Warning signs:** `TypeError: Cannot read properties of null` in chat rendering.

### Pitfall 5: Thread List Missing GET Endpoint
**What goes wrong:** The existing `PublicChatController` only has `POST /api/v1/chat/threads` (create). There is no `GET /api/v1/chat/threads` endpoint to list existing threads in the current codebase.
**Why it happens:** Phase 3 built the multi-turn posting contract but did not add a thread list API.
**How to avoid:** The plan must include a Wave 1 backend task to add `GET /api/v1/chat/threads` returning `List<ChatThreadResponse>`. The thread list UI cannot be built without this.
**Warning signs:** If the frontend tries to load threads and gets 404 or 405.

### Pitfall 6: TanStack Query Keys Drift
**What goes wrong:** Multiple components use different query key arrays for the same data, causing cache misses and stale data after mutations.
**Why it happens:** Developers write inline query keys (`['sources']` vs `['admin', 'sources']`).
**How to avoid:** Centralize all query keys in `lib/query-keys.ts`:
```typescript
export const queryKeys = {
  threads: ['chat', 'threads'] as const,
  thread: (id: string) => ['chat', 'threads', id] as const,
  sources: ['admin', 'sources'] as const,
  indexSummary: ['admin', 'index', 'summary'] as const,
  readiness: ['admin', 'index', 'readiness'] as const,
  parameters: ['admin', 'parameters'] as const,
};
```

### Pitfall 7: next.config.ts Uses `next.config.js` Syntax
**What goes wrong:** Developers copy old `module.exports = {}` syntax into `next.config.ts`.
**Why it happens:** Stack Overflow and old tutorials use the `.js` form.
**How to avoid:** Use ES module syntax in `next.config.ts`:
```typescript
import type { NextConfig } from 'next';
const nextConfig: NextConfig = { /* options */ };
export default nextConfig;
```

---

## Code Examples

Verified patterns from official sources and confirmed codebase contracts:

### Backend API Paths (from codebase)
```typescript
// lib/api/paths.ts — mirrors src/main/java/.../ApiPaths.java
// Source: D:\ai\traffic-law-chatbot\src\main\java\...\common\api\ApiPaths.java
export const API_PATHS = {
  CHAT_THREADS:    '/api/v1/chat/threads',
  CHAT_MESSAGES:   (threadId: string) => `/api/v1/chat/threads/${threadId}/messages`,
  SOURCES:         '/api/v1/admin/sources',
  SOURCE_APPROVE:  (id: string) => `/api/v1/admin/sources/${id}/approve`,
  SOURCE_REJECT:   (id: string) => `/api/v1/admin/sources/${id}/reject`,
  SOURCE_ACTIVATE: (id: string) => `/api/v1/admin/sources/${id}/activate`,
  SOURCE_DEACTIVATE:(id: string) => `/api/v1/admin/sources/${id}/deactivate`,
  SOURCE_REINGEST: (id: string) => `/api/v1/admin/sources/${id}/reingest`,
  CHUNK_READINESS: '/api/v1/admin/chunks/readiness',
  INDEX_SUMMARY:   '/api/v1/admin/index/summary',
  // New in Phase 4 (backend built this phase):
  PARAMETERS:      '/api/v1/admin/parameter-sets',
  PARAM_ACTIVATE:  (id: string) => `/api/v1/admin/parameter-sets/${id}/activate`,
  PARAM_COPY:      (id: string) => `/api/v1/admin/parameter-sets/${id}/copy`,
};
```

### TypeScript Types Mirroring Spring DTOs
```typescript
// Source: verified against codebase DTOs
export type GroundingStatus = 'GROUNDED' | 'LIMITED_GROUNDING' | 'REFUSED';
export type ResponseMode = 'STANDARD' | 'CLARIFICATION_NEEDED' | 'SCENARIO_ANALYSIS' | 'FINAL_ANALYSIS' | 'REFUSED';

export interface ChatAnswerResponse {
  groundingStatus: GroundingStatus;
  threadId: string;
  responseMode: ResponseMode;
  answer: string | null;
  conclusion: string | null;
  disclaimer: string | null;
  uncertaintyNotice: string | null;
  legalBasis: string[];
  penalties: string[];
  requiredDocuments: string[];
  procedureSteps: string[];
  nextSteps: string[];
  pendingFacts: PendingFactResponse[];
  rememberedFacts: RememberedFactResponse[];
  scenarioAnalysis: ScenarioAnalysisResponse | null;
  citations: CitationResponse[];
  sources: SourceReferenceResponse[];
}

export interface ScenarioAnalysisResponse {
  facts: string[];
  rule: string;
  outcome: string;
  actions: string[];
  sources: SourceReferenceResponse[];
}

export interface ChatThreadResponse {
  threadId: string;
  createdAt: string;   // ISO 8601 OffsetDateTime
  updatedAt: string;
}

export type SourceStatus = 'PENDING' | 'APPROVED' | 'ACTIVE' | 'REJECTED';
export type SourceType = 'URL' | 'PDF' | 'DOCX' | 'STRUCTURED';

export interface SourceSummaryResponse {
  id: string;
  title: string;
  sourceType: SourceType;
  status: SourceStatus;
  trustedState: string;
  approvalState: string;
  createdAt: string;
}

// New entity built in Phase 4
export interface AiParameterSetResponse {
  id: string;
  name: string;
  active: boolean;
  content: string;   // raw YAML
  createdAt: string;
  updatedAt: string;
}
```

### next.config.ts with API Rewrite (dev CORS workaround)
```typescript
// Source: nextjs.org/docs/app/api-reference/config/next-config-js/rewrites
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.SPRING_API_URL ?? 'http://localhost:8080'}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
```
Note: With rewrites, frontend calls `/api/v1/...` which Next.js proxies to Spring. This avoids CORS in development. In production, deploy behind the same origin or configure Spring CORS properly.

### shadcn cn() Utility
```typescript
// Source: shadcn/ui standard setup
// lib/utils.ts
import { type ClassValue, clsx } from 'clsx';
import { tailwind-merge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
```
(Note: `lib/utils.ts` is generated by `npx shadcn init` — do not create manually)

### Status Badge Pattern (matches UI-SPEC)
```typescript
// components/admin/sources/status-badge.tsx
// Source: UI-SPEC color contracts
const statusConfig: Record<SourceStatus, { label: string; className: string }> = {
  PENDING:  { label: 'Chờ duyệt',   className: 'bg-yellow-100 text-yellow-800' },
  APPROVED: { label: 'Đã duyệt',    className: 'bg-blue-100 text-blue-800' },
  ACTIVE:   { label: 'Đang hoạt động', className: 'bg-green-100 text-green-800' },
  REJECTED: { label: 'Từ chối',     className: 'bg-red-100 text-red-800' },
};
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `middleware.ts` | `proxy.ts` (middleware deprecated) | Next.js 16 (Oct 2025) | Rename only; logic identical |
| Sync `params.id` in page | `await params` then `.id` | Next.js 15+ → enforced in 16 | Breaking change; must audit all `[id]` pages |
| Turbopack as opt-in flag | Turbopack is default bundler | Next.js 16 | Faster builds; no config needed |
| `module.exports` in next.config | `export default` in `next.config.ts` | Next.js 15+ | Use ES module syntax |
| Webpack as default bundler | Turbopack default; webpack via `--webpack` flag | Next.js 16 | No custom webpack needed for this app |
| React Compiler opt-in | Stable in Next.js 16, still opt-in | Next.js 16 | Can enable `reactCompiler: true` for auto-memoization |
| `experimental.ppr` flag | `cacheComponents: true` | Next.js 16 | Renamed/redesigned; not needed for this app |

**Deprecated/outdated:**
- `middleware.ts`: deprecated in favor of `proxy.ts` — will be removed in future Next.js version. [VERIFIED: nextjs.org/blog/next-16]
- `next/legacy/image`: use `next/image`. Not applicable here (no images).
- `serverRuntimeConfig`/`publicRuntimeConfig`: removed in Next.js 16. Use env vars.

---

## Reference Project Findings (D:\DTH\demo-next-frontend)

The reference project is a JHipster-generated monolith with a Webpack-based React 19 frontend embedded in a Spring Boot build. It is **not** a Next.js App Router project — its frontend patterns are not applicable here.

**Do borrow from reference project:**
- Husky + lint-staged Git hook setup (`.husky/pre-commit` running lint-staged)
- Prettier formatting coverage (`.md`, `.json`, `.yml`, `.ts`, `.tsx`, `.css`)
- ESLint flat config format (`eslint.config.ts`) — this aligns with Next.js 16's default which also uses flat config
- Script naming conventions: `lint`, `lint:fix`, `prettier:check`, `prettier:format`

**Do not copy from reference project:**
- Webpack config (Turbopack is default in Next.js 16)
- Redux/redux-saga state pattern (TanStack Query replaces this entirely)
- Axios-based interceptors (TanStack Query + native fetch is the approach)
- JHipster routing structure (App Router route groups are the pattern)
- React class components or JHipster-generated page structure

---

## Backend Preconditions (Must Land Before Frontend Work)

These backend tasks must complete in Wave 1 before any Wave 2 frontend admin screens can be built:

| Precondition | Required By | Backend Change |
|-------------|-------------|----------------|
| `GET /api/v1/chat/threads` endpoint | Chat thread list in sidebar | Add list endpoint to `PublicChatController` |
| `AiParameterSet` entity + migration | Admin parameters screen | New JPA entity, Flyway migration |
| `POST/PUT/DELETE /api/v1/admin/parameter-sets` | Parameter CRUD UI | New controller + service |
| `POST /api/v1/admin/parameter-sets/{id}/activate` | Activate parameter set | Activation endpoint |
| `POST /api/v1/admin/parameter-sets/{id}/copy` | Copy parameter set | Copy endpoint |
| Move hardcoded constants to `AiParameterSet` | Parameter set actually works | Refactor `AnswerCompositionPolicy`, `ClarificationPolicy`, `ChatPromptFactory` |
| Seed default parameter set on startup | Backend works out of box | `@PostConstruct` or `ApplicationReadyEvent` seeder |
| Add `ChatMessageType.CLARIFICATION` enum value | Clean message type filtering | Fix `ChatThreadService.countClarificationMessages()` |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Next.js dev server, npm | ✓ | v24.14.0 | — |
| npm | Package management | ✓ | 11.9.0 | — |
| Java (Spring Boot) | Backend API | ✓ (via gradle) | Java 25 (per CLAUDE.md) | — |
| Git + Husky | Pre-commit hooks | ✓ | (current branch exists) | — |

**Missing dependencies:** None blocking. [VERIFIED: bash — node --version, npm --version on 2026-04-11]

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Jest + @testing-library/react (add in Wave 0) |
| Config file | `frontend/jest.config.ts` — Wave 0 gap |
| Quick run command | `npm run test -- --passWithNoTests` |
| Full suite command | `npm run test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PLAT-02 | Next.js app boots and renders without errors | smoke | `npm run build` | ❌ Wave 0 |
| ADMIN-01 | Source table renders, action buttons are context-sensitive | unit | `jest components/admin/sources/sources-table.test.tsx` | ❌ Wave 0 |
| ADMIN-02 | Index cards render with data from readiness endpoint | unit | `jest components/admin/index/index-cards.test.tsx` | ❌ Wave 0 |
| ADMIN-03 | Parameter set CRUD dialog validates required fields | unit | `jest components/admin/parameters/parameter-dialog.test.tsx` | ❌ Wave 0 |
| ADMIN-06 | Sidebar renders nav items and switches between sections | unit | `jest components/layout/app-sidebar.test.tsx` | ❌ Wave 0 |

### Wave 0 Gaps
- [ ] `frontend/jest.config.ts` — Jest setup for Next.js App Router
- [ ] `frontend/jest.setup.ts` — @testing-library/jest-dom matchers
- [ ] `frontend/tsconfig.json` — TypeScript config
- [ ] Framework install: `npm install --save-dev jest @testing-library/react @testing-library/jest-dom jest-environment-jsdom ts-jest`

### Sampling Rate
- **Per task commit:** `npm run lint && npm run build` (type check + build)
- **Per wave merge:** Full jest suite
- **Phase gate:** `npm run build` green + full jest suite green before `/gsd-verify-work`

---

## Security Domain

> `security_enforcement` absent from config — treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No auth in v1 scope (deferred) |
| V3 Session Management | No | No auth in v1 scope |
| V4 Access Control | No | No auth in v1 scope |
| V5 Input Validation | Yes | zod schema validation on parameter set form |
| V6 Cryptography | No | No crypto operations in frontend |
| V7 Error Handling | Yes | TanStack Query error states; Alert components for API failures |
| V14 Config | Yes | `NEXT_PUBLIC_API_BASE_URL` env var; no secrets in client bundle |

### Known Threat Patterns for Next.js + Spring REST

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| API base URL in client bundle | Info Disclosure | Only expose `NEXT_PUBLIC_*` env vars; Spring URL is not secret but should not include credentials |
| XSS via markdown rendering | Tampering | Use `react-markdown` with `rehype-sanitize` or render as plain text with whitespace-pre; do NOT use `dangerouslySetInnerHTML` for chat answers |
| CORS misconfiguration | Tampering | Spring `@CrossOrigin` limited to known origins; or next.config rewrites proxy in dev |
| Unvalidated form input to YAML | Tampering | Zod schema validates `name` (non-empty string); YAML content is opaque string — backend owns YAML schema validation |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `GET /api/v1/chat/threads` does not exist yet | Backend Preconditions | If it already exists, no backend work needed for thread list endpoint |
| A2 | Spring backend will be run on `localhost:8080` in development | Code Examples (rewrites) | If different port, `SPRING_API_URL` env var must be set |
| A3 | `ChatThreadResponse` does not include `title` — thread titles must be derived from first message client-side | Architecture Patterns | If backend adds title later, frontend can simplify |
| A4 | `SourceAdminController` does not expose a `reingest` endpoint yet (not in ApiPaths.java) | Code Examples | If reingest is already implemented, UI can wire it immediately |

---

## Open Questions

1. **Does `reingest` endpoint exist?**
   - What we know: `ApiPaths.java` has `SOURCE_REINGEST = SOURCE_BY_ID + "/reingest"` as a constant but `SourceAdminController` does not implement a `@PostMapping("/{sourceId}/reingest")` handler — the path constant exists, the handler may not.
   - What's unclear: Whether the service-layer reingest method exists.
   - Recommendation: Wave 1 task should verify and add the handler if missing.

2. **How should thread titles be derived?**
   - What we know: `ChatThreadResponse` only has `threadId`, `createdAt`, `updatedAt` — no title field. UI-SPEC says "derived from first user message, max 40 chars."
   - What's unclear: Whether to store first message client-side or fetch thread messages to get the title.
   - Recommendation: Store thread+first-message pair in TanStack Query cache when creating a thread; derive title from the creation-time question. No extra API call needed.

3. **CORS strategy for production**
   - What we know: D-18 says no proxy; but browser CORS requires same origin or explicit headers.
   - What's unclear: Production deployment topology (same nginx? Vercel? separate domains?).
   - Recommendation: Add Spring `@CrossOrigin` for development. Document production CORS config requirement for deployment. Use next.config rewrites in dev only.

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: npm registry] — next@16.2.3, @tanstack/react-query@5.97.0, @tanstack/react-table@8.21.3, react-hook-form@7.72.1, zod@4.3.6, shadcn@4.2.0, tailwindcss@4.2.2, lucide-react@1.8.0, typescript@5.9.3 — versions confirmed on 2026-04-11
- [VERIFIED: codebase] — `ApiPaths.java`, `ChatAnswerResponse.java`, `ScenarioAnalysisResponse.java`, `ChatThreadResponse.java`, `SourceSummaryResponse.java`, `PublicChatController.java`, `ChunkAdminController.java` — all DTOs and API contracts read directly
- [CITED: nextjs.org/blog/next-16] — Next.js 16 breaking changes, proxy.ts, async params, Turbopack default, removed features
- [CITED: github.com/shadcn-ui/ui/issues/9189] — SidebarProvider blocking route bug in Next.js 16

### Secondary (MEDIUM confidence)
- [CITED: ui.shadcn.com/docs/installation/next] — shadcn/ui Next.js installation steps
- [WebSearch verified with official source] — TanStack Query Next.js App Router setup: providers.tsx 'use client' pattern
- [WebSearch verified with official source] — shadcn Sidebar cookie persistence pattern; SidebarProvider in root layout
- [WebSearch verified with official source] — TanStack Table v8 + shadcn DataTable: columns.tsx + data-table.tsx separation pattern
- [WebSearch verified with official source] — react-hook-form + zod + shadcn Form pattern

### Tertiary (LOW confidence)
- [WebSearch only] — next.config.ts rewrites syntax for API proxy (verified against nextjs.org docs path)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified against npm registry; no training-data guesses
- Architecture patterns: HIGH — route structure from CONTEXT.md D-17; component patterns from official shadcn/TanStack docs
- Backend contracts: HIGH — read directly from codebase DTOs and controllers
- Next.js 16 specifics: MEDIUM — blog post verified; some edge cases (e.g., cacheComponents interaction) may have evolved
- Pitfalls: HIGH for items verified via GitHub issues; MEDIUM for inferred patterns

**Research date:** 2026-04-11
**Valid until:** 2026-05-11 (Next.js 16 is recent — monitor for patch releases; TanStack Query / shadcn are stable)
