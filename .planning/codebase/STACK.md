# Technology Stack

**Analysis Date:** 2026-04-11

## Languages

**Primary:**
- Java 25 - backend API, ingestion pipeline, retrieval, and admin services under `src/main/java/com/vn/traffic/chatbot/**`; enforced by the Gradle toolchain in `build.gradle`.
- TypeScript 5 - frontend App Router UI, API client, hooks, and test code under `frontend/app/**`, `frontend/components/**`, `frontend/hooks/**`, `frontend/lib/**`, and `frontend/__tests__/**`; configured in `frontend/tsconfig.json`.

**Secondary:**
- YAML - backend runtime configuration in `src/main/resources/application.yaml` and seeded AI parameter content in `src/main/resources/default-parameter-set.yml`.
- XML - Liquibase database migrations in `src/main/resources/db/changelog/*.xml`.
- CSS - Tailwind/shadcn styling in `frontend/app/globals.css`.
- Shell - local Git hook automation in `.husky/pre-commit`.

## Runtime

**Environment:**
- JVM application runtime via Spring Boot 4.0.5 in `build.gradle`, started from `src/main/java/com/vn/traffic/chatbot/TrafficLawChatbotApplication.java`.
- Node.js runtime for the separate frontend app in `frontend/`; the repo requires Node.js in `README.md` but does not pin a Node version in `frontend/package.json` or a root `.nvmrc`.

**Package Manager:**
- Gradle Wrapper 9.4.1 for the backend, pinned in `gradle/wrapper/gradle-wrapper.properties`.
- `pnpm@10.32.1` for the frontend, pinned in `frontend/package.json`.
- Lockfile: `frontend/pnpm-lock.yaml` is present; no Gradle dependency lockfile is detected.

## Frameworks

**Core:**
- Spring Boot 4.0.5 - backend web runtime, actuator, validation, JPA, multipart upload handling, and config binding via `build.gradle` and `src/main/resources/application.yaml`.
- Spring AI 2.0.0-M4 - chat client, vector-store integration, RAG helpers, token chunking, HTML parsing, and PDF parsing via `build.gradle`, `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java`, `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java`, and `src/main/java/com/vn/traffic/chatbot/ingestion/**`.
- Next.js 16.2.3 App Router - frontend shell and routing in `frontend/package.json` and `frontend/app/**`.
- React 19.2.4 - frontend component runtime in `frontend/package.json`.

**Testing:**
- JUnit Platform / JUnit 5 - backend tests enabled by `tasks.named('test') { useJUnitPlatform() }` in `build.gradle`.
- Spring Boot test starters - backend MVC, JPA, and actuator test support from `build.gradle`.
- Vitest 4.1.4 + jsdom - frontend unit/component tests configured in `frontend/vitest.config.ts`.
- Playwright 1.59.1 - frontend browser smoke tests configured in `frontend/playwright.config.ts`.

**Build/Dev:**
- Gradle Wrapper - backend build entrypoint via `gradlew` and `gradlew.bat`.
- Liquibase - database schema management from `build.gradle` and `src/main/resources/db/changelog/db.changelog-master.xml`.
- Tailwind CSS 4 + PostCSS - frontend styling pipeline via `frontend/package.json`, `frontend/postcss.config.mjs`, and `frontend/app/globals.css`.
- ESLint 9 + `eslint-config-next` - frontend linting via `frontend/eslint.config.mjs`.
- Prettier 3.8.2 + `prettier-plugin-tailwindcss` - frontend formatting via `frontend/.prettierrc`.
- Husky + lint-staged - local pre-commit automation via `.husky/pre-commit` and `frontend/package.json`.

## Key Dependencies

**Critical:**
- `org.springframework.boot:spring-boot-starter-webmvc` - REST API layer for chat and admin endpoints in `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java`, `src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java`, `src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java`, `src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java`, and `src/main/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetController.java`.
- `org.springframework.boot:spring-boot-starter-data-jpa` - relational persistence for sources, ingestion jobs, threads, facts, and parameter sets under `src/main/java/com/vn/traffic/chatbot/**/domain` and `src/main/java/com/vn/traffic/chatbot/**/repo`.
- `org.springframework.ai:spring-ai-starter-model-openai` - active chat/embedding provider; `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java` binds the `ChatClient` to `openAiChatModel`.
- `org.springframework.ai:spring-ai-starter-vector-store-pgvector` - vector search and embedding persistence; configured in `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java`.
- `next`, `react`, and `react-dom` - frontend app runtime in `frontend/package.json`.
- `axios` - frontend-to-backend HTTP client in `frontend/lib/api/client.ts`.
- `@tanstack/react-query` - frontend server-state layer in `frontend/components/layout/providers.tsx` and `frontend/hooks/use-*.ts`.

**Infrastructure:**
- `org.postgresql:postgresql` - PostgreSQL JDBC driver used by JPA, Liquibase, and direct chunk inspection in `src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java`.
- `org.springframework.boot:spring-boot-starter-liquibase` - migration runner for `src/main/resources/db/changelog/*.xml`.
- `org.apache.tika:tika-core` and `org.apache.tika:tika-parser-microsoft-module` - Word/Docx and structured document parsing in `src/main/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParser.java`.
- `org.jsoup:jsoup` and `org.springframework.ai:spring-ai-jsoup-document-reader` - safe URL fetch + HTML body extraction in `src/main/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcher.java` and `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/HtmlDocumentParser.java`.
- `org.springframework.ai:spring-ai-pdf-document-reader` - page-based PDF parsing in `src/main/java/com/vn/traffic/chatbot/ingestion/parser/springai/PdfDocumentParser.java`.
- `@base-ui/react`, `lucide-react`, `streamdown`, `react-hook-form`, and `zod` - frontend component primitives, icons, streamed markdown rendering, and form validation in `frontend/components/**`, `frontend/components/ai-elements/message.tsx`, and `frontend/components/admin/parameters/parameter-dialog.tsx`.
- `org.springframework.ai:spring-ai-starter-model-google-genai` - declared in `build.gradle` but not active because `src/main/resources/application.yaml` excludes Google GenAI chat auto-configuration.

## Configuration

**Environment:**
- Backend runtime configuration lives in `src/main/resources/application.yaml`; it imports the repo-root `.env` file via `spring.config.import: optional:file:.env[.properties]`.
- Required backend variables are `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `OPENAI_API_KEY`, surfaced in `src/main/resources/application.yaml` and `README.md`.
- Frontend API routing is configured through `NEXT_PUBLIC_API_BASE_URL` in `frontend/lib/api/client.ts`; `frontend/.env.local` exists for local overrides.
- CORS is controlled by `app.cors.allowed-origins` in `src/main/resources/application.properties` and enforced by `src/main/java/com/vn/traffic/chatbot/common/config/CorsConfig.java`.

**Build:**
- Backend build and dependency source of truth: `build.gradle`, `settings.gradle`, and `gradle/wrapper/gradle-wrapper.properties`.
- Backend schema/config source of truth: `src/main/resources/application.yaml` and `src/main/resources/db/changelog/db.changelog-master.xml`.
- Frontend build/config source of truth: `frontend/package.json`, `frontend/next.config.ts`, `frontend/tsconfig.json`, `frontend/postcss.config.mjs`, `frontend/eslint.config.mjs`, `frontend/vitest.config.ts`, `frontend/playwright.config.ts`, and `frontend/components.json`.
- Docker Compose is not an active runtime path: `src/main/resources/application.yaml` sets `spring.docker.compose.enabled: false`, and `compose.yaml` is only a commented example stub.

## Platform Requirements

**Development:**
- Java 25 is required by the backend toolchain in `build.gradle`.
- Gradle Wrapper 9.4.1 is required for backend builds via `gradlew` / `gradlew.bat`.
- Node.js plus `pnpm` are required for the frontend in `frontend/`, with `pnpm` pinned in `frontend/package.json`.
- PostgreSQL with the `vector`, `hstore`, and `uuid-ossp` extensions is required by `README.md`, `src/main/resources/db/changelog/001-schema-foundation.xml`, and `src/main/resources/db/changelog/003-vector-store-schema.xml`.

**Production:**
- The repo is set up as a Spring Boot API on port `8089` from `src/main/resources/application.yaml` plus a separate Next.js app that defaults to port `3000` in `frontend/playwright.config.ts` and `README.md`.
- PostgreSQL is the only detected production data backend; no object-store, cache, queue, or dedicated vector-database deployment target is configured.
- No deployment manifest is checked in for Docker, Vercel, Netlify, Fly.io, Railway, or GitHub Actions; production hosting is not codified in the repository.

---

*Stack analysis: 2026-04-11*
