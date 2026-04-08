# Phase 2: Grounded Legal Q&A Core - Research

**Researched:** 2026-04-08
**Domain:** Retrieval-backed Vietnamese legal Q&A on Spring REST + Spring AI
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

02-CONTEXT.md was requested but is not present at `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-a6abcda2\.planning\phases\02-grounded-legal-q-a-core\02-CONTEXT.md`. [VERIFIED: local filesystem]

### Locked Decisions
- No phase-specific locked decisions were available to copy because 02-CONTEXT.md does not exist yet. [VERIFIED: local filesystem]

### Claude's Discretion
- Research should stay within Phase 2 roadmap scope: source-backed Vietnamese legal Q&A with citations, disclaimer behavior, legal-basis/penalty/procedure/next-step structure, and backend contracts for later phases. [VERIFIED: .planning/ROADMAP.md]

### Deferred Ideas (OUT OF SCOPE)
- Multi-turn continuity, clarifying questions, and full case-analysis flow belong to Phase 3, not Phase 2. [VERIFIED: .planning/ROADMAP.md]
- Live traffic-condition integrations remain out of v1 scope. [VERIFIED: CLAUDE.md] [VERIFIED: .planning/REQUIREMENTS.md]
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CHAT-01 | User can ask a Vietnam traffic-law question in Vietnamese and receive a source-backed answer. [VERIFIED: .planning/REQUIREMENTS.md] | Retrieval policy reuse, Spring AI RAG advisor wiring, grounded REST response contract, and refusal behavior guidance. |
| CHAT-03 | User can view cited source references used to support an answer. [VERIFIED: .planning/REQUIREMENTS.md] | Citation/provenance mapping from vector-store metadata into explicit response objects. |
| CHAT-04 | User receives a clear disclaimer when the system provides informational legal guidance rather than formal legal advice. [VERIFIED: .planning/REQUIREMENTS.md] | Response envelope and prompt policy recommend mandatory disclaimer fields plus grounding-aware refusal path. |
| LEGAL-01 | User receives the relevant legal basis for an answer, tied to retrieved source content. [VERIFIED: .planning/REQUIREMENTS.md] | Retrieved-chunk metadata and citation mapping patterns support legal-basis sections tied to source snippets. |
| LEGAL-02 | User receives likely fine, penalty, or administrative consequence information when relevant to the question. [VERIFIED: .planning/REQUIREMENTS.md] | Structured answer schema with conditional penalty section and tests for omission when unsupported. |
| LEGAL-03 | User receives required documents, procedure, or compliance steps when relevant to the question. [VERIFIED: .planning/REQUIREMENTS.md] | Structured answer schema with procedure/documents section tied to retrieved support. |
| LEGAL-04 | User receives recommended next steps based on the described traffic-law situation. [VERIFIED: .planning/REQUIREMENTS.md] | Response design includes nextSteps array and grounding guardrails for support-dependent guidance. |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Read `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, and `.planning/ROADMAP.md` before major implementation work. [VERIFIED: CLAUDE.md]
- Prefer REST-first backend changes. [VERIFIED: CLAUDE.md]
- Keep traffic-condition integrations out of v1 unless requirements change. [VERIFIED: CLAUDE.md]
- Preserve trusted-source ingestion, vector-store management, parameters, chat logs, and answer checks as first-class capabilities. [VERIFIED: CLAUDE.md]
- Treat legal source provenance and answer grounding as critical, not optional. [VERIFIED: CLAUDE.md]
- Preserve core concepts from `jmix-ai-backend`. [VERIFIED: CLAUDE.md]
- Follow controller/service/API patterns similar to the migrated shoes backend. [VERIFIED: CLAUDE.md]

## Summary

Phase 1 already established the important retrieval safety baseline: ingested chunks are stored in pgvector with provenance-rich metadata, initial retrieval is filtered to approved/trusted/active content, and admin endpoints already expose source approval/activation and chunk inspection. [VERIFIED: src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java]

Phase 2 therefore should not rework ingestion or vector storage. It should add a thin, execution-focused chat slice on top of the existing stack: query-to-retrieval orchestration, Spring AI generation with explicit grounding policy, citation/provenance mapping into a REST contract, and refusal/disclaimer behavior when grounding is weak. [VERIFIED: .planning/ROADMAP.md] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]

The safest implementation direction is to keep the backend REST-first and return a structured answer envelope rather than raw model text. That envelope should separate answer text, disclaimer, legal-basis summary, penalty/procedure/next-step sections, and a normalized citations list so later frontend and evaluation work can consume stable fields. [VERIFIED: CLAUDE.md] [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java] [ASSUMED]

**Primary recommendation:** Implement Phase 2 as three executable plan files: retrieval-backed chat core, citation/provenance response mapping, and validation hardening around refusal/disclaimer/grounding thresholds. [VERIFIED: .planning/ROADMAP.md] [ASSUMED]

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.5 [VERIFIED: build.gradle] | REST application/runtime | Already pinned in repo and underpins existing controller/service/error patterns. [VERIFIED: build.gradle] [VERIFIED: src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java] |
| Spring AI ChatClient | 2.0.0-M4 in repo BOM [VERIFIED: build.gradle] | Fluent chat generation entry point | Official docs position ChatClient as the main prompt/advisor response API for REST use. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] |
| Spring AI RAG / advisors | 2.0.0-M4 in repo BOM [VERIFIED: build.gradle] | Retrieval-augmented generation wiring | Official docs provide `QuestionAnswerAdvisor` and `RetrievalAugmentationAdvisor` for retrieval-backed chat flows. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| Spring AI PGvector store | 2.0.0-M4 in repo BOM [VERIFIED: build.gradle] | Similarity search over approved/trusted/active chunks | Already configured in repo and supports metadata filter expressions for grounded retrieval. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java] [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] |
| PostgreSQL + pgvector table `kb_vector_store` | repo-configured [VERIFIED: src/main/resources/application.yaml] | Stores embeddings plus metadata JSON | Existing ingestion and chunk inspection already depend on it, so Phase 2 should reuse rather than abstract away from it. [VERIFIED: src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionService.java] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Validation / ProblemDetail | repo-configured [VERIFIED: build.gradle] | Validate request DTOs and consistent API errors | Use for chat request validation and refusal/error responses. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java] |
| JUnit 5 + AssertJ + Mockito | repo-configured [VERIFIED: src/test/java/com/vn/traffic/chatbot/TrafficLawChatbotApplicationTests.java] [VERIFIED: src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java] | Unit and Spring tests | Use for service logic, response mapping, and controller contract tests. [VERIFIED: src/test/java/com/vn/traffic/chatbot/common/SpringBootSmokeTest.java] |
| Spring Boot test modules | official current guidance [CITED: https://docs.spring.io/spring-boot/reference/testing/index.html] | Slice/full integration support | Use for `@WebMvcTest` or `@SpringBootTest` coverage as Phase 2 adds REST chat endpoints. [CITED: https://docs.spring.io/spring-boot/reference/testing/index.html] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `QuestionAnswerAdvisor` | `RetrievalAugmentationAdvisor` | `RetrievalAugmentationAdvisor` gives stronger empty-context control and more modular retrieval stages, but `QuestionAnswerAdvisor` is simpler if citation extraction is handled separately. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| Raw text response body | Structured response DTO | Structured DTO is slightly more work now but is much better for citations, disclaimers, and later frontend/admin evaluation flows. [VERIFIED: .planning/PROJECT.md] [ASSUMED] |
| Model-only fallback when retrieval is weak | Explicit refusal / limited-answer path | Fallback may improve answer rate but increases hallucination risk for legal guidance. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |

**Installation:**
```bash
./gradlew dependencies
```

**Version verification:** Repo-pinned framework versions are Spring Boot 4.0.5 and Spring AI BOM 2.0.0-M4. [VERIFIED: build.gradle]

## Existing Code and Assets Relevant to Phase 2

### Retrieval-backed Q&A foundations already present
- `RetrievalPolicy` already builds a `SearchRequest` with a default similarity threshold of `0.7` and a hard filter requiring `approvalState == 'APPROVED' && trusted == 'true' && active == 'true'`. [VERIFIED: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java]
- Ingestion writes chunk metadata including `sourceId`, `sourceVersionId`, `origin`, `pageNumber`, `sectionRef`, `approvalState`, `trusted`, and `active`. [VERIFIED: src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java]
- Source approval/activation updates chunk metadata so retrieval eligibility follows source lifecycle state. [VERIFIED: src/main/java/com/vn/traffic/chatbot/source/service/SourceService.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkMetadataUpdater.java]
- Chunk inspection endpoints can already expose the same metadata Phase 2 needs for citation/provenance mapping. [VERIFIED: src/main/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminController.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/chunk/api/dto/ChunkDetailResponse.java]

### REST design assets already present
- Existing controllers use a controller/service/DTO pattern with `ResponseEntity`, centralized `ApiPaths`, and `PageResponse` wrappers. [VERIFIED: src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/api/PageResponse.java]
- Existing API errors are standardized via `ProblemDetail` in `GlobalExceptionHandler`, which Phase 2 should preserve. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java]

### What is missing for Phase 2
- No public chat controller or chat service exists yet. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java] [VERIFIED: repository grep on src/main/java]
- No Spring AI `ChatClient`, `ChatModel`, or advisor wiring exists in the application code yet. [VERIFIED: repository grep on src/main/java]
- No answer DTO exists for citations, disclaimer text, legal-basis sections, penalties, procedures, or recommended next steps. [VERIFIED: repository grep on src/main/java]
- No tests currently validate answer grounding, refusal behavior, citation mapping, or chat response contract semantics. [VERIFIED: repository grep on src/test/java]

## Gaps Phase 2 Must Fill on Top of Phase 1

| Gap | Why It Matters | Phase 2 Action |
|-----|----------------|----------------|
| Chat entrypoint | There is no endpoint that turns a user question into retrieval + generation. [VERIFIED: repository grep on src/main/java] | Add public REST chat controller and orchestration service. [ASSUMED] |
| Retrieval-to-citation mapping | Retrieved metadata exists, but no user-facing citation object exists. [VERIFIED: src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] | Normalize citations from source/version/page/section/origin metadata into response DTOs. [ASSUMED] |
| Grounding policy | Current retrieval filter protects the corpus, but there is no answer-time rule for weak or empty retrieval. [VERIFIED: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] | Add explicit refusal/limited-answer policy and tests. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| Legal guidance structure | Requirements need legal basis, penalty, documents/procedure, disclaimer, and next steps. [VERIFIED: .planning/REQUIREMENTS.md] | Define structured answer contract and prompt policy around conditional sections. [ASSUMED] |
| Validation contract | Existing tests cover ingestion, source lifecycle, retrieval policy, and chunk inspection, not chat behavior. [VERIFIED: src/test/java/com/vn/traffic/chatbot/ingestion/service/IngestionServiceTest.java] [VERIFIED: src/test/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicyTest.java] | Add fast unit/slice tests and a full Spring integration path for Phase 2. [ASSUMED] |

## Architecture Patterns

### Recommended Project Structure
```text
src/main/java/com/vn/traffic/chatbot/
├── chat/api/                 # Public chat REST controller + request/response DTOs
├── chat/service/             # Q&A orchestration, prompt policy, refusal logic
├── chat/prompt/              # System prompt and answer-format helpers
├── chat/citation/            # Mapping retrieved documents/metadata to citations
├── retrieval/                # Reuse existing retrieval policy + retrieval helpers
└── common/api/               # Reuse ApiPaths / ProblemDetail conventions
```
[ASSUMED]

### Pattern 1: Thin REST controller, orchestration in service
**What:** Keep request validation and HTTP concerns in the controller; put retrieval, model call, refusal logic, and response shaping in a dedicated service. [VERIFIED: src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/ingestion/api/IngestionAdminController.java]
**When to use:** For the Phase 2 public chat endpoint. [ASSUMED]
**Example:**
```java
@RestController
class MyController {
    private final ChatClient chatClient;

    MyController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/ai")
    String generation(String userInput) {
        return this.chatClient.prompt()
            .user(userInput)
            .call()
            .content();
    }
}
```
// Source: Spring AI ChatClient docs [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]

### Pattern 2: Advisor-based retrieval augmentation
**What:** Use Spring AI advisors to attach retrieval behavior to a chat call instead of hand-building prompt concatenation. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]
**When to use:** For single-turn grounded Q&A in Phase 2. [ASSUMED]
**Example:**
```java
ChatResponse response = ChatClient.builder(chatModel)
        .build().prompt()
        .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
        .user(userText)
        .call()
        .chatResponse();
```
// Source: Spring AI RAG docs [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]

### Pattern 3: Prefer `ChatClientResponse` when citations must be returned
**What:** Use the response type that includes chat output plus advisor execution context, because citation mapping needs access to retrieved documents. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]
**When to use:** For the main Phase 2 answer flow if advisor-based retrieval is used. [ASSUMED]
**Example:**
```java
ChatClientResponse response = this.chatClient.prompt()
    .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
    .user(message)
    .call()
    .chatClientResponse();
```
// Source: Spring AI ChatClient docs [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]

### Pattern 4: Structured REST response envelope for grounded legal answers
**What:** Return explicit fields rather than raw text: `answer`, `disclaimer`, `citations`, `legalBasis`, `penalties`, `requiredDocuments`, `procedureSteps`, `nextSteps`, and a `groundingStatus`. [ASSUMED]
**When to use:** Always for public Q&A responses in Phase 2. [ASSUMED]
**Example:**
```typescript
{
  "answer": "...",
  "groundingStatus": "GROUNDED",
  "disclaimer": "Thông tin chỉ nhằm mục đích tham khảo, không phải tư vấn pháp lý chính thức.",
  "citations": [
    {
      "sourceId": "...",
      "sourceTitle": "...",
      "origin": "...",
      "pageNumber": 4,
      "sectionRef": "Điều 6"
    }
  ],
  "legalBasis": ["..."],
  "penalties": ["..."],
  "requiredDocuments": ["..."],
  "procedureSteps": ["..."],
  "nextSteps": ["..."]
}
```
[ASSUMED]

### Anti-Patterns to Avoid
- **Hand-assembling RAG prompt text in controller code:** use advisors or a dedicated service so prompt policy and citation handling stay testable. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]
- **Returning uncited free-form text only:** this blocks CHAT-03 and weakens later admin/evaluation work. [VERIFIED: .planning/REQUIREMENTS.md] [ASSUMED]
- **Allowing model-only fallback by default:** legal answers without retrieved support are the main hallucination path in this phase. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]
- **Mixing admin and public chat routes:** keep public chat API separate from `/api/v1/admin/*` patterns. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java] [ASSUMED]

## Weak-Grounding Refusal Behavior

### Recommended policy
Use a three-state grounding outcome: `GROUNDED`, `LIMITED_GROUNDING`, and `REFUSED`. [ASSUMED]

| Mode | When | User-visible behavior | Tradeoff |
|------|------|-----------------------|----------|
| GROUNDED | Retrieved chunks meet threshold and support answer sections. [VERIFIED: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] | Return full structured answer with citations and disclaimer. [ASSUMED] | Best product fit when evidence is present. |
| LIMITED_GROUNDING | Some relevant retrieval exists, but support is incomplete for penalty/procedure/next-step details. [ASSUMED] | Answer only supported parts, omit unsupported sections, add explicit uncertainty notice. [ASSUMED] | Higher answer rate, but requires careful section-level suppression. |
| REFUSED | Retrieval is empty or clearly below grounding bar. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] | Decline to answer substantively, explain lack of reliable supporting source, invite reformulation or admin source expansion. [ASSUMED] | Safest for legal trust, but more refusals. |

### Recommendation
Prefer `REFUSED` over model-only fallback for Phase 2 default behavior. Spring AI’s `RetrievalAugmentationAdvisor` defaults to not answering when retrieved context is empty, which aligns with legal-grounding safety. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]

### Design choice
If the team wants slightly better usability, allow `LIMITED_GROUNDING` only when at least one approved/trusted/active source is retrieved and the response generator is instructed to omit unsupported penalty/procedure claims. [VERIFIED: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [ASSUMED]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| RAG prompt concatenation | Custom string-joined context injection in controllers | Spring AI `QuestionAnswerAdvisor` or `RetrievalAugmentationAdvisor` | Official abstractions already handle retrieval augmentation and request-scoped params. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| Vector metadata filtering | Ad hoc SQL filtering outside vector search path | `SearchRequest.filterExpression(...)` | PGvector integration supports portable metadata filters over JSON metadata. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] |
| Chat response envelope as plain string | Raw model text endpoint | Stable DTO contract | Needed for citations, disclaimer, and later frontend/admin quality workflows. [VERIFIED: .planning/PROJECT.md] [ASSUMED] |
| Error payload conventions | Controller-specific exception JSON | Existing `ProblemDetail` strategy | Repo already has centralized API error handling. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java] |

**Key insight:** The difficult part of Phase 2 is not vector search itself; it is making grounded evidence, refusal rules, and structured legal guidance observable and testable through a stable REST contract. [VERIFIED: .planning/REQUIREMENTS.md] [ASSUMED]

## Common Pitfalls

### Pitfall 1: Filters and metadata types drift apart
**What goes wrong:** Retrieval silently returns too much or too little because stored metadata uses string booleans while future filter code may assume JSON booleans. [VERIFIED: src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [VERIFIED: src/main/java/com/vn/traffic/chatbot/chunk/service/ChunkMetadataUpdater.java]
**Why it happens:** Phase 1 currently writes `trusted` and `active` as string values like `"true"`/`"false"`. [VERIFIED: src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java]
**How to avoid:** Keep filter expressions consistent with current storage format or normalize metadata representation before widening retrieval logic. [VERIFIED: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [ASSUMED]
**Warning signs:** Relevant approved content disappears after a metadata or filter refactor. [ASSUMED]

### Pitfall 2: Citations are generated from model text instead of retrieved metadata
**What goes wrong:** The answer sounds cited, but the citation objects are not traceable to actual retrieved chunks. [ASSUMED]
**Why it happens:** It is tempting to ask the model to invent legal references in prose. [ASSUMED]
**How to avoid:** Build citations from retrieved document metadata first, then let generation refer to those sources. [VERIFIED: src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]
**Warning signs:** Citation text names law articles not present in retrieved chunk metadata. [ASSUMED]

### Pitfall 3: Weak-grounding fallback becomes hidden hallucination
**What goes wrong:** The system answers unsupported questions with plausible legal advice because the model fills gaps from prior knowledge. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]
**Why it happens:** RAG systems often optimize for answer rate instead of evidence sufficiency. [ASSUMED]
**How to avoid:** Refuse by default on empty context; only emit limited sections when evidence exists. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] [ASSUMED]
**Warning signs:** Penalty/procedure fields are populated even when no citations are returned. [ASSUMED]

### Pitfall 4: Validation plan inherits outdated Maven commands
**What goes wrong:** Developers copy old Phase 1 validation commands that use `./mvnw`, but this repo is Gradle-based. [VERIFIED: .planning/phases/01-backend-foundation-knowledge-base/01-VALIDATION.md] [VERIFIED: build.gradle]
**Why it happens:** Phase 1 validation draft was not updated to match the actual build tool. [VERIFIED: .planning/phases/01-backend-foundation-knowledge-base/01-VALIDATION.md]
**How to avoid:** Standardize all Phase 2 validation commands on `./gradlew`. [VERIFIED: build.gradle]
**Warning signs:** Validation docs mention Maven or `pom.xml`. [VERIFIED: .planning/phases/01-backend-foundation-knowledge-base/01-VALIDATION.md]

## Code Examples

Verified patterns from official sources:

### Spring AI advisor-based retrieval call
```java
ChatResponse response = ChatClient.builder(chatModel)
        .build().prompt()
        .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
        .user(userText)
        .call()
        .chatResponse();
```
// Source: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]

### SearchRequest with metadata filtering
```java
vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("The World")
        .topK(TOP_K)
        .similarityThreshold(SIMILARITY_THRESHOLD)
        .filterExpression("author in ['john', 'jill'] && article_type == 'blog'")
        .build()
);
```
// Source: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]

### Current repo retrieval filter baseline
```java
public static final String RETRIEVAL_FILTER =
    "approvalState == 'APPROVED' && trusted == 'true' && active == 'true'";
```
// Source: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java [VERIFIED: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Implicit pgvector schema initialization by defaults | Schema initialization is opt-in in current Spring AI pgvector guidance. [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html] | Current docs | Do not assume Phase 2 can rely on auto-created vector schema outside existing Phase 1 setup. |
| Free-form model output as chat API | Structured chat response with retrieval context is the stronger pattern for grounded apps. [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html] [ASSUMED] | Current docs + current product requirements | Better support for citations, grounding state, and admin evaluation. |
| Generic answer-on-no-context behavior | Spring AI `RetrievalAugmentationAdvisor` defaults to instructing the model not to answer when context is empty. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] | Current docs | Default safety posture favors refusal for legal answers. |

**Deprecated/outdated:**
- Phase 1 validation draft’s Maven commands are outdated for this repository. [VERIFIED: .planning/phases/01-backend-foundation-knowledge-base/01-VALIDATION.md] [VERIFIED: build.gradle]

## Recommended Plan Decomposition

Use a small number of executable plan files: [ASSUMED]

1. **02-01-PLAN.md — Chat endpoint and grounded generation core** [ASSUMED]
   - Add public REST path, request/response DTOs, service orchestration, ChatClient wiring, retrieval advisor integration, and baseline disclaimer/refusal policy. [ASSUMED]
2. **02-02-PLAN.md — Citation/provenance mapping and legal response shaping** [ASSUMED]
   - Map retrieved metadata into citations, legal-basis sections, penalty/procedure/next-step fields, and stable grounding status outputs. [ASSUMED]
3. **02-03-PLAN.md — Validation hardening and threshold tuning** [ASSUMED]
   - Add unit/slice/integration tests, tune similarity/refusal thresholds, and verify supported vs unsupported section suppression. [ASSUMED]

This decomposition is small enough to execute and review, while separating core capability, data-contract shaping, and validation hardening. [ASSUMED]

## Open Questions

1. **Should Phase 2 use `QuestionAnswerAdvisor` first or go directly to `RetrievalAugmentationAdvisor`?** [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]
   - What we know: both are official; `RetrievalAugmentationAdvisor` has the strongest documented empty-context refusal behavior. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]
   - What's unclear: whether the team prefers simpler first delivery or more configurable grounding hooks immediately. [ASSUMED]
   - Recommendation: start with `RetrievalAugmentationAdvisor` if citation access from execution context is straightforward; otherwise use `QuestionAnswerAdvisor` plus explicit refusal checks in service code. [ASSUMED]

2. **What exact Vietnamese disclaimer text is product-approved?** [VERIFIED: .planning/REQUIREMENTS.md]
   - What we know: CHAT-04 requires a clear informational-guidance disclaimer. [VERIFIED: .planning/REQUIREMENTS.md]
   - What's unclear: exact wording, length, and whether it should always appear or only on substantive answers. [ASSUMED]
   - Recommendation: lock one consistent disclaimer string in planning before implementation. [ASSUMED]

3. **What should the public chat route be?** [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java]
   - What we know: existing constants cover only admin APIs. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java]
   - What's unclear: whether the public API should live under `/api/v1/chat` or another user-facing namespace. [ASSUMED]
   - Recommendation: add a non-admin `/api/v1/chat` base to keep public and admin contracts clearly separated. [ASSUMED]

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java runtime | Spring Boot app/tests | ✓ [VERIFIED: local shell] | not captured in shell output, but `java` command exists [VERIFIED: local shell] | — |
| Gradle wrapper | Build and tests | ✓ [VERIFIED: local shell] | wrapper executable works [VERIFIED: local shell] | — |
| Node.js | GSD tooling / future frontend-adjacent tasks | ✓ [VERIFIED: local shell] | v24.14.0 [VERIFIED: local shell] | — |
| Docker | Optional local Postgres/container workflows | ✓ [VERIFIED: local shell] | 28.0.4 [VERIFIED: local shell] | local installed DB if preferred [ASSUMED] |
| PostgreSQL CLI (`psql`) | Direct DB inspection commands | ✗ [VERIFIED: local shell] | — | use app tests, Dockerized DB, or JDBC-driven checks [ASSUMED] |

**Missing dependencies with no fallback:**
- None identified for planning. [VERIFIED: local shell]

**Missing dependencies with fallback:**
- `psql` is missing, but Phase 2 validation can rely on Gradle tests and/or Docker-backed app flows instead of direct CLI inspection. [VERIFIED: local shell] [ASSUMED]

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + AssertJ + Mockito [VERIFIED: src/test/java/com/vn/traffic/chatbot/TrafficLawChatbotApplicationTests.java] [VERIFIED: src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java] |
| Config file | `D:\ai\traffic-law-chatbot\.claude\worktrees\agent-a6abcda2\build.gradle` [VERIFIED: build.gradle] |
| Quick run command | `./gradlew test --tests "*Chat*Test" --tests "*Retrieval*Test" --tests "*Citation*Test"` [ASSUMED] |
| Full suite command | `./gradlew test` [VERIFIED: build.gradle] |

### Existing Test Infrastructure Relevant to Phase 2
- Smoke/context coverage already exists via `TrafficLawChatbotApplicationTests` and `SpringBootSmokeTest`. [VERIFIED: src/test/java/com/vn/traffic/chatbot/TrafficLawChatbotApplicationTests.java] [VERIFIED: src/test/java/com/vn/traffic/chatbot/common/SpringBootSmokeTest.java]
- Retrieval safety baseline already has `RetrievalPolicyTest`. [VERIFIED: src/test/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicyTest.java]
- Metadata inspection baseline already has `ChunkInspectionServiceTest`. [VERIFIED: src/test/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionServiceTest.java]
- Source lifecycle baseline already has `SourceServiceTest` and ingestion tests. [VERIFIED: src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java] [VERIFIED: src/test/java/com/vn/traffic/chatbot/ingestion/service/IngestionServiceTest.java]

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CHAT-01 | Chat endpoint returns a grounded answer when approved/trusted/active retrieval succeeds. [VERIFIED: .planning/REQUIREMENTS.md] | service unit + controller slice [ASSUMED] | `./gradlew test --tests "*ChatServiceTest" --tests "*ChatControllerTest"` [ASSUMED] | ❌ Wave 0 |
| CHAT-03 | Response includes citations derived from retrieved metadata. [VERIFIED: .planning/REQUIREMENTS.md] | unit | `./gradlew test --tests "*CitationMapperTest"` [ASSUMED] | ❌ Wave 0 |
| CHAT-04 | Response always includes approved disclaimer or refusal disclaimer path. [VERIFIED: .planning/REQUIREMENTS.md] | unit | `./gradlew test --tests "*ChatServiceTest"` [ASSUMED] | ❌ Wave 0 |
| LEGAL-01 | Legal basis section is only populated from supported retrieved material. [VERIFIED: .planning/REQUIREMENTS.md] | unit | `./gradlew test --tests "*AnswerComposerTest"` [ASSUMED] | ❌ Wave 0 |
| LEGAL-02 | Penalty section appears only when supported by retrieved evidence. [VERIFIED: .planning/REQUIREMENTS.md] | unit | `./gradlew test --tests "*AnswerComposerTest"` [ASSUMED] | ❌ Wave 0 |
| LEGAL-03 | Required documents/procedure section appears only when supported. [VERIFIED: .planning/REQUIREMENTS.md] | unit | `./gradlew test --tests "*AnswerComposerTest"` [ASSUMED] | ❌ Wave 0 |
| LEGAL-04 | Next-step guidance is present and grounding-aware. [VERIFIED: .planning/REQUIREMENTS.md] | unit + integration [ASSUMED] | `./gradlew test --tests "*AnswerComposerTest" --tests "*ChatFlowIntegrationTest"` [ASSUMED] | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "*Chat*Test" --tests "*Retrieval*Test" --tests "*Citation*Test"` [ASSUMED]
- **Per wave merge:** `./gradlew test` [VERIFIED: build.gradle]
- **Phase gate:** Full suite green before `/gsd-verify-work`. [VERIFIED: .planning/config.json]

### Wave 0 Gaps
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java` — grounded answer, refusal behavior, disclaimer inclusion, and section suppression for unsupported claims. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java` — request validation, response contract shape, and ProblemDetail behavior for bad requests. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/citation/CitationMapperTest.java` — mapping from retrieved metadata to citation DTOs. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerTest.java` — legal-basis/penalty/procedure/next-step field population rules. [ASSUMED]
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java` — end-to-end chat wiring with mocked model/retrieval boundary or Spring test configuration. [ASSUMED]

### Per-task verification guidance
- Any task touching retrieval thresholds or filter expressions must rerun `RetrievalPolicyTest` plus the new chat service tests. [VERIFIED: src/test/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicyTest.java] [ASSUMED]
- Any task touching response DTOs or citation mapping must rerun controller tests and citation mapper tests. [ASSUMED]
- Any task touching refusal/disclaimer logic must include both grounded and no-context test cases. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] [ASSUMED]

## Security Domain

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no [ASSUMED] | Public Phase 2 chat appears unauthenticated in current requirements, but admin APIs remain separate. [VERIFIED: .planning/REQUIREMENTS.md] [ASSUMED] |
| V3 Session Management | no [ASSUMED] | Single-turn Phase 2 scope does not require session state. [VERIFIED: .planning/ROADMAP.md] [ASSUMED] |
| V4 Access Control | yes [ASSUMED] | Keep public chat endpoints separate from `/api/v1/admin/*`. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java] |
| V5 Input Validation | yes [VERIFIED: build.gradle] | Spring validation + centralized `ProblemDetail` handling. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java] |
| V6 Cryptography | no direct Phase 2 feature [ASSUMED] | Use provider SDK defaults; do not hand-roll cryptography. [ASSUMED] |

### Known Threat Patterns for Spring REST + RAG
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via retrieved content | Tampering | Keep retrieval restricted to approved/trusted/active sources and enforce answer-only-from-context instructions. [VERIFIED: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] |
| Citation spoofing by model output | Spoofing | Build citations from retrieval metadata, not generated prose. [VERIFIED: src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java] [ASSUMED] |
| Over-disclosure of unsupported legal claims | Information Disclosure | Use refusal or limited-grounding suppression when evidence is weak. [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html] [ASSUMED] |
| Invalid request payloads | Tampering | DTO validation + `ProblemDetail` error responses. [VERIFIED: src/main/java/com/vn/traffic/chatbot/common/error/GlobalExceptionHandler.java] |

## Risks and Dependencies

| Item | Type | Impact | Mitigation |
|------|------|--------|------------|
| Spring AI milestone version (`2.0.0-M4`) | Dependency risk [VERIFIED: build.gradle] | API drift is more likely than with a GA release. [ASSUMED] | Keep Phase 2 surface area thin and isolate chat wiring behind one service/config layer. [ASSUMED] |
| Missing 02-CONTEXT.md | Planning risk [VERIFIED: local filesystem] | User-locked decisions could be absent from this research. | Create/confirm phase context before locking implementation details in plans. [ASSUMED] |
| No current chat-specific tests | Validation risk [VERIFIED: repository grep on src/test/java] | Grounding regressions could ship silently. | Treat chat tests as Wave 0 for Phase 2. [ASSUMED] |
| Missing local `psql` | Tooling risk [VERIFIED: local shell] | Direct SQL spot-checks are less convenient. | Use Gradle test coverage and Docker/local DB app flows. [ASSUMED] |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A structured response envelope should include `answer`, `groundingStatus`, `citations`, `legalBasis`, `penalties`, `requiredDocuments`, `procedureSteps`, and `nextSteps`. | Architecture Patterns | Planner may over-specify DTO design before product wording is confirmed. |
| A2 | Phase 2 should be decomposed into exactly three plan files. | Summary / Recommended Plan Decomposition | Planner may choose a different slice count. |
| A3 | Quick test command should target future `*Chat*Test`, `*Retrieval*Test`, and `*Citation*Test` patterns. | Validation Architecture | Test naming may differ from implementation. |
| A4 | Public chat route should be `/api/v1/chat`. | Open Questions | Route naming may conflict with future frontend/backend conventions. |
| A5 | `LIMITED_GROUNDING` is worth keeping as an explicit middle state. | Weak-Grounding Refusal Behavior | Product may prefer binary grounded/refused behavior only. |
| A6 | Public Phase 2 chat is unauthenticated. | Security Domain | Access-control assumptions may change later. |

## Sources

### Primary (HIGH confidence)
- Local repository files listed throughout this document. [VERIFIED: local filesystem]
- Spring AI ChatClient docs — https://docs.spring.io/spring-ai/reference/api/chatclient.html [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]
- Spring AI RAG docs — https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html [CITED: https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html]
- Spring AI PGvector docs — https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html [CITED: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html]
- Spring Boot testing docs — https://docs.spring.io/spring-boot/reference/testing/index.html [CITED: https://docs.spring.io/spring-boot/reference/testing/index.html]

### Secondary (MEDIUM confidence)
- None needed beyond official docs and repository inspection. [VERIFIED: session research log]

### Tertiary (LOW confidence)
- All `[ASSUMED]` items above require confirmation during planning/discuss. [VERIFIED: this document]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - repo dependencies and official Spring docs align clearly. [VERIFIED: build.gradle] [CITED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]
- Architecture: MEDIUM - controller/service shape is verified in repo, but exact chat DTO and advisor choice still need planning decisions. [VERIFIED: src/main/java/com/vn/traffic/chatbot/source/api/SourceAdminController.java] [ASSUMED]
- Pitfalls: MEDIUM - metadata/filter mismatch and outdated validation commands are verified; some citation/refusal failure modes are inferred from the phase design. [VERIFIED: src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java] [VERIFIED: .planning/phases/01-backend-foundation-knowledge-base/01-VALIDATION.md] [ASSUMED]

**Research date:** 2026-04-08
**Valid until:** 2026-04-15
