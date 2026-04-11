# Phase 4: Next.js Chat & Admin App - Context

**Gathered:** 2026-04-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the combined sidebar-style Next.js frontend application for public chat and core admin operations, wired entirely to the Spring REST backend built in Phases 1–3. No frontend existed before this phase — it is built from scratch as a `frontend/` subfolder in this repo.

This phase also includes backend work that unblocks the admin UI: the `AiParameterSet` domain (entity, CRUD REST API) and a targeted refactoring of hardcoded AI settings into admin-managed parameter sets. The retrieval safety filter and legal citation signals remain hardcoded — only tunable settings move to parameter sets.

Phase 4 does not add new backend AI capabilities, new legal reasoning, or chat log review (Phase 5).

</domain>

<decisions>
## Implementation Decisions

### UI Component Stack
- **D-01:** shadcn/ui + Tailwind CSS for all UI components — sidebar shell, admin tables, forms, dialogs, badges.
- **D-02:** TanStack Query (`@tanstack/react-query`) for all Spring REST API calls — handles caching, loading/error states, and refetch.
- **D-03:** No Vercel AI SDK — the Spring backend owns all AI orchestration and returns complete JSON responses. No streaming proxy layer is needed.
- **D-04:** shadcn/ui `Sidebar` component (with `SidebarProvider`) is the foundation for the sidebar shell (ADMIN-06).
- **D-05:** shadcn/ui `DataTable` (powered by TanStack Table v8) is the foundation for all admin list screens.

### Chat Interface Layout
- **D-06:** Thread list panel on the left + active thread main area on the right. User can switch threads and create new ones ("+ New" button). Standard Claude/ChatGPT pattern.
- **D-07:** Structured scenario answers (Facts / Rule / Outcome / Actions / Sources) are rendered as collapsible accordion sections using shadcn/ui `Accordion`. Regular Q&A answers render as plain text bubbles.
- **D-08:** Clarifying questions from the system appear inline as normal AI message bubbles — no special highlighted card. User replies in the same input box.
- **D-09:** Thread list lives in the chat section of the sidebar, not a separate panel. The sidebar nav switches between Chat (with thread list) and Admin sections.

### Admin Screens Scope
- **D-10:** All three admin capability areas are fully functional in Phase 4:
  1. **Source management** (ADMIN-01): Full lifecycle DataTable — columns for title/URL, type, status badge, created date, and per-row action buttons (Approve, Reject, Activate, Deactivate, Reingest). Context-sensitive: only valid actions for current status are shown.
  2. **Knowledge index inspection** (ADMIN-02): Read-only view of chunk readiness counts and index summary (`/api/v1/admin/chunks/readiness`, `/api/v1/admin/index/summary`). No editing.
  3. **AI parameter sets** (ADMIN-03): Full CRUD — list view with active badge, create (blank or copy), edit (YAML content + name), activate, delete. One active set at a time.

### AI Parameter Sets — Backend Design (precondition for Phase 4 admin UI)
- **D-11:** Follow jmix-ai-backend pattern — YAML content stored in a `ai_parameter_set` DB table. Fields: `id` (UUID), `name` (VARCHAR), `active` (BOOLEAN), `content` (TEXT/YAML), `created_at`, `updated_at`. One active set at a time; activation deactivates all others.
- **D-12:** Parameter set YAML structure covers:
  - `model`: name, temperature, maxTokens
  - `retrieval`: topK, similarityThreshold, groundingLimitedThreshold
  - `systemPrompt`: full Vietnamese legal guidance prompt (multi-line)
  - `caseAnalysis.maxClarifications`: integer
  - `caseAnalysis.requiredFacts[]`: list with `key`, `alwaysRequired`, `triggerKeywords[]`, `question`, `explanation`
  - `messages`: disclaimer, refusal, limitedNotice, refusalNextStep1/2/3, clarificationIntro, clarificationNextStep
- **D-13:** Backend refactoring required before admin UI can be built:
  - Add `ChatMessageType.CLARIFICATION` enum value (currently only QUESTION, ANSWER). Fix `ChatThreadService.countClarificationMessages()` to filter by messageType instead of `[CLARIFICATION]` string content.
  - Move all hardcoded constants from `AnswerCompositionPolicy`, `ClarificationPolicy`, and `ChatPromptFactory` out of Java into the active parameter set. Services read from the active `AiParameterSet` at runtime.
  - **Keep hardcoded (safety-critical):** retrieval filter expression (`approvalState == 'APPROVED' && trusted == 'true' && active == 'true'`), legal citation signal keywords, JSON schema field names in the prompt.
- **D-14:** REST API for parameter sets under `/api/v1/admin/parameter-sets`: GET list, GET by id, POST create, PUT update, DELETE, POST `/{id}/activate`, POST `/{id}/copy`.
- **D-15:** A default parameter set (seeded from a YAML resource file on first startup) ensures the backend works out of the box with no manual admin setup required.

### Frontend Repo Structure
- **D-16:** Next.js app lives in `frontend/` subfolder alongside `src/` (Spring Boot). Same git repo. Backend and frontend deployed independently.
- **D-17:** App Router with route groups:
  ```
  frontend/app/
  ├── layout.tsx              ← sidebar shell (shared)
  ├── (chat)/
  │   ├── page.tsx            ← new chat / thread list entry
  │   └── threads/[id]/page.tsx
  └── (admin)/
      ├── sources/page.tsx
      ├── index/page.tsx
      └── parameters/page.tsx
  ```
- **D-18:** All API calls go directly from Next.js client components to the Spring REST backend via TanStack Query. No Next.js API routes acting as proxy.

### Claude's Discretion
- Exact shadcn/ui component variants (sizes, colors, icon choices) for chat bubbles and status badges.
- TanStack Query stale times, retry config, and cache invalidation strategy.
- Whether thread titles are derived from the first message or kept as timestamps.
- Exact YAML schema field names in the parameter set (follow the structure in D-12 but precise naming is implementation detail).
- How the parameter set YAML editor is presented in the admin UI — plain textarea vs a code editor component.
- Error boundary and loading skeleton treatment.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase definition
- `.planning/ROADMAP.md` — Phase 4 goal, requirements (PLAT-02, ADMIN-01, ADMIN-02, ADMIN-03, ADMIN-06), and success criteria.
- `.planning/REQUIREMENTS.md` — Full requirement text for all Phase 4 requirements.
- `.planning/PROJECT.md` — Vietnamese-first, trusted-source, REST-first, and sidebar-style constraints.

### Prior locked context
- `.planning/phases/02-grounded-legal-q-a-core/02-CONTEXT.md` — Chat answer structure, citations, disclaimer behavior.
- `.planning/phases/03-multi-turn-case-analysis/03-CONTEXT.md` — Thread/message REST contract, scenario answer structure (Facts/Rule/Outcome), clarifying question behavior.

### Existing backend REST contracts (frontend wires to these)
- `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java` — All REST path constants. Frontend must use these paths.
- `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java` — Full chat answer shape frontend must render.
- `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatThreadResponse.java` — Thread list item shape.
- `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ScenarioAnalysisResponse.java` — Scenario-specific response structure.
- `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java` — Source management endpoints and their request/response shapes.

### Reference for AI parameter sets
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/entity/Parameters.java` — The jmix entity pattern this phase mirrors.
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/resources/io/jmix/ai/backend/init/default-params-chat.yml` — Reference for YAML structure and field coverage.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java` — All REST paths are constant-defined. Frontend must read these to know correct URLs.
- `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java` — Entry point for thread creation and message posting. Frontend chat screens call these.
- `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java` — Full source lifecycle API already implemented.
- `src/main/java/com/vn/traffic/chatbot/chunk/api/` — Chunk readiness and index summary endpoints already implemented.

### Backend Hardcoded Settings to Refactor (before admin UI)
- `src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessageType.java` — Currently only QUESTION and ANSWER. CLARIFICATION must be added.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java` — `countClarificationMessages()` uses string content detection (`[CLARIFICATION]`) — must switch to messageType.
- `src/main/java/com/vn/traffic/chatbot/chat/service/AnswerCompositionPolicy.java` — All static constants (disclaimer, refusal, limited notice, next steps) move to parameter set.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicy.java` — Hardcoded keyword triggers and fact labels move to parameter set YAML. maxClarifications already @Value-injectable.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java` — Hardcoded system prompt moves to parameter set.
- `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java` — topK and similarityThreshold become readable from parameter set (filter expression stays hardcoded).

### Established Patterns
- Spring backend is REST-first, constant-driven API paths, controller/service/DTO split.
- Source approval lifecycle (PENDING → APPROVED → ACTIVE) is the model for how parameter set activation should work.
- No frontend exists yet — `frontend/` is created fresh in this phase.

### Integration Points
- Frontend `(chat)` routes call `/api/v1/chat/threads` and `/api/v1/chat/threads/{threadId}/messages`.
- Frontend `(admin)/sources` calls `/api/v1/admin/sources` and its sub-paths.
- Frontend `(admin)/index` calls `/api/v1/admin/chunks/readiness` and `/api/v1/admin/index/summary`.
- Frontend `(admin)/parameters` calls `/api/v1/admin/parameter-sets` (new endpoint built in this phase).

</code_context>

<specifics>
## Specific Ideas

- The jmix-ai-backend admin UI is the reference for how AI parameter sets should feel — list with active badge, YAML editor, copy/activate actions.
- The sidebar layout uses shadcn/ui's own Sidebar component, which already handles collapsible nav, mobile/desktop modes, and provider context. Do not build a custom sidebar.
- Scenario answers should feel structured and readable, not wall-of-text. Collapsible sections are the chosen treatment.
- Clarifying questions should feel like natural conversation, not like a blocking form — inline AI bubble is the right pattern.
- The parameter set admin must be usable without needing to know YAML syntax manually — consider whether a form-per-field view is better than raw YAML. Claude's discretion on this UX choice.

</specifics>

<deferred>
## Deferred Ideas

- Chat log review and answer check workflows belong to Phase 5.
- Streaming responses from the Spring backend — not in v1 scope.
- User authentication / role separation — explicitly out of v1 scope.
- Mobile-specific optimizations beyond what shadcn/ui handles by default.

</deferred>

---

*Phase: 04-next-js-chat-admin-app*
*Context gathered: 2026-04-10*
