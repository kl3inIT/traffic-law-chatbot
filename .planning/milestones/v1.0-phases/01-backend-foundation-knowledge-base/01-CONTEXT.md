# Phase 1: Backend Foundation & Knowledge Base - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish the Java 25 Spring REST backend foundation, PostgreSQL + pgvector persistence, source registry, ingestion pipelines, provenance tracking, and trusted-source retrieval controls for the Vietnam Traffic Law Chatbot. This phase sets up the admin/backend knowledge-base backbone; full chat answering, multi-turn analysis, and the Next.js admin/chat UI belong to later phases.

</domain>

<decisions>
## Implementation Decisions

### Ingestion flow
- **D-01:** Source ingestion uses an async pipeline. Source creation returns quickly, then parsing/chunking/embedding proceeds in background processing tracked by jobs.
- **D-02:** At source creation time, capture baseline provenance only: source origin, source type, title/name, and uploader or import trigger.
- **D-03:** Trusted website ingestion in Phase 1 is page-by-page URL import, not broad crawling.

### Source trust rules
- **D-04:** New sources start in a draft inactive state.
- **D-05:** Production retrieval is hard-filtered to sources that are both active and trusted.
- **D-06:** A source becomes retrieval-eligible only through explicit manual admin approval.

### Storage model
- **D-07:** Phase 1 uses PostgreSQL as the primary relational store and pgvector for embeddings/vector search.
- **D-08:** Embedded chunk records must keep source linkage plus location hints (such as page, section, or URL fragment) and processing version.
- **D-09:** Ingestion processing is tracked with first-class job records including status, timestamps, and errors.

### REST API surface
- **D-10:** Phase 1 must expose source CRUD endpoints for source records and provenance metadata management.
- **D-11:** Phase 1 must expose ingestion endpoints for uploads/URLs, processing triggers, and job/source status inspection.
- **D-12:** Phase 1 must expose chunk/index inspection endpoints for admin/backend visibility into indexed knowledge content.

### Claude's Discretion
- Whether retrieval-test-only APIs are needed in Phase 1 for validation can be decided during research/planning.
- Exact endpoint shapes, DTO boundaries, and job-state enum naming are open for planning.
- Exact parser/chunker/embedding orchestration implementation is open for planning.

</decisions>

<specifics>
## Specific Ideas

- Legal-source safety should win over convenience: ingestion is asynchronous, but retrieval eligibility is gated by manual approval.
- Website ingestion should stay narrow and reviewable in v1: import specific trusted pages rather than introducing crawler behavior.
- Phase 1 API scope should focus on source management, ingestion operations, and indexed-content inspection rather than premature chat-facing endpoints.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase definition
- `.planning/ROADMAP.md` — Defines Phase 1 goal, requirements, and success criteria.
- `.planning/REQUIREMENTS.md` — Defines KNOW-01..06 and PLAT-01/03/04 constraints for this phase.
- `.planning/PROJECT.md` — Defines REST-first Spring alignment, Jmix-ai-backend preservation goals, shoes-backend structural alignment, and Vietnamese-first trusted-source constraints.
- `.planning/STATE.md` — Confirms current phase and priority order for backend foundation work.

### Existing backend foundation
- `build.gradle` — Current backend stack includes Java 25, Spring Boot, Spring Data JPA, Spring AI, PostgreSQL, and pgvector dependencies.
- `src/main/java/com/vn/traffic/chatbot/TrafficLawChatbotApplication.java` — Current Spring Boot application entrypoint.
- `src/main/resources/application.yaml` — Current application configuration file placeholder.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `build.gradle`: Already establishes Java 25, Spring Boot 4, Spring Data JPA, PostgreSQL, Spring AI, and pgvector as the implementation baseline.
- `src/main/java/com/vn/traffic/chatbot/TrafficLawChatbotApplication.java`: Existing Spring Boot bootstrap class for wiring future REST/backend modules.

### Established Patterns
- The codebase is still at foundation stage: no domain modules, controllers, services, entities, or ingestion pipelines exist yet.
- Current dependency choices strongly favor a PostgreSQL + Spring Data JPA + pgvector design over introducing separate storage stacks in Phase 1.
- Because no admin/chat API structure exists yet, planner should define a clean REST-first backend slice aligned with project instructions.

### Integration Points
- New Phase 1 work will connect through the Spring Boot application module under `src/main/java/com/vn/traffic/chatbot/`.
- Persistence and ingestion configuration will connect through `build.gradle` and `src/main/resources/application.yaml`.
- Future chat and admin phases will build on the source registry, chunk/index records, trust controls, and ingestion jobs created here.

</code_context>

<deferred>
## Deferred Ideas

- Broad website crawling/seed-based crawl behavior is out of scope for Phase 1.
- Chat-facing retrieval test APIs are not locked for this phase and can be added later only if research/planning shows they are necessary.
- Full chat answering, multi-turn analysis, and Next.js admin/chat UI remain in later roadmap phases.

</deferred>

---

*Phase: 01-backend-foundation-knowledge-base*
*Context gathered: 2026-04-07*
