# Roadmap: Vietnam Traffic Law Chatbot

**Created:** 2026-04-07
**Mode:** yolo
**Granularity:** standard
**Coverage:** 28 / 28 v1 requirements mapped

## Summary

This roadmap delivers the Vietnam Traffic Law Chatbot as a REST-first Spring + Next.js system in five phases. It prioritizes trusted-source ingestion and retrieval first, then grounded legal Q&A, then case analysis, then the combined admin/chat application, and finally quality hardening through checks and observability.

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 1 | Backend Foundation & Knowledge Base | Establish the Java 25 Spring REST foundation, persistence, source metadata, and ingestion/vector-store backbone | PLAT-01, PLAT-03, PLAT-04, KNOW-01, KNOW-02, KNOW-03, KNOW-04, KNOW-05, KNOW-06 | 5 |
| 2 | Grounded Legal Q&A Core | Deliver source-backed Vietnamese chat answers with citations and legal guidance structure | CHAT-01, CHAT-03, CHAT-04, LEGAL-01, LEGAL-02, LEGAL-03, LEGAL-04 | 5 |
| 3 | Multi-turn Case Analysis | Add thread memory, clarifying questions, and structured scenario analysis | CHAT-02, CASE-01, CASE-02, CASE-03, CASE-04 | 5 |
| 4 | Next.js Chat & Admin App | Build the sidebar-style Next.js app for public chat and core admin workflows | PLAT-02, ADMIN-01, ADMIN-02, ADMIN-03, ADMIN-06 | 5 |
| 4.1 | Backend Hardening, ETL Maturation & Use-Case Architecture | Harden backend operations layer, complete Spring AI ETL pipeline, and explore legal use-case management architecture | PLAT-01, KNOW-01, KNOW-02, KNOW-03, KNOW-04 | 4 |
| 5 | Quality Operations & Evaluation | Add chat-log review, answer checks, and operational safeguards for safe iteration | ADMIN-04, ADMIN-05 | 4 |

## Phase Details

### Phase 1: Backend Foundation & Knowledge Base
**Goal:** Establish the Java 25 Spring REST backend, persistent storage, source registry, ingestion pipelines, and vector-store foundation.

**Requirements:** PLAT-01, PLAT-03, PLAT-04, KNOW-01, KNOW-02, KNOW-03, KNOW-04, KNOW-05, KNOW-06

**UI hint:** no

**Plans:** 4 plans

Plans:
- [x] 01-01-PLAN.md — Infrastructure foundation: Liquibase schema, pgvector config, REST conventions layer, async executor
- [x] 01-02-PLAN.md — Source registry domain: entities, service with approval lifecycle, REST endpoints
- [x] 01-03-PLAN.md — Async ingestion pipeline: Tika/JSoup parsers, chunker, orchestrator, ingestion REST endpoints
- [x] 01-04-PLAN.md — Chunk inspection, retrieval policy hard-filter, and chunk metadata updater

**Success criteria:**
1. Spring backend runs on Java 25 and exposes a stable REST API base for chatbot and admin capabilities.
2. PostgreSQL and pgvector-backed persistence store source metadata, operational records, and embeddings reliably.
3. Admin/API workflows can ingest PDF, Word, structured regulation documents, and trusted website content.
4. Ingested sources retain provenance metadata such as type and origin.
5. Retrieval is constrained to active and trusted sources only.

### Phase 01.1: Refactor ingestion parser architecture toward a Spring AI-first ETL pipeline while preserving SSRF safety, provenance, source_version/job boundaries, and fixing final_url tracking (INSERTED)

**Goal:** Fix URL provenance first, then promote Spring AI ETL/readers toward the primary ingestion path without weakening SSRF safety, provenance guarantees, async job visibility, retrieval-gating behavior, or auditability.

**Requirements**: PLAT-01, KNOW-01, KNOW-02, KNOW-03, KNOW-04, KNOW-05, KNOW-06
**Depends on:** Phase 1
**Plans:** 3/4 plans executed

Plans:
- [x] 01.1-01-PLAN.md — Fix final_url provenance with a safe fetch/result contract and URL snapshot regression coverage
- [x] 01.1-02-PLAN.md — Add adapter-based Spring AI HTML parsing behind ParsedDocument while preserving retrieval/inspection metadata
- [x] 01.1-03-PLAN.md — Run bounded PDF parser parity evaluation and adopt Spring AI PDF only if provenance compatibility is proven
- [ ] 01.1-04-PLAN.md — Promote Spring AI ETL/readers to the primary ingestion path, replace safe remaining default Tika paths, and move chunking toward token splitting while preserving metadata and auditability

### Phase 2: Grounded Legal Q&A Core
**Goal:** Deliver Vietnamese-first source-backed legal Q&A with visible citations and practical legal guidance.

**Requirements:** CHAT-01, CHAT-03, CHAT-04, LEGAL-01, LEGAL-02, LEGAL-03, LEGAL-04

**UI hint:** yes

**Success criteria:**
1. A user can ask a Vietnam traffic-law question in Vietnamese and receive a relevant answer grounded in retrieved content.
2. Answers display source references clearly enough for user and admin verification.
3. Answers include the relevant legal basis when the source supports it.
4. Answers include likely penalty/consequence and required documents/procedure when relevant.
5. Answers include a clear informational-guidance disclaimer and recommended next steps.

### Phase 3: Multi-turn Case Analysis
**Goal:** Support realistic traffic-law scenario analysis with conversation continuity and clarifying-question behavior.

**Requirements:** CHAT-02, CASE-01, CASE-02, CASE-03, CASE-04

**UI hint:** yes

**Plans:** 3 plans

Plans:
- [ ] 03-01-PLAN.md — Thread/persistence foundation, Wave 0 test scaffolds, and thread-aware REST contract
- [ ] 03-02-PLAN.md — Explicit-fact memory and clarification-needed orchestration with correction-wins behavior
- [ ] 03-03-PLAN.md — Grounded final scenario composition, regression lock, and end-to-end workflow verification

**Success criteria:**
1. Users can continue a multi-turn thread without losing the key facts of the case.
2. The system can analyze a real-life traffic scenario rather than only direct FAQ-style questions.
3. The system detects when critical facts are missing and avoids overconfident conclusions.
4. The system asks clarifying follow-up questions before finalizing ambiguous scenario guidance.
5. Scenario responses follow a consistent structure covering facts, rule, consequence, action, and sources.

### Phase 4: Next.js Chat & Admin App
**Goal:** Build the combined sidebar-style Next.js application for chat and core admin operations.

**Requirements:** PLAT-02, ADMIN-01, ADMIN-02, ADMIN-03, ADMIN-06

**UI hint:** yes

**Plans:** 7 plans

Plans:
- [ ] 04-01-PLAN.md — AiParameterSet backend: entity, Liquibase migration, CRUD REST API, default seed, thread list endpoint
- [ ] 04-02-PLAN.md — Refactor hardcoded AI settings to read from active parameter set + CORS config
- [ ] 04-03-PLAN.md — Frontend scaffold: Next.js 16 project, shadcn/ui init, TypeScript types, API client, test infra
- [ ] 04-04-PLAN.md — Sidebar shell, thread list component, route group layouts
- [ ] 04-05-PLAN.md — Chat interface: message bubbles, scenario accordion, chat input, page wiring
- [ ] 04-06-PLAN.md — Source management DataTable + Knowledge index inspection cards
- [ ] 04-07-PLAN.md — AI parameter sets admin screen + E2E visual verification checkpoint

**Success criteria:**
1. The frontend is implemented in Next.js and supports the v1 chat workflow end-to-end.
2. The application includes one sidebar-style shell combining chat and admin screens.
3. Admins can view/manage ingested sources from the app.
4. Admins can inspect vector-store/indexed content and manage AI parameter sets.
5. Frontend flows are wired to the Spring REST backend rather than duplicating backend logic in Node.

### Phase 4.1: Backend Hardening, ETL Maturation & Use-Case Architecture (INSERTED)
**Goal:** Strengthen the backend operations layer based on a gap analysis against JHipster conventions, complete the Spring AI ETL pipeline, and shape the architecture for legal use-case management — without locking ETL or use-case design prematurely.

**Requirements:** PLAT-01, KNOW-01, KNOW-02, KNOW-03, KNOW-04
**Depends on:** Phase 4

**Tracks:**

**Track A — Backend operational hardening** (concrete):
- AOP-based `LoggingAspect` for service/repository/controller exception logging and debug-level method tracing, dev-profile-gated
- `AsyncConfig` upgraded to implement `AsyncConfigurer` with an explicit `getAsyncUncaughtExceptionHandler()` to surface silent `@Async` failures from ingestion tasks
- Typed `AppProperties` `@ConfigurationProperties` bean to centralize `app.*` config, replacing scattered `@Value` injection
- `GlobalExceptionHandler` hardening: `DataAccessException` → 500 with masked details in prod, `ConstraintViolationException` → 400, 404 for unmapped paths, profile-aware error detail suppression
- CRLF log injection prevention in log output

**Track B — ETL maturation** (partially concrete; final design open for discussion):
- Complete Phase 01.1-04: promote Spring AI ETL/readers to the primary ingestion path with token splitting and preserved metadata
- Identify what real trusted Vietnamese legal sources should be batch-imported and sketch the ingestion shape
- Leave dedup, refresh, and scheduling strategy open for a dedicated discussion before locking the design

**Track C — Use-case architecture exploration** (discussion-first, no implementation lock):
- Define what "legal use cases" means in the chatbot context and what documents they should be extracted from
- Explore storage options: YAML files vs. DB table vs. hybrid
- Sketch a review/approval flow for extracted or authored use cases
- Explore runtime retrieval strategy and tool/function-call integration approach
- Produce an architecture decision record capturing options and trade-offs, not a fixed implementation plan

**Plans:** TBD (planned in /gsd-plan-phase 4.1)

**Success criteria:**
1. The backend emits structured exception logs from service and repository layers without requiring per-method try/catch.
2. Async ingestion failures are surfaced in logs rather than silently dropped.
3. All `app.*` configuration properties are centralized in a typed `AppProperties` bean.
4. The exception handler handles `DataAccessException`, validation, and unmapped-path errors with appropriate status codes and prod-safe detail masking.
5. Spring AI ETL/readers are the primary ingestion path with token-based chunking and full provenance metadata.
6. A written architecture summary documents the options explored for use-case management and retrieval, including storage model, approval flow, and runtime retrieval strategy.

### Phase 5: Quality Operations & Evaluation
**Goal:** Make the system safe to operate and improve through observable logs and repeatable answer checks.

**Requirements:** ADMIN-04, ADMIN-05

**UI hint:** yes

**Success criteria:**
1. Admins can review chat logs for past conversations and investigate answer quality.
2. Admins can define and run answer checks against the chatbot.
3. Quality workflows can identify regressions after source or parameter changes.
4. The admin interface supports ongoing iteration without bypassing provenance and retrieval visibility.

## Coverage Validation

| Category | Total | Mapped |
|----------|-------|--------|
| Chat Foundation | 4 | 4 |
| Legal Guidance | 4 | 4 |
| Case Analysis | 4 | 4 |
| Knowledge Sources | 6 | 6 |
| Admin Operations | 6 | 6 |
| Platform | 4 | 4 |
| **Total** | **28** | **28** |

**Unmapped requirements:** 0 ✓

## Sequencing Rationale

1. **Foundation before intelligence** — source ingestion, provenance, and retrieval controls must exist before trustworthy legal answers.
2. **Grounded Q&A before scenario complexity** — direct cited answers should work before multi-turn case analysis.
3. **Case analysis before polish-heavy admin expansion** — real scenario handling is central to the product promise.
4. **Unified app after backend capabilities stabilize** — the Next.js shell should sit on top of working REST capabilities.
5. **Quality ops last, but not optional** — logs and answer checks complete the Jmix-like operational loop.

---
*Last updated: 2026-04-11 after inserting Phase 4.1 — backend hardening, ETL maturation, use-case architecture*
