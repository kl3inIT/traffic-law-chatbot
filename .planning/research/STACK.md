# Stack Research — v1.1 Spring AI Modular RAG + API Key Admin

**Domain:** Vietnam Traffic Law Chatbot — v1.1 subsequent milestone
**Researched:** 2026-04-17
**Confidence:** HIGH (Spring AI + frontend libs via Context7 / package.json); MEDIUM (OpenRouter caching header semantics — verified via WebSearch against OpenRouter docs, not Context7)

## Scope Note

v1.0 stack is already frozen and validated. This document is **delta-only** — additions, removals, and version pins required for v1.1 features:

- Spring AI idiomatic modular RAG (RetrievalAugmentationAdvisor + custom post-processor)
- `BeanOutputConverter` structured output (drop manual JSON extraction)
- OpenRouter prompt caching via `cache_control`
- Caffeine-backed embedding cache
- Encrypted user-managed API key admin

No frontend framework changes — the existing Next.js 16 / shadcn / base-ui / react-hook-form / zod stack already covers every UI requirement for the admin key screens. No build-tool upgrades.

## What Stays (No Change)

| Technology | Version | Still Good Because |
|------------|---------|--------------------|
| Spring Boot | 4.0.5 | Already on latest; compatible with Spring AI 2.0.0-M4 BOM |
| Java | 25 toolchain | Required by project, supports virtual threads (already enabled) |
| Spring AI BOM | **2.0.0-M4** | Current milestone; includes `spring-ai-rag` with `RetrievalAugmentationAdvisor`, `ContextualQueryAugmenter`, `VectorStoreDocumentRetriever`, `BeanOutputConverter`. No upgrade needed. |
| `spring-ai-rag` | from BOM | Already on classpath — required for `RetrievalAugmentationAdvisor` |
| `spring-ai-advisors-vector-store` | from BOM | Keep (alternate simpler `QuestionAnswerAdvisor`), but v1.1 migrates to modular RAG advisor |
| `spring-ai-starter-model-openai` | from BOM | OpenRouter uses OpenAI-compatible protocol — no change |
| `spring-ai-starter-vector-store-pgvector` | from BOM | pgvector schema unchanged |
| `spring-ai-starter-model-chat-memory-repository-jdbc` | from BOM | Keep — already wired via `MessageChatMemoryAdvisor` |
| PostgreSQL + pgvector | existing | No schema migration for RAG changes; new table for encrypted API keys only |
| Liquibase | existing | Used for new `api_credentials` table migration |
| Next.js | 16.2.3 | No change |
| shadcn/ui, base-ui, react-hook-form 7.72, zod 4, @hookform/resolvers 5.2 | existing | Complete for masked-secret admin form — no new deps |
| @tanstack/react-query 5.97 | existing | For admin key CRUD |

## New Backend Dependencies

Add to `build.gradle` dependencies block:

```groovy
// --- v1.1: Embedding cache (CACHE-02) ---
implementation 'com.github.ben-manes.caffeine:caffeine:3.2.0'
implementation 'org.springframework.boot:spring-boot-starter-cache'

// --- v1.1: Encrypted API key storage (ADMIN-07) ---
// Pull the standalone crypto module (not the full security starter) to get
// AES-256-GCM via BytesEncryptor.stronger() without authn/filter machinery.
implementation 'org.springframework.security:spring-security-crypto:6.5.0'
```

### Rationale per dep

| Dep | Version | Purpose | Why this choice |
|-----|---------|---------|-----------------|
| `caffeine` | 3.2.0 | In-JVM LRU cache for OpenAI embedding vectors keyed by normalized query string | Industry-standard Java cache; first-class Spring `CacheManager` integration via `CaffeineCacheManager`; supports size + TTL + stats out of the box. Single-node chatbot → no need for Redis. |
| `spring-boot-starter-cache` | from BOM | Enables `@Cacheable` and autowires `CacheManager` | Smallest stable abstraction; lets us wrap `EmbeddingModel.embed()` behind a cache-aware delegate without custom `ConcurrentHashMap` code. |
| `spring-security-crypto` | 6.5.0 | AES-256-GCM `BytesEncryptor.stronger(password, salt)` for column-level encryption of stored OpenRouter/provider API keys | Official Spring module, audited, no external deps. Supports PBKDF2 key derivation + GCM authenticated encryption. Lighter than pulling in full `spring-boot-starter-security` just for crypto primitives. |

### Why NOT Jasypt

| Consideration | Jasypt | spring-security-crypto |
|---------------|--------|------------------------|
| Maintenance | `jasypt-spring-boot` 3rd-party wrapper; Jasypt core last major release 2014, GCM added 3.0.5 (2023) | Released with each Spring Security cycle (6.5.x on Spring Boot 4 line) |
| Default cipher | PBEWithMD5AndDES unless explicitly overridden — easy to misconfigure | `stronger()` defaults to AES-256-GCM with PBKDF2 |
| Spring Boot 4 compatibility | Unverified on Spring Boot 4.0.5 / Java 25 (community reports of reflection issues on newer JDKs) | First-party, tracks Spring Boot releases |
| Fit for DB column encryption | Designed for config-file property decryption, not entity attribute conversion | Clean fit for JPA `AttributeConverter<String,String>` wrapper |

**Decision:** use `spring-security-crypto` + a hand-rolled `@Converter` (~40 LOC) backed by `BytesEncryptor.stronger()`. Master key comes from env var `APP_SECRET_KEY` (32+ chars) + a fixed-per-installation salt, matching the sultanov.dev and Baeldung-referenced patterns but without Jasypt baggage.

### Why NOT Spring Vault / HashiCorp Vault / AWS KMS

Overkill for v1.1. Single-node deployment, single operator, small number of keys (one per AI provider). Vault integration can be added in v1.2 if multi-tenant isolation becomes a requirement.

## Configuration Additions

### `application.yaml` (new keys)

```yaml
spring:
  cache:
    type: caffeine
    cache-names: embeddings
    caffeine:
      spec: maximumSize=2000,expireAfterWrite=24h,recordStats

app:
  security:
    encryption:
      # 32+ char random; provision via env, never commit
      master-key: ${APP_SECRET_KEY:}
      # Hex-encoded 16-byte salt for PBKDF2; pinned per-installation
      salt-hex: ${APP_SECRET_SALT:}
  chat:
    rag:
      similarity-threshold: 0.50      # feeds VectorStoreDocumentRetriever
      allow-empty-context: true       # ContextualQueryAugmenter — enables chitchat mode instead of hard refusal
    cache:
      embedding:
        enabled: true
```

### OpenRouter prompt caching — NO new dependency

OpenRouter accepts **OpenAI-compatible JSON** but understands Anthropic-style `cache_control` blocks when the target model is Anthropic. Two mechanisms, both work through Spring AI's existing `OpenAiChatModel`:

1. **Automatic** (top-level `cache_control` — only when routed to Anthropic direct): pass via extra body params.
2. **Explicit breakpoints** (universal across Anthropic, Bedrock, Vertex): attach `"cache_control": {"type": "ephemeral"}` on the last static system-block content part.

Implementation path in Spring AI M4: shape the system `Message` content parts (or use a raw request interceptor) to emit the Anthropic multimodal-content-block format that OpenRouter proxies. **No SDK dependency needed — this is pure JSON body shaping in our `ChatPromptFactory`.** Verify the exact `OpenAiChatOptions` extension surface during Phase 10 implementation — fall back to a custom `RequestInterceptor` on the underlying `RestClient` if options map doesn't serialize nested objects cleanly.

**TTL options:** `{"type":"ephemeral"}` (5 min) or `{"type":"ephemeral","ttl":"1h"}`. Use 1h for the static RAG system block.

**Provider note:** caching is free/automatic on OpenAI (`gpt-4o-mini`, `gpt-5.1`), DeepSeek, and Gemini 2.5+ (implicit). Only Anthropic models require explicit `cache_control` breakpoints. Since our default is `openai/gpt-4o-mini`, CACHE-01 primarily benefits users who switch to the Claude family via the model dropdown.

## What Gets Removed (post-migration)

These become dead code once v1.1 lands — track for deletion in Phase 8/9:

| Item | Replaced By | Location |
|------|-------------|----------|
| Manual `vectorStore.similaritySearch(request)` call in `ChatService.doAnswer` | `RetrievalAugmentationAdvisor` + `VectorStoreDocumentRetriever` | `ChatService.java` lines 97-102 |
| `parseDraft()` + `extractJson()` (markdown fence stripping, lenient Jackson, bracket hunting) | `.call().entity(LegalAnswerResponse.class)` via `BeanOutputConverter` | `ChatService.java` lines 271-311 |
| `fallbackDraft()` JSON-parse failure branch | Native structured output guarantees schema — failures become hard errors, not silent fallbacks | `ChatService.java` lines 313-338 |
| `containsAnyLegalCitation` + `looksLikeLegalCitation` + `containsLegalSignal` hardcoded Vietnamese keyword list | Score-threshold-based `GroundingGuardAdvisor` (CallAroundAdvisor) using retriever similarity scores + policy config | `ChatService.java` lines 231-269 (per ARCH-03 principle) |
| Direct `ObjectMapper` field | Keep only if needed elsewhere; remove from `ChatService` constructor | `ChatService.java` line 42 |

## Installation

```bash
# From project root
./gradlew --refresh-dependencies build

# Frontend — NO changes needed
# (react-hook-form 7.72, zod 4.3, @hookform/resolvers 5.2, shadcn 4.2 already installed)
```

## Alternatives Considered

| Recommended | Alternative | When Alternative Is Better |
|-------------|-------------|----------------------------|
| `spring-security-crypto` + JPA `AttributeConverter` | Jasypt 3.0.5 with `gcm-secret-key-string` | Greenfield Spring 2.x/3.x project with heavy property-level encryption needs. Not us. |
| `spring-security-crypto` + `AttributeConverter` | Postgres `pgcrypto` extension (DB-side `PGP_SYM_ENCRYPT`) | Multi-language clients sharing the DB. We own the whole stack → app-side is simpler + keeps keys out of DB logs. |
| Caffeine in-JVM cache | Redis + `spring-boot-starter-data-redis` | Multi-instance deployments. v1.1 is single-node. |
| `RetrievalAugmentationAdvisor` (modular RAG) | `QuestionAnswerAdvisor` (simpler, from `spring-ai-advisors-vector-store`) | Quick prototypes without post-processing. We need custom `DocumentPostProcessor` for citation mapping + chitchat-aware empty-context handling — modular wins. |
| `BeanOutputConverter` via `.entity(Class)` | Keep manual JSON parsing | `gpt-4o-mini` supports JSON-mode natively — native structured output is strictly better than our current markdown-fence-stripping regex. No downside. |
| OpenRouter `cache_control` breakpoints | Our own Caffeine cache of full prompt → completion pairs | We lose cost/latency savings on OpenRouter's infra. `cache_control` is nearly free to add. Do both (Caffeine for embeddings only; `cache_control` for LLM prompt). |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Jasypt `jasypt-spring-boot` | Unverified on Spring Boot 4 / Java 25; default algorithm weak unless explicitly overridden; third-party maintenance | `spring-security-crypto` 6.5.0 |
| `spring-boot-starter-security` (full starter) | Drags in filters, authn machinery we don't need yet (no user roles in v1) | `spring-security-crypto` module only |
| Redis / Hazelcast for embedding cache | Operational complexity for a single-node app | Caffeine 3.2 |
| Custom markdown/JSON extraction regex (current `extractJson`) | Fragile; already hit edge cases (code-fence variants) | `BeanOutputConverter` — Jackson-compatible JSON schema, native JSON emission |
| `QuestionAnswerAdvisor` for primary pipeline | Hardcoded prompt template; no hook for citation post-processing or grounding-score inspection | `RetrievalAugmentationAdvisor` with custom `DocumentPostProcessor` |
| Hardcoded Vietnamese legal-keyword lists for grounding | Caused false refusals in v1.0; brittle | Score threshold from `VectorStoreDocumentRetriever` + config-driven policy + optional LLM classifier (v1.2 if needed) |
| Hibernate envers / DB triggers for API key audit | Over-engineered | Simple `api_credential_audit` table written by service layer on CRUD |
| `spring-cloud-vault` / AWS Secrets Manager | Out of scope for v1.1 (single operator, small secret count) | Env-var-derived master key + encrypted DB column |

## Stack Patterns by Variant

**If we stay on Spring AI M4 (recommended):**
- `RetrievalAugmentationAdvisor.builder().documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(vectorStore).similarityThreshold(0.50).build()).queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build()).documentPostProcessors(citationCollectingPostProcessor).build()`
- Pair with `chatClient.prompt().advisors(rag, memory, grounding).user(q).call().entity(LegalAnswerResponse.class)`

**If Spring AI GA (1.1.x line) is needed for production stability:**
- Spring AI 1.1.2 also exposes `RetrievalAugmentationAdvisor` (modular RAG stabilized in 1.0) — API is compatible. Would require switching BOM to `1.1.2` and dropping the milestone repo. Keep M4 unless a blocking bug surfaces.

**If the admin screen grows to multi-user / RBAC later:**
- Add `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`, keep `spring-security-crypto` (already a transitive dep of the starter), no rework of the `AttributeConverter` needed.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Spring Boot 4.0.5 | Spring AI 2.0.0-M4 BOM | Already verified by existing build. M4 requires Spring Boot 4.x. |
| Spring Boot 4.0.5 | spring-security-crypto 6.5.0 | 6.5.x tracks Spring Boot 4.0.x. Don't pin older 5.x — breaks on Spring 6/Boot 4 config. |
| Java 25 toolchain | Caffeine 3.2.x | Caffeine 3.x requires JDK 11+; tested on JDK 21/24. JDK 25 works (pure Java, no native deps). |
| Spring Boot 4.0.5 | spring-boot-starter-cache + Caffeine | Native `CaffeineCacheManager` auto-config; no extra wiring needed beyond `spring.cache.type=caffeine`. |
| OpenRouter OpenAI-compatible endpoint | Anthropic `cache_control` | Only for Anthropic-family model IDs (`anthropic/claude-*`). OpenAI/DeepSeek/Gemini models ignore the field safely (or get implicit caching). |
| `BeanOutputConverter` | `openai/gpt-4o-mini` via OpenRouter | OpenRouter passes JSON-mode/`response_format` through. Confirmed working on OpenAI, DeepSeek, Gemini 2.5. For Anthropic models, Spring AI falls back to schema-in-prompt (`FORMAT` instruction) — still better than current regex. |

## ADR — Single `OpenAiChatModel` for all providers via OpenRouter (v1.1)

**Status:** Accepted (2026-04-17)

**Context:** OpenRouter exposes an Anthropic-compatible "Anthropic Skin" endpoint in addition to the OpenAI-compatible one. Spring AI ships per-provider starters (`spring-ai-starter-model-anthropic`, `spring-ai-starter-model-vertex-ai-gemini`, etc.). Question raised: should v1.1 add native Anthropic/Gemini SDKs so `anthropic/*` models get full native-feature access (extended thinking, native tool-use, 1h `cache_control` TTL)?

**Decision:** Keep a single `OpenAiChatModel` pointing to OpenRouter's OpenAI-compatible endpoint (`https://openrouter.ai/api/v1`) for **all** models — `anthropic/*`, `openai/*`, `google/*`, `deepseek/*`. Do **not** add `spring-ai-starter-model-anthropic` or other provider-specific starters in v1.1.

**Rationale:**
- OpenRouter translates OpenAI-shape requests transparently to every model — verified working for all 8 cataloged models. No regression from current v1.0 behavior.
- Explicit per-block `cache_control: {"type":"ephemeral"}` breakpoints **still flow through OpenAI-compat** to Anthropic providers — CACHE-01 is achievable without multi-SDK.
- Multi-SDK would require refactoring `Map<String,ChatClient>` → `Map<String,ChatClient>` keyed by provider-resolved builder, with conditional factory branches per model prefix. Non-trivial complexity for features v1.1 doesn't need yet.
- Google Gemini via native `VertexAiGeminiChatModel` requires GCP auth — **not routable via OpenRouter at all**. Native route is impossible for Gemini regardless of intent.
- DeepSeek has no Spring AI native module; their API is OpenAI-compat anyway.

**What this means we give up (accepted):**
- Anthropic extended-thinking / reasoning tokens (native `thinking` blocks)
- Native Anthropic tool-use batching shape
- 1h cache TTL top-level flag (5m via explicit per-block still works)

**Trigger to revisit (v1.2+):** any of the following becomes a real requirement → add `spring-ai-starter-model-anthropic` with base-url pointed at OpenRouter's Anthropic-compat endpoint, route `anthropic/*` through it:
- Scenario analysis needs extended thinking for complex multi-step legal reasoning
- System prompt exceeds 5-minute cache TTL utility (prompt reuse window > 5m)
- Need native Anthropic tool-use format for downstream integrations

**Sketch of v1.2 upgrade path** (for future reference, not implemented now):
```java
@Bean
public Map<String, ChatClient> chatClientMap(...) {
    Map<String, ChatClient> m = new LinkedHashMap<>();
    for (ModelEntry e : props.models()) {
        ChatModel model = switch (providerOf(e.id())) {  // "anthropic", "openai", ...
            case "anthropic" -> buildAnthropic(e);   // native Anthropic Skin via OpenRouter
            default -> buildOpenAi(e);               // OpenAI-compat for everything else
        };
        m.put(e.id(), ChatClient.builder(model).build());
    }
    return m;
}
```

## Caffeine configuration — reference pattern from JHipster

Study target: `D:/jhipster/src/main/java/com/vn/core/config/CacheConfiguration.java` (the project's internal JHipster reference app).

Adopt these 4 patterns when implementing CACHE-02:

1. **Single `@EnableCaching` class** that exposes `CacheManager` bean — no annotation scattering.
2. **Cache-name constants live at the consumer** (e.g. `public static final String EMBEDDING_CACHE = "embeddings"` on the embedding wrapper class), never string literals inline.
3. **Correctness comes from write-path eviction, not TTL** — the JHipster comment on `PermissionMatrix` applies: "The TTL is set to 3600 s as a non-semantic safety ceiling only. Actual cache correctness comes from write-path eviction ... TTL prevents unbounded map growth if eviction is somehow missed". For embeddings: cache key = hash(modelId + text), so write-path eviction is trivial — new ingestion invalidates nothing (different text → different key).
4. **Per-map/per-cache config** (size, TTL, stats) set per cache name, not a single global default. Caffeine equivalent:

```java
@Configuration
@EnableCaching
public class CacheConfiguration {
    public static final String EMBEDDING_CACHE = "embeddings";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setCacheSpecification("maximumSize=2000,expireAfterWrite=24h,recordStats");
        mgr.setCacheNames(List.of(EMBEDDING_CACHE));
        return mgr;
    }
}
```

**Forward-compat with Hazelcast/Redis (v2):** Because `@Cacheable` targets the abstract `CacheManager`, swapping to `HazelcastCacheManager` or `RedisCacheManager` requires only a dep swap + different `@Bean` body — zero business-code change. This is the JHipster selling point, and the exact same pattern works here.

## Sources

- `/spring-projects/spring-ai` (Context7, v2.0.0-M4 line) — verified `RetrievalAugmentationAdvisor`, `VectorStoreDocumentRetriever`, `ContextualQueryAugmenter.allowEmptyContext`, `BeanOutputConverter` via `.entity()`, `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` — **HIGH confidence**
- [OpenRouter Prompt Caching docs](https://openrouter.ai/docs/guides/best-practices/prompt-caching) — `cache_control` ephemeral + 1h TTL, automatic vs explicit, provider sticky routing — **MEDIUM confidence** (direct URL 404'd; content gathered via WebSearch snippets from official doc path)
- [sultanov.dev — Database column-level encryption with Spring Data JPA](https://sultanov.dev/blog/database-column-level-encryption-with-spring-data-jpa/) — `AttributeConverter` + AES pattern — **MEDIUM**
- [Spring Security Crypto Module docs](https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html) — `BytesEncryptor.stronger()` AES-256-GCM + PBKDF2 — **HIGH**
- [Baeldung — Spring Boot Configuration with Jasypt](https://www.baeldung.com/spring-boot-jasypt) — used to cross-check Jasypt tradeoffs — **MEDIUM**
- Project files: `build.gradle`, `src/main/resources/application.yaml`, `ChatService.java`, `frontend/package.json` — **HIGH** (direct read)

---
*Stack research for: Vietnam Traffic Law Chatbot v1.1 — Spring AI Modular RAG migration + API key admin*
*Researched: 2026-04-17*
