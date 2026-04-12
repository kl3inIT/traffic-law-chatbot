# External Integrations

**Analysis Date:** 2026-04-11

## APIs & External Services

**Model providers:**
- OpenAI - active chat and embedding provider for grounded answers and vector indexing.
  - SDK/Client: `org.springframework.ai:spring-ai-starter-model-openai` in `build.gradle`; runtime `ChatClient` is wired to `openAiChatModel` in `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java`.
  - Auth: `OPENAI_API_KEY`, bound in `src/main/resources/application.yaml`.
- Google GenAI - dependency is installed but not active in the running app.
  - SDK/Client: `org.springframework.ai:spring-ai-starter-model-google-genai` in `build.gradle`.
  - Auth: Not configured; `src/main/resources/application.yaml` explicitly excludes `GoogleGenAiChatAutoConfiguration`, so no Google key path is active.

**External fetch/internet access:**
- Public website ingestion - user-supplied legal-source URLs are fetched over `http`/`https` through `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`.
  - SDK/Client: Java `HttpClient` plus `org.jsoup:jsoup`.
  - Auth: None; only public hosts are allowed, and loopback/site-local/private IPs are rejected before fetch.

**Internal service boundary:**
- Frontend-to-backend REST - the Next.js app calls the Spring Boot API over HTTP.
  - SDK/Client: `axios` in `frontend/lib/api/client.ts`.
  - Auth: None detected; requests go directly to `/api/v1/chat` and `/api/v1/admin/*`.

## Data Storage

**Databases:**
- PostgreSQL - primary relational database for sources, source versions, approval events, ingestion jobs, chat threads, chat messages, thread facts, and AI parameter sets.
  - Connection: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` in `src/main/resources/application.yaml`.
  - Client: Spring Data JPA from `build.gradle`; schema managed by Liquibase in `src/main/resources/db/changelog/*.xml`.
- pgvector inside the same PostgreSQL database - only detected vector store.
  - Connection: same PostgreSQL connection in `src/main/resources/application.yaml`.
  - Client: `PgVectorStore` bean in `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java`.

**Vector stores:**
- `kb_vector_store` in PostgreSQL/pgvector - embeddings plus metadata JSON are defined in `src/main/resources/db/changelog/003-vector-store-schema.xml`, queried through Spring AI vector search in `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`, and inspected through direct JDBC in `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java`.
- No Pinecone, Qdrant, Weaviate, Milvus, Chroma, or other external vector database is detected.

**File Storage:**
- Local filesystem only - uploaded files are written to temp paths with `Files.createTempFile(...)` in `src/main/java/com/vn/traffic/chatbot/ingestion/service/IngestionService.java`, and later reopened from `storageUri` with `FileInputStream` in `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`.
- No S3, GCS, Azure Blob, MinIO, or CDN integration is detected.

**Caching:**
- None detected; no Redis, Memcached, Caffeine, or application cache integration is present in `build.gradle`, `src/main/java/**`, or `frontend/**`.

## Ingestion Sources

**Accepted automated sources:**
- PDF uploads - handled by `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/PdfDocumentParser.java` using Spring AI `PagePdfDocumentReader`.
- Word uploads (`.doc`, `.docx`) - handled by `src/main/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParser.java` via Apache Tika, selected by `src/main/java/com/vn/traffic/chatbot/ingestion/parser/FileIngestionParserResolver.java`.
- Public website pages - handled by `src/main/java/com/vn/traffic/chatbot/ingestion/parser/UrlPageParser.java` and `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/HtmlDocumentParser.java` after safe fetch by `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`.

**Source registry/domain types:**
- The source model supports `PDF`, `WORD`, `STRUCTURED_REGULATION`, and `WEBSITE_PAGE` in `src/main/java/com/vn/traffic/chatbot/source/domain/SourceType.java`.
- `STRUCTURED_REGULATION` exists as a source type, but no dedicated parser or external structured-regulation feed is implemented under `src/main/java/com/vn/traffic/chatbot/ingestion/**`.
- `SYSTEM_IMPORT` exists in `src/main/java/com/vn/traffic/chatbot/source/domain/OriginKind.java`, but no active system-import pipeline is detected in `src/main/java/**`.

## Authentication & Identity

**Auth Provider:**
- None detected.
  - Implementation: Public and admin endpoints are plain Spring MVC controllers in `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java`, `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, `src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java`, and `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java`.
- No Spring Security, JWT, OAuth, session auth, NextAuth, Clerk, Auth0, Supabase Auth, or Firebase Auth integration is detected.

## Monitoring & Observability

**Error Tracking:**
- None detected; no Sentry, Rollbar, Datadog APM, Honeycomb, or OpenTelemetry application config is present in `build.gradle`, `src/main/resources/**`, or `frontend/**`.

**Logs:**
- Spring Boot / SLF4J logging is the only detected logging path, used broadly through Lombok `@Slf4j` in files such as `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`, and `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`.
- Operational health/info/metrics are exposed through Spring Boot Actuator from `build.gradle` and `src/main/resources/application.yaml`.

## Operational Integrations

**Runtime operations:**
- Spring Boot Actuator exposes `health`, `info`, and `metrics` per `src/main/resources/application.yaml`.
- Async ingestion jobs run on the Spring task executor defined by `src/main/java/com/vn/traffic/chatbot/common/config/AsyncConfig.java` and the `spring.task.execution.*` pool settings in `src/main/resources/application.yaml`.
- Local pre-commit automation runs `pnpm lint-staged` through `.husky/pre-commit` and `frontend/package.json`.

**Not detected:**
- No message queue or job broker integration such as Kafka, RabbitMQ, SQS, NATS, or Redis queues.
- No scheduler or external worker platform integration.
- No active Docker Compose wiring at runtime; `spring.docker.compose.enabled` is `false` in `src/main/resources/application.yaml`, and `compose.yaml` is only a commented sample.

## CI/CD & Deployment

**Hosting:**
- Not detected in repo.

**CI Pipeline:**
- None detected; no `.github/workflows/` directory or alternative CI manifest is present.

## Environment Configuration

**Required env vars:**
- `DB_URL` - PostgreSQL JDBC URL in `src/main/resources/application.yaml`.
- `DB_USERNAME` - PostgreSQL username in `src/main/resources/application.yaml`.
- `DB_PASSWORD` - PostgreSQL password in `src/main/resources/application.yaml`.
- `OPENAI_API_KEY` - OpenAI credential in `src/main/resources/application.yaml`.
- `NEXT_PUBLIC_API_BASE_URL` - frontend API base URL in `frontend/lib/api/client.ts`.
- `app.cors.allowed-origins` - backend allowed origins property in `src/main/resources/application.properties` and `src/main/java/com/vn/traffic/chatbot/common/config/CorsConfig.java`.

**Secrets location:**
- Repo-root `.env` is the backend local-secret entrypoint because `src/main/resources/application.yaml` imports it.
- `frontend/.env.local` exists for frontend local overrides.
- No external secret manager or cloud secret service is detected.

## Webhooks & Callbacks

**Incoming:**
- None; no webhook controller, callback endpoint, or signature-verification flow is detected in `src/main/java/**`.

**Outgoing:**
- URL-ingestion fetches are the only outbound HTTP integration detected, initiated by `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java`.
- No outbound webhook delivery, Slack notifications, email provider, SMS provider, or third-party callback integration is detected.

---

*Integration audit: 2026-04-11*
