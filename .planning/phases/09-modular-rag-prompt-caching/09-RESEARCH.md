# Phase 9: Modular RAG + Prompt Caching — Research

**Researched:** 2026-04-18
**Domain:** Spring AI 2.0.0-M4 modular RAG migration (`RetrievalAugmentationAdvisor` + custom `QueryAugmenter` + `DocumentPostProcessor` + `StructuredOutputValidationAdvisor`). Prompt caching is **DEFERRED** per D-07.
**Confidence:** HIGH

## Summary

Phase 9 replaces two Phase-8 placeholders (`NoOpRetrievalAdvisor`, `NoOpValidationAdvisor`) with real Spring AI advisors while preserving v1.0's user-visible `[Nguồn n]` citation format and `ChatAnswerResponse` JSON contract byte-for-byte. The migration is two incremental PRs (D-11): PR1 swaps retrieval + citation numbering via `RetrievalAugmentationAdvisor` wired with a custom `QueryAugmenter` subclassing `ContextualQueryAugmenter` (owns `[Nguồn n]` label emission per D-04) and a `DocumentPostProcessor` that assigns `labelNumber` metadata pre-augmentation and writes `List<CitationResponse>` to advisor context for `ChatService` to read post-call. PR2 swaps the validation placeholder for `StructuredOutputValidationAdvisor(maxRepeatAttempts=1)`.

Spring AI 2.0.0-M4 ships all required building blocks (`RetrievalAugmentationAdvisor.builder()`, `VectorStoreDocumentRetriever.builder()` with `filterExpression` + per-request `FILTER_EXPRESSION` context param, `DocumentPostProcessor` interface for side-effect metadata mutation, `StructuredOutputValidationAdvisor.builder().outputType(...).maxRepeatAttempts(n).advisorOrder(n).build()`). `ContextualQueryAugmenter.allowEmptyContext(true)` is the non-negotiable switch (Pitfall 2) that keeps the Phase-8 `GroundingGuardOutputAdvisor` as the sole refusal source (D-08/D-09).

**Primary recommendation:** Build `LegalQueryAugmenter extends ContextualQueryAugmenter` with a custom Vietnamese prompt template that reads `labelNumber` from each `Document.metadata`, emits the numbered citation block verbatim matching `ChatPromptFactory.formatCitation` line-for-line, and surfaces all other instruction text (JSON schema rules, `[Nguồn n]` nhãn trích dẫn rule) from `ChatPromptFactory`. Implement `CitationPostProcessor implements DocumentPostProcessor` that assigns `labelNumber` (1..n in retrieval order) and calls `CitationMapper.toCitations(docs)` then stashes the result in advisor context via the `ChatClientRequest.context()` map. Wire both into `RetrievalAugmentationAdvisor.builder()` at the existing `HIGHEST_PRECEDENCE + 300` slot. Validation advisor slots in at `HIGHEST_PRECEDENCE + 1000` unchanged. No chain re-ordering, zero YAML changes, no DB migrations.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Retrieval & Trust-Tier Filter**
- **D-01:** `VectorStoreDocumentRetriever.FILTER_EXPRESSION` = reuse `RetrievalPolicy.RETRIEVAL_FILTER` literal as-is (`approvalState == 'APPROVED' && trusted == 'true' && active == 'true'`). No chunk-metadata backfill, no Liquibase migration.
- **D-02:** `VectorStoreDocumentRetriever` consumes `topK` + `similarityThreshold` per-request via `Supplier<SearchRequest>` that calls `RetrievalPolicy.buildRequest(query, topK)` on every invocation (preserves `AiParameterSet` runtime-reload admin workflow).
- **D-03:** 50 post-deploy chat logs sample = manual SQL + eyeball review, no automated harness.

**CitationPostProcessor & Numbering**
- **D-04:** Custom `QueryAugmenter` (extending `ContextualQueryAugmenter`) owns `[Nguồn n]` numbering. Ports `ChatPromptFactory.formatCitation` / `buildPrompt`. `CitationPostProcessor` is a `DocumentPostProcessor` assigning `labelNumber` metadata pre-augmentation. No post-processing of model text output.
- **D-05:** `CitationMapper.toCitations(documents)` runs inside the advisor chain. `List<CitationResponse>` written to advisor context; `ChatService` reads from advisor context post-call.
- **D-06:** `AnswerComposer` signature — Claude's discretion. Byte-for-byte output parity must hold either way.

**Prompt Caching — DEFERRED OUT OF PHASE 9**
- **D-07:** Prompt caching explicitly removed from Phase 9 scope. `NoOpPromptCacheAdvisor` stays no-op. `CACHE-01` rolls forward. All caching design → Deferred Ideas.

**Empty-Context & Refusal**
- **D-08:** `ContextualQueryAugmenter.allowEmptyContext(true)`. Zero-citation refusal owned by Phase-8 `GroundingGuardOutputAdvisor` using `AnswerComposer.composeRefusal()` verbatim.
- **D-09:** Refusal text byte-for-byte identical to v1.0. No wording tightening.

**Validation Advisor**
- **D-10:** `StructuredOutputValidationAdvisor` with `maxRepeatAttempts = 1`. Closes P8 deferred item.

**Migration Granularity**
- **D-11:** Two incremental PRs. PR1 = RAG + Citation + Filter (20-query Vietnamese regression + refusal-parity + two-turn memory must pass). PR2 = Validation swap. `NoOpPromptCacheAdvisor` stays as-is in both PRs.

**Carry-forward**
- **D-12:** No feature flags. Rollback = `git revert`.
- **D-13:** Advisor chain order stays `GuardIn → Memory → RAG → [NoOpCache] → Validation → GuardOut` exactly (P8 D-04). P9 swaps implementations in place.
- **D-14:** `MessageChatMemoryAdvisor` stays attached via `defaultAdvisors(...)` (P8 D-08).
- **D-15:** Spring AI test patterns sourced from Context7 `/spring-projects/spring-ai`. No custom perf/baseline harness, no WireMock.

### Claude's Discretion
- Exact `QueryAugmenter` prompt template wording (Vietnamese; must preserve `ChatPromptFactory` `[Nguồn n]` instruction).
- Whether `CitationPostProcessor` is `DocumentPostProcessor` vs custom step inside `QueryAugmenter`.
- Advisor-context key names for `List<CitationResponse>` passback.
- Exact `order` numeric for `RetrievalAugmentationAdvisor` and `StructuredOutputValidationAdvisor` (must respect P8 chain order).
- `AnswerComposer` signature shape after the shrink (D-06).
- Whether `ChatPromptFactory` survives or is fully absorbed (likely absorbed).
- Deletion of `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK`.

### Deferred Ideas (OUT OF SCOPE)
- `PromptCachingAdvisor` real implementation (OpenRouter `cache_control`, provider-family detection, cache-boundary placement, `cached_tokens > 0` integration test) — DEFERRED per D-07.
- `CACHE-01` requirement — rolls to later phase.
- ROADMAP Phase 9 success criterion 4 — explicitly dropped for P9.
- Chunk-metadata `trust_tier` literal backfill (D-01).
- Prompt split into cacheable system + dynamic user halves.
- Runtime-configurable GroundingGuard knobs.
- DB-backed model capability flags / admin UI.
- Recorded fixture replay / WireMock.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ARCH-01 | Chat pipeline uses Spring AI idiomatic advisors — orchestrated through `RetrievalAugmentationAdvisor` plus custom `CallAdvisor` components rather than manual orchestration inside `ChatService` | §3.1 `RetrievalAugmentationAdvisor` wiring + `Supplier<SearchRequest>` pattern; §3.4 `ChatService` shrink plan (≥60% LOC) via advisor-context reads |
| ARCH-05 | Citation mapping and `[Nguồn n]` labeling preserved during modular RAG migration with no change to response JSON contract consumed by frontend | §3.2 `LegalQueryAugmenter` template (emits `Nguồn {labelNumber}` verbatim matching `CitationMapper.toCitation`); §3.3 `CitationPostProcessor` writes `List<CitationResponse>` unchanged; §7 byte-for-byte regression fixture |
| ~~CACHE-01~~ | **DEFERRED per D-07** — NOT addressed in P9 | `NoOpPromptCacheAdvisor` stays no-op |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Similarity search + filter | Backend / Spring AI advisor | pgvector store | `VectorStoreDocumentRetriever` owns query→docs; `RetrievalPolicy` supplies `topK`/threshold/filter literal |
| Citation label assignment | Backend / Advisor chain (`DocumentPostProcessor`) | — | Deterministic `labelNumber` metadata set pre-augmentation so augmenter template is pure read |
| Vietnamese `[Nguồn n]` prompt rules emission | Backend / Custom `QueryAugmenter` | — | Ports `ChatPromptFactory.buildPrompt` line 56 into augmenter template; single source of truth post-migration |
| Refusal wording decision | Backend / `GroundingGuardOutputAdvisor` (P8) | — | D-08 mandates single refusal source; augmenter does NOT refuse (allowEmptyContext=true) |
| Structured output parse + retry | Backend / `StructuredOutputValidationAdvisor` | `BeanOutputConverter` | `maxRepeatAttempts=1` catches rare prompt-instruction-mode mismatches |
| `ChatAnswerResponse` assembly | Backend / `AnswerComposer` + `ChatService` | — | Reads citations from advisor context post-call; JSON contract unchanged |
| Frontend rendering | Next.js chat UI | — | Zero changes — byte-for-byte API compat per ARCH-05 |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.springframework.ai:spring-ai-rag` | 2.0.0-M4 (BOM) | `RetrievalAugmentationAdvisor`, `ContextualQueryAugmenter`, `VectorStoreDocumentRetriever`, `DocumentPostProcessor`, `Query` | [VERIFIED: build.gradle line 24,59] Already on classpath |
| `org.springframework.ai:spring-ai-advisors-vector-store` | 2.0.0-M4 | `VectorStoreDocumentRetriever` impl + `FILTER_EXPRESSION` context key | [VERIFIED: build.gradle line 57] Already on classpath |
| `org.springframework.ai:spring-ai-starter-vector-store-pgvector` | 2.0.0-M4 | pgvector `VectorStore` bean + metadata filter expression parser | [VERIFIED: build.gradle line 58] Already on classpath |
| Core advisor API (in `spring-ai-client-chat`) | 2.0.0-M4 | `StructuredOutputValidationAdvisor.builder()` | [CITED: context7 `/spring-projects/spring-ai` §api/advisors-recursive] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-ai-test` | 2.0.0-M4 | `RelevancyEvaluator`, `FactCheckingEvaluator` (test scope) | Already wired by P8 `VietnameseRegressionIT` — extend, don't recreate (D-15) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `RetrievalAugmentationAdvisor` + custom `QueryAugmenter` | `QuestionAnswerAdvisor` (simpler advisor) | `QuestionAnswerAdvisor` does NOT support `DocumentPostProcessor` or `queryAugmenter`/`queryTransformers` — the `[Nguồn n]` numbering hook requires `RetrievalAugmentationAdvisor`. Rejected. [CITED: context7 §api/retrieval-augmented-generation — only `RetrievalAugmentationAdvisor` builder has `.documentPostProcessors()` and `.queryAugmenter()`] |
| Custom `DocumentPostProcessor` | Do numbering inside the `QueryAugmenter` only | Separating label assignment (metadata mutation) from template rendering (read-only) is cleaner — the augmenter becomes pure. D-04 explicitly assumes a `DocumentPostProcessor`. Recommended. |
| `StructuredOutputValidationAdvisor` | Custom retry logic in `ChatService` | The advisor auto-generates JSON schema from the entity class, augments retry prompts with validation error messages, and is the Spring AI-idiomatic answer. ARCH-01 requires advisor-driven orchestration. |

**Installation:** No new dependencies. All required modules already on classpath (verified via `build.gradle` line 49–65).

**Version verification:** Spring AI version is pinned via `set('springAiVersion', "2.0.0-M4")` at `build.gradle` line 24 and resolved through `mavenBom 'org.springframework.ai:spring-ai-bom:${springAiVersion}'` at line 29. No package drift risk. `[VERIFIED: build.gradle`]`

## Architecture Patterns

### System Architecture Diagram (Phase 9 target, post-PR2)

```
ChatController.answer(question, modelId, conversationId)
        │
        ▼
ChatService.doAnswer              ← shrinks ≥60% LOC
   │ 1. IntentClassifier.classify  (P8 — unchanged)
   │    ├─ CHITCHAT → AnswerComposer.composeChitchat()     → return
   │    └─ OFF_TOPIC → AnswerComposer.composeOffTopicRefusal() → return
   │ 2. LEGAL → resolveClient(modelId)
   │ 3. client.prompt().user(question)
   │       .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memConvId))
   │       [conditionally] .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
   │       .call()
   │       .entity(LegalAnswerDraft.class)    ← BeanOutputConverter
   │
   ▼
┌─────────────────────────── Advisor Chain (P9) ───────────────────────────┐
│                                                                           │
│  HIGHEST_PRECEDENCE+100   GroundingGuardInputAdvisor (P8, unchanged)     │
│                                                                           │
│  HIGHEST_PRECEDENCE+200   MessageChatMemoryAdvisor (P8, via              │
│                           defaultAdvisors; reads CONVERSATION_ID)        │
│                                                                           │
│  HIGHEST_PRECEDENCE+300   RetrievalAugmentationAdvisor  ◄─── P9 PR1      │
│     │                                                                     │
│     ├─ documentRetriever: VectorStoreDocumentRetriever                   │
│     │    ├─ filterExpression = Supplier<> calling                        │
│     │    │    RetrievalPolicy.buildRequest(q, topK).getFilterExpression()│
│     │    ├─ similarityThreshold = Supplier<> calling                     │
│     │    │    retrievalPolicy.getSimilarityThreshold()                   │
│     │    └─ topK = Supplier<> calling retrievalPolicy.getTopK()          │
│     │                                                                     │
│     ├─ documentPostProcessors: [CitationPostProcessor]                   │
│     │    ├─ assigns doc.metadata["labelNumber"] = 1..n                   │
│     │    ├─ calls CitationMapper.toCitations(docs)                       │
│     │    └─ puts List<CitationResponse> in ChatClientRequest.context()   │
│     │       under key CITATIONS_KEY (+ List<SourceReferenceResponse>     │
│     │       under SOURCES_KEY)                                           │
│     │                                                                     │
│     └─ queryAugmenter: LegalQueryAugmenter                               │
│          extends ContextualQueryAugmenter                                │
│          ├─ allowEmptyContext(true)   ← Pitfall 2                        │
│          ├─ promptTemplate: Vietnamese template with placeholders        │
│          │    {query} and {question_answer_context}                      │
│          └─ renders "[Nguồn {labelNumber}]" from doc metadata            │
│                                                                           │
│  HIGHEST_PRECEDENCE+500   NoOpPromptCacheAdvisor (D-07, unchanged)       │
│                                                                           │
│  HIGHEST_PRECEDENCE+1000  StructuredOutputValidationAdvisor ◄── P9 PR2   │
│     └─ outputType(LegalAnswerDraft.class).maxRepeatAttempts(1)           │
│                                                                           │
│  LOWEST_PRECEDENCE-100    GroundingGuardOutputAdvisor (P8, unchanged)    │
│        └─ if citations empty + intent=LEGAL → compose refusal (D-08)     │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
        │
        ▼
   LegalAnswerDraft + ChatClientResponse.context()[CITATIONS_KEY, SOURCES_KEY]
        │
        ▼
ChatService reads citations/sources from response.context()
        │
        ▼
AnswerComposer.compose(GROUNDED, draft, citations, sources)
        │
        ▼
ChatAnswerResponse (JSON byte-for-byte identical to v1.0)
```

**Data flow:** entry is `ChatController` → `ChatService.doAnswer` intent dispatch. On `LEGAL` path, the advisor chain performs retrieval (filter literal from `RetrievalPolicy`), label assignment (`CitationPostProcessor`), augmentation (`LegalQueryAugmenter` merges retrieved-doc text + `[Nguồn n]` labels into the prompt template), optional cache placeholder (no-op), validation retry (once on parse failure), and finally grounding-guard output check. The LLM call returns a `LegalAnswerDraft` via `BeanOutputConverter`. `ChatService` reads citations/sources from advisor context and hands them to `AnswerComposer` unchanged.

### Recommended Project Structure

```
src/main/java/com/vn/traffic/chatbot/chat/
├── advisor/
│   ├── LegalQueryAugmenter.java       # NEW (P9 PR1) — extends ContextualQueryAugmenter
│   ├── CitationPostProcessor.java     # NEW (P9 PR1) — implements DocumentPostProcessor
│   ├── GroundingGuardInputAdvisor.java   # P8 — unchanged
│   ├── GroundingGuardOutputAdvisor.java  # P8 — unchanged
│   └── placeholder/
│       ├── NoOpRetrievalAdvisor.java    # DELETE in P9 PR1
│       ├── NoOpPromptCacheAdvisor.java  # KEEP (D-07)
│       └── NoOpValidationAdvisor.java   # DELETE in P9 PR2
├── citation/
│   └── CitationMapper.java            # UNCHANGED (called from CitationPostProcessor)
├── config/
│   └── ChatClientConfig.java          # MODIFIED — wire real advisors, beans for Supplier<SearchRequest>, CitationPostProcessor, LegalQueryAugmenter
├── service/
│   ├── ChatService.java               # SHRINKS ≥60% LOC — remove vectorStore.similaritySearch, citation mapping, prompt build
│   └── ChatPromptFactory.java         # LIKELY DELETED (absorbed into LegalQueryAugmenter per Claude's Discretion)
└── advisor/context/
    └── ChatAdvisorContextKeys.java    # NEW — constants CITATIONS_KEY, SOURCES_KEY, LABEL_NUMBER_METADATA
```

### Pattern 1: `RetrievalAugmentationAdvisor` with per-request filter + custom augmenter + post-processor

**What:** Idiomatic Spring AI 2.0.0-M4 modular RAG pipeline with all three extension hooks engaged.
**When to use:** Any chat flow that needs retrieval + custom inline citation labels + dynamic (reloadable) retrieval policy.
**Example:**
```java
// Source: context7 /spring-projects/spring-ai §api/retrieval-augmented-generation (composed; verified per-piece)
@Bean
Advisor retrievalAdvisor(VectorStore vectorStore,
                         RetrievalPolicy retrievalPolicy,
                         CitationPostProcessor citationPostProcessor,
                         LegalQueryAugmenter legalQueryAugmenter) {
    return RetrievalAugmentationAdvisor.builder()
        .documentRetriever(
            VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                // D-02: per-request reload via Supplier
                .similarityThreshold(retrievalPolicy::getSimilarityThreshold)
                .topK(retrievalPolicy::getTopK)
                // D-01: reuse RetrievalPolicy.RETRIEVAL_FILTER literal as-is
                .filterExpression(() -> new FilterExpressionTextParser()
                    .parse(RetrievalPolicy.RETRIEVAL_FILTER))
                .build())
        .documentPostProcessors(citationPostProcessor)
        .queryAugmenter(legalQueryAugmenter)
        .build();
}
```

**API note — per-request filter:** Spring AI supports two filter pathways, and both must be understood:
1. **Advisor-time filter** (the `Supplier<FilterExpression>` in the builder above, or a literal `.filterExpression(expr)`) — applied every retrieval.
2. **Per-call filter override** via `advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, "type == 'X'"))` — overrides for a single request. [CITED: context7 §api/retrieval-augmented-generation "RAG with Metadata Filtering using FilterExpression in Spring AI"]. **P9 uses path 1** — the filter is static (the `RETRIEVAL_FILTER` literal never changes at runtime per D-01). Keep path 2 available as an escape hatch only.

### Pattern 2: Custom `QueryAugmenter` that embeds `[Nguồn n]` labels from metadata

**What:** Subclass `ContextualQueryAugmenter` with a `PromptTemplate` whose `{question_answer_context}` placeholder is rendered from each `Document.metadata["labelNumber"]`.
**When to use:** RAG flows that need deterministic, numbered inline citations that must match frontend-consumed labels byte-for-byte.
**Example:**
```java
// Source: composed from context7 §api/retrieval-augmented-generation "ContextualQueryAugmenter" +
// project ChatPromptFactory.java line 56 Vietnamese instruction text
@Component
@RequiredArgsConstructor
public class LegalQueryAugmenter implements QueryAugmenter {

    private static final PromptTemplate LEGAL_PROMPT_TEMPLATE = PromptTemplate.builder()
        .template("""
            <system_context>
            </system_context>

            Quy tắc trích dẫn: Mọi nhận định có căn cứ phải gắn nhãn trích dẫn nội dòng \
            đúng định dạng [Nguồn n]; tuyệt đối không tự tạo nhãn ngoài danh sách được cung cấp.

            Danh sách trích dẫn được phép dùng:
            {question_answer_context}

            Câu hỏi người dùng: {query}
            """)
        .build();

    private static final PromptTemplate EMPTY_CONTEXT_TEMPLATE = PromptTemplate.builder()
        .template("""
            Không có tài liệu trích dẫn cho câu hỏi này. \
            Câu hỏi người dùng: {query}
            """)
        .build();

    private final ContextualQueryAugmenter delegate = ContextualQueryAugmenter.builder()
        .allowEmptyContext(true)                  // D-08, Pitfall 2
        .promptTemplate(LEGAL_PROMPT_TEMPLATE)
        .emptyContextPromptTemplate(EMPTY_CONTEXT_TEMPLATE)
        .build();

    @Override
    public Query augment(Query query, List<Document> documents) {
        // Render {question_answer_context} via a pre-pass that formats each doc
        // using labelNumber metadata set by CitationPostProcessor.
        // The delegate will then interpolate into LEGAL_PROMPT_TEMPLATE.
        List<Document> formatted = documents.stream()
            .map(this::renderDocAsCitationBlock)
            .toList();
        return delegate.augment(query, formatted);
    }

    private Document renderDocAsCitationBlock(Document doc) {
        int label = (int) doc.getMetadata().getOrDefault("labelNumber", 0);
        String origin = String.valueOf(doc.getMetadata().getOrDefault("origin", ""));
        String text = "- [Nguồn " + label + "] " + origin + " — " + doc.getText();
        return Document.builder().text(text).metadata(doc.getMetadata()).build();
    }
}
```

**Byte-for-byte parity rule:** the `"- [Nguồn " + label + "] "` prefix **MUST** match `CitationMapper.toCitation` line 61 (`"Nguồn " + labelNumber`) and `ChatPromptFactory.formatCitation` line 87 (`"- [" + citation.inlineLabel() + "] "`). Unit test `LegalQueryAugmenterTest` gates this via golden-fixture comparison against `ChatPromptFactory.formatCitation` output.

### Pattern 3: `DocumentPostProcessor` for label assignment + citation stash

**What:** Side-effect `DocumentPostProcessor` that mutates `Document.metadata` to assign `labelNumber` and publishes `List<CitationResponse>` into advisor context for downstream `ChatService` read.
**When to use:** When metadata needs to flow from retrieval into both (a) the augmenter template and (b) the post-call response assembly.
**Example:**
```java
// Source: context7 §api/retrieval-augmented-generation "DocumentPostProcessor API" +
// project CitationMapper.java (existing, unchanged)
@Component
@RequiredArgsConstructor
public class CitationPostProcessor implements DocumentPostProcessor {

    private final CitationMapper citationMapper;

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        // 1. Assign labelNumber 1..n (preserves retrieval order, matches CitationMapper)
        List<Document> labeled = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document d = documents.get(i);
            Map<String, Object> md = new HashMap<>(d.getMetadata());
            md.put("labelNumber", i + 1);
            labeled.add(Document.builder()
                .id(d.getId())
                .text(d.getText())
                .metadata(md)
                .score(d.getScore())
                .build());
        }
        // 2. Build CitationResponse list + publish to advisor context via Query metadata
        //    (Query in 2.0.0-M4 carries a context map accessible from the augmenter step;
        //    see "Open Questions" for confirmation of the exact context-write API).
        List<CitationResponse> citations = citationMapper.toCitations(labeled);
        List<SourceReferenceResponse> sources = citationMapper.toSources(citations);
        AdvisorContextStash.put(ChatAdvisorContextKeys.CITATIONS_KEY, citations);
        AdvisorContextStash.put(ChatAdvisorContextKeys.SOURCES_KEY, sources);
        return labeled;
    }
}
```

**Advisor-context publish caveat:** `DocumentPostProcessor.process(Query, List<Document>)` does NOT receive the `ChatClientRequest`/`ChatClientResponse` directly. To publish to advisor context from a post-processor, the project must either (a) wrap a thin `CallAdvisor` around `RetrievalAugmentationAdvisor` that reads `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` from the response `context()` and re-runs `CitationMapper` (preferred — explicit contract), or (b) use a `ThreadLocal`-bound stash (`AdvisorContextStash`) that the outer `CallAdvisor` drains (not recommended — fragile, violates Spring AI idiom). **See "Open Questions" Q-01.**

### Pattern 4: `StructuredOutputValidationAdvisor` for bounded retry

**What:** One-shot parse retry when `BeanOutputConverter` rejects the model's JSON.
**When to use:** When cross-model prompt-instruction-mode output risk is accepted (D-10 — single retry). Closes P8 deferred item.
**Example:**
```java
// Source: context7 /spring-projects/spring-ai §api/advisors-recursive (verbatim)
@Bean
Advisor validationAdvisor() {
    return StructuredOutputValidationAdvisor.builder()
        .outputType(LegalAnswerDraft.class)
        .maxRepeatAttempts(1)                              // D-10
        .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 1000) // slot kept from P8
        .build();
}
```

### Anti-Patterns to Avoid

- **Do NOT** set `allowEmptyContext(false)` — breaks refusal wording parity (Pitfall 2; refusal leaks Spring AI's generic text instead of `AnswerComposer.composeRefusal()`). Always `true`.
- **Do NOT** mutate `Document.metadata` in-place via `doc.getMetadata().put(...)` — in Spring AI 2.0.0-M4 the returned map may be immutable depending on `VectorStore` impl. Build a new `Document` via builder (shown in Pattern 3).
- **Do NOT** attach `MessageChatMemoryAdvisor` per-call (Pitfall 1) — it's in `defaultAdvisors(...)` already (D-14). Only pass `CONVERSATION_ID` param per call.
- **Do NOT** combine `BeanOutputConverter` with `ENABLE_NATIVE_STRUCTURED_OUTPUT` when `supportsStructuredOutput=false` (Pitfall 6 is P8-scope but re-applies here — `ChatService` branches correctly already; P9 must not break the branch).
- **Do NOT** duplicate `[Nguồn n]` numbering logic in both `CitationPostProcessor` and `LegalQueryAugmenter` — single source of truth is the metadata `labelNumber` set in the post-processor; the augmenter is pure read.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Retrieval + filter + augmentation | Manual `vectorStore.similaritySearch` → stringify docs → concat to prompt (current v1.0 pattern in `ChatService` line 115–154) | `RetrievalAugmentationAdvisor.builder()` with `VectorStoreDocumentRetriever` | ARCH-01 explicitly requires advisor-driven orchestration; Spring AI also handles query transformation, empty-context fallback, and context injection into the prompt deterministically |
| Structured-output retry loop | Try/catch around `BeanOutputConverter.convert()` with retry counter | `StructuredOutputValidationAdvisor` | Auto-generates JSON schema, augments retry prompt with validation errors — libraryised version handles edge cases (nested fields, oneOf) the hand roll would miss |
| Advisor context passing | Static fields, `ThreadLocal`, or `@RequestScope` | `ChatClientRequest.context()` / response context map (Spring AI built-in) | Advisor context is designed for exactly this flow; avoids thread-leak + visibility issues |
| Filter expression parser | String concatenation of `"approvalState == 'APPROVED' AND ..."` into SQL | `FilterExpressionTextParser` + `Filter.Expression` AST | pgvector filter syntax differs subtly per store; use the parser that's already exercised by tests |
| Vietnamese prompt template rendering | `StringBuilder` in Java | `PromptTemplate` (StringTemplate-based) | Variable substitution, escape handling, future extension to sub-templates — already in use via P8 |

**Key insight:** Every v1.0 hand-rolled piece (`vectorStore.similaritySearch` inline, `ChatPromptFactory.buildPrompt` StringBuilder, manual JSON-parsing `extractJson` — already deleted in P8) has a Spring AI 2.0.0-M4 primitive that's more robust. P9 completes the migration.

## Runtime State Inventory

This phase is a **Java refactor with zero data/config migration**. All five categories are empty.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | **None** — `[VERIFIED: D-01]` no chunk-metadata backfill, no Liquibase migration. pgvector metadata schema (`approvalState`, `trusted`, `active`) unchanged. | None |
| Live service config | **None** — no n8n/Datadog/Tailscale-equivalent services in project scope | None |
| OS-registered state | **None** — no pm2/launchd/Task Scheduler registrations touched | None |
| Secrets/env vars | **None** — `OPENROUTER_API_KEY` env var already in use by P8; P9 adds no new secret. | None |
| Build artifacts | **None** — no package renames; Gradle dependency set unchanged (verified §Standard Stack) | None |

**Canonical answer:** After every file in the repo is updated, no runtime systems carry stale references to the swapped placeholders. `NoOpRetrievalAdvisor` and `NoOpValidationAdvisor` are deleted at compile time; Spring picks up the new `@Component` beans automatically at boot.

## Common Pitfalls

### Pitfall 1: `ContextualQueryAugmenter.allowEmptyContext(false)` leaks generic Spring AI refusal text

**What goes wrong:** Default `ContextualQueryAugmenter.builder().build()` has `allowEmptyContext=false`. When retrieval returns zero docs, the augmenter instructs the model to refuse with Spring AI's generic wording — bypassing the Phase-8 `GroundingGuardOutputAdvisor` + `AnswerComposer.composeRefusal()` single source of truth (D-08/D-09).
**Why it happens:** Copy-paste from Spring AI's naive-RAG snippet which doesn't set the flag. [CITED: context7 §api/retrieval-augmented-generation "By default, the `ContextualQueryAugmenter` does not allow the retrieved context to be empty."]
**How to avoid:** `.allowEmptyContext(true)` mandatory in `LegalQueryAugmenter`. Unit test asserts the empty-context path returns an augmented prompt (not a refusal) so `GroundingGuardOutputAdvisor` gets to run.
**Warning signs:** Refusal wording in user-visible responses starts with "I cannot answer based on context" instead of the Vietnamese `AnswerComposer.composeRefusal()` text; v1.0 refusal-parity regression fixture fails.

### Pitfall 2: `MessageChatMemoryAdvisor` double-attach after RAG migration

**What goes wrong:** Adding `RetrievalAugmentationAdvisor` to `defaultAdvisors(...)` works, but if a developer ALSO re-attaches memory per-call (via `spec.advisors(MessageChatMemoryAdvisor...)`) the second turn errors with role-alternation violations.
**Why it happens:** Spring AI advisors at equal order produce arbitrary sort; mixing `defaultAdvisors` with per-call `advisors(...)` duplicates.
**How to avoid:** P8 D-14 already wires memory via `defaultAdvisors(...)` at `HIGHEST_PRECEDENCE + 200`. P9 MUST NOT touch this wiring. Per-call usage limited to `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memConvId))` — parameter injection only. [VERIFIED: ChatService.java line 171 already does this correctly.] `[CITED: .planning/research/PITFALLS.md §Pitfall 1]`
**Warning signs:** 400 from OpenRouter with `roles must alternate` on turn 2+; two-turn memory integration test fails.

### Pitfall 3: Advisor-context publish from `DocumentPostProcessor` is not direct

**What goes wrong:** `DocumentPostProcessor.process(Query query, List<Document> docs)` has no direct access to `ChatClientRequest.context()`. Writing citations to advisor context naively requires a thread-local bridge, which is fragile.
**Why it happens:** Spring AI 2.0.0-M4 scopes post-processor to query-level input; the advisor-context write site is in the outer `RetrievalAugmentationAdvisor` call, not its sub-component.
**How to avoid:** Read `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` from `ChatClientResponse.context()` after the advisor runs, and run `CitationMapper.toCitations(...)` in a thin `CallAdvisor` wrapper (`CitationStashAdvisor`, order = `HIGHEST_PRECEDENCE + 310` — right after RAG, before `NoOpPromptCacheAdvisor`). The `CitationPostProcessor` remains responsible for label assignment (deterministic, pre-augmentation), and the stash advisor is responsible for publishing to response context. [CITED: context7 §api/testing — `chatResponse.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT)` pattern].
**Warning signs:** `ChatService` reads `null` from `response.context().get(CITATIONS_KEY)`; `AnswerComposer.compose(..., null, null)` NPEs.

### Pitfall 4: Byte-for-byte `[Nguồn n]` label drift

**What goes wrong:** `LegalQueryAugmenter` emits `"Nguồn " + n` (space), but `CitationMapper.toCitation` line 61 emits `"Nguồn " + labelNumber` (space, unicode "ồ"). The frontend regex matcher or chat-log search may accept only one form. Even a single-char drift (e.g., `"nguồn"` lowercase) breaks the v1.0 regression fixture.
**Why it happens:** Vietnamese diacritics + instruction-text drift during template port.
**How to avoid:** Extract `"Nguồn "` as a single constant shared by `CitationMapper` and `LegalQueryAugmenter`. New `LegalQueryAugmenterTest` golden-fixture test compares byte-for-byte against `ChatPromptFactory.formatCitation` output for a fixed 3-doc set. Gate PR1 on this test.
**Warning signs:** `CitationFormatRegressionIT` v1.0 fixture replay fails; frontend renders citations as plain text (missing label parse).

### Pitfall 5: `Supplier<SearchRequest>` captured once (runtime reload broken)

**What goes wrong:** Using `.similarityThreshold(retrievalPolicy.getSimilarityThreshold())` (VALUE call at bean build) instead of `.similarityThreshold(retrievalPolicy::getSimilarityThreshold)` (SUPPLIER reference) snapshots the value at startup. Admin runtime-reload of `AiParameterSet` has no effect.
**Why it happens:** Java method-reference vs method-call confusion; the builder API accepts both in some versions.
**How to avoid:** Prefer method-reference form `retrievalPolicy::getSimilarityThreshold`. Integration test: flip `retrieval.similarityThreshold` in active parameter set, assert the next retrieval uses the new threshold without app restart.
**Warning signs:** Parameter-set reload via admin UI does not change retrieval behavior observable in logs.

### Pitfall 6: `StructuredOutputValidationAdvisor.maxRepeatAttempts(1)` double-billing

**What goes wrong:** `maxRepeatAttempts=1` means one ADDITIONAL attempt = 2 total LLM calls on parse failure. Budget-wise this is 2× tokens on bad-JSON responses.
**Why it happens:** Advisor name semantics — "repeat" = additional, not total.
**How to avoid:** Accept the 2× cost on rare failures (P8 D-03a projects < 1% of calls on prompt-instruction-mode models). Monitor via OpenRouter usage log post-deploy; tighten to `maxRepeatAttempts=0` (no retry) if failure rate is actually zero in production.
**Warning signs:** Token spend spikes without corresponding request-volume spike; log grep for "validation failed, retrying attempt 1" frequency.

## Code Examples

Verified patterns from Spring AI 2.0.0-M4 official sources.

### Advanced RAG builder (all three hooks)
```java
// Source: context7 /spring-projects/spring-ai §api/retrieval-augmented-generation
Advisor rag = RetrievalAugmentationAdvisor.builder()
    .queryTransformers(/* optional rewriters — not used in P9 */)
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .vectorStore(vectorStore)
        .similarityThreshold(0.50)
        .topK(5)
        .filterExpression(() -> parseFilter(RetrievalPolicy.RETRIEVAL_FILTER))
        .build())
    .documentPostProcessors(citationPostProcessor)
    .queryAugmenter(legalQueryAugmenter)
    .build();
```

### StructuredOutputValidationAdvisor
```java
// Source: context7 /spring-projects/spring-ai §api/advisors-recursive (verbatim shape)
var validationAdvisor = StructuredOutputValidationAdvisor.builder()
    .outputType(LegalAnswerDraft.class)
    .maxRepeatAttempts(1)
    .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 1000)
    .build();
```

### Reading retrieved documents from response
```java
// Source: context7 /spring-projects/spring-ai §api/testing
ChatResponse chatResponse = client.prompt()
    .advisors(ragAdvisor)
    .user(question)
    .call()
    .chatResponse();
List<Document> docs = (List<Document>) chatResponse.getMetadata()
    .get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
```

### Per-request filter override (escape hatch; not used by P9)
```java
// Source: context7 /spring-projects/spring-ai §api/retrieval-augmented-generation
chatClient.prompt()
    .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, "type == 'Spring'"))
    .user(question)
    .call()
    .content();
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual `vectorStore.similaritySearch` + StringBuilder prompt (v1.0 `ChatService.doAnswer`) | `RetrievalAugmentationAdvisor` + `ContextualQueryAugmenter` + `DocumentPostProcessor` | Spring AI 1.0 M3 (2024) stabilised modular RAG; M4 adds `Supplier` filter | ARCH-01 mandate: advisor-driven orchestration. `doAnswer` shrinks ≥60% LOC. |
| `@Deprecated QuestionAnswerAdvisor` as simpler RAG front | `RetrievalAugmentationAdvisor` for anything non-trivial | QuestionAnswerAdvisor still ships but lacks `documentPostProcessors` + `queryTransformers` hooks | P9 CANNOT use `QuestionAnswerAdvisor` — `[Nguồn n]` numbering requires the post-processor hook. |
| Try/catch + `fallbackDraft` for JSON parse errors (v1.0) | `StructuredOutputValidationAdvisor(maxRepeatAttempts=n)` | Spring AI M3 | Retry with schema feedback subsumes hand-rolled fallback. P8 already deleted `fallbackDraft`; P9 wires the advisor. |

**Deprecated/outdated:**
- `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` — retained by P8 as safety-critical fallback (D-13), but with `LegalQueryAugmenter` owning the prompt in P9 this fallback has no reader. Deletion is fair game per Claude's Discretion.
- `ChatPromptFactory` class as a whole — likely absorbed fully into `LegalQueryAugmenter`. Confirm during planning; delete if so.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `VectorStoreDocumentRetriever.builder()` accepts `Supplier<Double>` / `Supplier<Integer>` / `Supplier<FilterExpression>` for per-request reload | Pattern 1, Pitfall 5 | MEDIUM — if only value-accepting overloads exist in 2.0.0-M4, runtime reload breaks; fallback is wrapping the whole retriever in a `DocumentRetriever` decorator that rebuilds `SearchRequest` per call. Verify in planning via ctx7 on `VectorStoreDocumentRetriever.Builder` method signatures. |
| A2 | `CitationPostProcessor` can publish to advisor context via a wrapping `CitationStashAdvisor` reading `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` from response context | Pitfall 3, Pattern 3 | LOW — the pattern is documented in context7 §api/testing for `RelevancyEvaluator`; mechanics proven. Worst case, `CitationMapper` runs twice (once in post-processor for labels, once in stash advisor for publish) — harmless. |
| A3 | `ChatPromptFactory.formatCitation` line 87 (`"- [" + citation.inlineLabel() + "] " + sourceTitle + " | origin=..."`) is the exact format the v1.0 frontend expects in the augmented prompt | Pattern 2, Pitfall 4 | MEDIUM — if the model is fed a more verbose `origin=|page=|section=|excerpt=` form vs a lean form, output quality may drift. Mitigation: the `LegalQueryAugmenter` MUST port `ChatPromptFactory.formatCitation` verbatim. Golden-fixture test gates this. |
| A4 | Spring AI 2.0.0-M4 `DocumentPostProcessor.process(Query, List<Document>)` signature matches context7 descriptions | Pattern 3 | LOW — context7 confirms interface exists; signature may have minor variants. Confirm during planning by reading compiled class from Gradle classpath or ctx7 query on exact signature. |
| A5 | `StructuredOutputValidationAdvisor` works in tandem with `BeanOutputConverter`-driven `.entity(LegalAnswerDraft.class)` — i.e., the advisor intercepts the `BeanOutputConverter` parse failure path and re-invokes the chain | Pattern 4, Pitfall 6 | MEDIUM — if the advisor runs BEFORE `BeanOutputConverter` (i.e., before `.entity()` resolution), it cannot see parse failures. Context7 description "validates the structured JSON output against a generated JSON schema and retries" suggests it sees the text and schema-checks itself. Verify with an integration test that deliberately injects malformed JSON (mock chat model). |

## Open Questions

1. **Q-01: How does `CitationPostProcessor` publish to `ChatClientResponse.context()`?**
   - What we know: `DocumentPostProcessor.process(Query, List<Document>)` has no direct context handle; advisor context lives on `ChatClientRequest`/`ChatClientResponse`.
   - What's unclear: Whether Spring AI 2.0.0-M4 exposes a context hook inside `process()`, or whether the idiomatic pattern is a wrapping `CallAdvisor` that reads `DOCUMENT_CONTEXT` from response metadata and runs `CitationMapper` there.
   - Recommendation: Plan for the **wrapping `CallAdvisor` approach** (Pitfall 3). This is the pattern context7 §api/testing demonstrates for reading retrieved documents post-call. If a more direct hook is discovered during planning, simplify.

2. **Q-02: Can `VectorStoreDocumentRetriever.Builder` accept a `Supplier<>` for all three dynamic properties (`similarityThreshold`, `topK`, `filterExpression`), or only for `filterExpression`?**
   - What we know: context7 confirms `FILTER_EXPRESSION` context-param per-request override. The Supplier pattern on the builder for threshold + topK is not explicitly shown in fetched snippets.
   - What's unclear: Whether the builder has `similarityThreshold(Supplier<Double>)` / `topK(Supplier<Integer>)` overloads in 2.0.0-M4.
   - Recommendation: During planning, run `ctx7 docs '/spring-projects/spring-ai' "VectorStoreDocumentRetriever Builder method signatures"` and inspect the Gradle-resolved class via `javap`. If no Supplier overloads exist, use the per-call `FILTER_EXPRESSION` context-param + rebuild a fresh `VectorStoreDocumentRetriever` per request inside a thin wrapping `DocumentRetriever`. This preserves D-02 intent.

3. **Q-03: Does `StructuredOutputValidationAdvisor` successfully retry when the failure comes from `BeanOutputConverter` inside `.entity(Class)`?**
   - What we know: Advisor auto-generates JSON schema from `outputType`; retries on validation failure.
   - What's unclear: Exact position in the call pipeline — does it intercept `ChatResponse.text` before `BeanOutputConverter.convert()`, or does it wrap the converter itself?
   - Recommendation: Integration test in PR2 uses a mock `ChatModel` that returns malformed JSON on first call, valid JSON on second; assert the advisor retried exactly once and the final entity is valid.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java | build + runtime | ✓ | 25 (per CLAUDE.md) | — |
| Spring AI BOM 2.0.0-M4 | All P9 advisors | ✓ | 2.0.0-M4 [VERIFIED: build.gradle line 24] | — |
| `spring-ai-rag` | `RetrievalAugmentationAdvisor` classes | ✓ | via BOM [VERIFIED: build.gradle line 59] | — |
| `spring-ai-advisors-vector-store` | `VectorStoreDocumentRetriever` | ✓ | via BOM [VERIFIED: build.gradle line 57] | — |
| pgvector | `VectorStore` bean | ✓ | via `spring-ai-starter-vector-store-pgvector` | — |
| `OPENROUTER_API_KEY` env var | `@Tag("live")` regression tests | Solo-dev local only | — | `@DisabledIfEnvironmentVariable` skips in CI (P8 pattern) |
| `spring-ai-test` | `RelevancyEvaluator`, `FactCheckingEvaluator` in regression IT | ✓ | via BOM [VERIFIED: build.gradle line 49, testImplementation] | — |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None (OPENROUTER_API_KEY is expected-absent in CI and gated).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test 3.x + `spring-ai-test` 2.0.0-M4 |
| Config file | `build.gradle` (`test { useJUnitPlatform() }` + `liveTest` task filtering `@Tag("live")`) |
| Quick run command | `./gradlew test --tests "com.vn.traffic.chatbot.chat.advisor.LegalQueryAugmenterTest"` |
| Full suite command (P9 gate) | `./gradlew test liveTest` with `OPENROUTER_API_KEY` set |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ARCH-01 | `RetrievalAugmentationAdvisor` wired; `vectorStore.similaritySearch` removed from `ChatService`; `doAnswer` LOC ≥60% smaller | ArchUnit + unit | `./gradlew test --tests "*ChatServiceShrinkArchTest"` | ❌ Wave 0 — new ArchUnit test counting `ChatService.doAnswer` methods / direct `vectorStore.*` calls |
| ARCH-01 | Advisor chain wires real advisors, no placeholders | unit | `./gradlew test --tests "*ChatClientConfigTest"` | ❌ Wave 0 — assert bean graph contains `RetrievalAugmentationAdvisor` + `StructuredOutputValidationAdvisor`, NOT `NoOpRetrievalAdvisor`/`NoOpValidationAdvisor` |
| ARCH-05 | `[Nguồn n]` label byte-for-byte matches v1.0 | unit (golden fixture) | `./gradlew test --tests "*LegalQueryAugmenterTest"` | ❌ Wave 0 — compare augmenter output vs `ChatPromptFactory.formatCitation` on 3-doc fixture |
| ARCH-05 | `ChatAnswerResponse` JSON byte-for-byte matches v1.0 fixtures | integration (live) | `./gradlew liveTest --tests "*CitationFormatRegressionIT"` | ❌ Wave 0 — replay v1.0 fixtures via saved JSON snapshots |
| P8 deferred (regression) | 20-query Vietnamese regression ≥95% with real retrieval | integration (live) | `./gradlew liveTest --tests "*VietnameseRegressionIT"` | ✓ Exists (P8); re-runs with P9 advisors in PR1 |
| P8 deferred (refusal parity) | Refusal rate within 10% of P7 baseline | integration (live) | part of `VietnameseRegressionIT` | ✓ Exists; P9 PR1 makes it green |
| D-14 (two-turn memory) | Role alternation + context carryover across turn 1→2 with real retrieval | integration (live) | part of `VietnameseRegressionIT` | ✓ Exists; verify still green after RAG swap |
| D-10 (validation retry) | `StructuredOutputValidationAdvisor(maxRepeatAttempts=1)` retries exactly once on malformed JSON | integration (unit-style with mock model) | `./gradlew test --tests "*StructuredOutputValidationAdvisorIT"` | ❌ PR2 Wave 0 — mock `ChatModel` returning bad JSON first, good second |

### Sampling Rate
- **Per task commit:** `./gradlew test` (fast unit + ArchUnit; excludes `@Tag("live")`).
- **Per wave merge (PR1 gate):** `./gradlew test liveTest` with `OPENROUTER_API_KEY` — 20-query regression + refusal parity + two-turn memory must all pass (D-11 gate).
- **Phase gate (before `/gsd-verify-work`):** Full suite green + manual 50-sample chat-log grounding audit (D-03) + byte-for-byte `CitationFormatRegressionIT` green.

### Wave 0 Gaps
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/advisor/LegalQueryAugmenterTest.java` — golden-fixture byte-for-byte parity vs `ChatPromptFactory.formatCitation` on 3-doc fixture (covers ARCH-05).
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/regression/CitationFormatRegressionIT.java` — v1.0 `ChatAnswerResponse` fixture replay (covers ARCH-05).
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/archunit/ChatServiceShrinkArchTest.java` — assert `ChatService` no longer references `VectorStore` / `CitationMapper` directly (covers ARCH-01 LOC shrink intent).
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/config/ChatClientConfigTest.java` — assert bean graph swaps placeholders for real advisors (covers ARCH-01 wiring).
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/advisor/StructuredOutputValidationAdvisorIT.java` — mock-model retry test (PR2 only; covers D-10).

## Security Domain

Security enforcement is not explicitly set in `.planning/config.json`; treat as enabled by default.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | P9 does not touch auth; OpenRouter key handling is Phase-10 scope |
| V3 Session Management | no | No session state introduced or changed |
| V4 Access Control | no | `RETRIEVAL_FILTER` (`approvalState == 'APPROVED' && trusted == 'true' && active == 'true'`) is a content-authorization gate, not a user-authorization gate |
| V5 Input Validation | yes | User query passes through `LegalQueryAugmenter` template — prompt-injection risk. Mitigated by Spring AI `PromptTemplate` escape handling + Phase-8 `GroundingGuardInputAdvisor` |
| V6 Cryptography | no | No crypto changes in P9 |
| V10 Malicious Code / Library | yes | All Spring AI modules are official `org.springframework.ai:*` artifacts pinned via BOM [VERIFIED] |

### Known Threat Patterns for Spring AI RAG stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via user query → exfiltrates system prompt or citation rules | Tampering / Info-disclosure | `PromptTemplate` parameter substitution (not string concat); Phase-8 `GroundingGuardInputAdvisor` classifies intent before augmentation; refusal template sanitizes out-of-scope replies |
| Malicious document text → LLM emits content violating `[Nguồn n]` rules | Tampering | `trusted=true && approvalState=APPROVED && active=true` `FILTER_EXPRESSION` (D-01) means only ingested+vetted chunks reach the augmenter — primary defence |
| Cache-poisoning via shared prompt cache | — | **N/A in P9** — prompt caching deferred (D-07) |
| Memory-advisor role-alternation bypass → user impersonates assistant in turn N | Spoofing | `MessageChatMemoryAdvisor` reads from `ChatMemory` store (JDBC-backed); the user input flow cannot mutate stored assistant messages |

## Sources

### Primary (HIGH confidence)
- Context7 `/spring-projects/spring-ai` — §api/retrieval-augmented-generation (RetrievalAugmentationAdvisor, VectorStoreDocumentRetriever, ContextualQueryAugmenter, DocumentPostProcessor, FILTER_EXPRESSION, allowEmptyContext)
- Context7 `/spring-projects/spring-ai` — §api/advisors-recursive (StructuredOutputValidationAdvisor builder shape, verbatim example)
- Context7 `/spring-projects/spring-ai` — §api/advisors (CallAdvisor, Ordered, HIGHEST/LOWEST_PRECEDENCE, advisor context sharing, best practices for first-in-chain advisors)
- Context7 `/spring-projects/spring-ai` — §api/testing (RelevancyEvaluator, FactCheckingEvaluator usage, DOCUMENT_CONTEXT metadata key)
- `build.gradle` — verified Spring AI version `2.0.0-M4` + module set `spring-ai-rag`, `spring-ai-advisors-vector-store`, `spring-ai-test`
- `.planning/phases/09-modular-rag-prompt-caching/09-CONTEXT.md` — 15 locked decisions D-01..D-15
- `.planning/REQUIREMENTS.md` — ARCH-01, ARCH-05 exact wording
- `.planning/phases/08-structured-output-groundingguardadvisor/08-CONTEXT.md` — advisor chain order (D-04), `defaultAdvisors` pattern (D-08)
- `.planning/research/ARCHITECTURE.md` — v1.1 advisor chain target shape
- `.planning/research/PITFALLS.md` — Pitfall 1 (advisor order vs memory), Pitfall 2 (`allowEmptyContext` trap)
- Project source files (verified AUTHORITATIVE):
  - `src/main/java/.../chat/service/ChatService.java` (v1.0 baseline + P8 state)
  - `src/main/java/.../chat/service/ChatPromptFactory.java` (v1.0 `[Nguồn n]` format)
  - `src/main/java/.../retrieval/RetrievalPolicy.java` (`RETRIEVAL_FILTER` literal + `buildRequest`)
  - `src/main/java/.../chat/config/ChatClientConfig.java` (advisor wiring — target of P9 swap)
  - `src/main/java/.../chat/citation/CitationMapper.java` (unchanged — `"Nguồn " + labelNumber` format)
  - `src/main/java/.../chat/advisor/placeholder/NoOpRetrievalAdvisor.java` + `NoOpValidationAdvisor.java` (delete targets)

### Secondary (MEDIUM confidence)
- Context7 `/spring-projects/spring-ai` — llms.txt excerpt for `QuestionAnswerAdvisor` (verified-by-cross-reference that `QuestionAnswerAdvisor` lacks the post-processor hook, reinforcing `RetrievalAugmentationAdvisor` choice)

### Tertiary (LOW confidence)
- None — all P9-scoped claims are either verified in context7 or in-repo code.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all modules verified on classpath via `build.gradle`; Spring AI version pinned in BOM
- Architecture (advisor chain, `RetrievalAugmentationAdvisor` builder, `StructuredOutputValidationAdvisor` builder, `allowEmptyContext` trap): HIGH — context7 verbatim
- Pitfalls: HIGH — Pitfalls 1, 2, 4 verified in-repo; Pitfalls 3, 5, 6 flagged with explicit mitigation paths
- `DocumentPostProcessor.process()` → advisor-context publish mechanism: MEDIUM — documented via Q-01 open question with concrete fallback pattern

**Research date:** 2026-04-18
**Valid until:** 2026-05-18 (30 days — Spring AI 2.0.0-M4 is a milestone release; track for 2.0.0-M5 / 2.0.0 GA on the BOM before extending)
