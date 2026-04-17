# Architecture Research — v1.1 Spring AI Modular RAG Integration

**Domain:** RAG chat pipeline migration (manual orchestration → Spring AI idiomatic advisors)
**Researched:** 2026-04-17
**Confidence:** HIGH (Spring AI `BaseAdvisor`, `RetrievalAugmentationAdvisor`, `BeanOutputConverter`, `ENABLE_NATIVE_STRUCTURED_OUTPUT`, `StructuredOutputValidationAdvisor` verified via Context7 against `/spring-projects/spring-ai` current docs)

## Scope

This document covers ONLY the integration of six v1.1 features into the already-implemented v1.0 chat pipeline. Existing architecture (ChatService.doAnswer flow, ChatClientConfig, CitationMapper, AnswerComposer, LegalAnswerDraft, RetrievalPolicy, AnswerCompositionPolicy) is assumed and not re-researched.

**Six v1.1 features addressed:**

1. GroundingGuardAdvisor (replaces `containsAnyLegalCitation` + `determineGroundingStatus` gate)
2. RetrievalAugmentationAdvisor (replaces manual `vectorStore.similaritySearch` + `citationMapper.toCitations`)
3. BeanOutputConverter / `.entity(...)` (replaces `parseDraft` + `extractJson`)
4. Prompt caching via OpenRouter `cache_control` (modifies ChatPromptFactory + ChatClient call)
5. Embedding cache via Caffeine (wraps `EmbeddingModel`, transparent to chat flow)
6. API-key admin (new DB-backed layer beside/in front of `AiModelProperties`)

---

## 1. Target Pipeline (After Full v1.1 Migration)

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         ChatController (unchanged)                         │
└───────────────────────────────────────────┬───────────────────────────────┘
                                            │
                                            ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                      ChatService.answer(...)  (slim)                       │
│   Responsibilities left: resolve client, build prompt spec, persist log    │
└───────────────────────────────────────────┬───────────────────────────────┘
                                            │ client.prompt().advisors(...).user(q).call().entity(LegalAnswerDraft.class)
                                            ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                        Spring AI Advisor Chain                             │
│                                                                            │
│   order=MIN_VALUE   GroundingGuardAdvisor.input                           │
│                      ├─ classify intent (chitchat / legal / refuse)       │
│                      └─ short-circuit chitchat → ChatClientResponse       │
│                                                                            │
│   order=-500        MessageChatMemoryAdvisor  (existing)                  │
│                                                                            │
│   order=0           RetrievalAugmentationAdvisor                          │
│                      ├─ VectorStoreDocumentRetriever (uses RetrievalPolicy)│
│                      ├─ DocumentPostProcessor → CitationPostProcessor      │
│                      │     (calls CitationMapper, stores citations in ctx) │
│                      └─ augments user message with context                 │
│                                                                            │
│   order=+500        PromptCachingAdvisor                                  │
│                      └─ injects OpenRouter cache_control markers           │
│                                                                            │
│   order=+1000       StructuredOutputValidationAdvisor                     │
│                      └─ retries if LegalAnswerDraft parse fails           │
│                                                                            │
│   order=MAX_VALUE   GroundingGuardAdvisor.output                          │
│                      └─ if ctx.citations empty → rewrite to refusal draft  │
└───────────────────────────────────────────┬───────────────────────────────┘
                                            │ entity(LegalAnswerDraft.class)
                                            ▼
┌───────────────────────────────────────────────────────────────────────────┐
│       AnswerComposer.compose(status, draft, citations, sources)  (kept)   │
│       status + citations pulled from AdvisorContext by ChatService         │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Table — New / Modified / Retired

| Component | Fate | Notes |
|-----------|------|-------|
| `ChatService.doAnswer` | **Modified** — shrinks ~70%; retrieval + parsing + gating moved into advisors | Keeps: client resolution, logging, chat-log persistence |
| `RetrievalPolicy` | **Kept** — becomes input to `VectorStoreDocumentRetriever.Builder` | `buildRequest` deconstructed into `similarityThreshold` + `topK` + filter |
| `CitationMapper` | **Kept** — called from new `CitationPostProcessor` | No change to class itself |
| `AnswerComposer` | **Kept** — still composes final `ChatAnswerResponse` | Consumes parsed `LegalAnswerDraft` from `.entity()` |
| `AnswerCompositionPolicy` | **Kept** | Used by `AnswerComposer` and `GroundingGuardAdvisor` refusal path |
| `LegalAnswerDraft` | **Kept as-is** (12-field record) | Becomes `BeanOutputConverter` target type |
| `ChatPromptFactory.buildPrompt` | **Modified** — system block moves to advisor-provided template, user block simplifies | Context text injected by RAG advisor, not concatenated manually |
| `containsAnyLegalCitation` + keyword list | **Retired** | Replaced by `GroundingGuardAdvisor` (LLM classification + score thresholds) |
| `determineGroundingStatus` | **Retired** | Status derived from `AdvisorContext.DOCUMENT_CONTEXT` size + advisor decision |
| `parseDraft` + `extractJson` + `fallbackDraft` | **Retired** | Replaced by `.entity(LegalAnswerDraft.class)` + `StructuredOutputValidationAdvisor` |
| `GroundingStatus` enum | **Kept** | Still used by `AnswerComposer` + chat log |
| `ChatClientConfig` | **Modified** — reads API keys from new `ApiKeyService` with YAML fallback | Rebuild map on key rotation via `@EventListener` |
| `AiModelProperties` | **Kept for model catalog**; API-key field becomes optional fallback | Catalog entries (id, baseUrl, model name) stay YAML |
| `ApiKeyService` + `ApiKeyEntity` | **New** | DB-backed, AES-GCM encrypted, audit-logged |
| `GroundingGuardAdvisor` | **New** — split into `InputAdvisor` + `OutputAdvisor` | Intent classification in `before`, refusal override in `after` |
| `CitationPostProcessor` | **New** — `DocumentPostProcessor` | Runs inside `RetrievalAugmentationAdvisor`; stores `List<CitationResponse>` in advisor context |
| `PromptCachingAdvisor` | **New** — `BaseAdvisor` | Mutates system message metadata to set OpenRouter `cache_control` |
| `EmbeddingCache` (Caffeine) | **New** — `EmbeddingModel` decorator bean | Wraps auto-configured embedding model; keyed by normalized text hash |

---

## 3. Feature-by-Feature Integration Details

### 3.1 GroundingGuardAdvisor — advisor chain placement

**Plugs in:** outermost on both sides of the chain. Must run before `RetrievalAugmentationAdvisor` (to skip retrieval for chitchat) and last on response (to override to refusal if post-retrieval / post-LLM signals warrant it).

**Recommended split (confirmed by Spring AI docs — "For advisors that need to be first in the chain on both request and response, use separate advisors for each side with different order values and share state via advisor context"):**

```java
public class GroundingGuardInputAdvisor implements CallAdvisor {
    @Override public int getOrder() { return BaseAdvisor.HIGHEST_PRECEDENCE; }
    @Override public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        IntentClass intent = intentClassifier.classify(req.prompt().getUserMessage().getText());
        req.context().put(GroundingGuardKeys.INTENT, intent);
        if (intent == IntentClass.CHITCHAT) {
            // short-circuit: build chitchat response, skip rest of chain
            return ChatClientResponse.builder()
                .chatResponse(chitchatResponse(req))
                .context(req.context())
                .build();
        }
        if (intent == IntentClass.OUT_OF_SCOPE) {
            req.context().put(GroundingGuardKeys.FORCE_REFUSAL, true);
        }
        return chain.nextCall(req);
    }
}

public class GroundingGuardOutputAdvisor implements CallAdvisor {
    @Override public int getOrder() { return BaseAdvisor.LOWEST_PRECEDENCE; }
    @Override public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        ChatClientResponse resp = chain.nextCall(req);
        List<Document> docs = (List<Document>) resp.context()
            .getOrDefault(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of());
        boolean forceRefuse = Boolean.TRUE.equals(req.context().get(GroundingGuardKeys.FORCE_REFUSAL));
        resp.context().put(GroundingGuardKeys.STATUS,
            (forceRefuse || docs.isEmpty()) ? GroundingStatus.REFUSED : GroundingStatus.GROUNDED);
        return resp;
    }
}
```

**Intent classifier** uses small/fast model (gpt-4o-mini) with 3-way enum output via structured output — eliminates hardcoded keyword gate (ARCH-03, PERF-02).

### 3.2 RetrievalAugmentationAdvisor — manual retrieval replacement

**Replaces:** lines 97–118 of `ChatService.doAnswer` (vectorStore search + citationMapper.toCitations + toSources + docSummary log).

**Bean wiring:**

```java
@Bean
Advisor retrievalAdvisor(VectorStore vectorStore,
                         RetrievalPolicy retrievalPolicy,
                         CitationPostProcessor citationPostProcessor) {
    return RetrievalAugmentationAdvisor.builder()
        .documentRetriever(VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .similarityThreshold(retrievalPolicy.getSimilarityThreshold())
            .topK(retrievalPolicy.getTopK())
            .build())
        .documentPostProcessors(citationPostProcessor)   // runs CitationMapper
        .build();
}
```

**Preserving CitationMapper/AnswerComposer:** the `CitationPostProcessor` implements `DocumentPostProcessor` (returns documents unchanged) but as a side effect writes `List<CitationResponse>` and `List<SourceReferenceResponse>` into the advisor context:

```java
@Component
public class CitationPostProcessor implements DocumentPostProcessor {
    public static final String CITATIONS = "chat.citations";
    public static final String SOURCES = "chat.sources";
    private final CitationMapper mapper;
    @Override public List<Document> process(Query q, List<Document> docs) {
        List<CitationResponse> cites = mapper.toCitations(docs);
        AdvisorContextHolder.put(CITATIONS, cites);
        AdvisorContextHolder.put(SOURCES, mapper.toSources(cites));
        return docs;   // unchanged
    }
}
```

`ChatService` then reads citations from the response `context()` map after the call returns and passes them to `AnswerComposer.compose()`. No change to `CitationMapper` or `AnswerComposer` signatures.

Per-call trust/active filter is passed in via `VectorStoreDocumentRetriever.FILTER_EXPRESSION` context param — already documented in Spring AI for metadata-filtered RAG.

### 3.3 BeanOutputConverter + LegalAnswerDraft

**Current flow:** `client.prompt().user(...).call().chatResponse()` → raw text → `parseDraft` → `LegalAnswerDraft`.

**Target flow (verified in Spring AI docs):**

```java
LegalAnswerDraft draft = client.prompt()
    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)   // if model supports it
    .user(prompt)
    .call()
    .entity(LegalAnswerDraft.class);
```

**Zero changes to `LegalAnswerDraft`** — it is already a plain Java record with simple fields, which is exactly what `BeanOutputConverter` consumes. Spring AI generates a JSON schema from the record and appends format instructions to the prompt automatically (or supplies a native `response_format` for models that support it).

**Model caveat:** `ENABLE_NATIVE_STRUCTURED_OUTPUT` requires provider support. OpenRouter proxies Anthropic/OpenAI/Google — most current catalog models support it. For older or partial-support models (e.g. DeepSeek V3.2), fall back to non-native mode (`.entity(LegalAnswerDraft.class)` without the advisor) which uses prompt-augmented schema via `BeanOutputConverter`. Decision is per-model: add `supportsNativeStructuredOutput: true|false` to each `AiModelProperties.ModelEntry`.

**Validation:** wrap with `StructuredOutputValidationAdvisor.builder().outputType(LegalAnswerDraft.class).maxRepeatAttempts(2).advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 1000).build()`. This subsumes the current `fallbackDraft` defensive path by auto-retrying bad JSON.

### 3.4 API-key admin vs AiModelProperties

**Split of concerns:**

| Concern | Owner | Storage |
|---------|-------|---------|
| Model catalog (id, display name, provider) | `AiModelProperties` | YAML (stays) |
| Per-model `baseUrl` | `AiModelProperties` | YAML (stays) |
| API keys (per provider, not per model id) | **new `ApiKeyService`** | DB (`api_key` table, AES-GCM encrypted) |
| Default/bootstrap API key | `spring.ai.openai.api-key` | YAML/env (fallback only) |

**Resolution chain** (in `ChatClientConfig`, when building each `ChatClient`):
1. `apiKeyService.findActiveByProvider(entry.provider())` → decrypt → use
2. else `entry.apiKey()` (YAML per-model override, existing)
3. else `spring.ai.openai.api-key` (existing fallback)

**Key rotation:** `ApiKeyService.save()` publishes `ApiKeyRotatedEvent`. `ChatClientConfig` listens, rebuilds affected `ChatClient` entries in the map. The map bean changes from `@Bean Map<String, ChatClient>` to a small `ChatClientRegistry` component wrapping a `ConcurrentHashMap` so it can be mutated without app restart.

**Schema (Liquibase):**
```
api_key(id UUID PK, provider VARCHAR, key_ciphertext BYTEA, key_iv BYTEA,
        key_tag BYTEA, masked_display VARCHAR, created_at, rotated_at,
        rotated_by, active BOOLEAN)
api_key_audit(id PK, api_key_id FK, actor, action, at, request_ip)
```

**Admin endpoints** (under `/api/admin/api-keys`): `GET` returns `masked_display` only; `POST`/`PUT` accepts new raw key and encrypts; `DELETE` deactivates. All write endpoints log to `api_key_audit`.

### 3.5 Prompt caching (OpenRouter `cache_control`)

**Mechanism:** OpenRouter accepts Anthropic-style `cache_control: {"type":"ephemeral"}` markers on message content parts. Static system prompt (disclaimer, output schema instructions, role definition — roughly 1–3 KB) should be marked cached; dynamic per-request content (retrieved docs, user question) must not.

**Integration point:** new `PromptCachingAdvisor` (order=+500, runs AFTER retrieval so user message contains doc context, but BEFORE the model call). It mutates the `SystemMessage` metadata to add the provider-specific `cache_control` flag. Alternative: set `OpenAiChatOptions.metadata` per call in `ChatService`. Advisor approach preferred — keeps ChatService slim.

**Implication:** requires splitting `ChatPromptFactory.buildPrompt` — current implementation concatenates everything into a single user prompt. Must produce separate `SystemMessage` (cacheable) + `UserMessage` (dynamic). This refactor is a prerequisite for F.

### 3.6 Embedding cache (Caffeine)

**Integration point:** decorator bean around the auto-configured `EmbeddingModel`. Does NOT touch chat flow directly — but `VectorStoreDocumentRetriever` calls `EmbeddingModel.embed(query)` for each chat. Caching is the quickest latency win (PERF-01).

```java
@Bean @Primary
EmbeddingModel cachingEmbeddingModel(EmbeddingModel delegate) {
    Cache<String, float[]> cache = Caffeine.newBuilder()
        .maximumSize(10_000).expireAfterWrite(Duration.ofHours(24)).build();
    return new CachingEmbeddingModel(delegate, cache);  // delegates on miss
}
```

Keyed by SHA-256 of normalized (trim + lowercase + collapse whitespace) query string. Cache is read-through; ingestion path should bypass (use delegate directly) to avoid polluting with unique per-chunk texts.

---

## 4. Advisor Chain Ordering Table

Verified against Spring AI docs — "Lower order values = higher priority = execute earlier in request phase. Response phase executes in reverse order (stack unwinding)."

| Order | Advisor | Purpose |
|-------|---------|---------|
| `HIGHEST_PRECEDENCE` (MIN_VALUE) | `GroundingGuardInputAdvisor` | Intent classify, short-circuit chitchat |
| `-500` | `MessageChatMemoryAdvisor` (existing, per-call) | Load thread history |
| `0` | `RetrievalAugmentationAdvisor` | Retrieve + post-process (CitationMapper) |
| `+500` | `PromptCachingAdvisor` | Mark system block cacheable |
| `+1000` | `StructuredOutputValidationAdvisor` | Retry on bad JSON (bounded) |
| `LOWEST_PRECEDENCE` (MAX_VALUE) | `GroundingGuardOutputAdvisor` | Final refusal override |

**Request phase (winding):** GuardIn → Memory → RAG → Cache → Validation → GuardOut → LLM
**Response phase (unwinding):** LLM → GuardOut → Validation → Cache → RAG → Memory → GuardIn

GuardOut sees fully-processed response first; GuardIn sees the final response last (useful for classifier cache warming).

---

## 5. AdvisorContext Keys (shared state contract)

Central contract for cross-advisor + ChatService communication. Constants live in new interface `ChatAdvisorContextKeys` to avoid string-typo bugs.

| Key | Writer | Reader | Type |
|-----|--------|--------|------|
| `GroundingGuardKeys.INTENT` | InputGuard | OutputGuard, ChatService (logging) | `IntentClass` enum |
| `GroundingGuardKeys.FORCE_REFUSAL` | InputGuard | OutputGuard | `Boolean` |
| `GroundingGuardKeys.STATUS` | OutputGuard | ChatService (composer + chat-log) | `GroundingStatus` |
| `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` | RAG advisor | OutputGuard, ChatService | `List<Document>` |
| `CitationPostProcessor.CITATIONS` | CitationPostProcessor | ChatService | `List<CitationResponse>` |
| `CitationPostProcessor.SOURCES` | CitationPostProcessor | ChatService | `List<SourceReferenceResponse>` |
| `VectorStoreDocumentRetriever.FILTER_EXPRESSION` | ChatService (per-call) | RAG advisor | `Filter.Expression` (trust tier, active flag) |

---

## 6. Data Flow — Before vs After

### 6.1 v1.0 (current)
```
question → RetrievalPolicy.buildRequest → vectorStore.similaritySearch → [docs]
        → CitationMapper.toCitations → [citations]
        → containsAnyLegalCitation (keyword gate) → refuse / continue
        → ChatPromptFactory.buildPrompt → single user prompt string
        → ChatClient.call().chatResponse() → raw text
        → parseDraft(extractJson) → LegalAnswerDraft | fallbackDraft
        → AnswerComposer.compose → ChatAnswerResponse
        → chatLogService.save (sync, blocking on return path)
```

### 6.2 v1.1 (target)
```
question → client.prompt().advisors(chain).user(q).call().entity(LegalAnswerDraft)
           │
           └─ advisor chain does: intent classify → retrieve+cite → cache →
              native structured output → validate → final refusal check
           │
           returns: LegalAnswerDraft + AdvisorContext{status, citations, sources, docs}

ChatService: → AnswerComposer.compose(status, draft, citations, sources)
             → chatLogService.saveAsync (PERF-01 quick win)
             → return ChatAnswerResponse
```

`ChatService.doAnswer` shrinks from ~250 lines to ~70.

---

## 7. Backward Compatibility & Incremental Rollout

Each feature can ship as its own phase without breaking v1.0 behaviour. Recommended build order reflects dependency graph:

| Phase | Deliverable | Depends on | Risk if done earlier |
|-------|-------------|------------|----------------------|
| A | Latency quick wins: async chat log, slim LegalAnswerDraft JSON schema in prompt, prompt trim, loosen/disable keyword gate behind feature flag | none | none |
| B | Embedding cache (Caffeine) | A | none — transparent decorator |
| C | BeanOutputConverter (`.entity(LegalAnswerDraft.class)` + `StructuredOutputValidationAdvisor`) | A | Structured output across OpenRouter providers varies — mitigate with per-model native vs bean-converter toggle |
| D | GroundingGuardAdvisor (input classifier; output trivial context read) | C (classifier uses `.entity(IntentDecision.class)`) | false refusals if classifier prompt weak — gate behind feature flag, A/B against keyword gate |
| E | RetrievalAugmentationAdvisor + CitationPostProcessor | D (guard must short-circuit chitchat BEFORE retrieval runs) | trust/active filter regression — port `RetrievalPolicy` filter to `FILTER_EXPRESSION` carefully |
| F | PromptCachingAdvisor + system/user message split | E (system block must be stable post-retrieval) | no correctness risk; only cost/latency impact |
| G | API-key admin (DB schema, service, UI, ChatClientRegistry rebuild on rotation) | none technically; parallelizable with A–F | rotation race conditions — use copy-on-write map |

**Feature flags:** each of D, E, F guarded by `app.chat.v11.<feature>=true|false`. `ChatService` branches between old path and new path until flag flips. Remove legacy code (parseDraft, keyword gate, manual retrieval) only after each flag is permanently true in staging.

**Rollback story:** every phase leaves v1.0 code path present behind flag. Rollback = flip flag + redeploy. No DB migration blocks rollback except Phase G (`api_key` table is additive and tolerates YAML fallback, so even G is reversible).

---

## 8. Risk Register (integration-specific)

| Risk | Severity | Mitigation |
|------|----------|------------|
| `DocumentPostProcessor` signature lacks direct access to advisor context in some Spring AI versions | MED | Use ThreadLocal-bound `AdvisorContextHolder` OR call `CitationMapper` inside a thin wrapping `CallAdvisor` at order=+10 (right after RAG advisor) that reads `DOCUMENT_CONTEXT` from response context |
| `ENABLE_NATIVE_STRUCTURED_OUTPUT` unsupported by a catalog model | MED | Per-model boolean `supportsNativeStructuredOutput` in `AiModelProperties.ModelEntry`; toggle the advisor at client build time |
| OpenRouter `cache_control` semantics differ per upstream provider | MED | Caching advisor skips if catalog entry provider ≠ `anthropic`/`openai` |
| Advisor chain order conflicts if `MessageChatMemoryAdvisor` must see the augmented (post-RAG) user message | LOW | Current v1.0 places memory advisor on raw question — keep that; memory should store the user's actual words, not injected context |
| API key rotation races with in-flight requests | LOW | `ChatClientRegistry` uses `ConcurrentHashMap`; rebuild publishes new reference atomically; in-flight requests complete on old client |
| Intent classifier adds one LLM roundtrip and raises latency | MED | Use gpt-4o-mini with 64-token cap; cache recent classifications (Caffeine, 5-min TTL); skip classifier when strong legal keyword present (cheap pre-check, NOT a hard gate) |

---

## 8b. Provider routing ADR — single `OpenAiChatModel` (v1.1)

**Decision:** All 8 cataloged models (`anthropic/*`, `openai/*`, `google/*`, `deepseek/*`) go through one `OpenAiChatModel` bean per model, all pointing to OpenRouter's OpenAI-compat endpoint. No `spring-ai-starter-model-anthropic` or other provider-specific starter in v1.1.

**Why this is safe:**
- OpenRouter translates OpenAI-shape requests transparently to every upstream provider — verified for all 8 models.
- Explicit per-block `cache_control: {"type":"ephemeral"}` still flows through OpenAI-compat to Anthropic upstreams, so CACHE-01 is achievable without multi-SDK.
- Gemini's native Spring AI client requires GCP auth — not routable via OpenRouter anyway.

**What v1.1 gives up (accepted):** Anthropic extended-thinking blocks, native tool-use shape, 1h `cache_control` TTL (5m via per-block still works).

**Revisit trigger (v1.2+):** scenario analysis needs extended reasoning tokens, or system prompt reuse window > 5m. Then add `spring-ai-starter-model-anthropic`, route `anthropic/*` via it (OpenRouter Anthropic-Skin endpoint). Full sketch in `STACK.md` ADR section.

## 9. Summary for Requirement-Definer

- **Six v1.1 features map to a clean advisor chain** with ordering `GuardIn → Memory → RAG → Cache → Validation → GuardOut`.
- **`CitationMapper`, `AnswerComposer`, `AnswerCompositionPolicy`, `LegalAnswerDraft`, `RetrievalPolicy`, `GroundingStatus` — all kept.** Integration is via Spring AI extension points (`DocumentPostProcessor`, `BeanOutputConverter`, `CallAdvisor`), not rewrites.
- **Retired code:** `parseDraft` + `extractJson` + `fallbackDraft`, `containsAnyLegalCitation` + keyword list, `determineGroundingStatus`, raw `vectorStore.similaritySearch` in ChatService.
- **API-key admin is an additive DB layer** in front of the YAML model catalog; catalog remains YAML.
- **Build order A→B→C→D→E→F (with G parallel)** enables phase-by-phase shipping; each phase feature-flagged for incremental rollout and trivial rollback.
- **Single file to modify aggressively:** `ChatService.doAnswer` shrinks by ~70%; `ChatClientConfig` gains a key-resolution step and an event listener.

## Sources

- Spring AI docs — RetrievalAugmentationAdvisor & Modular RAG: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/retrieval-augmented-generation.adoc (Context7, HIGH)
- Spring AI docs — Advisors chain ordering, CallAdvisor, BaseAdvisor, HIGHEST/LOWEST_PRECEDENCE: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/advisors.adoc (Context7, HIGH)
- Spring AI docs — StructuredOutputValidationAdvisor: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/advisors-recursive.adoc (Context7, HIGH)
- Spring AI docs — BeanOutputConverter + `.entity()`: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/structured-output-converter.adoc (Context7, HIGH)
- Spring AI docs — ENABLE_NATIVE_STRUCTURED_OUTPUT advisor: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chatclient.adoc (Context7, HIGH)
- Existing v1.0 classes read for integration points: `ChatService`, `ChatClientConfig`, `AnswerComposer`, `LegalAnswerDraft`, `CitationMapper` (verified, HIGH)

---
*Architecture research for: v1.1 Spring AI modular RAG integration*
*Researched: 2026-04-17*
