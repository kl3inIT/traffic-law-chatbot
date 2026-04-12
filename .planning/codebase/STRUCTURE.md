# Codebase Structure

**Analysis Date:** 2026-04-11

## Directory Layout

```text
traffic-law-chatbot/
├── src/
│   ├── main/java/com/vn/traffic/chatbot/   # Spring Boot application code by feature slice
│   ├── main/resources/                     # app config, seeded YAML, Liquibase changelogs
│   └── test/java/com/vn/traffic/chatbot/   # backend tests mirroring production packages
├── frontend/
│   ├── app/                                # Next.js App Router entrypoints
│   ├── components/                         # shared UI and feature-specific components
│   ├── hooks/                              # React Query hooks and client-side adapters
│   ├── lib/api/                            # axios REST clients for backend endpoints
│   ├── types/                              # shared frontend DTO typings
│   ├── __tests__/                          # Vitest component and smoke tests
│   └── e2e/                                # Playwright browser tests
├── docs/                                   # project documentation
├── .planning/                              # planning state and generated maps
├── build.gradle                            # backend build and dependency graph
├── compose.yaml                            # local compose stub, currently commented out
└── README.md                               # repo-level run and usage guide
```

## Directory Purposes

**`src/main/java/com/vn/traffic/chatbot/chat`:**
- Purpose: Public chat API, thread memory, citation mapping, clarification, and answer composition.
- Contains: `api/`, `citation/`, `config/`, `domain/`, `repo/`, `service/`
- Key files: `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java`

**`src/main/java/com/vn/traffic/chatbot/ingestion`:**
- Purpose: Accept source material, create jobs, parse content, chunk text, and index embeddings.
- Contains: `api/`, `chunking/`, `domain/`, `fetch/`, `orchestrator/`, `parser/`, `repo/`, `service/`
- Key files: `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/FileIngestionParserResolver.java`

**`src/main/java/com/vn/traffic/chatbot/source`:**
- Purpose: Knowledge source registry, approval lifecycle, activation, and approval audit trail.
- Contains: `api/`, `domain/`, `repo/`, `service/`
- Key files: `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`, `src/main/java/com/vn/traffic/chatbot/source/domain/KbSource.java`, `src/main/java/com/vn/traffic/chatbot/source/domain/KbSourceVersion.java`

**`src/main/java/com/vn/traffic/chatbot/parameter`:**
- Purpose: Store and activate YAML parameter sets that drive runtime chat behavior.
- Contains: `api/`, `domain/`, `repo/`, `service/`
- Key files: `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java`, `src/main/java/com/vn/traffic/chatbot/parameter/service/ActiveParameterSetProvider.java`, `src/main/java/com/vn/traffic/chatbot/parameter/service/DefaultParameterSetSeeder.java`

**`src/main/java/com/vn/traffic/chatbot/chunk`:**
- Purpose: Inspect vector index contents and mutate vector-store metadata when source state changes.
- Contains: `api/`, `service/`
- Key files: `src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java`, `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java`, `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkMetadataUpdater.java`

**`src/main/java/com/vn/traffic/chatbot/retrieval`:**
- Purpose: Centralize vector search request construction and the eligibility filter used by chat retrieval.
- Contains: `RetrievalPolicy.java`
- Key files: `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java`

**`src/main/java/com/vn/traffic/chatbot/common`:**
- Purpose: Shared backend infrastructure and cross-feature utilities.
- Contains: `api/`, `config/`, `error/`
- Key files: `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java`, `src/main/java/com/vn/traffic/chatbot/common/config/CorsConfig.java`, `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java`, `src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java`

**`frontend/app`:**
- Purpose: Route tree and shell for the web app.
- Contains: `layout.tsx`, `error.tsx`, `loading.tsx`, and route groups `'(chat)'` and `'(admin)'`
- Key files: `frontend/app/layout.tsx`, `frontend/app/(chat)/page.tsx`, `frontend/app/(chat)/threads/[id]/page.tsx`, `frontend/app/(admin)/sources/page.tsx`

**`frontend/components`:**
- Purpose: Shared UI primitives and feature components.
- Contains: `admin/`, `ai-elements/`, `chat/`, `layout/`, `ui/`
- Key files: `frontend/components/layout/app-sidebar.tsx`, `frontend/components/chat/message-bubble.tsx`, `frontend/components/admin/sources/add-source-dialog.tsx`, `frontend/components/ui/sidebar.tsx`

**`frontend/hooks`:**
- Purpose: React Query wrappers over REST clients and page-facing state adapters.
- Contains: Hook modules named by feature
- Key files: `frontend/hooks/use-chat.ts`, `frontend/hooks/use-sources.ts`, `frontend/hooks/use-index.ts`, `frontend/hooks/use-parameters.ts`, `frontend/hooks/use-threads.ts`

**`frontend/lib/api`:**
- Purpose: HTTP client adapters for backend endpoints.
- Contains: Per-feature REST helpers and the shared axios client
- Key files: `frontend/lib/api/client.ts`, `frontend/lib/api/chat.ts`, `frontend/lib/api/sources.ts`, `frontend/lib/api/chunks.ts`, `frontend/lib/api/parameters.ts`, `frontend/lib/api/ingestion.ts`

**`src/main/resources`:**
- Purpose: Runtime config and schema evolution.
- Contains: `application.yaml`, `application.properties`, `default-parameter-set.yml`, `db/changelog/*`
- Key files: `src/main/resources/application.yaml`, `src/main/resources/default-parameter-set.yml`, `src/main/resources/db/changelog/db.changelog-master.xml`

**`src/test/java` and `frontend/__tests__`:**
- Purpose: Package-aligned backend tests and frontend unit/component tests.
- Contains: Backend tests mirroring module packages, frontend Vitest tests, and Playwright smoke tests in `frontend/e2e/`
- Key files: `src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java`, `src/test/java/com/vn/traffic/chatbot/ingestion/service/IngestionServiceTest.java`, `frontend/__tests__/smoke.test.tsx`, `frontend/e2e/smoke.spec.ts`

## Key File Locations

**Entry Points:**
- `src/main/java/com/vn/traffic/chatbot/TrafficLawChatbotApplication.java`: Spring Boot bootstrap and async enablement
- `frontend/app/layout.tsx`: global web-app shell
- `frontend/app/(chat)/page.tsx`: new-thread page at `/`
- `frontend/app/(chat)/threads/[id]/page.tsx`: existing-thread page at `/threads/[id]`

**Configuration:**
- `build.gradle`: backend dependencies and runtime stack
- `frontend/package.json`: frontend scripts and web dependencies
- `src/main/resources/application.yaml`: backend ports, datasource, Spring AI, task executor, and chat settings
- `src/main/resources/application.properties`: CORS override defaults
- `frontend/tsconfig.json`: frontend path alias `@/*`
- `frontend/components.json`: shadcn/ui aliases and styling registry config

**Core Logic:**
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`: retrieval, model call, and answer composition
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java`: threaded conversation orchestration
- `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`: async ingestion pipeline
- `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`: source governance transitions
- `src/main/java/com/vn/traffic/chatbot/parameter/service/ActiveParameterSetProvider.java`: runtime YAML lookup
- `frontend/components/chat/message-bubble.tsx`: structured chat rendering
- `frontend/components/admin/index/index-chunk-table.tsx`: admin index inspection table

**Testing:**
- `src/test/java/com/vn/traffic/chatbot/`: backend tests by feature
- `frontend/__tests__/`: Vitest tests
- `frontend/e2e/`: Playwright smoke coverage

## Naming Conventions

**Files:**
- Java production files use one `PascalCase` type per file, matching class and record names such as `ChatService.java` and `KbIngestionJob.java`.
- Backend package directories use lowercase feature names such as `chat`, `ingestion`, `source`, `parameter`, `chunk`, `common`, and `retrieval`.
- Next.js route files use framework names such as `page.tsx`, `layout.tsx`, `loading.tsx`, and `error.tsx`.
- Frontend hooks use `use-*.ts` filenames such as `use-chat.ts` and `use-sources.ts`.
- Frontend REST clients are grouped by resource in `frontend/lib/api/*.ts`.

**Directories:**
- Under each backend feature, place controller DTOs in `api/dto`, persistence entities in `domain`, Spring Data interfaces in `repo`, and orchestration code in `service`.
- Frontend components are grouped first by area (`admin`, `chat`, `layout`, `ui`, `ai-elements`) and then by screen or widget concern, for example `frontend/components/admin/sources/`.
- Route groups in `frontend/app` use parentheses when they should not appear in the URL, for example `frontend/app/(chat)` and `frontend/app/(admin)`.

## Where to Add New Code

**New Backend Feature:**
- Primary code: Create a new package under `src/main/java/com/vn/traffic/chatbot/<feature>/`
- Primary code: Follow the existing subpackage split `api/`, `domain/`, `repo/`, `service/`
- Tests: Mirror the package under `src/test/java/com/vn/traffic/chatbot/<feature>/`
- Persistence changes: Add a new Liquibase changelog under `src/main/resources/db/changelog/` and include it from `src/main/resources/db/changelog/db.changelog-master.xml`

**New Chat Or Ingestion Behavior:**
- Chat orchestration: extend `src/main/java/com/vn/traffic/chatbot/chat/service/`
- Retrieval policy: extend `src/main/java/com/vn/traffic/chatbot/retrieval/`
- Source lifecycle or approval rules: extend `src/main/java/com/vn/traffic/chatbot/source/service/`
- Parser or fetcher work: extend `src/main/java/com/vn/traffic/chatbot/ingestion/parser/` or `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/`

**New Frontend Page Or Workflow:**
- Chat page: add route files under `frontend/app/(chat)/`
- Admin page: add route files under `frontend/app/(admin)/`
- Screen-specific components: place them under `frontend/components/<area>/<feature>/`
- Data fetching and mutations: add a hook under `frontend/hooks/` and a transport helper under `frontend/lib/api/`
- Shared DTO updates: extend `frontend/types/api.ts`

**New Component/Module:**
- Implementation: use `frontend/components/ui/` only for generic reusable primitives, `frontend/components/layout/` for shell-level pieces, and `frontend/components/chat/` or `frontend/components/admin/` for feature logic
- Implementation: keep page files thin by moving query state and display logic into components and hooks

**Utilities:**
- Shared frontend helpers: `frontend/lib/utils.ts` or `frontend/lib/query-keys.ts`
- Shared backend helpers or cross-feature config: `src/main/java/com/vn/traffic/chatbot/common/`
- Do not place feature-specific business logic in `common/`; keep it inside the owning feature package

## Special Directories

**`src/main/resources/db/changelog`:**
- Purpose: Versioned database schema history for sources, vector store, chat threads, and parameter sets
- Generated: No
- Committed: Yes

**`frontend/app/(chat)` and `frontend/app/(admin)`:**
- Purpose: URL-hidden route groups that separate end-user chat from operator/admin surfaces
- Generated: No
- Committed: Yes

**`build/`:**
- Purpose: Gradle build outputs, compiled classes, reports, and generated resources
- Generated: Yes
- Committed: No

**`frontend/.next/`:**
- Purpose: Next.js build output and incremental cache
- Generated: Yes
- Committed: No

**`.planning/`:**
- Purpose: Project planning state, research, and codebase maps consumed by GSD workflows
- Generated: No
- Committed: Yes

---

*Structure analysis: 2026-04-11*
