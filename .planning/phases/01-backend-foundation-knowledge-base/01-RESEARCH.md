# Phase 1: Backend Foundation & Knowledge Base - Research

**Researched:** 2026-04-07
**Domain:** Java 25 Spring REST backend, PostgreSQL + pgvector knowledge-base foundation
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

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

### Deferred Ideas (OUT OF SCOPE)
- Broad website crawling/seed-based crawl behavior is out of scope for Phase 1.
- Chat-facing retrieval test APIs are not locked for this phase and can be added later only if research/planning shows they are necessary.
- Full chat answering, multi-turn analysis, and Next.js admin/chat UI remain in later roadmap phases.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PLAT-01 | Backend exposes the core chatbot and admin capabilities through REST APIs. [VERIFIED: REQUIREMENTS.md] | Admin-only source, ingestion, job, and chunk inspection REST surface is recommended; chat APIs stay out of Phase 1. [VERIFIED: REQUIREMENTS.md][VERIFIED: 01-CONTEXT.md] |
| PLAT-03 | System stores operational data, source metadata, and vector embeddings in a persistent backend data layer. [VERIFIED: REQUIREMENTS.md] | PostgreSQL relational tables plus Spring AI PgVector-backed chunk storage and job tracking satisfy the persistence baseline. [VERIFIED: build.gradle][CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] |
| PLAT-04 | Backend targets Java 25. [VERIFIED: REQUIREMENTS.md] | Existing build already targets Java 25; planner should preserve that and verify local JDK availability before execution. [VERIFIED: build.gradle][VERIFIED: environment probe] |
| KNOW-01 | Admin can ingest PDF legal documents into the knowledge base. [VERIFIED: REQUIREMENTS.md] | File-backed source creation plus async parsing/chunking jobs should include PDF ingestion with page-aware provenance. [VERIFIED: 01-CONTEXT.md][ASSUMED] |
| KNOW-02 | Admin can ingest Word legal documents into the knowledge base. [VERIFIED: REQUIREMENTS.md] | The same ingestion job framework should support DOCX/Word parsing through a shared document parsing abstraction. [VERIFIED: 01-CONTEXT.md][CITED: https://tika.apache.org/] |
| KNOW-03 | Admin can ingest structured policy or regulation documents into the knowledge base. [VERIFIED: REQUIREMENTS.md] | Add a structured-document ingest path that preserves article/section hierarchy in metadata before chunking. [VERIFIED: 01-CONTEXT.md][ASSUMED] |
| KNOW-04 | Admin can ingest website content from trusted sources into the knowledge base. [VERIFIED: REQUIREMENTS.md] | Restrict Phase 1 website import to explicit page URLs with captured canonical URL and retrieval timestamp; no crawler scope. [VERIFIED: 01-CONTEXT.md] |
| KNOW-05 | Ingested knowledge items retain provenance metadata including source type and origin. [VERIFIED: REQUIREMENTS.md] | Source, source version, chunk location hints, import trigger, and processing version should be first-class fields. [VERIFIED: 01-CONTEXT.md][VERIFIED: reference repo AbstractIngester.java][ASSUMED] |
| KNOW-06 | Only active and trusted knowledge sources are eligible for retrieval in production answers. [VERIFIED: REQUIREMENTS.md] | Retrieval eligibility should be derived from manual approval plus active/trusted status and enforced as a hard metadata/source filter. [VERIFIED: 01-CONTEXT.md][CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Read `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, and `.planning/ROADMAP.md` before major implementation work. [VERIFIED: CLAUDE.md]
- Prefer REST-first backend changes. [VERIFIED: CLAUDE.md]
- Keep traffic-condition integrations out of v1 unless requirements change. [VERIFIED: CLAUDE.md]
- Preserve trusted-source ingestion, vector-store management, parameters, chat logs, and answer checks as first-class capabilities. [VERIFIED: CLAUDE.md]
- Treat legal source provenance and answer grounding as critical, not optional. [VERIFIED: CLAUDE.md]

## Summary

Phase 1 should establish a backend-only knowledge-base platform with clear separation between source registry, ingestion orchestration, retrieval-index records, and approval/trust controls. The current repo already declares Spring Boot 4.0.5, Java 25, Spring Data JPA, PostgreSQL, and Spring AI pgvector dependencies, so the planner does not need to introduce a second persistence stack to satisfy Phase 1. [VERIFIED: build.gradle][CITED: https://docs.spring.io/spring-boot/system-requirements.html][CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]

The strongest Phase 1 architecture is a domain-oriented Spring REST layout with admin-facing modules for `source`, `ingestion`, `chunk`, and shared `common/config` concerns. The external `jmix-ai-backend` demonstrates useful concepts to preserve: a dedicated ingester manager, vector-store inspection capability, parameter/chat-log/check entities as first-class future domains, and metadata-rich vector documents. The shoes backend demonstrates the REST shape to adapt: constants for API paths, controller/service/repository separation, paged response DTOs, and centralized exception handling. [VERIFIED: reference repo IngesterManager.java][VERIFIED: reference repo VectorStoreEntity.java][VERIFIED: shoes backend ApiPaths.java][VERIFIED: shoes backend AdminVectorStoreController.java][VERIFIED: shoes backend GlobalExceptionHandler.java]

The main planning risk is not the vector store itself; it is provenance fidelity and trust gating. If the planner allows ingestion to write retrieval-eligible chunks before admin approval, later legal-answer trust will be weakened. The ingestion workflow should therefore separate `parse/index` completion from `approval/activation` eligibility and should store enough location detail to explain where each chunk came from. [VERIFIED: 01-CONTEXT.md][ASSUMED]

**Primary recommendation:** Use one Spring Boot monolith with modular packages, one PostgreSQL database with pgvector enabled, async job-driven ingestion, and source-level approval gates that control retrieval eligibility. [VERIFIED: build.gradle][VERIFIED: 01-CONTEXT.md][CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.5 [VERIFIED: build.gradle] | Application platform, REST, config, actuator, test integration. [VERIFIED: build.gradle] | Already declared in-repo, and Boot 4.0.5 supports Java 17-26 with Spring Framework 7.0.6+. [VERIFIED: build.gradle][CITED: https://docs.spring.io/spring-boot/system-requirements.html] |
| Java | 25 toolchain [VERIFIED: build.gradle] | Required runtime target for the backend. [VERIFIED: build.gradle] | Locked project runtime and already configured in Gradle. [VERIFIED: build.gradle][VERIFIED: REQUIREMENTS.md] |
| Spring Data JPA | Boot-managed via starter [VERIFIED: build.gradle] | Relational persistence for sources, jobs, approvals, and operational tables. [VERIFIED: build.gradle] | Matches repo baseline and fits the planner's need for conventional REST domain entities. [VERIFIED: build.gradle][VERIFIED: PROJECT.md] |
| PostgreSQL | Project DB baseline [VERIFIED: 01-CONTEXT.md] | Primary relational store for metadata and operations. [VERIFIED: 01-CONTEXT.md] | Locked by phase decisions and aligns with reference systems. [VERIFIED: 01-CONTEXT.md][VERIFIED: reference repo application.properties] |
| pgvector via Spring AI starter | Spring AI 2.0.0-M4 declared in repo [VERIFIED: build.gradle] | Embedding storage and similarity search. [VERIFIED: build.gradle] | Spring AI PgVector supports schema/table customization, metadata filtering, and HNSW/IVFFlat index options. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] |
| Liquibase | Recommended via Spring Boot starter [ASSUMED] | Explicit schema migration control for relational tables and pgvector extension setup. [ASSUMED] | The shoes backend already uses Liquibase, and Phase 1 needs deterministic DB evolution rather than relying on auto-DDL. [VERIFIED: shoes backend build.gradle][CITED: https://docs.liquibase.com/oss/integration-guide-4-33/springboot/configuration.html] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Apache Tika | 3.3.0 current site release [CITED: https://tika.apache.org/] | Broad parser facade for PDF, Word, HTML, and other text-bearing files. [CITED: https://tika.apache.org/] | Use as the default parser abstraction for uploaded legal documents and fetched pages. [CITED: https://tika.apache.org/] |
| JSoup | Present in reference repo [VERIFIED: reference repo build.gradle] | HTML cleanup and trusted page extraction. [VERIFIED: reference repo build.gradle] | Use for page-by-page URL imports before chunking. [ASSUMED] |
| Spring Validation / ProblemDetail | Boot-managed [VERIFIED: shoes backend GlobalExceptionHandler.java] | Request validation and uniform API errors. [VERIFIED: shoes backend GlobalExceptionHandler.java] | Use on all admin ingestion and source endpoints. [VERIFIED: shoes backend GlobalExceptionHandler.java] |
| Spring Actuator | Boot-managed starter declared [VERIFIED: build.gradle] | Health and operational observability. [VERIFIED: build.gradle] | Use to expose ingestion/job-health signals and DB readiness. [VERIFIED: build.gradle][ASSUMED] |
| Spring Task Execution / @Async or queue-backed executor | Boot/Spring built-in [ASSUMED] | Background ingestion execution. [ASSUMED] | Use for Phase 1 async jobs; defer external queue infrastructure unless load proves necessary. [VERIFIED: 01-CONTEXT.md][ASSUMED] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| One PostgreSQL database with relational + pgvector tables [RECOMMENDED] | Separate app DB and vector DB as in `jmix-ai-backend` [VERIFIED: reference repo PgvectorStoreConfiguration.java][VERIFIED: reference repo application.properties] | Separate stores preserve stronger physical isolation but add complexity that this repo does not currently need. [VERIFIED: reference repo PgvectorStoreConfiguration.java][ASSUMED] |
| Liquibase migrations [RECOMMENDED] | Hibernate schema auto-generation | Auto-DDL is faster to start but weaker for controlled phase planning, extension setup, and later environment promotion. [CITED: https://docs.liquibase.com/oss/integration-guide-4-33/springboot/configuration.html][ASSUMED] |
| Admin-only source APIs in Phase 1 [RECOMMENDED] | Add retrieval-test/chat APIs now | Earlier retrieval endpoints may help validation, but they expand scope beyond the locked phase boundary. [VERIFIED: 01-CONTEXT.md] |
| Tika-first parser layer [RECOMMENDED] | Format-specific parsers only | Format-specific parsers can give tighter control for page fidelity, but Tika reduces initial ingestion surface area. [CITED: https://tika.apache.org/][ASSUMED] |

**Installation:**
```bash
./gradlew dependencies
```

**Version verification:** The repo already declares Spring Boot 4.0.5 and Spring AI 2.0.0-M4 in `build.gradle`. [VERIFIED: build.gradle]

## Architecture Patterns

### Recommended Project Structure
```text
src/main/java/com/vn/traffic/chatbot/
├── common/
│   ├── config/              # datasource, pgvector, async executor, OpenAPI, validation
│   ├── api/                 # ApiPaths, response envelopes, pagination DTOs
│   ├── error/               # exception types, global handler, problem mapping
│   └── util/                # shared helpers with no domain logic
├── source/
│   ├── api/                 # SourceAdminController, DTOs
│   ├── domain/              # Source, SourceVersion, SourceApproval, enums
│   ├── repo/                # JPA repositories
│   └── service/             # source CRUD and approval logic
├── ingestion/
│   ├── api/                 # upload/url/job controllers
│   ├── domain/              # IngestionJob, job events/status enums
│   ├── parser/              # file/url parsers
│   ├── chunking/            # chunker strategies
│   ├── orchestrator/        # job runner / pipeline coordinator
│   └── service/             # trigger and status services
├── chunk/
│   ├── api/                 # chunk/index inspection endpoints
│   ├── dto/                 # chunk inspection responses
│   └── service/             # raw pgvector/native inspection queries
└── TrafficLawChatbotApplication.java
```
This structure preserves REST-first controller/service separation from the shoes backend while introducing domain boundaries that match the Phase 1 requirement set. [VERIFIED: shoes backend README.md][VERIFIED: 01-CONTEXT.md]

### Pattern 1: Source Registry as System of Record
**What:** Store authoritative source metadata and trust state in relational tables; treat vector chunks as derived artifacts. [VERIFIED: 01-CONTEXT.md][ASSUMED]

**When to use:** Always for legal-source ingestion, because activation/trust/approval are source-level business decisions rather than chunk-level technical details. [VERIFIED: 01-CONTEXT.md][ASSUMED]

**Example:**
```text
source
  -> source_version
     -> ingestion_job
        -> chunk rows in pgvector table with source_id/source_version_id metadata
```
Source concept preserved from reference repo: vector rows should keep metadata-rich linkage rather than existing as anonymous embeddings. [VERIFIED: reference repo VectorStoreEntity.java][VERIFIED: reference repo AbstractIngester.java]

### Pattern 2: Async Job Pipeline with Explicit State Transitions
**What:** `SUBMITTED -> FETCHING -> PARSING -> CHUNKING -> EMBEDDING -> INDEXED -> AWAITING_APPROVAL -> APPROVED_ACTIVE` with terminal failure/cancel states. [VERIFIED: 01-CONTEXT.md][ASSUMED]

**When to use:** For every upload or URL import because the phase explicitly requires fast returns plus background processing. [VERIFIED: 01-CONTEXT.md]

**Example:**
```text
POST /api/v1/admin/sources/pdf
  -> create source row + source_version row + ingestion_job row
  -> return 202 with sourceId and jobId
  -> background worker parses, chunks, embeds, writes vector rows
  -> source remains draft/inactive until admin approval endpoint is called
```
This adapts the reference repo's ingester-manager idea into a first-class job model that better fits REST workflows. [VERIFIED: reference repo IngesterManager.java][VERIFIED: 01-CONTEXT.md]

### Pattern 3: Vector Store as Inspectable Derived Read Model
**What:** Expose admin inspection endpoints over indexed chunks without making the vector table the business source of truth. [VERIFIED: shoes backend AdminVectorStoreController.java][VERIFIED: shoes backend VectorStoreAdminServiceImpl.java]

**When to use:** For chunk preview, metadata audit, filter verification, and later retrieval debugging. [VERIFIED: 01-CONTEXT.md]

**Example:**
```text
GET /api/v1/admin/chunks?sourceId=...&version=...&page=...
GET /api/v1/admin/chunks/{chunkId}
```
The shoes backend already demonstrates page-based vector-document inspection via JDBC + DTO mapping, which is worth preserving. [VERIFIED: shoes backend AdminVectorStoreController.java][VERIFIED: shoes backend VectorStoreAdminServiceImpl.java]

### Pattern 4: Shared API Conventions Layer
**What:** Centralize API path constants, page DTOs, and global exception handling. [VERIFIED: shoes backend ApiPaths.java][VERIFIED: shoes backend PageResponse.java][VERIFIED: shoes backend GlobalExceptionHandler.java]

**When to use:** From Phase 1 onward, because this backend is greenfield and later phases will build on the same conventions. [VERIFIED: source tree]

### Anti-Patterns to Avoid
- **Writing directly to retrieval-eligible state during ingestion:** This violates the locked approval gate and makes unreviewed legal sources searchable. [VERIFIED: 01-CONTEXT.md]
- **Using vector rows as the only provenance store:** Vector metadata alone is not sufficient for workflow state, approvals, source lifecycle, or audit history. [VERIFIED: 01-CONTEXT.md][ASSUMED]
- **Broad web crawling in Phase 1:** Page-by-page import is the locked scope. [VERIFIED: 01-CONTEXT.md]
- **Conflating source status with job status:** A source can be valid but pending approval, while a job can succeed or fail independently. [VERIFIED: 01-CONTEXT.md][ASSUMED]
- **Relying on automatic pgvector schema creation in production:** Spring AI no longer initializes schema by default; planner should decide explicitly between managed migrations and explicit initialize-schema for non-prod. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Vector storage/search | Custom cosine search in SQL or Java | Spring AI PgVectorStore + pgvector extension [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html][CITED: https://github.com/pgvector/pgvector] | Exact/approximate search, metadata filters, and index tuning already exist. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html][CITED: https://github.com/pgvector/pgvector] |
| Schema lifecycle | Ad hoc SQL startup scripts only | Liquibase changelogs [CITED: https://docs.liquibase.com/oss/integration-guide-4-33/springboot/configuration.html] | Phase 1 needs reproducible table creation, extension setup, and later migrations. [CITED: https://docs.liquibase.com/oss/integration-guide-4-33/springboot/configuration.html][ASSUMED] |
| File parsing matrix | Separate parser logic for every file type from day 1 | Tika-first parser abstraction [CITED: https://tika.apache.org/] | PDF, Word, and HTML extraction breadth is already solved by mature tooling. [CITED: https://tika.apache.org/] |
| API error payloads | Per-controller custom error JSON | Global `@RestControllerAdvice` + ProblemDetail pattern [VERIFIED: shoes backend GlobalExceptionHandler.java] | Uniform admin APIs are easier to document and test. [VERIFIED: shoes backend GlobalExceptionHandler.java] |
| Pagination payloads | Raw Spring `Page<?>` serialization | Explicit page DTO wrapper [VERIFIED: shoes backend PageResponse.java] | Stable client contracts and cleaner JSON. [VERIFIED: shoes backend PageResponse.java] |

**Key insight:** In this domain, the hard part is trustworthy provenance and operational control, not inventing custom infrastructure. Reuse the stack for storage, parsing, migrations, and error handling; spend planning effort on source lifecycle and legal-review gates. [VERIFIED: CLAUDE.md][ASSUMED]

## Common Pitfalls

### Pitfall 1: Source approval modeled only as a boolean on chunks
**What goes wrong:** Chunks from a formerly approved source can remain retrievable after status changes or re-ingestion confusion. [ASSUMED]

**Why it happens:** Teams push trust state down into the vector table instead of treating source approval as the governing rule. [ASSUMED]

**How to avoid:** Keep source/source_version approval state in relational tables and derive retrieval eligibility from source-level rules plus metadata filters. [VERIFIED: 01-CONTEXT.md][CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]

**Warning signs:** Manual DB edits to chunk metadata are needed to disable a source. [ASSUMED]

### Pitfall 2: No separate source version record
**What goes wrong:** Re-ingesting an updated legal document overwrites evidence of what was previously indexed. [ASSUMED]

**Why it happens:** Initial designs collapse source identity and ingestion event into one row. [ASSUMED]

**How to avoid:** Create stable `source` identity plus immutable `source_version` rows tied to jobs and chunks. [ASSUMED]

**Warning signs:** Planner cannot explain whether a chunk came from the original upload or a later replacement. [ASSUMED]

### Pitfall 3: Async jobs without durable checkpoints
**What goes wrong:** Restarts leave sources stuck in ambiguous states and operators cannot tell whether parsing, embedding, or indexing failed. [ASSUMED]

**Why it happens:** Background work is implemented as fire-and-forget methods without persisted job transitions. [VERIFIED: 01-CONTEXT.md][ASSUMED]

**How to avoid:** Persist each job step, timestamps, retry count, and terminal error summary in `ingestion_job`. [VERIFIED: 01-CONTEXT.md][ASSUMED]

**Warning signs:** Only logs, not tables, can answer job-status questions. [ASSUMED]

### Pitfall 4: URL ingestion without SSRF and allowlist controls
**What goes wrong:** Admin URL import can fetch unintended internal or non-trusted targets. [ASSUMED]

**Why it happens:** "Trusted pages" is treated as a UI concept instead of an enforced backend policy. [VERIFIED: 01-CONTEXT.md][ASSUMED]

**How to avoid:** Require explicit allowlisted hostnames, normalize URLs, reject private-address targets, and record the final fetched URL. [ASSUMED]

**Warning signs:** The API accepts arbitrary HTTP URLs with no host validation. [ASSUMED]

### Pitfall 5: Depending on default pgvector table setup
**What goes wrong:** Environments diverge because extension/table/index creation is inconsistent. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]

**Why it happens:** Older examples relied on automatic schema creation, but current Spring AI requires opt-in initialization. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]

**How to avoid:** Treat pgvector DDL and extension enablement as migration-managed infrastructure. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html][CITED: https://docs.liquibase.com/oss/integration-guide-4-33/springboot/configuration.html]

**Warning signs:** Local environments work only after manual SQL steps that are not codified. [ASSUMED]

## Code Examples

Verified patterns from official or repo sources:

### Spring AI PgVector builder options
```java
// Source: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .dimensions(1536)
        .distanceType(COSINE_DISTANCE)
        .indexType(HNSW)
        .initializeSchema(true)
        .schemaName("public")
        .vectorTableName("vector_store")
        .maxDocumentBatchSize(10000)
        .build();
}
```

### Metadata-filtered similarity search
```java
// Source: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("The World")
        .topK(TOP_K)
        .similarityThreshold(SIMILARITY_THRESHOLD)
        .filterExpression("author in ['john', 'jill'] && article_type == 'blog'")
        .build());
```
Use the same filter mechanism later for `sourceId`, `trusted`, `active`, and `sourceVersionId` constraints. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html][ASSUMED]

### REST paging wrapper pattern to preserve
```java
// Source: shoes-shopping-online-system-be/src/main/java/com/sba/ssos/dto/response/PageResponse.java
public record PageResponse<T>(
    List<T> content,
    int pageNumber,
    int number,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }
}
```

### Global exception boundary pattern to preserve
```java
// Source: shoes-shopping-online-system-be/src/main/java/com/sba/ssos/controller/advice/GlobalExceptionHandler.java
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    // map field errors into a consistent API response
  }
}
```

## Recommended Backend Module / Domain Structure

### Module boundaries
- **`source` domain:** Owns source identity, source type, canonical origin, title, legal authority metadata, lifecycle flags, and manual approval state. [VERIFIED: 01-CONTEXT.md][ASSUMED]
- **`source_version` subdomain:** Owns every concrete upload/import revision, fetched file/hash, parser outcome, extraction summary, and effective index snapshot. [ASSUMED]
- **`ingestion` domain:** Owns job orchestration, retries, errors, timing, executor handoff, and import triggers. [VERIFIED: 01-CONTEXT.md][ASSUMED]
- **`chunk` admin domain:** Owns read-only inspection of indexed documents/chunks and vector metadata. [VERIFIED: 01-CONTEXT.md][VERIFIED: shoes backend AdminVectorStoreController.java]
- **`retrieval_policy` concern:** Owns trust/activation filters and future retrieval parameter integration, but should not expose public chat APIs in this phase. [VERIFIED: 01-CONTEXT.md][ASSUMED]

### Boundary recommendation
Use `source` as the approval boundary and `source_version` as the indexing boundary. That gives the planner a clean model for re-ingestion, rollbacks, and future legal-versioning work without forcing Phase 1 to answer Phase 2 chat questions. [ASSUMED]

## Recommended Data Model

### Relational tables
| Table | Purpose | Key fields |
|------|---------|------------|
| `kb_source` | Stable identity for a legal source. [ASSUMED] | `id`, `source_type`, `title`, `origin_kind`, `origin_value`, `publisher_name`, `language_code`, `status`, `trusted_state`, `approval_state`, `active_version_id`, `created_at`, `created_by`, `updated_at`, `updated_by`. [ASSUMED] |
| `kb_source_version` | Immutable ingestion revision for a source. [ASSUMED] | `id`, `source_id`, `version_no`, `content_hash`, `ingest_method`, `mime_type`, `file_name`, `storage_uri`, `canonical_url`, `fetched_at`, `parser_name`, `parser_version`, `chunking_strategy`, `chunking_version`, `processing_version`, `index_status`, `summary_json`, `created_at`. [ASSUMED] |
| `kb_ingestion_job` | Durable async pipeline record. [VERIFIED: 01-CONTEXT.md] | `id`, `source_id`, `source_version_id`, `job_type`, `status`, `queued_at`, `started_at`, `finished_at`, `retry_count`, `triggered_by`, `trigger_type`, `error_code`, `error_message`, `step_name`, `step_detail_json`. [VERIFIED: 01-CONTEXT.md][ASSUMED] |
| `kb_source_approval_event` | Audit log of manual approval/trust changes. [ASSUMED] | `id`, `source_id`, `source_version_id`, `action`, `previous_state`, `new_state`, `reason`, `acted_by`, `acted_at`. [ASSUMED] |
| `kb_source_fetch_snapshot` | Optional capture of fetched HTTP metadata for URL imports. [ASSUMED] | `id`, `source_version_id`, `requested_url`, `final_url`, `http_status`, `etag`, `last_modified`, `content_sha256`, `fetched_at`. [ASSUMED] |

### pgvector table recommendation
Use the Spring AI `vector_store` table or a custom-named equivalent, but require metadata fields at minimum: `sourceId`, `sourceVersionId`, `sourceType`, `trusted`, `active`, `approvalState`, `origin`, `locationType`, `pageNumber`, `sectionRef`, `url`, `urlFragment`, `contentHash`, `processingVersion`, and `chunkOrdinal`. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html][VERIFIED: 01-CONTEXT.md][ASSUMED]

### Status enums to plan now
- `SourceStatus`: `DRAFT`, `READY_FOR_REVIEW`, `ACTIVE`, `ARCHIVED`, `DISABLED`. [ASSUMED]
- `TrustedState`: `UNTRUSTED`, `TRUSTED`, `REVOKED`. [ASSUMED]
- `ApprovalState`: `PENDING`, `APPROVED`, `REJECTED`. [ASSUMED]
- `IngestionJobStatus`: `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `RETRYING`. [ASSUMED]
- `IngestionStep`: `FETCH`, `PARSE`, `NORMALIZE`, `CHUNK`, `EMBED`, `INDEX`, `FINALIZE`. [ASSUMED]

### Retrieval eligibility rule
Retrieval should be hard-filtered by relational source state first and vector metadata second: `source.approval_state = APPROVED AND source.trusted_state = TRUSTED AND source.status = ACTIVE`, with matching metadata on indexed chunks for efficient filtering. [VERIFIED: 01-CONTEXT.md][CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html][ASSUMED]

## Ingestion Pipeline Architecture

### Recommended workflow
1. **Create source request** stores baseline provenance and creates `kb_source`, `kb_source_version`, and `kb_ingestion_job`. [VERIFIED: 01-CONTEXT.md][ASSUMED]
2. **Background fetch/parse** reads uploaded file or fetches one trusted URL page. [VERIFIED: 01-CONTEXT.md][CITED: https://tika.apache.org/][ASSUMED]
3. **Normalization** converts extracted content into a canonical internal document model with title, body, sections, location hints, and extraction warnings. [ASSUMED]
4. **Chunking** emits deterministic chunks with location metadata and processing version. [VERIFIED: 01-CONTEXT.md][ASSUMED]
5. **Embedding/indexing** writes chunks to pgvector with metadata filters enabled. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]
6. **Awaiting approval** marks the source indexed but not retrievable. [VERIFIED: 01-CONTEXT.md]
7. **Manual approval endpoint** flips trust/active state and makes the source retrieval-eligible. [VERIFIED: 01-CONTEXT.md]

### Parsing strategy recommendation
- **PDF:** use a page-aware extraction path so each chunk can keep `pageNumber` where possible. [ASSUMED]
- **Word/DOCX:** extract document text and section headings through the shared parser abstraction. [CITED: https://tika.apache.org/][ASSUMED]
- **Structured regulation docs:** support a typed ingest adapter that preserves article/chapter/section fields instead of flattening everything to plain text immediately. [ASSUMED]
- **Trusted URL page import:** fetch one URL, normalize HTML to text, capture final URL, title, and heading anchors. [VERIFIED: 01-CONTEXT.md][ASSUMED]

### Chunking recommendation
Use deterministic chunking with overlap and strong metadata, but chunk on semantic boundaries where available: article/section for regulations, heading blocks for HTML, and page-plus-paragraph windows for PDFs. The reference repo already treats chunking as a dedicated concern, which should be preserved. [VERIFIED: reference repo Chunker.java][VERIFIED: reference repo AbstractIngester.java][ASSUMED]

### Retry and idempotency recommendation
Use `content_hash + processing_version` to detect repeated content and avoid duplicate active versions. Keep re-run support at the `source_version` or `job` level rather than mutating chunks in place. The reference repo uses content hashing in metadata, which is a useful concept to preserve. [VERIFIED: reference repo AbstractIngester.java]

## Recommended REST API Surface

Use a shoes-backend style API path constant layer and keep Phase 1 APIs under `/api/v1/admin`. [VERIFIED: shoes backend ApiPaths.java][VERIFIED: PROJECT.md]

### Source registry
| Method | Path | Purpose |
|-------|------|---------|
| `POST` | `/api/v1/admin/sources/upload` | Create a source from uploaded file and enqueue ingestion. [ASSUMED] |
| `POST` | `/api/v1/admin/sources/url` | Create a source from one trusted page URL and enqueue ingestion. [VERIFIED: 01-CONTEXT.md][ASSUMED] |
| `GET` | `/api/v1/admin/sources` | List/filter sources by type, status, trusted state, approval state. [ASSUMED] |
| `GET` | `/api/v1/admin/sources/{sourceId}` | Source detail with current version/job summary. [ASSUMED] |
| `PATCH` | `/api/v1/admin/sources/{sourceId}` | Update editable metadata only; not the indexed content itself. [ASSUMED] |
| `POST` | `/api/v1/admin/sources/{sourceId}/approve` | Explicit approval gate. [VERIFIED: 01-CONTEXT.md][ASSUMED] |
| `POST` | `/api/v1/admin/sources/{sourceId}/reject` | Explicit rejection with reason. [ASSUMED] |
| `POST` | `/api/v1/admin/sources/{sourceId}/activate` | Mark approved source active. [VERIFIED: 01-CONTEXT.md][ASSUMED] |
| `POST` | `/api/v1/admin/sources/{sourceId}/deactivate` | Remove from retrieval eligibility without deleting history. [ASSUMED] |
| `POST` | `/api/v1/admin/sources/{sourceId}/reingest` | Create a new source version/job. [ASSUMED] |

### Ingestion jobs
| Method | Path | Purpose |
|-------|------|---------|
| `GET` | `/api/v1/admin/ingestion/jobs` | Paged job list with status/error filters. [ASSUMED] |
| `GET` | `/api/v1/admin/ingestion/jobs/{jobId}` | Full job timeline and failure details. [ASSUMED] |
| `POST` | `/api/v1/admin/ingestion/jobs/{jobId}/retry` | Retry failed job. [ASSUMED] |
| `POST` | `/api/v1/admin/ingestion/jobs/{jobId}/cancel` | Cancel queued/running job if supported. [ASSUMED] |

### Chunk/index inspection
| Method | Path | Purpose |
|-------|------|---------|
| `GET` | `/api/v1/admin/chunks` | Page/filter indexed chunks by source, version, page, or metadata. [ASSUMED] |
| `GET` | `/api/v1/admin/chunks/{chunkId}` | Chunk content + metadata for provenance inspection. [ASSUMED] |
| `DELETE` | `/api/v1/admin/chunks/{chunkId}` | Optional admin cleanup endpoint; low priority for Phase 1. [ASSUMED] |
| `GET` | `/api/v1/admin/index/summary` | Counts by source, status, trusted state, and recent job outcomes. [ASSUMED] |

### API boundaries to avoid now
- No public chat endpoints in this phase. [VERIFIED: ROADMAP.md][VERIFIED: 01-CONTEXT.md]
- No broad search/retrieval endpoint unless the planner explicitly needs an admin validation endpoint. [VERIFIED: 01-CONTEXT.md]
- No parameter/chat-log/check CRUD in Phase 1, but keep package space and API conventions compatible with later phases. [VERIFIED: ROADMAP.md][VERIFIED: CLAUDE.md]

## jmix-ai-backend Patterns to Preserve vs Adapt

### Preserve
- **Dedicated ingester abstraction and manager concept.** The reference repo has `Ingester`, `AbstractIngester`, and `IngesterManager`, which is the right conceptual seam for multiple source types. [VERIFIED: reference repo IngesterManager.java][VERIFIED: reference repo AbstractIngester.java]
- **Metadata-rich vector documents.** `VectorStoreEntity` and `AbstractIngester#createMetadata` show that chunk records should carry source/type/hash/update metadata. [VERIFIED: reference repo VectorStoreEntity.java][VERIFIED: reference repo AbstractIngester.java]
- **Vector-store inspection as an explicit admin concern.** Both the reference repo and shoes backend expose index visibility rather than hiding it. [VERIFIED: reference repo VectorStoreView.java][VERIFIED: shoes backend AdminVectorStoreController.java]
- **Parameters/chat-log/checks as first-class future domains.** Even though Phase 1 should not implement them, planner should avoid a source-only design that makes those later domains awkward. [VERIFIED: CLAUDE.md][VERIFIED: reference repo Parameters.java][VERIFIED: reference repo ChatLog.java]

### Adapt
- **Replace Jmix entity/store wiring with conventional Spring REST modules.** The reference repo uses Jmix multi-store configuration for pgvector; this repo should adapt the concept, not the framework-specific implementation. [VERIFIED: reference repo PgvectorStoreConfiguration.java][VERIFIED: PROJECT.md]
- **Replace UI-triggered ingestion with explicit admin REST endpoints and background jobs.** The reference repo's manager methods are synchronous strings; this phase needs durable job records and 202-style workflows. [VERIFIED: reference repo IngesterManager.java][VERIFIED: 01-CONTEXT.md]
- **Adopt shoes-backend controller/service/DTO/error patterns.** The planner should copy that structure, not Jmix view classes. [VERIFIED: PROJECT.md][VERIFIED: shoes backend README.md][VERIFIED: shoes backend GlobalExceptionHandler.java]

### Useful external reference observations
- The reference repo uses separate `main` and `pgvector` datasources, which proves the concept but is heavier than necessary for this phase. [VERIFIED: reference repo application.properties][VERIFIED: reference repo PgvectorStoreConfiguration.java]
- The reference repo stores metadata as JSON and uses content hashing in ingestion logic; both ideas are worth preserving. [VERIFIED: reference repo VectorStoreEntity.java][VERIFIED: reference repo AbstractIngester.java]
- The reference repo's current vector chunk size constant is `30_000` characters, which is too implementation-specific to lock as-is for legal sources, but it confirms chunking needs explicit policy. [VERIFIED: reference repo AbstractIngester.java]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Spring AI pgvector examples that implicitly initialized schema | Current docs require explicit opt-in for schema initialization. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] | Current Spring AI reference states this as a breaking change. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] | Planner should not rely on auto-created extension/table/index behavior. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] |
| Vector search as exact scan only | pgvector now supports HNSW and IVFFlat approximate indexes in addition to exact search. [CITED: https://github.com/pgvector/pgvector] | Official pgvector README as checked in this session. [CITED: https://github.com/pgvector/pgvector] | Planner can leave room for index tuning instead of designing for brute-force scan only. [CITED: https://github.com/pgvector/pgvector] |
| Jmix UI-driven admin flows | This project explicitly wants REST-first Spring backend flows. [VERIFIED: PROJECT.md][VERIFIED: CLAUDE.md] | Locked at project level. [VERIFIED: PROJECT.md] | APIs and DTOs should be designed for Next.js/admin consumption, not server-rendered Jmix screens. [VERIFIED: PROJECT.md] |

**Deprecated/outdated:**
- Relying on Jmix-specific view/entity-store patterns for this repo is outdated for this project direction. [VERIFIED: PROJECT.md][VERIFIED: CLAUDE.md]

## Open Decisions the Planner Should Lock

1. **One database/schema design vs split relational/vector schema**
   - Recommendation: one PostgreSQL instance with migration-managed relational tables plus a pgvector table in the same database. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html][ASSUMED]
   - Why lock now: it affects datasource config, migrations, local dev, and inspection queries. [ASSUMED]

2. **Source versioning depth in Phase 1**
   - Recommendation: include `source_version` now even if the UI only shows latest. [ASSUMED]
   - Why lock now: later re-ingestion is much harder without it. [ASSUMED]

3. **PDF provenance fidelity target**
   - Recommendation: require page-number capture for PDF chunks whenever technically extractable. [ASSUMED]
   - Why lock now: parser and chunker selection depend on it. [ASSUMED]

4. **Whether to include an admin-only retrieval smoke endpoint**
   - Recommendation: optional, only if needed for Phase 1 verification; keep it clearly admin/internal. [VERIFIED: 01-CONTEXT.md][ASSUMED]
   - Why lock now: affects API scope and test plan. [ASSUMED]

5. **Raw file storage location**
   - Recommendation: choose local filesystem or object storage abstraction now, even if Phase 1 starts with local storage. [ASSUMED]
   - Why lock now: source-version records need a stable `storage_uri`. [ASSUMED]

## Open Questions

1. **Should Phase 1 physically store raw uploaded files, or only extracted text plus metadata?**
   - What we know: provenance and re-ingestion strongly benefit from raw artifact retention. [ASSUMED]
   - What's unclear: no project decision yet defines storage medium or retention policy. [VERIFIED: planning docs read in this session; no explicit retention rule found]
   - Recommendation: lock raw artifact retention with a storage abstraction in PLAN.md. [ASSUMED]

2. **How much legal-source metadata beyond baseline provenance belongs in Phase 1?**
   - What we know: baseline provenance is locked; legal grounding is critical. [VERIFIED: 01-CONTEXT.md][VERIFIED: CLAUDE.md]
   - What's unclear: whether law code, issuing authority, issue date, effective date, and citation labels should be Phase 1 fields or deferred. [ASSUMED]
   - Recommendation: at least reserve optional fields for issuing authority and citation label now to avoid schema churn. [ASSUMED]

3. **Should chunk inspection allow destructive actions in Phase 1?**
   - What we know: inspection is required; deletion is not explicitly required. [VERIFIED: REQUIREMENTS.md][VERIFIED: 01-CONTEXT.md]
   - What's unclear: whether direct chunk deletion helps or harms provenance discipline. [ASSUMED]
   - Recommendation: prefer source/version re-ingestion and deactivation over chunk-by-chunk mutation. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Gradle wrapper | Build/test execution | ✓ [VERIFIED: environment probe] | 9.4.1 [VERIFIED: environment probe] | — |
| Node.js | GSD tooling only | ✓ [VERIFIED: environment probe] | v24.14.0 [VERIFIED: environment probe] | — |
| Docker | Local PostgreSQL/pgvector setup if used | ✓ [VERIFIED: environment probe] | 28.0.4 [VERIFIED: environment probe] | Native local Postgres install [ASSUMED] |
| `psql` CLI | Manual DB inspection and extension setup verification | ✗ [VERIFIED: environment probe] | — | Use application migrations or Docker exec into Postgres container. [ASSUMED] |
| Java runtime | App execution | Unclear [VERIFIED: environment probe] | Could not be confirmed from shell output, though Gradle wrapper runs. [VERIFIED: environment probe] | Planner should add explicit local JDK 25 verification step. [VERIFIED: build.gradle][ASSUMED] |
| PostgreSQL server | Persistent storage | Unclear [VERIFIED: repo evidence] | DB details referenced by user in `mcp.json`, but that file was not accessible at the provided path during research. [VERIFIED: missing mcp.json read] | Use Dockerized Postgres + pgvector for local bootstrap. [ASSUMED] |

**Missing dependencies with no fallback:**
- None confirmed at research time, but local JDK 25 and reachable PostgreSQL still need explicit verification before implementation. [VERIFIED: build.gradle][VERIFIED: environment probe]

**Missing dependencies with fallback:**
- `psql` CLI is missing; migration-driven setup and container exec are viable fallbacks. [VERIFIED: environment probe][ASSUMED]

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 via Spring Boot test starter. [VERIFIED: src/test/java/com/vn/traffic/chatbot/TrafficLawChatbotApplicationTests.java][VERIFIED: build.gradle] |
| Config file | none detected yet. [VERIFIED: source tree] |
| Quick run command | `D:/ai/traffic-law-chatbot/gradlew test --tests "*Source*"` after Phase 1 test classes exist. [ASSUMED] |
| Full suite command | `D:/ai/traffic-law-chatbot/gradlew test`. [VERIFIED: build.gradle] |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PLAT-01 | Admin REST endpoints expose source/ingestion/chunk operations and validation errors. [VERIFIED: REQUIREMENTS.md] | web slice | `D:/ai/traffic-law-chatbot/gradlew test --tests "*ControllerTest"` [ASSUMED] | ❌ Wave 0 |
| PLAT-03 | Source metadata, jobs, and vector-index integration persist reliably. [VERIFIED: REQUIREMENTS.md] | integration | `D:/ai/traffic-law-chatbot/gradlew test --tests "*RepositoryIT"` [ASSUMED] | ❌ Wave 0 |
| PLAT-04 | App starts with Java 25-targeted build and core config loads. [VERIFIED: REQUIREMENTS.md] | smoke | `D:/ai/traffic-law-chatbot/gradlew test --tests "*ApplicationTests"` [VERIFIED: existing test] | ✅ |
| KNOW-01 | PDF ingestion creates source/version/job and page-aware chunks. [VERIFIED: REQUIREMENTS.md] | integration | `D:/ai/traffic-law-chatbot/gradlew test --tests "*PdfIngestionIT"` [ASSUMED] | ❌ Wave 0 |
| KNOW-02 | Word ingestion creates source/version/job and extractable chunks. [VERIFIED: REQUIREMENTS.md] | integration | `D:/ai/traffic-law-chatbot/gradlew test --tests "*WordIngestionIT"` [ASSUMED] | ❌ Wave 0 |
| KNOW-03 | Structured regulation ingest preserves section/article provenance. [VERIFIED: REQUIREMENTS.md] | unit + integration | `D:/ai/traffic-law-chatbot/gradlew test --tests "*StructuredDoc*"` [ASSUMED] | ❌ Wave 0 |
| KNOW-04 | Trusted URL import handles one page, captures final URL metadata, and blocks disallowed hosts. [VERIFIED: REQUIREMENTS.md][ASSUMED] | integration | `D:/ai/traffic-law-chatbot/gradlew test --tests "*UrlIngestionIT"` [ASSUMED] | ❌ Wave 0 |
| KNOW-05 | Provenance metadata is retained end-to-end from source to chunks. [VERIFIED: REQUIREMENTS.md] | integration | `D:/ai/traffic-law-chatbot/gradlew test --tests "*ProvenanceIT"` [ASSUMED] | ❌ Wave 0 |
| KNOW-06 | Retrieval eligibility excludes unapproved, inactive, or untrusted sources. [VERIFIED: REQUIREMENTS.md] | integration | `D:/ai/traffic-law-chatbot/gradlew test --tests "*EligibilityIT"` [ASSUMED] | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** targeted controller/service/repository test selection for the domain being touched. [ASSUMED]
- **Per wave merge:** `D:/ai/traffic-law-chatbot/gradlew test`. [VERIFIED: build.gradle]
- **Phase gate:** full suite green plus one ingestion happy-path integration for each source class implemented in Phase 1. [ASSUMED]

### Wave 0 Gaps
- [ ] `src/test/java/com/vn/traffic/chatbot/source/api/SourceAdminControllerTest.java` — validates CRUD/approval API boundary. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/ingestion/api/IngestionJobControllerTest.java` — validates async trigger/status endpoints. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/chunk/api/ChunkInspectionControllerTest.java` — validates index inspection pagination/filtering. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/ingestion/PdfIngestionIT.java` — covers KNOW-01. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/ingestion/WordIngestionIT.java` — covers KNOW-02. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/ingestion/StructuredDocumentIngestionIT.java` — covers KNOW-03. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/ingestion/UrlIngestionIT.java` — covers KNOW-04 and trust-host validation. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/retrieval/RetrievalEligibilityIT.java` — covers KNOW-06 without exposing public chat APIs. [ASSUMED]
- [ ] Testcontainers-based PostgreSQL + pgvector test support is recommended if the planner wants realistic DB/index behavior. [ASSUMED]

## Security Domain

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no for end-user flows in this phase; admin auth design is deferred. [VERIFIED: REQUIREMENTS.md][ASSUMED] | Reserve admin namespace for later security wiring. [ASSUMED] |
| V3 Session Management | no for this backend-only phase. [ASSUMED] | — |
| V4 Access Control | yes at endpoint-boundary design level because these are admin operations. [ASSUMED] | Keep all source/ingestion/chunk endpoints under `/api/v1/admin/**` and plan for later admin-only enforcement. [VERIFIED: shoes backend ApiPaths.java][ASSUMED] |
| V5 Input Validation | yes. [ASSUMED] | Bean Validation, ProblemDetail-based error handling, strict enum/URL/file validation. [VERIFIED: shoes backend GlobalExceptionHandler.java][ASSUMED] |
| V6 Cryptography | limited. [ASSUMED] | Use provider-managed secret handling for model/API keys; never hand-roll hashing beyond non-security content fingerprints. [VERIFIED: reference repo AbstractIngester.java][ASSUMED] |

### Known Threat Patterns for Spring REST + document ingestion
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SSRF through URL ingestion | Information Disclosure / Elevation | Allowlist trusted domains, block private IP ranges, record final URL, enforce one-page fetch semantics. [ASSUMED] |
| Malicious oversized document upload | Denial of Service | Request size limits, async jobs, parser timeouts, file-type validation before parse. [ASSUMED] |
| Prompt-injection-like hostile content in imported pages | Tampering | Treat imported text as untrusted until manual approval; never auto-activate. [VERIFIED: 01-CONTEXT.md][ASSUMED] |
| SQL injection through ad hoc metadata filters | Tampering | Use parameterized JDBC queries and Spring AI filter expressions; enable schema validation for custom pgvector schema/table names if used. [VERIFIED: shoes backend VectorStoreAdminServiceImpl.java][CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] |
| Orphaned retrieval-eligible chunks after source deactivation | Tampering | Recompute eligibility from source state and keep source/version linkage in metadata. [VERIFIED: 01-CONTEXT.md][ASSUMED] |

## Sources

### Primary (HIGH confidence)
- Local repo `D:/ai/traffic-law-chatbot/CLAUDE.md` - project rules and phase constraints. [VERIFIED: CLAUDE.md]
- Local repo `D:/ai/traffic-law-chatbot/.planning/PROJECT.md` - project scope and architectural direction. [VERIFIED: PROJECT.md]
- Local repo `D:/ai/traffic-law-chatbot/.planning/REQUIREMENTS.md` - requirement mapping for Phase 1. [VERIFIED: REQUIREMENTS.md]
- Local repo `D:/ai/traffic-law-chatbot/.planning/ROADMAP.md` - phase goal and success criteria. [VERIFIED: ROADMAP.md]
- Local repo `D:/ai/traffic-law-chatbot/.planning/phases/01-backend-foundation-knowledge-base/01-CONTEXT.md` - locked implementation decisions. [VERIFIED: 01-CONTEXT.md]
- Local repo `D:/ai/traffic-law-chatbot/build.gradle` - declared stack versions and Java target. [VERIFIED: build.gradle]
- Official Spring Boot system requirements - Java support and Spring Framework baseline. [CITED: https://docs.spring.io/spring-boot/system-requirements.html]
- Official Spring AI pgvector reference - schema init, metadata filtering, index types, configuration. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]
- Official pgvector README - extension setup and HNSW/IVFFlat/indexing details. [CITED: https://github.com/pgvector/pgvector]
- Official Apache Tika site - current release and broad parsing support. [CITED: https://tika.apache.org/]
- Official Liquibase Spring Boot integration guide - migration/configuration guidance. [CITED: https://docs.liquibase.com/oss/integration-guide-4-33/springboot/configuration.html]

### Secondary (MEDIUM confidence)
- External reference repo `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend` - preserved concepts for ingesters, vector metadata, and future admin domains. [VERIFIED: reference repo files read in this session]
- External reference repo `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be` - preserved REST/controller/service/error/paging conventions. [VERIFIED: shoes backend files read in this session]

### Tertiary (LOW confidence)
- Parser-specific recommendation for page-aware PDF extraction beyond Tika-first abstraction. [ASSUMED]
- Exact enum naming and storage details for source/version/job records. [ASSUMED]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Liquibase should be introduced in Phase 1 rather than later. | Standard Stack | Medium - planner may stage migrations one phase later. |
| A2 | One PostgreSQL database is preferable to split relational/vector stores for this repo. | Open Decisions / Standard Stack | Medium - datasource/config design could change. |
| A3 | Phase 1 should include a separate `source_version` table now. | Data Model | High - re-ingestion and audit design would change. |
| A4 | PDF ingestion should target page-aware chunk provenance whenever possible. | Ingestion Pipeline | High - parser/chunker selection and schema fields depend on it. |
| A5 | Testcontainers-backed PostgreSQL + pgvector integration testing is the best validation path. | Validation Architecture | Medium - local test strategy could differ. |
| A6 | Raw artifact retention should be planned even if not fully implemented in Phase 1. | Open Questions | Medium - storage design may be simplified. |

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - most recommendations are anchored in repo-declared dependencies and official Spring/pgvector docs. [VERIFIED: build.gradle][CITED: https://docs.spring.io/spring-boot/system-requirements.html][CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]
- Architecture: MEDIUM - structure is strongly informed by repo constraints and reference repos, but some module boundaries are prescriptive design recommendations. [VERIFIED: PROJECT.md][VERIFIED: 01-CONTEXT.md][VERIFIED: shoes backend README.md][ASSUMED]
- Pitfalls: MEDIUM - grounded in locked trust/provenance requirements and current vector-store behavior, but some operational failure modes are extrapolated. [VERIFIED: 01-CONTEXT.md][CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html][ASSUMED]

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 for repo-local findings; re-check library docs before implementation if Phase 1 starts later. [ASSUMED]
