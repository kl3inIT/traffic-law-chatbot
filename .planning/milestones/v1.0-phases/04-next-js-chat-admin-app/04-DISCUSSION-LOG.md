# Phase 4: Next.js Chat & Admin App - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-10
**Phase:** 04-next-js-chat-admin-app
**Areas discussed:** UI component stack, Chat interface layout, Admin screens scope, Frontend repo structure

---

## UI Component Stack

| Option | Description | Selected |
|--------|-------------|----------|
| shadcn/ui + Tailwind | Composable, owned components, sidebar + DataTable built-in, no lock-in | ✓ |
| shadcn/ui + Vercel AI SDK | Adds useChat hook for streaming — not needed since Spring owns AI orchestration | |
| Ant Design | Richer admin tables/forms but heavier, CSS conflicts with Tailwind | |

**User's choice:** shadcn/ui + Tailwind + TanStack Query. Explicitly rejected Vercel AI SDK after research confirmed it targets a Next.js-to-AI-provider proxy pattern, not a Spring REST backend.

**Notes:** Research via Context7 confirmed shadcn/ui has a production-ready Sidebar component and DataTable (TanStack Table). Vercel AI SDK's useChat expects a /api/chat Next.js proxy route; since Spring handles all AI logic and returns complete JSON, there is no benefit.

---

## Chat Interface Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Thread list + active thread | Left panel lists threads, right area shows messages. Claude/ChatGPT pattern. | ✓ |
| Single thread, no history panel | Simpler but loses multi-thread navigation | |

**Scenario answer display:**

| Option | Description | Selected |
|--------|-------------|----------|
| Collapsible sections | shadcn/ui Accordion per scenario section | ✓ |
| Flat sections always visible | Simpler but dense for long scenario answers | |
| Same as regular answer | No visual distinction | |

**Clarifying question display:**

| Option | Description | Selected |
|--------|-------------|----------|
| Inline as AI message | Normal bubble, user replies in same input | ✓ |
| Highlighted prompt card | Visually distinct card signaling required step | |

---

## Admin Screens Scope

| Option | Description | Selected |
|--------|-------------|----------|
| All 3 areas fully functional | Sources + index inspection + parameter sets | ✓ |
| Source management + inspection only | Defer parameter sets to Phase 5 | |

**Source management:**

| Option | Description | Selected |
|--------|-------------|----------|
| Full lifecycle table | DataTable with status badges and per-row action buttons | ✓ |
| Read-only list + modal actions | Detail modal for actions, more clicks | |

**AI parameter set fields:** Discussed jmix-ai-backend reference. User requested an investigation of the jmix project before answering, then confirmed the jmix YAML-in-DB pattern should be followed. Captured as D-11 through D-15 in CONTEXT.md.

---

## Frontend Repo Structure

| Option | Description | Selected |
|--------|-------------|----------|
| /frontend subfolder in this repo | Same git repo, simplest setup | ✓ |
| Separate Next.js project | Separate repo, more isolation | |
| pnpm/npm workspace monorepo | Workspace root, more tooling overhead | |

**Route structure:**

| Option | Description | Selected |
|--------|-------------|----------|
| App Router with route groups | (chat) and (admin) groups, shared sidebar shell | ✓ |
| Flat routes, no groups | All routes flat under app/ | |

---

## Claude's Discretion

- shadcn/ui component variants for chat bubbles and status badges
- TanStack Query cache configuration
- Thread title derivation (first message vs timestamp)
- YAML editor UX in parameter set admin (form-per-field vs raw textarea)
- Error boundary and loading skeleton treatment

## Deferred Ideas

- Streaming responses — not in v1
- Chat log review — Phase 5
- Answer checks UI — Phase 5
- User authentication / role separation — out of v1 scope
