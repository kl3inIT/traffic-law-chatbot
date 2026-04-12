# Architecture

**Analysis Date:** 2026-04-11

## Pattern Overview

**Overall:** Full-stack split monorepo with a Spring Boot modular monolith backend and a Next.js App Router frontend.

**Key Characteristics:**
- `src/main/java/com/vn/traffic/chatbot/` is organized by business slice (`chat`, `ingestion`, `source`, `parameter`, `chunk`, `retrieval`) rather than by technical tier alone.
- `frontend/` is a separate web app that calls the backend directly over REST; no Next.js route handlers or server-side BFF layer were detected under `frontend/app/`.
- Persistence is intentionally split between relational tables managed through JPA/Liquibase and a pgvector document store accessed through Spring AI `VectorStore` plus direct `JdbcTemplate` queries.

## Layers

**Frontend Route Shell:**
- Purpose: Own browser entrypoints, layouts, loading states, and route grouping for chat and admin screens.
- Location: `frontend/app/`
- Contains: `frontend/app/layout.tsx`, `frontend/app/error.tsx`, `frontend/app/loading.tsx`, `frontend/app/(chat)/page.tsx`, `frontend/app/(chat)/threads/[id]/page.tsx`, `frontend/app/(admin)/index/page.tsx`, `frontend/app/(admin)/sources/page.tsx`, `frontend/app/(admin)/parameters/page.tsx`
- Depends on: `frontend/components/layout/`, `frontend/components/chat/`, `frontend/components/admin/`, `frontend/hooks/`, `frontend/types/api.ts`
- Used by: Browser requests to `/`, `/threads/[id]`, `/sources`, `/index`, and `/parameters`

**Frontend Feature UI And Client Data Layer:**
- Purpose: Keep pages thin by separating display components, query orchestration, and HTTP transport.
- Location: `frontend/components/`, `frontend/hooks/`, `frontend/lib/api/`, `frontend/lib/query-keys.ts`, `frontend/types/api.ts`
- Contains: `frontend/components/chat/message-bubble.tsx`, `frontend/components/chat/scenario-accordion.tsx`, `frontend/components/admin/sources/add-source-dialog.tsx`, `frontend/components/admin/index/index-chunk-table.tsx`, `frontend/hooks/use-chat.ts`, `frontend/hooks/use-sources.ts`, `frontend/lib/api/chat.ts`, `frontend/lib/api/sources.ts`
- Depends on: `@tanstack/react-query`, `axios`, and shared UI primitives in `frontend/components/ui/`
- Used by: Route files under `frontend/app/`

**REST API Layer:**
- Purpose: Expose HTTP contracts and map requests onto backend services.
- Location: `src/main/java/com/vn/traffic/chatbot/*/api/`
- Contains: `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java`, `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java`, `src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java`, `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java`, plus DTOs in sibling `dto/` packages
- Depends on: Service packages and `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java`
- Used by: `frontend/lib/api/*.ts` and any external REST caller

**Application Service Layer:**
- Purpose: Implement chat orchestration, ingestion setup, approval transitions, parameter management, and vector-index inspection.
- Location: `src/main/java/com/vn/traffic/chatbot/*/service/` and `src/main/java/com/vn/traffic/chatbot/retrieval/`
- Contains: `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`, `src/main/java/com/vn/traffic/chatbot/parameter/service/AiParameterSetService.java`, `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java`, `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java`
- Depends on: Repositories, Spring AI `ChatClient`, Spring AI `VectorStore`, parser/fetch infrastructure, and `JdbcTemplate`
- Used by: Controllers and background jobs

**Persistence And Domain Layer:**
- Purpose: Persist knowledge sources, ingestion jobs, chat state, thread facts, and runtime parameter sets.
- Location: `src/main/java/com/vn/traffic/chatbot/*/domain/` and `src/main/java/com/vn/traffic/chatbot/*/repo/`
- Contains: `src/main/java/com/vn/traffic/chatbot/source/domain/KbSource.java`, `src/main/java/com/vn/traffic/chatbot/source/domain/KbSourceVersion.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/domain/KbIngestionJob.java`, `src/main/java/com/vn/traffic/chatbot/chat/domain/ChatThread.java`, `src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessage.java`, `src/main/java/com/vn/traffic/chatbot/chat/domain/ThreadFact.java`, `src/main/java/com/vn/traffic/chatbot/parameter/domain/AiParameterSet.java`
- Depends on: Spring Data JPA, Hibernate, PostgreSQL JSON/UUID support
- Used by: Service classes and the async ingestion pipeline

**Parsing And Indexing Infrastructure:**
- Purpose: Normalize external source material into a shared parsed representation, split it into chunks, and index it into `kb_vector_store`.
- Location: `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/`, `src/main/java/com/vn/traffic/chatbot/ingestion/chunking/`, `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java`
- Contains: `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/UrlPageParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/FileIngestionParserResolver.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/PdfDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/HtmlDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/chunking/TokenChunkingService.java`
- Depends on: Jsoup, Spring AI readers, Apache Tika, Java `HttpClient`, and pgvector
- Used by: `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`

## Frontend/Backend Boundary

**Boundary:** The browser-facing app in `frontend/` talks to the Spring API over HTTP. Backend code is not imported into the frontend, and the frontend does not proxy requests through Next.js route handlers.

**Rules:**
- Use `frontend/lib/api/client.ts` as the transport entrypoint for REST calls. It reads `NEXT_PUBLIC_API_BASE_URL` and otherwise falls back to `http://localhost:8088`.
- Keep transport contracts aligned by updating Java DTOs such as `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java` together with frontend types in `frontend/types/api.ts`.
- Add new backend routes through `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java` and expose them to the frontend through a matching file in `frontend/lib/api/`.
- Cross-origin browser access is allowed by `src/main/java/com/vn/traffic/chatbot/common/config/CorsConfig.java`; there is no server-side session or auth boundary inside `frontend/`.

## Data Flow

**Threaded Chat Flow:**

1. `frontend/app/(chat)/page.tsx` creates a thread, and `frontend/app/(chat)/threads/[id]/page.tsx` posts follow-up messages through `frontend/hooks/use-chat.ts` and `frontend/lib/api/chat.ts`.
2. `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java` delegates thread creation and message posting to `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java`.
3. `ChatThreadService` writes `ChatThread` and `ChatMessage` rows through `src/main/java/com/vn/traffic/chatbot/chat/repo/ChatThreadRepository.java` and `src/main/java/com/vn/traffic/chatbot/chat/repo/ChatMessageRepository.java`, then asks `src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java` to upsert `ThreadFact` rows.
4. `src/main/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicy.java` decides whether the thread can continue, should ask for more facts, or should refuse after the clarification budget is exhausted.
5. When the request is answerable, `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` builds a vector `SearchRequest` through `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java`, queries the configured `VectorStore`, maps citations through `src/main/java/com/vn/traffic/chatbot/chat/citation/CitationMapper.java`, and invokes the Spring AI `ChatClient` configured in `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java`.
6. `src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ScenarioAnswerComposer.java`, and `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadMapper.java` convert the model draft plus remembered facts into the final `ChatAnswerResponse`, which the frontend renders in `frontend/components/chat/message-bubble.tsx` and `frontend/components/chat/scenario-accordion.tsx`.

**Source Ingestion And Activation Flow:**

1. `frontend/components/admin/sources/add-source-dialog.tsx` sends either multipart upload or URL import requests through `frontend/lib/api/ingestion.ts`.
2. `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java` hands the request to `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, which validates input, creates `KbSource`, `KbSourceVersion`, and `KbIngestionJob` records, and schedules background work after the surrounding transaction commits.
3. `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java` runs asynchronously on the `ingestionExecutor` defined in `src/main/java/com/vn/traffic/chatbot/common/config/AsyncConfig.java`.
4. The orchestrator fetches remote HTML with `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java` or reads uploaded files, parses content through `src/main/java/com/vn/traffic/chatbot/ingestion/parser/UrlPageParser.java` or `src/main/java/com/vn/traffic/chatbot/ingestion/parser/FileIngestionParserResolver.java`, then splits it with `src/main/java/com/vn/traffic/chatbot/ingestion/chunking/TokenChunkingService.java`.
5. The orchestrator writes vector documents into `kb_vector_store` through the Spring AI `VectorStore` configured in `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java`, embedding governance metadata such as `approvalState`, `trusted`, and `active`.
6. `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java` later approves, rejects, activates, or deactivates a source and synchronizes vector metadata through `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkMetadataUpdater.java`, which makes the chunks eligible or ineligible for retrieval.

**Admin Inspection And Runtime Parameter Flow:**

1. Admin pages such as `frontend/app/(admin)/index/page.tsx` and `frontend/app/(admin)/parameters/page.tsx` call hooks in `frontend/hooks/use-index.ts` and `frontend/hooks/use-parameters.ts`.
2. `src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java` exposes index state through `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java`, which queries `kb_vector_store` directly with `JdbcTemplate` instead of JPA.
3. `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java` persists YAML parameter sets through `src/main/java/com/vn/traffic/chatbot/parameter/service/AiParameterSetService.java`.
4. Runtime chat components such as `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicy.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/AnswerCompositionPolicy.java`, and `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java` read the active YAML through `src/main/java/com/vn/traffic/chatbot/parameter/service/ActiveParameterSetProvider.java`, so admin edits affect later chat requests without a redeploy.

**State Management:**
- Browser state lives mostly in TanStack Query caches created by `frontend/components/layout/providers.tsx`, with page-local `useState` for transient UI state.
- Backend state lives in PostgreSQL tables declared by Liquibase changelogs under `src/main/resources/db/changelog/`.
- Retrieval state is duplicated on purpose: source governance lives in relational tables, while chunk eligibility is mirrored into `kb_vector_store.metadata` for fast vector filtering.

## Key Abstractions

**Knowledge Source Lifecycle:**
- Purpose: Track the lifecycle from draft source to indexed, approved, trusted, active retrieval material.
- Examples: `src/main/java/com/vn/traffic/chatbot/source/domain/KbSource.java`, `src/main/java/com/vn/traffic/chatbot/source/domain/KbSourceVersion.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/domain/KbIngestionJob.java`
- Pattern: One relational source record can have version and job records, while vector documents store only the denormalized metadata needed at retrieval time.

**Conversation Memory:**
- Purpose: Preserve thread history and derived facts across multi-turn case analysis.
- Examples: `src/main/java/com/vn/traffic/chatbot/chat/domain/ChatThread.java`, `src/main/java/com/vn/traffic/chatbot/chat/domain/ChatMessage.java`, `src/main/java/com/vn/traffic/chatbot/chat/domain/ThreadFact.java`
- Pattern: User messages are persisted first, facts are extracted and upserted, then assistant responses are stored with a serialized `structured_response` payload.

**Parsed Document Contract:**
- Purpose: Give every parser the same output shape before chunking and indexing.
- Examples: `src/main/java/com/vn/traffic/chatbot/ingestion/parser/ParsedDocument.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/PdfDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/HtmlDocumentParser.java`
- Pattern: Parser implementations convert source-specific input into `ParsedDocument.PageSection` records with `pageNumber`, `sectionRef`, and normalized text.

**Runtime Parameter Set:**
- Purpose: Externalize retrieval thresholds, clarification requirements, disclaimers, and system prompt fragments into editable YAML.
- Examples: `src/main/java/com/vn/traffic/chatbot/parameter/service/ActiveParameterSetProvider.java`, `src/main/resources/default-parameter-set.yml`, `frontend/app/(admin)/parameters/page.tsx`
- Pattern: Parameter sets are CRUD-managed through the admin API, one record is active, and consumers read values on demand by dot-path.

**Vector Metadata Bridge:**
- Purpose: Keep relational source governance and retrieval filters aligned.
- Examples: `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`, `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkMetadataUpdater.java`, `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java`
- Pattern: Ingestion writes conservative chunk metadata first, then source transitions mutate JSON metadata in place so retrieval can filter entirely inside the vector store.

## Entry Points

**Backend Bootstrap:**
- Location: `src/main/java/com/vn/traffic/chatbot/TrafficLawChatbotApplication.java`
- Triggers: JVM startup through `gradlew.bat bootRun`, packaged jar execution, or tests that load the Spring context
- Responsibilities: Boot the Spring application and enable asynchronous execution

**Public Chat API:**
- Location: `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java`
- Triggers: `POST /api/v1/chat`, `GET /api/v1/chat/threads`, `POST /api/v1/chat/threads`, and `GET/POST /api/v1/chat/threads/{threadId}/messages`
- Responsibilities: Accept public question and thread requests and return `ChatAnswerResponse` or message-history DTOs

**Admin Source And Ingestion APIs:**
- Location: `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java` and `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java`
- Triggers: Admin REST calls from the frontend sources screen or external operators
- Responsibilities: Manage sources, list per-source ingestion jobs, queue uploads or URLs, and inspect, retry, or cancel jobs

**Admin Index And Parameter APIs:**
- Location: `src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java` and `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java`
- Triggers: Admin dashboard and parameter editor calls
- Responsibilities: Expose chunk inspection/readiness metrics and CRUD plus activation for YAML parameter sets

**Frontend Shell:**
- Location: `frontend/app/layout.tsx`
- Triggers: Every page load in the Next.js app
- Responsibilities: Mount `Providers`, `SidebarProvider`, `AppSidebar`, and the top-level `ErrorBoundary`

**Frontend Route Entries:**
- Location: `frontend/app/(chat)/page.tsx`, `frontend/app/(chat)/threads/[id]/page.tsx`, `frontend/app/(admin)/sources/page.tsx`, `frontend/app/(admin)/index/page.tsx`, `frontend/app/(admin)/parameters/page.tsx`
- Triggers: Browser navigation to `/`, `/threads/[id]`, `/sources`, `/index`, and `/parameters`
- Responsibilities: Compose feature-specific components and bind them to React Query hooks

## Background Jobs

**Async Ingestion Worker:**
- Location: `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`
- Trigger: `IngestionService.schedulePipelineAfterCommit(...)` after a source/version/job transaction commits
- Execution model: `@Async("ingestionExecutor")` using the thread pool bean from `src/main/java/com/vn/traffic/chatbot/common/config/AsyncConfig.java`
- Responsibilities: Run `FETCH -> PARSE -> CHUNK -> EMBED -> INDEX -> FINALIZE`, update job state, store fetch provenance, and persist parser and chunking metadata on the source version

**Startup Seeding Job:**
- Location: `src/main/java/com/vn/traffic/chatbot/parameter/service/DefaultParameterSetSeeder.java`
- Trigger: Spring Boot startup through `ApplicationRunner`
- Execution model: Synchronous during application boot
- Responsibilities: Load `src/main/resources/default-parameter-set.yml` and create the first active `AiParameterSet` when the table is empty

**Recurring Jobs:**
- Location: Not detected
- Trigger: Not applicable
- Responsibilities: No `@Scheduled` jobs or external queue consumers were found under `src/main/java/`

## Error Handling

**Strategy:** Fail fast for malformed admin inputs, surface REST errors as `ProblemDetail`, and prefer explicit chat refusal responses over exceptions when retrieval grounding is insufficient.

**Patterns:**
- `src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java` maps `AppException`, bean validation failures, type mismatches, and unexpected exceptions into HTTP responses.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` returns a structured `REFUSED` answer when vector search returns no usable grounded citations.
- `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java` catches async pipeline failures, marks the job `FAILED`, and stores the error on the job record instead of throwing back to the caller.
- `frontend/components/layout/query-boundary.tsx` and `frontend/app/error.tsx` centralize loading and error rendering on the web client.

## Cross-Cutting Concerns

**Logging:** Lombok `@Slf4j` is used across controllers and services such as `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, and `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`.

**Validation:** Jakarta validation is applied at controller DTO boundaries, source and job state transitions are enforced in services such as `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`, and URL SSRF protections live in `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`.

**Authentication:** Not detected. No Spring Security configuration or Next.js auth layer was found; `src/main/java/com/vn/traffic/chatbot/common/config/CorsConfig.java` only controls cross-origin access.

---

*Architecture analysis: 2026-04-11*
