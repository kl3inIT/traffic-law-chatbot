# Phase 5: Quality Operations & Evaluation - Context

**Gathered:** 2026-04-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Make the chatbot safe to operate and improve through observable chat logs and repeatable answer checks. This phase delivers:
- Chat log capture at the backend (ChatService retrofitted to persist each exchange)
- Chat log admin UI: browseable, filterable, searchable log review
- Answer check definitions: admin-managed question + reference answer pairs
- Check runs: on-demand execution that scores all active checks and stores results
- LLM-as-judge evaluator: model-agnostic semantic scoring via Spring AI
- Model selection: configurable chat model and evaluator model as independent AiParameterSet fields

This phase does NOT add new chat AI capabilities, modify retrieval behavior, or change ingestion pipelines.
</domain>

<decisions>
## Implementation Decisions

### Chat Log Capture

- **D-01:** Each log entry stores: `question` (user input), `answer` (full answer text), `sources` (comma-separated citation references), `promptTokens`, `completionTokens`, `responseTime`, `groundingStatus` (GROUNDED / LIMITED_GROUNDING / REFUSED), `conversationId`, `createdDate`. This matches and extends the jmix `ChatLog` shape.
- **D-02:** One log entry is written **per chatbot answer** (per exchange). Thread grouping uses `conversationId`. `ChatService.answer()` is retrofitted to persist a log entry after composing the response.
- **D-03:** `groundingStatus` is a dedicated column (enum-backed). Enables filtering refused/weak answers without parsing the sources field.
- **D-04:** Chat logs are **append-only** — no delete or soft-delete endpoint. Logs are audit records for quality investigation and regression detection.

### Chat Log Admin UI

- **D-05:** Log list uses the **DataTable pattern** (same as sources screen): paginated, sortable by date desc, filterable by date range and grounding status. A search input filters by question text (backend LIKE query).
- **D-06:** Log detail view (on row click): question text, full answer, structured citations panel, grounding status badge, token counts (prompt + completion), response time. Enough context to investigate any individual answer.
- **D-07:** Text search applies to the `question` column via a LIKE query on the backend. This is sufficient for v1 quality investigation.

### Answer Check Definitions

- **D-08:** `CheckDef` entity fields: `question` (text), `referenceAnswer` (text), `category` (optional label string), `active` (boolean toggle). Matches the jmix `CheckDef` model — proven shape for this use case.
- **D-09:** Admin UI CRUD form only — create / edit / delete individual check definitions. No bulk import in v1. The admin screen follows existing CRUD form patterns in the admin app.

### Check Execution & Results

- **D-10:** Check runs are **on-demand only** — admin clicks a "Run checks" button in the UI. The backend runs all active `CheckDef` entries asynchronously and stores results in a `CheckRun` record. No scheduler or cron in v1.
- **D-11:** Each `CheckRun` captures a **reference to the active `AiParameterSet`** at the time the run is created (or a snapshot of its key fields). This links score changes to parameter or model changes over time.
- **D-12:** Results UI: run history list shows date, overall score, parameter set reference. Clicking a run shows per-check results table: question, reference answer, actual answer, score. No chart/trend view in v1.

### Semantic Evaluator & Model Configuration

- **D-13:** Evaluator uses **LLM-as-judge via Spring AI** — send reference answer + actual answer to the configured evaluator model with a scoring prompt. Returns a 0–1 semantic similarity score. Evaluator model is configurable independently of the chat model.
- **D-14:** Both **chat model** and **evaluator model** are new fields added to the existing `AiParameterSet` entity. They are independent settings — admins can use a lightweight model for evaluation and a stronger model for chat, or vice versa.
- **D-15:** The allowed model list is a **hardcoded enum or constant list** in the backend (e.g., `AllowedModel`). Admin picks from a dropdown. Adding a new model requires a code change — this keeps the config safe and predictable in v1.

### Claude's Discretion
- `ChatLog` entity package location (e.g., `chat/domain/` or `chat/log/`)
- Exact `groundingStatus` enum name and mapping from `GroundingStatus` already in `ChatService`
- `CheckRun` / `CheckDef` / `Check` entity package layout
- AiParameterSet Liquibase migration column names for `chatModel` and `evaluatorModel`
- LLM-as-judge prompt wording for the evaluator
- Whether `CheckRun` stores the full AiParameterSet snapshot as JSON or just a foreign-key reference
- Thread safety and error handling in the async check runner
- Frontend DataTable column selection and filter component reuse from existing admin screens

</decisions>

<specifics>
## Specific Ideas

- **groundingStatus is first-class:** Filtering for refused and limited-grounding answers is the primary quality investigation workflow. The column must be indexed and filterable.
- **LLM-as-judge is model-agnostic by design:** The evaluator should accept any configured Spring AI ChatModel — not hardcoded to Claude. The admin picks the evaluator model from the same AllowedModel list as the chat model.
- **Evaluator and chat models are independent:** An admin may choose to run evaluations with a cheaper/faster model than the one serving end users. The parameter set stores both separately.
- **ChatService retrofit is the critical path:** All other Phase 5 features (log UI, checks) depend on logs being captured. The first plan should land the log entity + service integration before building any UI.
- **Check run async pattern:** The check runner submits all active CheckDef items in parallel (matching jmix CheckRunner with ExecutorService). The REST endpoint returns a run ID immediately; the admin UI polls or refreshes to see results.
- **AiParameterSet already exists:** Phase 4 built this entity and its admin screen. Phase 5 extends it (Liquibase migration) rather than replacing it. The existing parameters admin screen should be updated to expose the new model selection fields.
</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase definition
- `.planning/ROADMAP.md` — Phase 5 goal, requirements (ADMIN-04, ADMIN-05), success criteria.
- `.planning/REQUIREMENTS.md` — Full requirement text for ADMIN-04 and ADMIN-05.
- `.planning/PROJECT.md` — REST-first Spring, Vietnamese-first, trusted-source constraints.

### Prior locked context
- `.planning/phases/04-next-js-chat-admin-app/04-CONTEXT.md` — AiParameterSet entity shape (Phase 5 extends it), shadcn DataTable + TanStack Query admin UI patterns, sidebar shell conventions.
- `.planning/phases/04.1-backend-hardening-etl-usecase-architecture/04.1-CONTEXT.md` — Trust policy admin screen pattern; Phase 5 admin screens follow the same conventions.

### Reference: jmix-ai-backend (benchmark — adapt, do not copy)
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/entity/ChatLog.java` — ChatLog entity shape.
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/chatlog/ChatLogManager.java` — Log persistence pattern.
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/entity/CheckDef.java` — CheckDef entity shape.
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/entity/CheckRun.java` — CheckRun entity shape.
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/entity/Check.java` — Check (per-item result) entity shape.
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/checks/CheckRunner.java` — Parallel check execution pattern.
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/checks/ExternalEvaluator.java` — Semantic evaluator interface.
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/checks/ExternalEvaluatorImpl.java` — Evaluator implementation pattern.

### Existing backend files to extend
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` — Retrofit `answer()` to persist chat logs after composing response.
- `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java` — Add path constants for chat log and check endpoints.
- `src/main/java/com/vn/traffic/chatbot/parameter/` — AiParameterSet entity to extend with `chatModel` and `evaluatorModel` fields.

### Existing frontend files to extend
- `frontend/app/(admin)/parameters/` — Extend parameters admin screen to expose new model selection fields.
- `frontend/app/(admin)/` — Add `chat-logs/` and `checks/` screen directories alongside existing admin screens.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ChatService.answer()` — already computes grounding status, citations, token counts; log persistence hooks in here.
- `GroundingStatus` enum — already defined in `ChatService`; `groundingStatus` column on `ChatLog` maps to this.
- AiParameterSet entity + admin screen (Phase 4) — extended with `chatModel` and `evaluatorModel` fields.
- shadcn DataTable + TanStack Query — established pattern in sources and trust-policy admin screens; log list and check run list reuse this.
- `GlobalExceptionHandler` + `ProblemDetail` — all new endpoints follow existing error response shape.
- `AppException` + `ErrorCode` pattern — new errors (e.g., NO_ACTIVE_CHECK_DEFS) follow this.
- `ApiPaths` constants — all new endpoint paths defined here first.

### Established Patterns
- Admin CRUD screens follow: DataTable list → row click detail/modal → form for create/edit.
- REST endpoints follow: controller → service → repository, with `ApiPaths` path constants.
- Async operations: `@Async` on service method + `SimpleAsyncTaskExecutor` (virtual threads from Phase 4.1).
- Liquibase migrations in `src/main/resources/db/changelog/` — new tables for chat_log, check_def, check_run, check go here.

### Integration Points
- `ChatService.answer()` calls `answerComposer.compose()` to get final response — log entry written after this, before returning to controller.
- Check runner calls `ChatService.answer()` (or equivalent) per check definition.
- AiParameterSet is read by `ChatService` for topK/threshold — `chatModel` field will be read here too to configure the ChatClient model dynamically.

</code_context>

<deferred>
## Deferred Ideas

- **Scheduled / automatic check runs** — on-demand only in v1. Scheduled regression runs (nightly CI-like) deferred to a future phase.
- **Score trend charts / dashboards** — no charting in v1. Run history list with scores is sufficient for regression detection.
- **Bulk import of check definitions** — no CSV/JSON bulk import in v1. Admin UI form create only.
- **Chat log deletion / retention policy** — append-only in v1. Soft-delete or retention TTL deferred if PII or storage concerns arise.
- **Multi-provider model registry** — the allowed model list is a hardcoded enum in v1. A dynamic provider registry (adding models without code changes) is a future concern.
- **Per-check grading rubrics** — question + reference answer is the v1 shape. Rubric-based scoring deferred.

</deferred>

---

*Phase: 05-quality-operations-evaluation*
*Context gathered: 2026-04-12*
