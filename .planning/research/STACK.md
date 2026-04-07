# Technology Stack Research Memo

**Project:** Vietnam Traffic Law Chatbot
**Dimension:** Stack
**Researched:** 2026-04-07
**Overall recommendation confidence:** MEDIUM-HIGH

## Executive Recommendation

Build this as a **Spring Boot 4 REST backend + Spring AI 1.1.x RAG layer + PostgreSQL/pgvector + Next.js 16 App Router frontend**.

That is the most standard 2026 stack that still fits the project's constraints:
- it preserves the main idea of `jmix-ai-backend` by keeping **RAG, ingestion, parameter management, chat logging, answer-checking, and pgvector-backed retrieval**,
- it replaces Jmix UI flows with **clean REST APIs**,
- and it aligns well with the migrated shoes backend's **controller/service/dto/repository** structure.

The key opinionated choice is this: **keep Spring AI as the orchestration layer, PostgreSQL as both OLTP store and vector store, and use Next.js only for UI/admin, not as a second backend.** Do not split core chatbot logic across Java and Node. The backend should remain the system of record and the AI orchestration layer.

## Recommended Stack

### Core Backend

| Technology | Recommended Version | Purpose | Why | Confidence |
|------------|---------------------|---------|-----|------------|
| Spring Boot | 4.0.5 | REST application platform | Current docs show 4.0.5; fits the repo's current `build.gradle`; modern baseline with Java 17-26 support and clean REST foundation | HIGH |
| Java | 21 LTS | Runtime/toolchain | Better long-term team baseline than Java 25 for a new project; broad library compatibility and easier deployment than chasing the newest non-LTS | MEDIUM |
| Spring Web MVC | Boot-managed | Main REST API layer | Best fit for the shoes backend controller/service structure and normal CRUD/admin APIs | HIGH |
| Spring WebFlux | Boot-managed, add only if streaming is required | SSE/token streaming for chat responses | Spring AI docs note streaming is reactive; add WebFlux for streaming endpoints but keep the rest of the app REST-first | HIGH |
| Spring Validation | Boot-managed | Request validation | Standard for DTO validation in controller-first Spring APIs | HIGH |
| Spring Data JPA | Boot-managed | Relational persistence for app data | Natural fit for chat logs, parameters, ingestion jobs, document metadata, answer checks | HIGH |
| Liquibase | Boot-managed | Schema migrations | Strong fit for a legal/admin system; already used in the shoes backend and preferable to ad hoc schema init | HIGH |
| Spring Security | Boot-managed | Admin/API protection | Even if public/admin role separation is deferred, the admin surface still needs authentication | MEDIUM |
| springdoc OpenAPI | 3.0.1 | API docs | Already used in the reference shoes backend and useful for frontend-backend contract work | MEDIUM |

### AI / RAG Layer

| Technology | Recommended Version | Purpose | Why | Confidence |
|------------|---------------------|---------|-----|------------|
| Spring AI | 1.1.4 stable line | LLM integration, RAG, advisors, chat memory | Official stable docs currently show 1.1.4; directly matches the Jmix AI backend's architectural intent without inventing custom orchestration glue | HIGH |
| `spring-ai-starter-model-openai` | Spring AI managed | Primary chat + embedding provider integration | Best-supported baseline in Spring AI docs and simplest path to production RAG | MEDIUM |
| `spring-ai-starter-vector-store-pgvector` | Spring AI managed | Vector store integration | Official Spring AI pgvector support is mature and keeps the architecture inside the Spring ecosystem | HIGH |
| `spring-ai-rag` | Spring AI managed | Retrieval-augmented generation pipeline | Preserves the core idea from `jmix-ai-backend` instead of hand-rolling retrieval orchestration | HIGH |
| `spring-ai-advisors-vector-store` | Spring AI managed | Retrieval advisor chain | Best way to wire chat memory + retrieval in a maintainable service layer | HIGH |
| `spring-ai-starter-model-chat-memory-repository-jdbc` | Spring AI managed | Persisted chat memory | Useful when you want multi-turn context with persistence in the same relational stack | HIGH |

### Database / Storage

| Technology | Recommended Version | Purpose | Why | Confidence |
|------------|---------------------|---------|-----|------------|
| PostgreSQL | 16+ | Primary relational database | Standard production default for Spring systems; handles transactional app data and works well with pgvector | HIGH |
| pgvector | current PostgreSQL extension | Embedding storage and similarity search | Standard small-to-mid-scale RAG choice when you already use PostgreSQL; simpler than adding a dedicated vector DB too early | HIGH |
| MinIO or S3-compatible object storage | current stable | Raw file storage for ingested PDFs/DOCX/exports | Keep original source files outside Postgres while storing metadata and chunk references in Postgres | MEDIUM |

### Ingestion / Parsing

| Technology | Recommended Version | Purpose | Why | Confidence |
|------------|---------------------|---------|-----|------------|
| Apache Tika | 3.3.x line | Baseline text + metadata extraction for PDF/Word | Officially current and broadly used; the right Java-native default for extracting text/metadata from legal documents | MEDIUM |
| Jsoup | 1.17+ | Website ingestion and HTML cleanup | Lightweight, proven, already used in `jmix-ai-backend`; good for trusted websites and regulation pages | HIGH |
| Custom legal-document normalization layer | project code | Normalize law/article/chapter metadata | Vietnam legal RAG quality depends more on structure preservation than raw extraction alone | MEDIUM |
| Optional: Unstructured API/service | evaluate later, not default | Better layout-aware parsing for messy scans/tables | Useful only if Tika + custom normalization prove weak on complex documents; open-source docs explicitly warn it is not for production scenarios | MEDIUM |

### Frontend

| Technology | Recommended Version | Purpose | Why | Confidence |
|------------|---------------------|---------|-----|------------|
| Next.js | 16.2.x | Main frontend app | Current docs show Next.js 16 and App Router as the standard path for new apps | HIGH |
| React | Next-managed | UI layer | Standard with Next.js 16 | HIGH |
| App Router | Next standard | Routing and full-stack frontend structure | Recommended default in current docs; fits a sidebar app with chat and admin routes | HIGH |
| TypeScript | current stable | Frontend type safety | Essential for API-heavy admin/chat UI work | HIGH |
| Vercel AI SDK (`ai`, `@ai-sdk/react`) | current docs line | Chat UI hooks and streaming UX | Best practical way to avoid custom-heavy chat UI while still keeping backend ownership in Spring | MEDIUM |
| shadcn/ui | current | Admin/chat component primitives | Fastest maintainable path to a polished sidebar-style app without heavy design-system work | MEDIUM |
| TanStack Query | 5.x | REST data fetching/caching for admin screens | Better fit than overloading AI SDK hooks for all CRUD/admin traffic | MEDIUM |
| React Hook Form + Zod | current | Admin forms and validation | Standard, ergonomic pair for ingestion settings, parameters, and checks screens | MEDIUM |

### Observability / Operations

| Technology | Recommended Version | Purpose | Why | Confidence |
|------------|---------------------|---------|-----|------------|
| Spring Boot Actuator | Boot-managed | Health/metrics | Already in repo; required for ops visibility | HIGH |
| Micrometer + Prometheus | current | Metrics | Standard monitoring path for Spring services | MEDIUM |
| OpenTelemetry | current | Traces for LLM/retrieval/HTTP flows | `jmix-ai-backend` already points in this direction; useful for diagnosing bad retrieval and slow generations | MEDIUM |
| Docker Compose | current | Local dev stack | Good for local Postgres/pgvector/object storage/LLM proxy dependencies | HIGH |

## Prescriptive Stack Shape

### 1. Backend package structure

Follow the shoes backend style, not the Jmix UI package layout.

Recommended structure:

```text
com.vn.traffic.chatbot
  configuration/
  controller/
    public/
    admin/
  dto/
    request/
    response/
  service/
    chat/
    retrieval/
    ingestion/
    parameters/
    checks/
    citation/
  entity/
  repository/
  mapper/
  security/
  ai/
    config/
    advisor/
    memory/
    prompt/
  ingestion/
    parser/
    chunker/
    normalizer/
    source/
```

Why this shape:
- `jmix-ai-backend` gives the **capability map**: chat, retrieval, checks, parameters, vector store, logs.
- shoes backend gives the **delivery pattern**: controller -> service -> repository with DTO wrappers.
- this prevents the migration from becoming a half-Jmix, half-REST hybrid.

### 2. API response style

Use the shoes backend's wrapper style (`ResponseGeneral<T>`) for normal CRUD/admin endpoints.

Use either:
- `ResponseGeneral<T>` for admin/public non-streaming APIs,
- or SSE/stream endpoints for chat token streaming.

Do not make every endpoint "LLM-native". Most of the system is still standard business software: ingestion jobs, parameters, logs, source management, checks.

### 3. RAG pipeline shape

Recommended flow:

```text
Source file / website
  -> raw extraction (Tika / Jsoup)
  -> legal normalization (articles, clauses, headings, source metadata)
  -> chunking
  -> embeddings
  -> pgvector storage
  -> retrieval advisor
  -> LLM answer generation with citations
  -> chat log + answer-check persistence
```

Opinionated guidance:
- **Do not embed raw PDF text dumps directly.** Legal documents need structural metadata.
- **Do not treat website ingestion the same as PDF ingestion.** Website pages need canonical URL, crawl timestamp, and cleaned content extraction.
- **Do not skip metadata design.** Store source title, authority, publication date, document type, article/section markers, and canonical source URL/file reference.

## Exact Dependency Recommendation

### Backend Gradle dependencies

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.5'
    id 'io.spring.dependency-management' version '1.1.7'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-liquibase'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1'

    implementation platform('org.springframework.ai:spring-ai-bom:1.1.4')
    implementation 'org.springframework.ai:spring-ai-starter-model-openai'
    implementation 'org.springframework.ai:spring-ai-starter-vector-store-pgvector'
    implementation 'org.springframework.ai:spring-ai-advisors-vector-store'
    implementation 'org.springframework.ai:spring-ai-rag'
    implementation 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc'

    implementation 'org.apache.tika:tika-core:3.3.0'
    implementation 'org.apache.tika:tika-parsers-standard-package:3.3.0'
    implementation 'org.jsoup:jsoup:1.17.2'

    runtimeOnly 'org.postgresql:postgresql'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

Add WebFlux only if you need token streaming from Spring:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

### Frontend packages

```bash
npm install next react react-dom typescript
npm install ai @ai-sdk/react zod @tanstack/react-query react-hook-form @hookform/resolvers
npm install lucide-react class-variance-authority clsx tailwind-merge
```

## Configuration Guidance

### pgvector

Use Spring AI's pgvector starter, but do **not** rely on runtime schema auto-init in production.

Recommended rule:
- `initialize-schema=true` only for local/dev throwaway environments
- production schema via Liquibase
- explicitly lock embedding dimensions
- use `HNSW` + `COSINE_DISTANCE`

Reason: Spring AI docs now make schema initialization explicit, and changing embedding dimensions later forces vector table recreation.

### Chat client usage

Use configured `ChatClient` beans with default advisors instead of newing logic up inside controllers.

Recommended pattern:
- controller accepts request DTO
- chat service orchestrates prompt building
- service uses a configured `ChatClient`
- advisors apply memory + retrieval + optional logging
- response mapper returns answer + citations + metadata

This preserves the Jmix AI backend's orchestration intent while matching the shoes backend's service-centric style.

## What To Use for Each Requirement

### Public chatbot Q&A
- Spring REST endpoint
- Spring AI `ChatClient`
- Retrieval advisor over pgvector
- citation-rich response DTO

### Real-life case analysis
- same RAG stack, but with a stronger domain prompt template
- structured output DTO for: legal basis, likely penalty, required documents, next steps, confidence/disclaimer

### Admin source ingestion
- REST admin endpoints
- object storage for uploaded files
- Tika/Jsoup parsing
- custom normalization/chunking services
- ingestion job status persisted in Postgres

### Parameter management
- standard CRUD REST + JPA + Liquibase
- do not couple parameters to frontend-only config

### Chat logs and answer checks
- relational tables in Postgres
- separate service modules, not mixed into chat controller code

## What Not To Use

| Avoid | Why Not |
|------|---------|
| Jmix Flow UI / Vaadin UI for this project | Conflicts with the requirement to replace Jmix UI patterns with REST + Next.js |
| LangChain4j as the primary orchestration layer | Good library, but it adds unnecessary divergence from the existing Spring AI-based Jmix AI backend concept |
| A separate dedicated vector DB on day one (Pinecone, Weaviate, Qdrant Cloud) | Adds infra and cost without clear need when PostgreSQL/pgvector already fits the project |
| MongoDB as the main database | Weak fit for transactional admin/config/logging needs and would split persistence unnecessarily |
| Python ingestion microservice by default | Adds deployment complexity too early; start Java-native with Tika/Jsoup and add a specialist parser service only if evidence demands it |
| Full OCR-first pipeline as the default ingestion path | Most trusted legal materials should be sourced digitally first; OCR should be a fallback for bad scans |
| Next.js server actions as the primary business API layer | Violates the requirement that the backend be Spring REST-first |
| Heavy custom chat UI from scratch | Slows delivery and is unnecessary when AI SDK + shadcn cover the practical baseline |
| Java 25 as the team baseline | The repo currently targets it, but Java 21 LTS is the better production default unless there is a specific Java 25 feature you need |
| Spring AI milestone builds as default | Use stable 1.1.4 unless a later stable release is intentionally adopted |

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| AI orchestration | Spring AI | LangChain4j | Less aligned with the existing Jmix AI backend concept and current code references |
| Vector store | PostgreSQL + pgvector | Dedicated vector DB | Premature infra complexity for v1 |
| Document parsing | Tika + custom normalization | Unstructured OSS as default | Official docs warn OSS is a prototyping starting point and not designed for production scenarios |
| Frontend chat layer | Next.js + AI SDK | Fully custom React chat stack | More work for little benefit |
| Backend API style | Spring MVC REST | GraphQL | Adds complexity without clear need for this admin-heavy system |

## Alignment With Reference Systems

### Preserve from `jmix-ai-backend`
Keep these ideas:
- pgvector-backed retrieval
- chat service separated from retrieval concerns
- ingestion manager pattern by source type
- parameters repository/configuration concept
- answer checks/evaluation capability
- chat log persistence
- telemetry/observability hooks

### Replace from `jmix-ai-backend`
Replace these parts:
- Jmix Flow UI views
- UI-driven admin flows
- Jmix-specific security/UI abstractions
- dual datasource complexity unless clearly needed

### Reuse from shoes backend
Prefer these patterns:
- controller/service/repository layering
- DTO request/response packages
- response wrapper for non-streaming APIs
- Liquibase migrations
- security configuration style
- OpenAPI documentation

## Final Prescriptive Recommendation

If this project started implementation today, I would choose:

1. **Spring Boot 4.0.5 + Java 21**
2. **Spring MVC for standard APIs, plus WebFlux only for chat streaming**
3. **Spring AI 1.1.4 stable stack**
4. **PostgreSQL 16+ with pgvector as the only database/vector store in v1**
5. **Apache Tika + Jsoup + custom legal normalization for ingestion**
6. **Next.js 16 App Router + TypeScript + AI SDK + shadcn/ui**
7. **Liquibase, Actuator, Micrometer, OpenTelemetry**

That stack is the best balance of:
- current ecosystem standard,
- migration alignment,
- implementation speed,
- operational simplicity,
- and legal-RAG suitability.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Spring backend baseline | HIGH | Official Spring Boot docs are current and repo already points there |
| Spring AI as core RAG layer | HIGH | Official Spring AI stable docs directly support chat, memory, RAG, and pgvector |
| PostgreSQL + pgvector | HIGH | Strong official Spring AI support and best fit for this system shape |
| Next.js 16 + App Router | HIGH | Current Next docs clearly position App Router as the standard path |
| Vercel AI SDK for chat UI | MEDIUM | Official docs are clear, but this remains a frontend integration choice rather than a hard backend requirement |
| Tika as default parser | MEDIUM | Strong general-purpose fit, but real Vietnam legal document quality still needs project-specific validation |
| Unstructured as non-default | MEDIUM | Official docs support caution; production suitability depends on whether you use their hosted platform, not OSS alone |

## Sources

### Official / primary
- Spring Boot System Requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring AI Reference Index: https://docs.spring.io/spring-ai/reference/index.html
- Spring AI ChatClient docs: https://docs.spring.io/spring-ai/reference/api/chatclient.html
- Spring AI PGvector docs: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- Next.js docs index / App Router docs: https://nextjs.org/docs/app
- Next.js llms export used for current version signal: https://nextjs.org/docs/llms.txt
- AI SDK Introduction: https://ai-sdk.dev/docs/introduction
- AI SDK Next.js App Router quickstart: https://ai-sdk.dev/docs/getting-started/nextjs-app-router
- Apache Tika official site: https://tika.apache.org/
- Unstructured open-source overview: https://docs.unstructured.io/open-source/introduction/overview

### Local reference systems reviewed
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/build.gradle`
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/controller/SearchController.java`
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/PgvectorStoreConfiguration.java`
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/vectorstore/IngesterManager.java`
- `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/build.gradle`
- `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/controller/BrandController.java`
- `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/service/brand/BrandService.java`
- `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/dto/ResponseGeneral.java`
