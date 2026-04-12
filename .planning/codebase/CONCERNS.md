# Codebase Concerns

**Analysis Date:** 2026-04-11

## Tech Debt

**Backend hardening backlog is already identified but still unimplemented:**
- Issue: the repo still lacks the Phase 4.1 hardening items already called out in `.planning/ROADMAP.md`: no `LoggingAspect`, no typed `AppProperties`, no `AsyncConfigurer` with uncaught async error handling, and no richer exception mapping.
- Files: `.planning/ROADMAP.md`, `src/main/java/com/vn/traffic/chatbot/common/config/AsyncConfig.java`, `src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`, `src/main/java/com/vn/traffic/chatbot/common/config/CorsConfig.java`
- Impact: operational behavior is spread across ad hoc `@Value` usage, async failures rely on local try/catch instead of a standard handler, and production-safe error handling is incomplete.
- Fix approach: implement Phase 4.1 Track A exactly where the roadmap already points: `AsyncConfigurer`, typed `@ConfigurationProperties`, structured exception logging, and expanded exception mapping.

**Spring AI ETL promotion is only partially complete:**
- Issue: `.planning/ROADMAP.md` still shows `01.1-04` incomplete, and the current parser boundary still mixes Spring AI readers with Tika fallback plus runtime-only unsupported-operation paths.
- Files: `.planning/ROADMAP.md`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/DocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/FileIngestionParserResolver.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/HtmlDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/PdfDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/chunking/TokenChunkingService.java`
- Impact: ingestion is harder to extend safely, parser/input mismatches fail at runtime, and the ETL story remains split across multiple seams instead of one primary path.
- Fix approach: finish `01.1-04`, separate URL/file parser contracts, and remove runtime unsupported-operation branches from the main ingestion path.

## Known Bugs

**Reingest does not queue a new ingestion job:**
- Symptoms: `POST /api/v1/admin/sources/{sourceId}/reingest` resets approval, status, trust, and chunk metadata, but never creates a new `KbSourceVersion` or `KbIngestionJob`.
- Files: `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`, `frontend/lib/api/sources.ts`
- Trigger: call the reingest endpoint from the UI or API.
- Workaround: submit a fresh upload or URL import instead of relying on `/reingest`.

**Cancelled ingestion jobs can still execute:**
- Symptoms: `cancelJob()` only flips status to `CANCELLED`, while `schedulePipelineAfterCommit()` still invokes `runPipeline(jobId)` and `runPipeline()` never checks for `CANCELLED` before fetch/parse/chunk/index work.
- Files: `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`
- Trigger: cancel a queued or running job, especially shortly after submission.
- Workaround: none in repo; treat cancellation as advisory state only.

**Frontend defaults to the wrong backend port:**
- Symptoms: the frontend falls back to `http://localhost:8088`, but the backend default is `8089`. The docs already warn that a manual override is required.
- Files: `frontend/lib/api/client.ts`, `src/main/resources/application.yaml`, `README.md`, `docs/GETTING-STARTED.md`, `docs/CONFIGURATION.md`, `docs/API.md`
- Trigger: start `frontend` without setting `NEXT_PUBLIC_API_BASE_URL`.
- Workaround: set `NEXT_PUBLIC_API_BASE_URL=http://localhost:8089` in `frontend/.env.local` or the shell before `pnpm dev`.

**Several admin endpoints expose partial or inaccurate contracts:**
- Symptoms: `status` is accepted but ignored by `listSources()`, `getSource()` always returns `versions: []`, and `ChunkInspectionService.getChunk()` throws `INGESTION_FAILED` when a chunk is missing, which the API docs record as a `500` instead of a `404`.
- Files: `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java`, `docs/API.md`
- Trigger: filter sources by status, request source detail, or fetch a missing chunk ID.
- Workaround: client-side filtering plus separate job/source inspection; there is no clean workaround for the incorrect missing-chunk status code.

## Security Considerations

**Admin mutation and inspection endpoints are unauthenticated and actor fields are spoofable:**
- Risk: anyone who can reach the API can create sources, upload files, approve/activate content, inspect indexed chunks, and change active AI parameter sets. `createdBy` and `actedBy` are caller-supplied strings, not bound to a real identity.
- Files: `build.gradle`, `docs/API.md`, `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java`, `src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java`, `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java`, `src/main/java/com/vn/traffic/chatbot/source/api/dto/ApprovalRequest.java`, `src/main/java/com/vn/traffic/chatbot/source/api/dto/CreateSourceRequest.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/api/dto/UrlSourceRequest.java`, `frontend/components/layout/app-sidebar.tsx`
- Current mitigation: `/api/v1/admin/*` naming convention, CORS origin filtering, and deployment-boundary trust only.
- Recommendations: add real authentication/authorization, role checks, server-side identity binding for audit fields, and UI gating for admin routes.

**Public chat history is exposed without access control:**
- Risk: `GET /api/v1/chat/threads` returns all saved threads and `GET /api/v1/chat/threads/{threadId}/messages` returns full histories. The frontend sidebar renders the shared thread list for any visitor.
- Files: `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java`, `frontend/components/chat/thread-list.tsx`, `frontend/lib/api/chat.ts`, `docs/API.md`
- Current mitigation: none in repo.
- Recommendations: scope threads to authenticated users or sessions, paginate list APIs, and remove global thread visibility from anonymous views.

**Trusted-source policy depends on manual discipline, not code-level authority controls:**
- Risk: URL ingestion accepts any public `http/https` host once SSRF checks pass. There is no domain allowlist, issuer validation, or freshness/effective-date policy to enforce "trusted legal content only."
- Files: `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`, `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`, `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`
- Current mitigation: manual approve/activate workflow and retrieval gating on `APPROVED` + `trusted=true` + `active=true`.
- Recommendations: add domain allowlists, authority metadata, freshness/effective-date fields, and stricter approval review before activation.

## Performance Bottlenecks

**Chunk inspection loads and parses full embeddings for paged list views:**
- Problem: `ChunkInspectionService.listChunks()` selects `embedding` for every row, parses the full vector, then keeps only a short preview. `getIndexSummary()` and `getRetrievalReadinessCounts()` also run multiple full-table count queries.
- Files: `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java`, `src/main/java/com/vn/traffic/chatbot/chunk/api/dto/ChunkSummaryResponse.java`, `src/main/java/com/vn/traffic/chatbot/chunk/api/dto/IndexSummaryResponse.java`
- Cause: admin inspection is implemented directly against `kb_vector_store` with no projection or cached aggregates.
- Improvement path: remove `embedding` from summary queries, compute readiness totals with one aggregate query, and cache or materialize admin summary metrics.

**Thread listing and follow-up handling reread whole histories:**
- Problem: `listThreads()` loads all threads and then fetches each thread's messages to derive the first user prompt. `countClarificationMessages()` and `buildRetrievalQuestion()` also reread full message histories on each turn.
- Files: `src/main/java/com/vn/traffic/chatbot/chat/service/ChatThreadService.java`, `src/main/java/com/vn/traffic/chatbot/chat/repo/ChatMessageRepository.java`, `src/main/java/com/vn/traffic/chatbot/chat/repo/ChatThreadRepository.java`
- Cause: thread summary fields are derived on demand instead of queried directly or stored.
- Improvement path: add paginated thread APIs, persist lightweight summary fields on `ChatThread`, and replace full-history scans with focused count/title queries.

**Active parameter lookup reparses YAML on every access:**
- Problem: `ActiveParameterSetProvider.getActiveParams()` fetches `findByActiveTrue()` and reparses YAML each time a service asks for a value.
- Files: `src/main/java/com/vn/traffic/chatbot/parameter/service/ActiveParameterSetProvider.java`, `src/main/java/com/vn/traffic/chatbot/parameter/repo/AiParameterSetRepository.java`, `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`
- Cause: no cache or immutable active-parameter snapshot exists.
- Improvement path: cache the active parameter set in memory and invalidate on create/update/activate.

## Fragile Areas

**Uploaded document storage is tied to local temp files:**
- Files: `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, `src/main/java/com/vn/traffic/chatbot/source/domain/KbSourceVersion.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`
- Why fragile: uploads are written with `Files.createTempFile(...)`, and only the OS path is stored in `storageUri`.
- Inference: retries, reingest, restarts, or multi-node deployment can fail once the temp file is cleaned up or lives on another machine.
- Safe modification: move to durable shared storage or a managed repo-owned upload directory, then add explicit lifecycle cleanup.
- Test coverage: no test exercises temp-file persistence across restarts or multiple instances.

**Parser/input compatibility is enforced only at runtime:**
- Files: `src/main/java/com/vn/traffic/chatbot/ingestion/parser/DocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/UrlPageParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/HtmlDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/PdfDocumentParser.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/parser/FileIngestionParserResolver.java`
- Why fragile: the same interface accepts both file and fetch-based parsing, and unsupported pairings compile but explode with `UnsupportedOperationException`.
- Safe modification: split URL and file parser interfaces or introduce typed adapters so unsupported pairings are impossible at compile time.
- Test coverage: happy-path parser tests exist, but the boundary itself remains runtime-only.

**Scenario memory depends on narrow regex extraction:**
- Files: `src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicy.java`, `src/main/java/com/vn/traffic/chatbot/chat/service/ScenarioAnswerComposer.java`
- Why fragile: thread memory is built from hand-written regex lists for a fixed set of vehicles, violations, documents, alcohol, license, and injury phrases.
- Inference: alternate Vietnamese phrasing, spelling variation, or more complex case narratives can be missed or misclassified, which directly affects clarification and final analysis quality.
- Safe modification: build a regression corpus of real user utterances before expanding the extractor or replacing it with a structured parser.
- Test coverage: `src/test/java/com/vn/traffic/chatbot/chat/service/FactMemoryServiceTest.java` covers a few happy-path and correction cases, not broad linguistic variation.

## Scaling Limits

**Chat thread APIs are small-dataset only:**
- Current capacity: acceptable for a small internal set of threads with short histories.
- Limit: `GET /api/v1/chat/threads` returns the full list, and summary generation is O(threads + per-thread message scan).
- Scaling path: paginate `ChatThread` queries, scope them per user/session, and store summary/title fields instead of recomputing from message history.

**Upload ingestion is single-node by design:**
- Current capacity: one backend instance with access to the same local temp directory.
- Limit: `storageUri` points to local filesystem state, so retries and background jobs assume the same machine still owns the uploaded file.
- Scaling path: shared object storage or shared mounted volume plus explicit cleanup and retention rules.

**Admin vector inspection will degrade as `kb_vector_store` grows:**
- Current capacity: acceptable for small to moderate corpora.
- Limit: summary views still deserialize embeddings and readiness/index screens still count across the whole table.
- Scaling path: summary tables or materialized views, narrower projections, and cached readiness metrics.

## Dependencies at Risk

**Spring AI is pinned to a milestone release with milestone/snapshot repositories enabled:**
- Risk: `build.gradle` imports `org.springframework.ai:spring-ai-bom:2.0.0-M4` and adds `https://repo.spring.io/milestone` plus `https://repo.spring.io/snapshot`.
- Impact: API churn, transitive dependency instability, and harder build reproducibility around chat, ETL, and vector-store code.
- Migration plan: move to a GA Spring AI line and remove snapshot repository usage once compatible.

**Test code still uses deprecated Spring MVC test wiring:**
- Risk: `./gradlew.bat test` emits removal warnings for `MappingJackson2HttpMessageConverter` usage in several tests.
- Files: `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java`, `src/test/java/com/vn/traffic/chatbot/chat/api/ChatThreadControllerTest.java`, `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java`
- Impact: future Spring upgrades are likely to break tests before production code.
- Migration plan: switch standalone `MockMvc` tests to supported message-converter wiring.

## Missing Critical Features

**Chat-log review and answer-check workflows are still absent:**
- Problem: `ADMIN-04` and `ADMIN-05` remain pending in `.planning/REQUIREMENTS.md`, and Phase 5 is still untouched in `.planning/ROADMAP.md`.
- Files: `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`
- Blocks: operators cannot review historical conversations in an admin workflow, cannot run answer checks, and cannot gate corpus/prompt changes with legal-domain evaluation.

**Production observability and release automation are still missing:**
- Problem: `docs/DEPLOYMENT.md` reports no CI/CD pipeline and no external monitoring/alerting integration; the codebase only exposes basic Actuator endpoints.
- Files: `docs/DEPLOYMENT.md`, `build.gradle`, `src/main/resources/application.yaml`
- Blocks: safe production rollout, alerting on ingestion drift/failure, and repeatable deployment verification.

## Test Coverage Gaps

**The backend test suite is currently red at compile time:**
- What's not tested: the full backend regression suite is not runnable in its current state.
- Files: `src/test/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionServiceTest.java`, `src/main/java/com/vn/traffic/chatbot/chunk/api/dto/ChunkSummaryResponse.java`, `src/main/java/com/vn/traffic/chatbot/chunk/api/dto/ChunkDetailResponse.java`
- Risk: verified locally on 2026-04-11, `./gradlew.bat test` fails during `compileTestJava`, so backend changes are not protected by a green test gate.
- Priority: High

**The frontend test suite is failing and still contains stub coverage:**
- What's not tested: stable Vietnamese copy assertions, scenario accordion rendering, and multiple admin flows remain either failing or `todo`.
- Files: `frontend/__tests__/app-sidebar.test.tsx`, `frontend/__tests__/message-bubble.test.tsx`, `frontend/__tests__/smoke.test.tsx`, `frontend/__tests__/stubs/sources.test.tsx`, `frontend/__tests__/stubs/chat.test.tsx`, `frontend/__tests__/stubs/param.test.tsx`, `frontend/__tests__/stubs/vector.test.tsx`
- Risk: verified locally on 2026-04-11, `pnpm test` fails and reports 9 `todo` tests, so UI regressions are only weakly guarded.
- Priority: High

**Job-control and ops hardening paths have no focused regression coverage:**
- What's not tested: `cancelJob()` semantics, `SourceService.reingest()`, `CorsConfig`, `AsyncConfig`, and the missing Phase 4.1 exception-handling cases.
- Files: `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, `src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java`, `src/main/java/com/vn/traffic/chatbot/common/config/CorsConfig.java`, `src/main/java/com/vn/traffic/chatbot/common/config/AsyncConfig.java`, `src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java`, `src/test/java/com/vn/traffic/chatbot/ingestion/service/IngestionServiceTest.java`
- Risk: bugs around cancellation, operational error handling, and config behavior can regress unnoticed.
- Priority: High

---

*Concerns audit: 2026-04-11*
