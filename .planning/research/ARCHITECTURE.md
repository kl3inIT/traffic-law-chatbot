# Architecture Patterns

**Domain:** Vietnam traffic-law RAG chatbot
**Researched:** 2026-04-07
**Overall confidence:** HIGH for backend/service layering and Spring AI fit, MEDIUM for frontend integration details

## Executive Recommendation

Architect the system as a **modular monolith with explicit AI domain modules** inside a Spring Boot REST backend, paired with a **single Next.js App Router frontend** that contains both public chat and admin screens. This best preserves the conceptual capabilities of `jmix-ai-backend` while aligning implementation style with the migrated shoes system's controller/service/DTO/response-wrapper pattern.

The key design choice is to separate the system into **six backend capability slices** rather than one generic “AI service”:
1. **Chat orchestration**
2. **Case analysis orchestration**
3. **Retrieval + reranking**
4. **Ingestion + vector store administration**
5. **Parameter/version management**
6. **Observability + answer checks**

This keeps the strong ideas from `jmix-ai-backend`—active parameters, ingester manager, vector store management, chat logs, answer checks, RAG tools, and chat memory—but exposes them through REST endpoints shaped like the shoes backend: `Controller -> Service -> Repository/Client`, DTO-based contracts, and consistent `ResponseGeneral<T>` / `PageResponse<T>` wrappers.

The frontend should call the Spring backend directly for almost all authenticated and admin operations. Use **Next.js Route Handlers only as thin BFF endpoints when the browser should not talk directly to the backend**, such as streaming adaptation, secure cookie/session mediation, or file-upload token exchange. Do not duplicate business logic in Next.js.

## Recommended Architecture

```text
Next.js App Router
  ├─ Public chat UI
  ├─ Admin sidebar UI
  ├─ Route handlers (thin only: auth/session/stream proxy when needed)
  └─ API client layer
          │
          ▼
Spring Boot REST backend
  ├─ ai.chat            # regular legal Q&A chat
  ├─ ai.case            # structured case-analysis workflow
  ├─ ai.rag             # retrieval, filtering, reranking, grounding
  ├─ ai.ingestion       # import/reindex/update source documents
  ├─ ai.vectorstore     # browse/update/delete vector documents
  ├─ ai.parameters      # active YAML/JSON prompt-model-tool settings
  ├─ ai.checks          # regression/evaluation runs
  ├─ ai.chatlog         # logs, traces, token usage, sources
  ├─ knowledge          # source registry, crawl/file metadata, provenance
  └─ common             # DTOs, API paths, exceptions, security, config
          │
          ├─ PostgreSQL relational schema
          │    ├─ parameters
          │    ├─ source registry
          │    ├─ ingestion jobs
          │    ├─ chat logs
          │    ├─ check defs / runs / results
          │    └─ case analysis records (optional if persisted)
          │
          ├─ PostgreSQL + pgvector
          │    └─ embeddings + chunk metadata
          │
          ├─ Document/object storage
          │    └─ uploaded PDFs, Word docs, normalized text, snapshots
          │
          ├─ LLM / embedding providers
          │    ├─ chat model
          │    ├─ embedding model
          │    └─ evaluator / reranker model
          │
          └─ optional crawler/parser adapters
               ├─ website fetcher
               ├─ PDF/Word extraction
               └─ structured regulation importer
```

## Why this architecture fits the references

### From `jmix-ai-backend`, preserve these concepts

The reference backend clearly centers on:
- a `ChatController` delegating to a `Chat` service
- `ChatImpl` orchestrating prompt building, tool use, retrieval, reranking, memory, and logging
- `IngesterManager` invoking multiple source-specific ingesters
- active `Parameters` records loaded from the database
- `ChatLogManager` persisting logs/tokens/sources
- `CheckRunner` reusing chat logic to evaluate answer quality
- pgvector + PostgreSQL as the storage foundation

Those concepts should remain intact.

### From the shoes backend, adopt these structural patterns

The shoes backend shows the target REST shape:
- domain-specific controllers with `@RequestMapping(ApiPaths...)`
- service interfaces/implementations behind controllers
- `ResponseGeneral<T>` response envelopes and `PageResponse<T>` for pagination
- admin endpoints separated from public endpoints
- explicit DTO request/response models instead of exposing entities

So the migration target is not “rewrite the AI logic from scratch,” but rather “move Jmix concepts into shoes-style Spring REST modules.”

## Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| Next.js Chat UI | Public Vietnamese-first chat experience, thread management, source rendering, answer citations, case-analysis form UX | Next.js API client, optional route handler, Spring `/api/v1/chat`, `/api/v1/case-analysis` |
| Next.js Admin UI | Admin sidebar screens for sources, ingestion jobs, vector docs, parameters, chat logs, checks | Spring admin/public AI REST endpoints |
| Next.js Route Handlers | Thin proxy/BFF only for streaming, cookie mediation, or hiding backend URL/secrets | Browser, Spring backend |
| `ai.chat` controller/service | General legal Q&A endpoint; validate request, load active chat params, invoke orchestration, return answer + citations + logs | `ai.parameters`, `ai.rag`, `ai.chatlog`, chat memory, LLM provider |
| `ai.case` controller/service | Structured real-life scenario analysis; extract facts, classify issue, run targeted retrieval, produce legal guidance sections | `ai.parameters`, `ai.rag`, `ai.chatlog`, optional case classifier/rules service |
| `ai.rag` services/tools | Query transformation, retrieval, metadata filtering, reranking, grounding assembly, citation extraction | pgvector, parameter reader, embeddings/LLM clients |
| `ai.ingestion` controller/service | Run ingestion jobs by source or source type; parse, chunk, embed, upsert | knowledge source registry, parser adapters, pgvector, job tables |
| `ai.vectorstore` admin service | List, inspect, delete, refresh, and filter vector documents/chunks | pgvector and knowledge/source metadata |
| `knowledge` module | Canonical record of trusted sources, provenance, source type, fetch config, status | ingestion, vectorstore admin, admin UI |
| `ai.parameters` service | Store multiple prompt/model configs; activate one per target type (`CHAT`, `CASE_ANALYSIS`, `CHECKS`, maybe `INGESTION`) | chat, case, checks, rag services |
| `ai.chatlog` service | Persist answer traces, sources, token usage, timings, errors, thread identifiers | chat, case, streaming adapter, admin logs UI |
| `ai.checks` service | Maintain check definitions, trigger runs, compare actual vs reference answer, score regressions | chat/case service, evaluator model, parameter versions |
| Chat memory service | Maintain short-term conversational context for each thread/conversation | chat service, Spring AI `ChatMemory` / JDBC repository |
| Parser adapters | Extract text + structure from PDF/Word/HTML/legal documents | ingestion service |
| LLM client layer | Encapsulate chat model, embedding model, evaluator/reranker model configuration | chat, case, ingestion, checks |

## Module Layout Recommendation for Spring Boot

Use a package structure like this:

```text
com.vn.traffic.chatbot
  configuration/
  constant/
  dto/
    request/
      ai/
      admin/
    response/
      ai/
      admin/
  controller/
    ai/
      ChatController.java
      CaseAnalysisController.java
      SearchController.java
    admin/
      AdminKnowledgeSourceController.java
      AdminIngestionController.java
      AdminVectorStoreController.java
      AdminParameterController.java
      AdminChatLogController.java
      AdminCheckController.java
  service/
    ai/
      ChatService.java
      CaseAnalysisService.java
      SearchService.java
    admin/
      IngestionService.java
      VectorStoreAdminService.java
      ParameterService.java
      ChatLogAdminService.java
      CheckAdminService.java
    knowledge/
    retrieval/
    llm/
  repository/
  entity/
  mapper/
  security/
  exception/
  utils/
```

This mirrors the shoes backend more closely than the Jmix source tree, while still preserving the AI subdomains.

## Data Flow

### 1. Public chat flow

```text
User -> Next.js chat screen
     -> POST Spring /api/v1/chat
     -> ChatController validates DTO
     -> ChatService loads active CHAT parameters
     -> ChatService restores conversation memory using conversation/thread id
     -> RetrievalOrchestrator builds retrieval plan
     -> RAG tools query pgvector with filters (law category, source type, document status)
     -> Post-retrieval filtering removes low-quality or invalid chunks
     -> Reranker reorders candidates
     -> AnswerComposer calls chat model with system prompt + memory + grounded context
     -> CitationExtractor builds source list
     -> ChatLogService persists logs/tokens/sources/timing
     -> ResponseGeneral<ChatResponseDto>
     -> Next.js renders answer, citations, follow-ups
```

### 2. Case-analysis flow

This must be separate from normal chat logic, even if they share the same lower layers.

```text
User -> Next.js case-analysis form/chat
     -> POST Spring /api/v1/case-analysis
     -> CaseAnalysisController validates scenario payload
     -> CaseAnalysisService loads active CASE_ANALYSIS parameters
     -> FactExtractor structures the narrative
     -> IssueClassifier identifies topics (license, alcohol, accident, vehicle papers, lane violation, etc.)
     -> RetrievalOrchestrator performs targeted multi-query retrieval
     -> EvidenceAssembler groups retrieved chunks by legal issue
     -> AnswerComposer generates structured output:
           facts understood
           relevant legal basis
           likely violation/penalty
           required documents/procedure
           uncertainty / assumptions
           suggested next steps
     -> AnswerGuard checks for missing citations / unsupported claims
     -> ChatLogService persists detailed trace
     -> ResponseGeneral<CaseAnalysisResponseDto>
```

### 3. Ingestion flow

```text
Admin -> Next.js admin source screen
      -> POST /api/admin/knowledge-sources or upload/create source
      -> source registry saved in PostgreSQL
      -> POST /api/admin/ingestion/jobs or /run/{type}
      -> IngestionService selects adapter by source type
      -> parser fetches/extracts text
      -> normalizer cleans headers/footers/duplicate clauses
      -> legal chunker creates semantically useful chunks
      -> embedding model generates vectors
      -> VectorStoreWriter upserts into pgvector
      -> source/chunk metadata stored
      -> ingestion job status updated
      -> admin UI shows success/fail counts
```

### 4. Answer-check flow

```text
Admin -> Next.js checks screen
      -> POST /api/admin/checks/runs
      -> CheckAdminService creates run for active parameter set
      -> runner replays check definitions through ChatService and/or CaseAnalysisService
      -> evaluator model scores semantic alignment and groundedness heuristics
      -> failures and logs stored in DB
      -> admin UI compares runs over time
```

### 5. Raw retrieval debug flow

Keep a search endpoint for retrieval debugging, similar to the reference systems.

```text
Admin -> POST /api/v1/search
      -> SearchService similarity search only
      -> return chunks + scores + source metadata
```

This is important for diagnosing whether failures come from retrieval or generation.

## Where Each Required Concern Should Live

### Ingestion
Live in `admin` + `knowledge` + `service/ingestion` layers, not inside chat.

- `AdminKnowledgeSourceController`: create/update trusted source definitions
- `AdminIngestionController`: run ingestion/reindex jobs
- `IngestionService`: orchestration
- `Ingester` implementations per source type:
  - `PdfLegalIngester`
  - `WordLegalIngester`
  - `WebsiteLegalIngester`
  - `StructuredRegulationIngester`
- `KnowledgeSourceRepository`, `IngestionJobRepository`

Reason: ingestion is a back-office content pipeline, not a runtime chat concern.

### Vector store management
Live in a dedicated admin module.

- `AdminVectorStoreController`
- `VectorStoreAdminService`
- DTOs for document/chunk inspection, delete, refresh, and filter operations

Reason: this preserves the Jmix “vector store view” concept while expressing it in shoes-style REST.

### Parameters
Live in a dedicated parameter service with target types.

Recommended target types:
- `CHAT`
- `CASE_ANALYSIS`
- `ANSWER_CHECK`
- optionally `INGESTION` for chunking/parser settings if you want DB-managed runtime tuning

Reason: `jmix-ai-backend` already demonstrates that parameter versioning and activation is a first-class capability, and both chat and checks depend on it.

### Chat logs
Live in `ai.chatlog` as a separate persistence concern.

Persist at least:
- conversation/thread id
- endpoint type (`chat` vs `case-analysis`)
- user input
- answer text
- retrieved sources
- prompt/completion tokens
- duration
- parameter version used
- retrieval diagnostics
- error state

Reason: Spring AI chat memory is not full audit history; official docs distinguish memory from history. Keep chat memory for model context, and chat logs for admin observability/audit.

### Answer checks
Live in `ai.checks` and call existing chat/case services as clients.

Do not create a completely separate generation pipeline for checks. Reuse production answer paths, otherwise check results are misleading.

Recommended subparts:
- `CheckDefinition`
- `CheckRun`
- `CheckResult`
- `AnswerEvaluationService`
- `CheckRunner`

### Case-analysis logic
Live in its own `ai.case` module, not hidden inside generic chat.

It should reuse retrieval and generation primitives, but expose a distinct application service because it needs:
- structured input normalization
- scenario fact extraction
- issue classification
- multi-part output schema
- stricter answer guards around assumptions and ambiguity

This is one of the most important boundaries in the system.

## Recommended REST Surface

Follow shoes-style route naming and wrappers.

### Public/user-facing
- `POST /api/v1/chat`
- `POST /api/v1/chat/stream` or proxied through Next.js route handler
- `POST /api/v1/case-analysis`
- `POST /api/v1/search` (admin/debug only in UI, but can still be a versioned API)

### Admin-facing
- `GET/POST/PUT/DELETE /api/admin/knowledge-sources`
- `GET/POST /api/admin/ingestion/jobs`
- `POST /api/admin/ingestion/run/{sourceId|type}`
- `GET/DELETE /api/admin/vector-store/documents`
- `GET/POST/PUT/DELETE /api/admin/ai-parameters`
- `POST /api/admin/ai-parameters/{id}/activate`
- `GET /api/admin/chat-logs`
- `GET /api/admin/chat-logs/{id}`
- `GET/POST/PUT/DELETE /api/admin/checks/definitions`
- `POST /api/admin/checks/runs`
- `GET /api/admin/checks/runs`
- `GET /api/admin/checks/runs/{runId}/results`

## Patterns to Follow

### Pattern 1: Orchestration service above reusable AI primitives
**What:** Controllers should call application services (`ChatService`, `CaseAnalysisService`) that orchestrate lower-level retrieval, memory, and model clients.

**When:** Always. Do not let controllers assemble prompts or talk to vector stores directly.

**Why:** This preserves testability and keeps REST structure aligned with the shoes migration style.

**Example:**
```typescript
ChatController -> ChatService
CaseAnalysisController -> CaseAnalysisService
ChatService -> ParameterService + RetrievalOrchestrator + ChatLogService
```

### Pattern 2: Retrieval and generation are separate services
**What:** Keep retrieval planning/search/reranking separate from answer composition.

**When:** For both chat and case analysis.

**Why:** Legal systems fail differently in retrieval and generation; you need to inspect them separately.

### Pattern 3: Active parameter sets per target type
**What:** Store editable parameter records in DB and mark one active per target.

**When:** For chat, case analysis, and checks.

**Why:** This is core to the reference backend and critical for experimentation without redeploying.

### Pattern 4: Source registry before ingestion job execution
**What:** Maintain canonical metadata for legal sources before chunking/indexing.

**When:** Always for admin-managed sources.

**Why:** You need provenance, trust level, effective date, source type, and refresh status independent of vector chunks.

### Pattern 5: Thin Next.js BFF, fat Spring domain backend
**What:** Keep business rules in Spring. Let Next.js focus on UI composition, optimistic state, streaming UX, and auth/session glue.

**When:** Across the whole app.

**Why:** Otherwise the admin logic gets split across two runtimes and becomes hard to reason about.

## Anti-Patterns to Avoid

### Anti-Pattern 1: One giant `AiService`
**What:** A single service handling chat, ingestion, vector management, parameters, checks, and admin logic.

**Why bad:** It destroys boundaries, makes tests weak, and prevents independent evolution of chat vs ingestion vs evaluation.

**Instead:** Separate modules with one orchestration service per capability.

### Anti-Pattern 2: Putting case analysis inside prompt-only chat branching
**What:** A single `/chat` endpoint with prompt text like “if this looks like a case, analyze it.”

**Why bad:** Hard to validate, hard to monitor, hard to tune independently, and hard to provide structured UI output.

**Instead:** Create a first-class `CaseAnalysisService` and endpoint.

### Anti-Pattern 3: Treating chat memory as audit history
**What:** Assuming Spring AI memory tables replace chat logs.

**Why bad:** Memory is for short context windows, not full admin observability. Tool-call internals may not be preserved there.

**Instead:** Keep JDBC chat memory for context and a separate `chat_logs` domain for audit/debug.

### Anti-Pattern 4: Storing only vectors without legal metadata
**What:** Index chunks without clear metadata like source URL, regulation code, article number, effective date, issuer, and source trust tier.

**Why bad:** You cannot cite properly, filter outdated content, or explain answers.

**Instead:** Enforce rich metadata on every chunk.

### Anti-Pattern 5: Putting admin CRUD directly on vector rows only
**What:** Managing the knowledge base solely by editing vector entries.

**Why bad:** You lose the source-of-truth distinction between a document source and its derived chunks.

**Instead:** Manage sources in a source registry, and use vector-store admin for derived-document inspection and maintenance.

## Suggested Internal Services

| Service | Purpose | Notes |
|---------|---------|-------|
| `ChatService` | User legal Q&A orchestration | Reuses retrieval and memory |
| `CaseAnalysisService` | Structured scenario analysis | Separate endpoint and output schema |
| `RetrievalOrchestrator` | Query planning, filtering, reranking | Shared by chat and case analysis |
| `GroundingService` | Build citation-ready context package | Converts docs to prompt-safe evidence |
| `AnswerGuardService` | Check citation presence, unsupported claims, missing disclaimers | Lightweight runtime guard |
| `ParameterService` | Manage active configs and versions | Target-type aware |
| `ChatLogService` | Persist traces and metrics | Independent of chat memory |
| `CheckRunnerService` | Regression execution | Calls production services |
| `KnowledgeSourceService` | Trusted-source CRUD and status | Source of truth for ingestion |
| `IngestionService` | Job orchestration | Uses source adapters |
| `VectorStoreAdminService` | Inspect/delete/update chunks | Admin only |
| `LegalMetadataNormalizer` | Normalize document metadata | Very important for legal citations |
| `LegalChunkingService` | Chunk by article/section where possible | Better than naive fixed-size chunks |

## Suggested Data Model Boundaries

### Relational tables/entities
- `knowledge_source`
- `knowledge_source_snapshot` or `source_version`
- `ingestion_job`
- `ai_parameter`
- `chat_log`
- `check_definition`
- `check_run`
- `check_result`
- optionally `case_analysis_record`

### Vector metadata per chunk
At minimum:
- `source_id`
- `source_type`
- `source_url` or file path
- `document_title`
- `regulation_code`
- `article`
- `clause`
- `effective_date`
- `issuer`
- `jurisdiction`
- `language`
- `trust_level`
- `chunk_index`
- `content_hash`
- `status` (`active`, `superseded`, `draft`)

This metadata is what enables filtering outdated legal text and producing credible citations.

## Next.js Frontend Structure

```text
app/
  (chat)/
    chat/page.tsx
    case-analysis/page.tsx
  (admin)/
    admin/layout.tsx
    admin/sources/page.tsx
    admin/ingestion/page.tsx
    admin/vector-store/page.tsx
    admin/parameters/page.tsx
    admin/chat-logs/page.tsx
    admin/checks/page.tsx
  api/
    chat/stream/route.ts      # optional thin proxy for SSE/streaming
    auth/...                  # only if needed
components/
  chat/
  admin/
lib/
  api-client/
  schemas/
  auth/
```

### Frontend rule of thumb
- **Direct browser -> Spring REST** for normal CRUD and query endpoints.
- **Next.js route handler -> Spring REST** only when streaming, cookie mediation, or secret-bearing exchange benefits from a proxy.

## Build Order Implications

Build in this order because later pieces depend on earlier data and contracts.

### Phase 1: Backend AI foundations
1. **Core Spring project structure and common REST conventions**
2. **pgvector + PostgreSQL configuration**
3. **parameter management service**
4. **knowledge source registry**

Why first: every downstream AI workflow depends on stable storage, config, and source-of-truth metadata.

### Phase 2: Ingestion pipeline
5. **source adapters for PDF/Word/web/structured docs**
6. **chunking + metadata normalization**
7. **embedding + vector upsert pipeline**
8. **admin ingestion APIs**

Why second: chat quality depends on having a usable corpus before UI polish.

### Phase 3: Retrieval layer
9. **semantic search service**
10. **metadata filtering**
11. **reranker**
12. **retrieval debug endpoint**

Why third: validate retrieval independently before generation hides problems.

### Phase 4: Chat runtime
13. **chat memory integration**
14. **chat orchestration service**
15. **chat logging**
16. **public chat endpoint**
17. **basic Next.js chat UI**

Why fourth: once retrieval works, standard Q&A is the lowest-friction production path.

### Phase 5: Case analysis
18. **fact extraction + issue classification**
19. **structured case-analysis answer schema**
20. **answer guard checks**
21. **case-analysis endpoint + UI**

Why fifth: this is the most product-defining feature but should be built on already-proven retrieval and logging layers.

### Phase 6: Admin observability and evaluation
22. **vector-store admin UI**
23. **chat log screens**
24. **answer checks definitions + runs**
25. **parameter activation/comparison UX**

Why sixth: this completes the preserved Jmix-admin capability set and enables safe iteration.

## Dependency Graph

```text
Knowledge Source Registry -> Ingestion Pipeline -> Vector Store
Vector Store -> Retrieval/Reranking -> Chat
Parameters -> Retrieval/Reranking -> Chat
Parameters -> Case Analysis
Chat + Parameters -> Answer Checks
Chat + Case Analysis -> Chat Logs -> Admin Observability UI
```

Or more explicitly:

```text
Source registry
  -> ingestion jobs
  -> vector chunks indexed
  -> retrieval works
  -> chat works
  -> case analysis works
  -> checks become meaningful
```

## Scalability Considerations

| Concern | At 100 users | At 10K users | At 1M users |
|---------|--------------|--------------|-------------|
| Chat traffic | Single Spring app fine | Separate web/app and worker pools | Split chat runtime, ingestion workers, and read replicas |
| Ingestion jobs | Synchronous admin-triggered jobs acceptable | Move long jobs to async queue/executor | Dedicated ingestion pipeline and job orchestration |
| Vector search | pgvector in same Postgres is fine | Tune indexes, metadata filters, and table growth | Consider dedicated vector infra only if pgvector becomes bottleneck |
| Chat logs | Single table ok | Add paging/indexing/retention | Archive cold logs and separate analytics store |
| Checks | Manual runs ok | Background workers and run queue | Dedicated evaluation service |
| Frontend | Single Next.js app fine | Cache admin lists and chat shell assets | Edge/static optimization, backend streaming tuning |

## Confidence Notes

| Area | Confidence | Notes |
|------|------------|-------|
| Spring REST modular-monolith recommendation | HIGH | Strongly supported by both reference codebases and standard Spring layering |
| pgvector + PostgreSQL choice | HIGH | Explicitly supported by Spring AI docs and already used by the reference backend |
| Separate chat memory vs chat logs | HIGH | Official Spring AI docs explicitly distinguish memory from history |
| Separate case-analysis module | MEDIUM | Opinionated recommendation based on product requirements and maintainability rather than an official framework rule |
| Thin Next.js route-handler usage | MEDIUM | Strong architectural fit, but exact proxy choice depends on auth and streaming needs |

## Bottom-Line Recommendation

Build the Vietnam traffic-law chatbot as a **Spring Boot modular monolith with dedicated AI subdomains and a Next.js App Router frontend**, not as a generic chatbot service. Preserve `jmix-ai-backend`’s conceptual core—active parameters, ingesters, vector administration, chat logs, checks, reranking, and grounded chat—but express it through **shoes-style REST controllers and services**.

Most importantly:
- keep **chat** and **case analysis** as separate application services,
- keep **ingestion** and **vector management** as admin/back-office capabilities,
- keep **chat memory** separate from **audit logs**,
- keep **Spring** as the source of business and AI orchestration logic,
- keep **Next.js** focused on chat UX, admin UX, and thin proxy concerns only.

## Sources

### HIGH confidence
- Jmix AI Backend README: `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/README.md`
- Jmix chat controller: `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/controller/ChatController.java`
- Jmix chat implementation: `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/chat/ChatImpl.java`
- Jmix ingester manager: `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/vectorstore/IngesterManager.java`
- Jmix chat log manager: `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/chatlog/ChatLogManager.java`
- Jmix check runner: `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend/src/main/java/io/jmix/ai/backend/checks/CheckRunner.java`
- Shoes backend README: `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/README.md`
- Shoes AI chat controller: `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/ai/chat/ChatController.java`
- Shoes ingestion controller: `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/ai/ingestion/IngestionController.java`
- Shoes parameters controller: `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/ai/parameters/ParametersController.java`
- Shoes chat-log controller: `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/ai/chatlog/AdminChatLogController.java`
- Shoes checks controller: `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/ai/checks/AdminCheckController.java`
- Shoes vector-store controller: `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/ai/vectorstore/AdminVectorStoreController.java`
- Shoes search controller: `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be/src/main/java/com/sba/ssos/ai/search/SearchController.java`
- Spring AI chat memory reference: https://docs.spring.io/spring-ai/reference/api/chat-memory.html
- Spring AI vector database reference: https://docs.spring.io/spring-ai/reference/api/vectordbs.html

### MEDIUM confidence
- Next.js route handlers documentation path was not reliably fetched via WebFetch during this session; frontend guidance is based on current App Router conventions plus the project constraints rather than a successful official page extraction.
