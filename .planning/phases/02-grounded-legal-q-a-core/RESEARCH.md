# Phase 2: Grounded Legal Q&A Core - Research

**Researched:** 2026-04-08
**Domain:** Retrieval-backed Vietnamese legal Q&A on Spring AI + pgvector
**Confidence:** MEDIUM-HIGH

## Summary

Phase 1 already shipped the core ingestion and retrieval safety foundation that Phase 2 should build on: PostgreSQL + pgvector storage, Spring AI vector store wiring, asynchronous ingestion, source approval/activation workflow, retrieval hard-filtering, chunk inspection endpoints, and ProblemDetail-based REST error handling. [VERIFIED: codebase build.gradle] [VERIFIED: codebase src/main/resources/application.yaml] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java]

Phase 2 does not need new ingestion or trust-policy primitives first; it needs a chat application layer on top of them: a public Q&A endpoint, a retrieval orchestration service that always uses the existing `RetrievalPolicy`, a grounding/citation mapper that turns retrieved chunk metadata into user-visible references, and an answer contract that always carries disclaimer and next-step sections. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [VERIFIED: codebase .planning/REQUIREMENTS.md] [VERIFIED: codebase .planning/ROADMAP.md] [VERIFIED: codebase .planning/STATE.md]

The strongest implementation shape is a small three-plan decomposition: (1) chat domain and REST contract, (2) retrieval-grounding and refusal guardrails, and (3) validation plus retrieval-debug support. Spring AI’s current docs support `ChatClient` with advisor chains and expose `ChatClientResponse` for advisor execution context, while the RAG docs support both simple `QuestionAnswerAdvisor` flows and `RetrievalAugmentationAdvisor` flows with query transformation, expansion, filtering, and post-processing hooks. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]

**Primary recommendation:** Build Phase 2 as a non-streaming Spring MVC chat endpoint backed by `ChatClient` + existing pgvector retrieval, with mandatory citations, mandatory disclaimer, and a default refusal/partial-answer policy whenever retrieved evidence is missing or weak. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] [VERIFIED: codebase build.gradle] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java]

## User Constraints

No `02-CONTEXT.md` file exists under `.planning/phases/02-grounded-legal-q-a-core/` in this worktree, so there are no phase-specific locked decisions to copy verbatim for Phase 2 research. Planning must therefore rely on project-wide constraints from `CLAUDE.md`, plus the Phase 2 requirements and roadmap entries. [VERIFIED: codebase Read error for .planning/phases/02-grounded-legal-q-a-core/02-CONTEXT.md] [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase .planning/REQUIREMENTS.md] [VERIFIED: codebase .planning/ROADMAP.md]

## Project Constraints (from CLAUDE.md)

- Read `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, and `.planning/ROADMAP.md` before major implementation work. [VERIFIED: codebase CLAUDE.md]
- Prefer REST-first backend changes. [VERIFIED: codebase CLAUDE.md]
- Keep traffic-condition integrations out of v1 unless requirements change. [VERIFIED: codebase CLAUDE.md]
- Preserve trusted-source ingestion, vector-store management, parameters, chat logs, and answer checks as first-class capabilities. [VERIFIED: codebase CLAUDE.md]
- Treat legal source provenance and answer grounding as critical, not optional. [VERIFIED: codebase CLAUDE.md]
- Backend target is Java 25 with Spring REST architecture. [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase build.gradle]
- Reference alignment must preserve core concepts from `jmix-ai-backend` and follow controller/service/API patterns similar to the migrated shoes backend. [VERIFIED: codebase CLAUDE.md]

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CHAT-01 | User can ask a Vietnam traffic-law question in Vietnamese and receive a source-backed answer. | Public chat endpoint, retrieval service, `ChatClient` orchestration, grounding mapper, refusal guardrails. [VERIFIED: codebase .planning/REQUIREMENTS.md] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| CHAT-03 | User can view cited source references used to support an answer. | Citation DTO derived from chunk metadata fields already stored in pgvector. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java] |
| CHAT-04 | User receives a clear disclaimer when the system provides informational legal guidance rather than formal legal advice. | Response contract should include a mandatory disclaimer field and refusal/uncertainty branch. [VERIFIED: codebase .planning/REQUIREMENTS.md] [VERIFIED: codebase .planning/ROADMAP.md] |
| LEGAL-01 | User receives the relevant legal basis for an answer, tied to retrieved source content. | Grounding assembler should map retrieved chunks into legal-basis sections and citation references. [VERIFIED: codebase .planning/REQUIREMENTS.md] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] |
| LEGAL-02 | User receives likely fine, penalty, or administrative consequence information when relevant to the question. | Prompt/response schema should contain optional consequence section with evidence-backed generation only. [VERIFIED: codebase .planning/REQUIREMENTS.md] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] |
| LEGAL-03 | User receives required documents, procedure, or compliance steps when relevant to the question. | Prompt/response schema should contain optional procedure/documents section with evidence-backed generation only. [VERIFIED: codebase .planning/REQUIREMENTS.md] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] |
| LEGAL-04 | User receives recommended next steps based on the described traffic-law situation. | Response contract should contain next-step guidance plus uncertainty/disclaimer handling. [VERIFIED: codebase .planning/REQUIREMENTS.md] [VERIFIED: codebase .planning/ROADMAP.md] |
</phase_requirements>

## Existing Code and Assets Relevant to Phase 2

### Retrieval-backed Q&A foundation already present

- The backend already depends on Spring AI chat model starters, pgvector vector store support, vector-store advisors, RAG support, and JDBC chat-memory repository support. [VERIFIED: codebase build.gradle]
- `VectorStoreConfig` already creates a `PgVectorStore` with 1536 dimensions, cosine distance, HNSW indexing, `public` schema, and `kb_vector_store` table. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java]
- `RetrievalPolicy` already centralizes the production retrieval filter as `approvalState == 'APPROVED' && trusted == 'true' && active == 'true'` and sets a default similarity threshold of `0.7`. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java]
- Retrieval-policy tests already verify approved/trusted/active filtering and basic request construction. [VERIFIED: codebase src/test/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicyTest.java] [VERIFIED: gradlew focused test run]
- Phase 1 planning explicitly marked `RetrievalPolicy.buildRequest()` as the contract Phase 2 callers must use. [VERIFIED: codebase .planning/phases/01-backend-foundation-knowledge-base/01-04-PLAN.md]

### Spring AI chat-generation assets already present

- The codebase includes Spring AI model starters for OpenAI and Google GenAI, but application config currently excludes Google GenAI chat auto-configuration and only wires an OpenAI API-key property by default. [VERIFIED: codebase build.gradle] [VERIFIED: codebase src/main/resources/application.yaml]
- No chat controller, chat service, `ChatClient` bean, or answer DTO currently exists in `src/main/java`, so Phase 2 must introduce the runtime chat layer rather than only wiring existing code. [VERIFIED: codebase grep over src/main/java for ChatClient and chat controller artifacts]
- Spring AI’s docs show `ChatClient` as the intended orchestration layer and support advisor chains for memory and retrieval. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]

### Citation and provenance mapping assets already present

- Ingestion already stores chunk-level metadata with `sourceId`, `sourceVersionId`, `sourceType`, `approvalState`, `origin`, `pageNumber`, `sectionRef`, `contentHash`, `processingVersion`, and `chunkOrdinal`. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java]
- Source records already persist provenance-oriented fields such as `source_type`, `title`, `origin_kind`, `origin_value`, `publisher_name`, `language_code`, status, trust state, approval state, and source versions. [VERIFIED: codebase src/main/resources/db/changelog/001-schema-foundation.xml] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/source/domain/KbSourceVersion.java]
- URL imports already persist fetch snapshots including requested URL, final URL, HTTP status, ETag, last-modified, content hash, and fetch timestamp. [VERIFIED: codebase src/main/resources/db/changelog/001-schema-foundation.xml] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java]
- Chunk inspection endpoints already expose the metadata that a Phase 2 citation mapper will need for debug and validation. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/chunk/api/dto/ChunkDetailResponse.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/chunk/api/dto/IndexSummaryResponse.java]

### REST response design assets already present

- Existing admin APIs use controller + service + DTO layering with `ApiPaths` constants and `PageResponse<T>` pagination wrappers. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/api/PageResponse.java]
- Errors already use Spring `ProblemDetail` through a centralized `GlobalExceptionHandler`, so Phase 2 should follow the same error model for invalid requests and refusal states that are treated as successful-but-limited answers should stay inside normal response DTOs rather than exceptions. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java]
- `SourceDetailResponse` currently exposes domain entities directly via `List<KbSourceVersion>`, which is a warning sign for Phase 2: public Q&A responses should return purpose-built DTOs, not entity graphs. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/source/api/dto/SourceDetailResponse.java]

## Gaps Phase 2 Must Fill on Top of Phase 1

- There is no public `/api/v1/chat` or equivalent endpoint yet. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java] [VERIFIED: codebase grep over src/main/java for public chat endpoints]
- There is no chat orchestration service that combines retrieval, prompting, and answer mapping. [VERIFIED: codebase grep over src/main/java for ChatClient and chat service artifacts]
- There is no citation DTO or provenance mapper for end-user responses even though the underlying metadata exists. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [VERIFIED: codebase grep over src/main/java for citation response artifacts]
- There is no disclaimer or refusal policy implementation yet. [VERIFIED: codebase grep over src/main/java for disclaimer/refusal artifacts] [VERIFIED: codebase .planning/REQUIREMENTS.md]
- There is no dedicated retrieval-debug endpoint for Phase 2 chat validation; Phase 1 left that optional. [VERIFIED: codebase .planning/phases/01-backend-foundation-knowledge-base/01-CONTEXT.md] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java]
- There is no parameter-management runtime for prompts/model knobs yet, even though project constraints say parameters remain first-class capabilities. [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase grep over src/main/java for parameter artifacts]
- There is no chat-log persistence yet, which means Phase 2 can ship minimal chat functionality but should leave audit-heavy chat logging to a later phase only if the plan clearly marks that dependency. [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase grep over src/main/java for chat log artifacts] [VERIFIED: codebase .planning/ROADMAP.md]

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.5 | Web MVC, validation, config, actuator | Already the repo baseline and the active REST platform. [VERIFIED: codebase build.gradle] |
| Spring AI BOM | 2.0.0-M4 | AI dependency alignment | Already the repo baseline for all AI components, so Phase 2 should align with the shipped foundation instead of mixing lines. [VERIFIED: codebase build.gradle] |
| `spring-ai-starter-model-openai` | BOM-managed | Chat model integration | Already present and supported by current config defaults. [VERIFIED: codebase build.gradle] [VERIFIED: codebase src/main/resources/application.yaml] |
| `spring-ai-starter-vector-store-pgvector` | BOM-managed | Vector retrieval against PostgreSQL | Already wired through `PgVectorStore`. [VERIFIED: codebase build.gradle] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java] |
| `spring-ai-advisors-vector-store` | BOM-managed | Advisor-based retrieval integration | Official docs support `QuestionAnswerAdvisor` and vector-store memory advisors. [VERIFIED: codebase build.gradle] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| `spring-ai-rag` | BOM-managed | Modular RAG pipeline components | Official docs support `RetrievalAugmentationAdvisor`, query transforms, expansion, and post-processing. [VERIFIED: codebase build.gradle] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| PostgreSQL + pgvector | app-configured | Source metadata + vector retrieval | Use as the only retrieval store for Phase 2 to stay aligned with Phase 1. [VERIFIED: codebase src/main/resources/application.yaml] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java] |
| Spring Validation | Boot-managed | Request validation | Use on public chat request DTOs. [VERIFIED: codebase build.gradle] |
| Spring ProblemDetail | Boot-managed | Error responses | Use for invalid requests and unexpected failures. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java] |
| JDBC chat-memory repository starter | BOM-managed | Future thread memory persistence | Present now, but Phase 2 can keep single-turn if it avoids prematurely solving Phase 3. [VERIFIED: codebase build.gradle] [VERIFIED: codebase .planning/ROADMAP.md] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `QuestionAnswerAdvisor` plus existing `RetrievalPolicy` | `RetrievalAugmentationAdvisor` | `RetrievalAugmentationAdvisor` gives more modular hooks, but `QuestionAnswerAdvisor` is a smaller initial fit if Phase 2 stays simple. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| Non-streaming Spring MVC endpoint | Streaming endpoint with `WebFlux` | Streaming improves UX later, but current repo only includes Web MVC, and Spring AI docs note streaming support is reactive-stack oriented. [VERIFIED: codebase build.gradle] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] |
| Single answer endpoint | Separate retrieval-debug endpoint too | Debug endpoint adds implementation surface, but makes retrieval failures diagnosable before generation hides them. [VERIFIED: codebase .planning/research/ARCHITECTURE.md] |

**Installation:**
```bash
./gradlew dependencies
```

**Version verification:** Repo-locked stack versions were verified from `build.gradle`, and Spring AI feature guidance was checked against the current official reference pages for `ChatClient` and RAG. [VERIFIED: codebase build.gradle] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]

## Architecture Patterns

### Recommended Project Structure

```text
src/main/java/com/vn/traffic/chatbot/
├── chat/
│   ├── api/                # public Q&A controller + request/response DTOs
│   ├── service/            # chat orchestration service
│   └── prompt/             # prompt templates / builders
├── grounding/
│   ├── service/            # citation mapping + evidence assembly
│   └── api/dto/            # citation/evidence DTOs
├── retrieval/
│   ├── RetrievalPolicy.java
│   └── service/            # retrieval executor using existing policy
└── common/
    └── api/                # ApiPaths and shared wrappers
```

The key structural rule is to add a chat slice and a grounding slice without bypassing the existing retrieval policy or the existing REST conventions. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/api/PageResponse.java]

### Pattern 1: Chat orchestration service over retrieval + grounding
**What:** Controller validates input, calls a chat service, the chat service performs retrieval, then passes retrieved documents to a grounding mapper and answer mapper. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]

**When to use:** Always for Phase 2 public Q&A. [VERIFIED: codebase .planning/REQUIREMENTS.md]

**Example:**
```java
// Source: https://docs.spring.io/spring-ai/reference/api/chatclient.html
ChatClientResponse response = chatClient.prompt()
    .advisors(questionAnswerAdvisor)
    .user(question)
    .call()
    .chatClientResponse();
```

The reason to prefer `ChatClientResponse` over plain `content()` is that the docs say it exposes execution context from advisors, including relevant retrieved documents in a RAG flow. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]

### Pattern 2: Phase 2 retrieval must use `RetrievalPolicy.buildRequest()`
**What:** The retrieval executor should depend on the existing `RetrievalPolicy` rather than embedding filter expressions inside the chat service. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [VERIFIED: codebase .planning/phases/01-backend-foundation-knowledge-base/01-04-PLAN.md]

**When to use:** Every production retrieval call. [VERIFIED: codebase .planning/phases/01-backend-foundation-knowledge-base/01-04-PLAN.md]

**Example:**
```java
// Source: local codebase
SearchRequest request = retrievalPolicy.buildRequest(question, topK);
```

### Pattern 3: Citation mapping from chunk metadata, not from model prose
**What:** Build citations from `sourceId`, `sourceVersionId`, `origin`, `pageNumber`, `sectionRef`, and title/provenance metadata rather than trying to parse citations out of the generated answer text. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [VERIFIED: codebase src/main/resources/db/changelog/001-schema-foundation.xml]

**When to use:** Always for `CHAT-03` and `LEGAL-01`. [VERIFIED: codebase .planning/REQUIREMENTS.md]

### Pattern 4: Mandatory answer sections
**What:** The response DTO should always include `answer`, `citations`, `disclaimer`, and `nextSteps`, with optional `legalBasis`, `penalty`, and `requiredDocumentsOrProcedure` sections populated only when supported by evidence. [VERIFIED: codebase .planning/REQUIREMENTS.md] [VERIFIED: codebase .planning/ROADMAP.md]

**When to use:** All Phase 2 answers. [VERIFIED: codebase .planning/REQUIREMENTS.md]

### Anti-Patterns to Avoid

- **Bypassing retrieval policy:** Hard-coding ad hoc vector-store queries in the chat service would break Phase 1’s trust gate. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [VERIFIED: codebase .planning/phases/01-backend-foundation-knowledge-base/01-04-PLAN.md]
- **Entity leakage in public responses:** Returning entities or raw metadata maps would make the public API unstable and too coupled to storage shape. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/source/api/dto/SourceDetailResponse.java]
- **Prompt-only citations:** Letting the model invent reference strings without a server-side grounding mapper will produce unverifiable citations. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [VERIFIED: codebase .planning/research/PITFALLS.md]
- **Treating missing evidence as normal completion:** The RAG docs describe flows that can instruct the model not to answer when context is empty; legal Q&A should use that capability rather than fabricate a complete answer. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]

## Weak-Grounding Refusal Behavior: Options and Tradeoffs

### Option A: Hard refusal on empty or weak evidence
- Behavior: Return a limited answer stating that the system cannot provide grounded guidance from the approved knowledge base. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]
- Strength: Best protection against hallucinated legal advice. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] [VERIFIED: codebase .planning/research/PITFALLS.md]
- Weakness: Can feel overly blunt when some partial evidence exists. [ASSUMED]
- Confidence: MEDIUM. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] [ASSUMED]

### Option B: Partial answer with explicit uncertainty and sparse citations
- Behavior: Return only the portions supported by evidence, clearly label unsupported parts as unavailable, and include next steps for consulting official sources or authorities. [VERIFIED: codebase .planning/REQUIREMENTS.md] [VERIFIED: codebase .planning/ROADMAP.md]
- Strength: Better user utility when retrieval is incomplete but not empty. [ASSUMED]
- Weakness: Requires careful schema and validation so unsupported sections remain blank rather than guessed. [VERIFIED: codebase .planning/research/PITFALLS.md] [ASSUMED]
- Confidence: MEDIUM. [VERIFIED: codebase .planning/research/PITFALLS.md] [ASSUMED]

### Option C: Always answer with a disclaimer
- Behavior: Always generate a full answer and rely on a legal-information disclaimer. [VERIFIED: codebase .planning/REQUIREMENTS.md]
- Strength: Highest perceived responsiveness. [ASSUMED]
- Weakness: Weakest grounding discipline and explicitly contradicted by the project rule that answer grounding is critical, not optional. [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase .planning/research/PITFALLS.md]
- Confidence: HIGH that this is the wrong default for this project. [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase .planning/research/PITFALLS.md]

**Recommendation:** Use Option B as the default and fall back to Option A when retrieved evidence is empty or below the policy threshold. That gives users limited help without overstating certainty and stays consistent with Spring AI’s documented empty-context handling and the project’s grounding-first constraints. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase .planning/research/PITFALLS.md]

## Don’t Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Chat orchestration | Manual prompt-string assembly in controllers | Spring AI `ChatClient` in a service layer | Officially supported API with advisor integration and typed response access. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] |
| Retrieval augmentation | Custom glue that mixes search, filtering, and augmentation ad hoc | `QuestionAnswerAdvisor` or `RetrievalAugmentationAdvisor` plus existing `RetrievalPolicy` | Spring AI already provides advisor-based RAG flow hooks. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] |
| Citation extraction | Regex parsing of generated answer text | Server-side citation mapper from retrieved metadata | Metadata already exists and is more trustworthy than model prose. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] |
| Empty-context behavior | Homemade scoring rules only in prompts | Spring AI empty-context refusal path plus explicit app-level answer-state mapping | The RAG docs already define empty-context behavior controls. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| REST pagination wrappers | One-off paging shapes per endpoint | Existing `PageResponse<T>` pattern | Keeps Phase 2 aligned with current REST conventions. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/api/PageResponse.java] |

**Key insight:** Phase 2’s hard parts are legal grounding and response contract discipline, not inventing new AI plumbing from scratch. [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase .planning/research/PITFALLS.md] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]

## Recommended Plan Decomposition

### Plan 1: Public Q&A contract and chat orchestration
Scope: request/response DTOs, `ApiPaths` additions, public controller, chat service, prompt template, disclaimer fields, and Vietnamese-first response shaping. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java] [VERIFIED: codebase .planning/REQUIREMENTS.md]

### Plan 2: Retrieval-grounding pipeline and refusal guardrails
Scope: retrieval executor using `RetrievalPolicy`, Spring AI advisor wiring, grounding assembler, citation mapper, empty/weak-context refusal logic, and retrieval-debug visibility for developers/admins. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]

### Plan 3: Validation, fixtures, and quality checks for Phase 2 requirements
Scope: unit/integration tests for answer mapping, citation presence, refusal behavior, and endpoint contract; add fixtures built from approved source metadata and retrieved chunks. [VERIFIED: codebase .planning/config.json] [VERIFIED: gradlew focused test run]

This three-plan split is small enough to execute and review, while keeping retrieval safety, answer shaping, and validation from collapsing into one oversized plan. [VERIFIED: codebase .planning/STATE.md] [VERIFIED: codebase .planning/config.json]

## Common Pitfalls

### Pitfall 1: Citation payloads that cannot point back to evidence
**What goes wrong:** The answer shows a source title but not the page, section, or source version that supported the claim. [VERIFIED: codebase .planning/research/PITFALLS.md] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java]
**Why it happens:** Teams let the model author citations instead of mapping from stored chunk metadata. [VERIFIED: codebase .planning/research/PITFALLS.md] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java]
**How to avoid:** Build citations from retrieved documents and metadata only. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java]
**Warning signs:** Answers cite titles generically and cannot be traced in chunk inspection endpoints. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java]

### Pitfall 2: Retrieval bypass of trust/approval filters
**What goes wrong:** Draft or revoked sources leak into public legal guidance. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [VERIFIED: codebase .planning/phases/01-backend-foundation-knowledge-base/01-04-PLAN.md]
**Why it happens:** New chat code queries the vector store directly without `RetrievalPolicy`. [VERIFIED: codebase .planning/phases/01-backend-foundation-knowledge-base/01-04-PLAN.md]
**How to avoid:** Make `RetrievalPolicy` the only request builder used by production Q&A services. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java]
**Warning signs:** Phase 2 code contains filter strings outside `RetrievalPolicy` or no filter at all. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java]

### Pitfall 3: Full answers produced from weak context with only a disclaimer
**What goes wrong:** The system sounds authoritative while evidence is thin. [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase .planning/research/PITFALLS.md]
**Why it happens:** Disclaimer handling is implemented, but answer-state handling is not. [VERIFIED: codebase .planning/REQUIREMENTS.md] [ASSUMED]
**How to avoid:** Model answer states explicitly: grounded, partially grounded, refused. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] [ASSUMED]
**Warning signs:** Responses always include all legal sections regardless of retrieval quality. [ASSUMED]

### Pitfall 4: Overloading Phase 2 with multi-turn memory work
**What goes wrong:** Phase 2 slips into Phase 3 scope by solving thread memory and scenario continuity before direct grounded Q&A is stable. [VERIFIED: codebase .planning/ROADMAP.md]
**Why it happens:** The repo already includes JDBC chat-memory dependencies, which can tempt early expansion. [VERIFIED: codebase build.gradle]
**How to avoid:** Keep Phase 2 focused on single-turn grounded Q&A and treat memory as future-ready infrastructure, not a completion condition. [VERIFIED: codebase .planning/ROADMAP.md] [VERIFIED: codebase .planning/STATE.md]
**Warning signs:** Plan tasks include conversation history persistence as core delivery criteria for CHAT-01/03/04 and LEGAL-01..04. [VERIFIED: codebase .planning/REQUIREMENTS.md]

## Code Examples

Verified patterns from official sources and current code:

### Spring AI advisor-based retrieval chat
```java
// Source: https://docs.spring.io/spring-ai/reference/api/chatclient.html
ChatClientResponse response = ChatClient.builder(chatModel)
    .build()
    .prompt()
    .advisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build(),
        QuestionAnswerAdvisor.builder(vectorStore).build()
    )
    .user(userText)
    .call()
    .chatClientResponse();
```

### Existing retrieval safety contract
```java
// Source: local codebase - src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java
public static final String RETRIEVAL_FILTER =
    "approvalState == 'APPROVED' && trusted == 'true' && active == 'true'";
```

### Existing chunk metadata fields available for citations
```java
// Source: local codebase - src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java
meta.put("sourceId", s.getId().toString());
meta.put("sourceVersionId", c.sourceVersionId());
meta.put("origin", s.getOriginValue());
meta.put("pageNumber", c.pageNumber());
meta.put("sectionRef", c.sectionRef());
meta.put("contentHash", c.contentHash());
```

### RAG flow with refusal on empty context support
```java
// Source: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .similarityThreshold(0.50)
        .vectorStore(vectorStore)
        .build())
    .build();
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hand-built prompt pipelines around raw LLM clients | Advisor-based `ChatClient` and modular RAG composition in Spring AI | Current official Spring AI reference as of 2026-04-08 research session. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] | Phase 2 can compose retrieval and answer generation without inventing custom orchestration layers. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] |
| Retrieval only as plain vector similarity search | Retrieval plus query transformation, expansion, filtering, and document post-processing hooks | Current official Spring AI RAG reference. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] | Phase 2 can stay simple now while leaving room for refinement later without replacing the stack. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |

**Deprecated/outdated:**
- Generating public legal answers without server-side grounding metadata is outdated for this project because the current project constraints require provenance and grounding as first-class capabilities. [VERIFIED: codebase CLAUDE.md]

## Open Questions

1. **Should Phase 2 include a public retrieval-debug endpoint or keep debug visibility admin-only?**
   - What we know: Phase 1 left retrieval-test APIs open for planning discretion, and retrieval failures will be hard to diagnose without a debug surface. [VERIFIED: codebase .planning/phases/01-backend-foundation-knowledge-base/01-CONTEXT.md] [VERIFIED: codebase .planning/research/ARCHITECTURE.md]
   - What's unclear: whether the team wants that endpoint shipped in public API space, admin-only space, or test-only support. [ASSUMED]
   - Recommendation: include admin/test-only retrieval debug support in Plan 2, not a public feature. [VERIFIED: codebase .planning/research/ARCHITECTURE.md] [ASSUMED]

2. **Should Phase 2 ship with one provider path or an abstraction over OpenAI and Google GenAI?**
   - What we know: both starters are present, but Google chat auto-config is excluded and OpenAI config is the only default shown in `application.yaml`. [VERIFIED: codebase build.gradle] [VERIFIED: codebase src/main/resources/application.yaml]
   - What's unclear: whether provider-switching is a near-term requirement or just future-proofing. [ASSUMED]
   - Recommendation: implement one provider-backed Phase 2 path first and keep provider abstraction minimal. [VERIFIED: codebase src/main/resources/application.yaml] [ASSUMED]

3. **How strict should refusal be when citations exist for some sections but not all?**
   - What we know: requirements need legal basis, citations, disclaimer, and next steps; Spring AI supports empty-context refusal, but not project-specific weak-grounding thresholds. [VERIFIED: codebase .planning/REQUIREMENTS.md] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]
   - What's unclear: the exact threshold for partial-answer acceptance. [ASSUMED]
   - Recommendation: define explicit answer states and test them in Plan 3. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Gradle Wrapper | Build and tests | ✓ | repo wrapper present | — |
| Java runtime | Spring Boot tests/build | ✓ | exact version not captured, but `./gradlew test` succeeded | — |
| Node.js | Frontend-adjacent tooling only, not Phase 2 core backend | ✓ | v24.14.0 | — |
| npm | Frontend-adjacent tooling only, not Phase 2 core backend | ✓ | 11.9.0 | — |
| Docker client | Local infra workflows | ✓ | 28.0.4 client | optional; Phase 2 research did not require it |
| PostgreSQL CLI/server probe | Runtime data layer | not verified | — | rely on app config and existing test scope |
| LLM API key | Real chat generation | not verified | — | planner should treat as environment prerequisite |

**Missing dependencies with no fallback:**
- None proven missing during research, but live PostgreSQL availability and LLM credentials were not verified in this session. [VERIFIED: codebase src/main/resources/application.yaml]

**Missing dependencies with fallback:**
- PostgreSQL server/CLI was not verified directly; unit/service tests can still progress, but end-to-end chat validation will need a live database. [VERIFIED: gradlew focused test run] [VERIFIED: codebase src/main/resources/application.yaml]

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 on Gradle test task. [VERIFIED: codebase build.gradle] |
| Config file | none dedicated detected; Gradle defaults currently drive tests. [VERIFIED: codebase build.gradle] |
| Quick run command | `./gradlew test --tests com.vn.traffic.chatbot.retrieval.RetrievalPolicyTest --tests com.vn.traffic.chatbot.source.service.SourceServiceTest` [VERIFIED: gradlew focused test run] |
| Full suite command | `./gradlew test` [VERIFIED: codebase build.gradle] |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CHAT-01 | Vietnamese question returns source-backed answer | integration | `./gradlew test --tests *ChatController* --tests *ChatService*` | ❌ Wave 0 |
| CHAT-03 | Answer contains cited source references | unit/integration | `./gradlew test --tests *CitationMapper* --tests *ChatService*` | ❌ Wave 0 |
| CHAT-04 | Informational-guidance disclaimer is always present on answer/refusal responses | unit/web | `./gradlew test --tests *ChatResponseMapper* --tests *ChatController*` | ❌ Wave 0 |
| LEGAL-01 | Legal basis is tied to retrieved source content | unit/integration | `./gradlew test --tests *GroundingService* --tests *ChatService*` | ❌ Wave 0 |
| LEGAL-02 | Penalty section appears only when evidence supports it | unit | `./gradlew test --tests *AnswerSectionPolicy*` | ❌ Wave 0 |
| LEGAL-03 | Procedure/documents section appears only when evidence supports it | unit | `./gradlew test --tests *AnswerSectionPolicy*` | ❌ Wave 0 |
| LEGAL-04 | Next steps are returned with disclaimer-aware guidance | unit/web | `./gradlew test --tests *ChatResponseMapper* --tests *ChatController*` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** run targeted Phase 2 unit/web tests. [VERIFIED: codebase .planning/config.json]
- **Per wave merge:** run `./gradlew test`. [VERIFIED: codebase build.gradle]
- **Phase gate:** full suite green before `/gsd-verify-work`. [VERIFIED: codebase .planning/config.json]

### Wave 0 Gaps
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java` — covers CHAT-01, CHAT-03, CHAT-04. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java` — covers CHAT-01, LEGAL-01..04. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/grounding/service/CitationMapperTest.java` — covers CHAT-03 and LEGAL-01. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/AnswerSectionPolicyTest.java` — covers refusal and partial-grounding logic. [ASSUMED]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no for public Q&A path in current phase scope; admin already separate | public endpoint may stay unauthenticated if product scope requires it, but admin remains separate. [VERIFIED: codebase .planning/PROJECT.md] [ASSUMED] |
| V3 Session Management | no for single-turn Q&A in Phase 2 | defer with Phase 3 multi-turn memory. [VERIFIED: codebase .planning/ROADMAP.md] |
| V4 Access Control | yes | keep public chat path separate from admin ingestion/chunk endpoints. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java] |
| V5 Input Validation | yes | Spring Validation on request DTOs and ProblemDetail on validation failures. [VERIFIED: codebase build.gradle] [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java] |
| V6 Cryptography | yes | rely on provider SDKs and standard TLS/API-key handling; never hand-roll crypto. [VERIFIED: codebase build.gradle] [ASSUMED] |

### Known Threat Patterns for Spring AI legal Q&A

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection through retrieved source text | Tampering | Keep system prompt authoritative, map citations server-side, and refuse unsupported claims. [VERIFIED: codebase .planning/research/PITFALLS.md] [ASSUMED] |
| Retrieval of unapproved or revoked sources | Information Disclosure | Enforce `RetrievalPolicy` on every production search. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] |
| Overconfident unsupported legal guidance | Repudiation / Integrity | Explicit answer states, mandatory disclaimer, and refusal on empty context. [VERIFIED: codebase .planning/REQUIREMENTS.md] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| SSRF during source fetching | Tampering | Already mitigated in `UrlPageParser` and should not be regressed by Phase 2 work. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/parser/UrlPageParser.java] |

## Risks, Dependencies, and Validation Concerns

- **Risk:** current chunk metadata is enough for basic provenance, but not obviously enough for polished legal citation labels such as decree/article/clause formatting. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [VERIFIED: codebase src/main/resources/db/changelog/001-schema-foundation.xml]
- **Dependency:** Phase 2 quality depends on approved and active sources already being present in the vector store, because the retrieval filter excludes everything else. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [VERIFIED: codebase src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java]
- **Risk:** if the chat service directly queries `VectorStore` without exposing advisor/retrieval context, citation mapping may become harder than necessary. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [ASSUMED]
- **Dependency:** live model credentials are needed for realistic Phase 2 end-to-end validation even though unit and mapping tests can proceed without them. [VERIFIED: codebase src/main/resources/application.yaml]
- **Validation concern:** weak-grounding behavior must be tested as a first-class requirement, not treated as prompt polish. [VERIFIED: codebase .planning/REQUIREMENTS.md] [VERIFIED: codebase .planning/research/PITFALLS.md]
- **Validation concern:** citation presence alone is insufficient; tests should verify citation traceability back to retrieved metadata. [VERIFIED: codebase src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java] [VERIFIED: codebase .planning/research/PITFALLS.md]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Hard refusal will feel overly blunt when some partial evidence exists. | Weak-Grounding Refusal Behavior | Could bias planning against a stricter but acceptable product choice. |
| A2 | Partial-answer behavior will be more useful to users than strict refusal when evidence is incomplete. | Weak-Grounding Refusal Behavior | Could overcomplicate Phase 2 if the product wants a simpler refusal policy. |
| A3 | Phase 2 should define explicit answer states such as grounded / partially grounded / refused. | Common Pitfalls / Open Questions | Could add DTO complexity if the team prefers a smaller response contract. |
| A4 | Suggested new test-file names (`ChatControllerTest`, `ChatServiceTest`, `CitationMapperTest`, `AnswerSectionPolicyTest`) are the right Wave 0 decomposition. | Validation Architecture | Planner may choose different file boundaries. |
| A5 | Provider abstraction can stay minimal in Phase 2. | Open Questions | Could create rework if multi-provider switching is needed immediately. |
| A6 | Admin/test-only retrieval debug support is preferable to a public debug endpoint. | Open Questions | Could mismatch the intended UX/testing workflow. |

## Sources

### Primary (HIGH confidence)
- Local codebase: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\build.gradle` — active backend stack and Spring AI dependencies.
- Local codebase: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\src\main\resources\application.yaml` — current runtime configuration defaults.
- Local codebase: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\src\main\java\com\vn\traffic\chatbot\retrieval\RetrievalPolicy.java` — retrieval safety contract.
- Local codebase: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\src\main\java\com\vn\traffic\chatbot\ingestion\orchestrator\IngestionOrchestrator.java` — chunk metadata shape.
- Local codebase: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\src\main\java\com\vn\traffic\chatbot\chunk\service\ChunkInspectionService.java` — chunk inspection and current debug visibility.
- Local codebase: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\.planning\REQUIREMENTS.md` — Phase 2 requirement targets.
- Local codebase: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\.planning\ROADMAP.md` — Phase 2 goal and success criteria.
- Local codebase: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\CLAUDE.md` — project constraints.
- [Spring AI ChatClient reference](https://docs.spring.io/spring-ai/reference/api/chatclient.html) — advisor chain, `ChatClientResponse`, structured output, streaming notes.
- [Spring AI Retrieval-Augmented Generation reference](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html) — advisors, modular RAG, empty-context behavior, query transformation/expansion/post-processing.

### Secondary (MEDIUM confidence)
- Local planning research: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\.planning\research\ARCHITECTURE.md` — prior project-level architecture recommendations.
- Local planning research: `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-abf492ed\.planning\research\PITFALLS.md` — prior project-level legal-RAG risk analysis.

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — repo stack and official Spring AI docs align. [VERIFIED: codebase build.gradle] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]
- Architecture: MEDIUM-HIGH — codebase gaps are clear, but final plan boundaries still depend on missing Phase 2 context decisions. [VERIFIED: codebase Read error for .planning/phases/02-grounded-legal-q-a-core/02-CONTEXT.md] [VERIFIED: codebase .planning/REQUIREMENTS.md]
- Pitfalls: MEDIUM-HIGH — strongly supported by current code constraints and prior project research, but weak-grounding threshold details still need implementation decisions. [VERIFIED: codebase CLAUDE.md] [VERIFIED: codebase .planning/research/PITFALLS.md]

**Research date:** 2026-04-08
**Valid until:** 2026-05-08
