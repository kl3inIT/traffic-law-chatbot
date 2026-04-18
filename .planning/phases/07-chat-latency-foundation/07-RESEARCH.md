# Phase 7: Chat Latency Foundation - Research

**Researched:** 2026-04-17
**Domain:** Chat latency foundation — async persistence, slim LLM schema, embedding cache, code-level grounding gate loosening, Micrometer observation
**Confidence:** HIGH (Spring AI / Spring Boot / Caffeine patterns verified via Context7 `/spring-projects/spring-ai` and `/spring-projects/spring-boot`; v1.0 source read directly; JHipster reference file confirmed present; CONTEXT.md decisions locked by user)

## Summary

Phase 7 is an infrastructure-only, pre-advisor tune-up for the v1.0 chat pipeline. Four locked interventions land together under a single PR sequence: (1) move `ChatLogService.save` onto a dedicated bounded `chatLogExecutor` with `List.copyOf` snapshot and `REQUIRES_NEW` propagation, (2) drop four dead scenario fields (`scenarioRule`, `scenarioOutcome`, `scenarioActions` — and optionally `scenarioFacts`) from `LegalAnswerDraft` to slim the LLM JSON schema, (3) code-level short-circuit of greetings past `containsAnyLegalCitation` (no feature-flag layer), and (4) transparent `@Primary` `CachingEmbeddingModel` decorator with per-cache Caffeine config (JHipster pattern), dimension-mismatch guard, and `EmbeddingModelChangedEvent`-driven invalidation. Latency observation reuses the existing Micrometer infra; no custom baseline harness.

**Primary recommendation:** Wave-0 = Micrometer exposure (already installed — just expose `prometheus` endpoint) + `@EnableAsync` + `EmbeddingModelChangedEvent` event type. Wave-1 = async executor + `ChatLogService` refactor. Wave-2 = schema slim (BE + FE coordinated). Wave-3 = `CachingEmbeddingModel` decorator + `EmbeddingCacheConfig`. Wave-4 = `ChatService` chitchat short-circuit. Sampling tests are Spring-AI-docs-patterned (Context7 `/spring-projects/spring-ai`); no `ChatLatencyBaselineIT`. Verification p95 < 2.5s is a manual `curl` pre/post smoke.

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01** No `ChatV11Properties`, no `app.chat.v11.*` namespace, no Actuator `/refresh` wiring, no DB-backed flag table. Rollback = `git revert` + rebuild.
- **D-02** Keyword gate loosening = direct code change in `ChatService` (short-circuit greetings/chitchat past `containsAnyLegalCitation`). No config flag. Full deletion of the keyword list is P8.
- **D-03** Drop 4 scenario fields from `LegalAnswerDraft` LLM schema: `scenarioFacts`, `scenarioRule`, `scenarioOutcome`, `scenarioActions`.
- **D-04** Update backend and frontend simultaneously. No null/[] tolerance fill in `AnswerComposer`; frontend removes rendering branches for dropped fields in the same PR sequence.
- **D-05** No `ChatLatencyBaselineIT`. No `src/test/resources/benchmark/top20-queries.json`. No custom load/perf harness.
- **D-06** Latency observation = existing Micrometer + `/actuator/prometheus` + ad-hoc manual smoke on Vietnamese queries.
- **D-07** When Spring AI components are tested, planner MUST first read `/spring-projects/spring-ai` via Context7 and follow the official test pattern. No custom test harnesses.
- **D-08** Pure domain logic (`CitationMapper`, `AnswerComposer`, Java-only helpers) — unit tests OK with JUnit as normal.
- **D-09** Dedicated `ThreadPoolTaskExecutor` bean `chatLogExecutor`: core=2, max=8, queue=1000, `CallerRunsPolicy`.
- **D-10** `ChatLogService.save(...)` annotated `@Async("chatLogExecutor") @Transactional(propagation=REQUIRES_NEW)`.
- **D-11** `List.copyOf(logMessages)` snapshot captured on the request thread BEFORE async handoff.
- **D-12** Refusal path stays synchronous.
- **D-13** Async-log change is direct; no flag gate. Rollback = revert.
- **D-14** `CachingEmbeddingModel` decorator marked `@Primary` wrapping the OpenRouter `OpenAiEmbeddingModel`. Transparent to callers.
- **D-15** Cache key = `embeddingModelId + ":" + sha256(normalizedText)`; normalization = lowercase + NFC Unicode + trim. No accent-stripping.
- **D-16** Dimension-mismatch guard: cache value stores `(float[] vector, int dim)`; consumer asserts `dim == configuredPgvectorDim` on hit, evicts on mismatch.
- **D-17** `@EventListener(EmbeddingModelChangedEvent)` → `cache.invalidateAll()`. Event published by `AiModelProperties` reload path.
- **D-18** Cache config via JHipster pattern: `@EnableCaching`, per-cache config (maxSize=10_000, TTL=30min as safety ceiling).
- **D-19** Micrometer metrics `cache.gets{cache="embedding",result=hit|miss}` exposed via `/actuator/prometheus`.
- **D-20** P7 prompt trim is conservative — remove only the 4 scenario-field instruction block from `ChatPromptFactory.buildPrompt`. Do NOT split system/user yet (P9 prompt caching scope).
- **D-21** Keep `SYSTEM_CONTEXT_FALLBACK` untouched.

### Claude's Discretion

- JPA / Hibernate details of `ChatLogService` async save (entity mapping unchanged, only service method changes).
- Caffeine dependency versioning (align with Spring Boot 4 BOM).
- Exact shape of the chitchat short-circuit in `ChatService` (direct code, no flag).

### Deferred Ideas (OUT OF SCOPE)

- All feature-flag infrastructure (`ChatV11Properties`, Actuator `/refresh`, DB-backed flag table, admin UI, profile-based switch, granular per-feature flags).
- `ChatLatencyBaselineIT` + `top20-queries.json` fixture + `BASELINE.md` report.
- External k6 / JMeter load harness.
- Prompt split into cacheable system + dynamic user → P9.
- Deletion of `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` → P9 cleanup.
- Deletion of `containsAnyLegalCitation` + Vietnamese keyword list → P8 (ARCH-03).
- Accent-stripping in cache-key normalization.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PERF-01 | p95 < 2.5s on common lookups, E2E at API layer | Sections §2 (Executor), §4 (Embedding cache), §5 (Micrometer), §7 (Slim schema) — three additive wins (async shift + cached embedding + schema/prompt trim). Measured via §8 Validation Architecture. |
| PERF-02 | Greetings never trigger refusal template | Section §3 (Chitchat short-circuit in `ChatService.doAnswer`). Direct code — no flag. |
| PERF-03 | No blocking wait for chat_log persistence; writes happen asynchronously | Sections §2 (`chatLogExecutor`) + §6 Pitfalls 7/A/B. `@Async @Transactional(REQUIRES_NEW)` + `List.copyOf` + `CallerRunsPolicy`. |
| CACHE-02 | Embedding cache for repeat normalized queries; Micrometer hit/miss metrics | Section §4 (`CachingEmbeddingModel` decorator) + §5 (Micrometer `cache.gets`). JHipster pattern reference pinned. |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Async chat-log persistence | API / Backend (Spring `@Async`) | Database (`chat_log` table — unchanged schema) | Logging is server-side concern; persistence layer already owns row-write. Moving off request thread is a thread-management choice in the API tier. |
| Chitchat short-circuit | API / Backend (`ChatService.doAnswer`) | — | Server decides whether to invoke retrieval + LLM; cannot trust the client to classify. Same tier as v1.0 grounding logic. |
| Slim LLM JSON schema | API / Backend (`LegalAnswerDraft` record + `ChatPromptFactory.buildPrompt`) | Frontend (`types/api.ts`, `message-bubble.tsx`) | Schema is emitted by backend to the LLM AND consumed by frontend. Since `AnswerComposer` already reshapes the draft into `ChatAnswerResponse`, the public DTO is mostly stable; scenario-field removal touches both tiers in the same PR (D-04). |
| In-JVM embedding cache | API / Backend (`CachingEmbeddingModel` decorator bean) | — | Decorator wraps Spring AI `EmbeddingModel`; transparent to `VectorStore`. Single-node Caffeine — deliberately NOT the database or CDN tier (future Redis swap via Spring Cache abstraction). |
| Latency observation | API / Backend (Spring Boot Actuator + Micrometer) | Operator (manual `curl` + eyeball) | Metrics live server-side; scraping and analysis happen out-of-band. Per D-05/D-06, no in-tree harness. |
| Cache metrics (hit/miss) | API / Backend (`CacheMetricsAutoConfiguration` auto-binds Caffeine `recordStats` → Micrometer `cache.gets`) | Operator (Prometheus scrape) | Spring Boot auto-config handles binding; no custom `MeterBinder` needed. |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.github.ben-manes.caffeine:caffeine` | 3.2.x | In-JVM LRU cache for embedding vectors | `[CITED: docs.spring.io/spring-boot/reference/io/caching.html]` — Spring Boot 4 auto-detects Caffeine when on classpath and wires `CaffeineCacheManager` via `spring-boot-starter-cache`. JHipster reference already uses Spring Cache abstraction (with Hazelcast) — same pattern, different backend. |
| `org.springframework.boot:spring-boot-starter-cache` | from Spring Boot 4 BOM | Wires `CacheManager` + enables `@EnableCaching` | `[CITED: Context7 /spring-projects/spring-boot — caching.adoc]` — required for `CacheMetricsAutoConfiguration` to bind Caffeine stats to Micrometer. |
| `io.micrometer:micrometer-registry-prometheus` | from Spring Boot 4 BOM | Export `/actuator/prometheus` | `[VERIFIED: build.gradle]` — `spring-boot-starter-actuator` already present (line 34). Prometheus registry is NOT yet an explicit dep — verify on classpath or add. |
| Spring AI `EmbeddingModel` interface | 2.0.0-M4 | Decorator target | `[VERIFIED: Context7 /spring-projects/spring-ai — embeddings.adoc]` — interface has one abstract method `call(EmbeddingRequest)` plus 4 default methods (`embed(Document)`, `embed(String)`, `embed(List<String>)`, `embedForResponse(List<String>)`) and `dimensions()`. A decorator overriding just `call(...)` correctly intercepts all paths because defaults delegate down. See §4.1. |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.springframework.boot:spring-boot-starter-actuator` | Spring Boot 4 BOM | Actuator endpoints | `[VERIFIED: build.gradle line 34]` — already present. Needs `prometheus` endpoint added to `management.endpoints.web.exposure.include` (currently `health,info,metrics` — see §5). |
| `org.springframework:spring-aop` | Spring Boot 4 BOM | `@Async` proxy generation | `[VERIFIED: build.gradle line 35-36]` — already present (aspectjweaver too). |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Caffeine (in-JVM) | Redis via `spring-boot-starter-data-redis` | Cross-JVM sharing. Rejected — single-node v1.1; STACK.md ADR locked. |
| JSR-107 `@Cacheable` on `embed(String)` via AOP | Explicit decorator bean | `@Cacheable` can't easily carry the `(vector, dim)` tuple AND invalidate on event AND short-circuit batch `call()` correctly. Explicit decorator is clearer and aligns with JHipster pattern of "cache-name constants at the consumer." |
| Spring Boot auto-configured default `applicationTaskExecutor` for `@Async` | Dedicated `chatLogExecutor` bean | Default executor is shared with MVC, WebFlux, GraphQL, JPA background init `[CITED: spring-boot/reference/features/task-execution-and-scheduling]`. Dedicated bounded pool isolates chat-log backpressure (Pitfall 7). User locked D-09. |

**Installation (additions to `build.gradle` dependencies block):**

```groovy
// --- v1.1 P7: Embedding cache (CACHE-02) ---
implementation 'com.github.ben-manes.caffeine:caffeine:3.2.0'
implementation 'org.springframework.boot:spring-boot-starter-cache'

// --- v1.1 P7: Prometheus metrics export (PERF-01 observation) ---
// Add ONLY if `/actuator/prometheus` verification in §5 shows it's not already pulled transitively.
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

**Version verification:** Run before first Wave-0 task:

```bash
# Caffeine — verify latest 3.x pinned against Spring Boot 4 BOM
./gradlew dependencies --configuration runtimeClasspath | grep caffeine
# Micrometer prometheus — check if actuator already transitively includes it
./gradlew dependencyInsight --dependency micrometer-registry-prometheus
```

`[ASSUMED]` Caffeine 3.2.0 is current. Assumption flagged because training data may be stale; planner's Wave-0 should verify via `./gradlew dependencies` or `mvnrepository.com`.

## Architecture Patterns

### System Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        HTTP request: POST /api/chat                       │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ (request thread)
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                     ChatController → ChatService.answer                   │
│                                                                           │
│  1. question arrives + List<String> logMessages = new ArrayList<>()       │
│                                                                           │
│  2. ┌──────────────────────────────────────┐                             │
│     │  Chitchat short-circuit (P7 NEW)     │   D-02 / D-20 / PERF-02     │
│     │  isGreetingOrChitchat(question)?     │                             │
│     │    yes → build conversational reply,  │                             │
│     │          skip retrieval + LLM,        │                             │
│     │          SYNC log.save (refusal-like) │ ── D-12                    │
│     │    no  → continue                     │                             │
│     └──────────────────────────────────────┘                             │
│                                                                           │
│  3. retrievalPolicy.buildRequest  ─→  vectorStore.similaritySearch       │
│              │                                                            │
│              ▼                                                            │
│     ┌──────────────────────────────────────┐                             │
│     │  EmbeddingModel.embed (via @Primary  │   CACHE-02                  │
│     │  CachingEmbeddingModel decorator)    │                             │
│     │    key = modelId + sha256(norm(q))   │                             │
│     │    hit → return vector (dim check)   │                             │
│     │    miss → delegate.embed, cache, ret │                             │
│     └──────────────────────────────────────┘                             │
│                                                                           │
│  4. containsAnyLegalCitation check (UNCHANGED in P7, removed in P8)       │
│     → REFUSED path (still sync log save — D-12)                           │
│     → GROUNDED path                                                       │
│                                                                           │
│  5. chatPromptFactory.buildPrompt (TRIMMED — D-20)                        │
│         scenario-field instruction block removed                          │
│                                                                           │
│  6. chatClient.prompt().user(prompt).call().chatResponse()                │
│                                                                           │
│  7. parseDraft (UNCHANGED — BeanOutputConverter is P8 scope)              │
│         LegalAnswerDraft now has 8 fields instead of 12 (D-03)            │
│                                                                           │
│  8. answerComposer.compose → ChatAnswerResponse                           │
│                                                                           │
│  9. ┌──────────────────────────────────────┐                             │
│     │  pipelineSnapshot = List.copyOf(...) │   D-11                      │
│     │         ▼                             │                             │
│     │  chatLogService.save(..., snapshot)  │   D-09..D-11 / PERF-03      │
│     │         (proxy method annotated      │                             │
│     │          @Async("chatLogExecutor")   │                             │
│     │          @Transactional(REQUIRES_NEW) │                             │
│     │  returns VOID immediately            │                             │
│     └──────────────────────────────────────┘                             │
│              │                                                            │
│              │ handoff (different thread)                                 │
│              ▼                                                            │
│  10. return ChatAnswerResponse  ─→  HTTP response flushed                 │
└──────────────────────────────────────────────────────────────────────────┘
                                 │
            (background thread: chatLogExecutor core=2 max=8 queue=1000)
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│   ChatLogService.save proxy  →  new transaction  →  chat_log INSERT      │
│   (CallerRunsPolicy: if queue full, runs on request thread as backstop)  │
└──────────────────────────────────────────────────────────────────────────┘

Observation plane:
   Micrometer → /actuator/prometheus (EXPOSE — currently not in yaml)
      cache.gets{cache="embedding",result=hit|miss}  (auto-bound)
      executor.* for chatLogExecutor                  (auto-bound)
      http.server.requests p95                        (auto-bound)
```

### Recommended Project Structure

New / modified files (per CONTEXT.md §Canonical References):

```
src/main/java/com/vn/traffic/chatbot/
├── chat/
│   ├── service/
│   │   ├── ChatService.java                 # MODIFIED — chitchat short-circuit, scenario-field drop wiring
│   │   ├── ChatPromptFactory.java           # MODIFIED — drop scenario-field instruction block (D-20)
│   │   ├── LegalAnswerDraft.java            # MODIFIED — remove 4 scenario fields (D-03); NOTE actual file lives under chat.service package, not chat.domain as CONTEXT.md states
│   │   └── AnswerComposer.java              # MODIFIED — stop reading dropped fields; see §2.1 finding
│   └── config/
│       └── ChatLogAsyncConfig.java          # NEW — @EnableAsync + chatLogExecutor bean (D-09)
├── chatlog/
│   └── service/
│       └── ChatLogService.java              # MODIFIED — @Async @Transactional(REQUIRES_NEW) on save
└── ai/
    └── embedding/
        ├── CachingEmbeddingModel.java       # NEW — @Primary decorator (D-14)
        ├── EmbeddingCacheConfig.java        # NEW — @EnableCaching + per-cache Caffeine spec (D-18)
        └── EmbeddingModelChangedEvent.java  # NEW — ApplicationEvent (D-17)

frontend/
├── types/api.ts                             # MODIFIED — remove scenarioFacts from ChatAnswerResponse
├── components/chat/message-bubble.tsx       # MODIFIED — remove scenarioAnalysis branch if scenarioAnalysis dropped too
└── components/chat/scenario-accordion.tsx   # possibly DELETED — depends on D-03 interpretation (see §2.1)
```

`src/test/resources/benchmark/top20-queries.json` — **NOT created** (D-05).
`ChatV11Properties.java`, `ChatLatencyBaselineIT.java` — **NOT created** (D-01, D-05).

### Pattern 1: EmbeddingModel Decorator (Spring AI verified)

**What:** A `@Primary` bean that implements `EmbeddingModel` and wraps the auto-configured OpenRouter `OpenAiEmbeddingModel`.

**When to use:** Transparent cross-cutting concern over embedding calls (cache, metrics, retry, rate-limit). No call-site changes needed in `VectorStore` or `ChatService`.

**Example (verified against Context7 `/spring-projects/spring-ai` `embeddings.adoc` — current interface shape):**

```java
// Source: Context7 /spring-projects/spring-ai — embeddings.adoc (HIGH confidence)
public class CachingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final Cache cache;                 // Spring Cache abstraction (Caffeine-backed)
    private final String modelId;              // from AiModelProperties.embeddingModel()
    private final int configuredDim;           // pgvector column dimension

    public CachingEmbeddingModel(EmbeddingModel delegate, CacheManager cm,
                                 String modelId, int configuredDim) {
        this.delegate = delegate;
        this.cache = cm.getCache(EmbeddingCacheConfig.EMBEDDING_CACHE);  // D-18
        this.modelId = modelId;
        this.configuredDim = configuredDim;
    }

    // Single abstract method — all default embed(...) overloads funnel here.
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        // Optimize only for single-text case (the RAG query-embed path).
        // Batch ingestion calls (multi-text) bypass cache — keep delegate direct.
        if (request.getInstructions().size() != 1) {
            return delegate.call(request);
        }
        String text = request.getInstructions().get(0);
        String key = modelId + ":" + sha256(normalize(text));  // D-15

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            CachedVector cached = (CachedVector) wrapper.get();
            if (cached != null && cached.dim() == configuredDim) {  // D-16
                return wrapResponse(cached.vector());
            }
            cache.evict(key);  // dimension mismatch → evict
        }

        EmbeddingResponse response = delegate.call(request);
        float[] vec = response.getResults().get(0).getOutput();
        cache.put(key, new CachedVector(vec, vec.length));
        return response;
    }

    // D-15 normalization — NFC + lowercase + trim. NO accent stripping.
    private static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC).trim().toLowerCase(Locale.ROOT);
    }

    private record CachedVector(float[] vector, int dim) {}
}
```

**Critical property of this pattern (verified from interface definition):** Because `embed(String)`, `embed(Document)`, `embed(List<String>)`, `embedForResponse(List<String>)`, and `dimensions()` are all `default` methods that internally call `this.embed(...)` or `this.call(...)` — a decorator that overrides only `call(EmbeddingRequest)` automatically intercepts every call path. No need to re-override the defaults. `[VERIFIED: Context7 /spring-projects/spring-ai — embeddings.adoc]`

**Bean registration:**

```java
@Configuration
public class EmbeddingCacheConfig {
    public static final String EMBEDDING_CACHE = "embedding";  // JHipster pattern: constant at consumer

    @Bean
    @Primary  // D-14 — override auto-configured OpenAiEmbeddingModel in the ChatService/VectorStore injection
    public EmbeddingModel cachingEmbeddingModel(EmbeddingModel delegate,
                                                 CacheManager cm,
                                                 AiModelProperties props,
                                                 @Value("${app.vector.pgvector-dim:1536}") int dim) {
        return new CachingEmbeddingModel(delegate, cm, props.embeddingModel(), dim);
    }
}
```

`[ASSUMED]` The auto-configured `OpenAiEmbeddingModel` bean is still injectable as `EmbeddingModel delegate` when our `@Primary` decorator is also present. Spring resolves constructor-arg-type lookup with `@Primary` on the consumer bean's injection point; the decorator receives the non-primary bean. This works when there is a single non-primary `EmbeddingModel` bean. If a second auto-configured embedding bean is added later, the planner must add a `@Qualifier` to the constructor arg. Verify by booting once before Wave-3 merge.

### Pattern 2: @Async with REQUIRES_NEW (Spring Boot 4 verified)

**What:** Public method on a Spring-managed bean annotated with both `@Async("chatLogExecutor")` and `@Transactional(propagation=REQUIRES_NEW)`, called from a DIFFERENT bean.

**Self-invocation trap (critical):** `@Async` works via a Spring proxy. Calling `this.save(...)` from inside the same bean BYPASSES the proxy and the annotation is silently ignored. `ChatService` → `chatLogService.save(...)` is already cross-bean (see `ChatService.java:49` — `private final ChatLogService chatLogService`), so this is safe.

**Example (verified against `/spring-projects/spring-boot` `task-execution-and-scheduling.adoc`):**

```java
// Source: Context7 /spring-projects/spring-boot — task-execution-and-scheduling.adoc (HIGH)
@Configuration
@EnableAsync
public class ChatLogAsyncConfig {

    public static final String CHAT_LOG_EXECUTOR = "chatLogExecutor";

    @Bean(name = CHAT_LOG_EXECUTOR)
    public ThreadPoolTaskExecutor chatLogExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);                                              // D-09
        ex.setMaxPoolSize(8);                                               // D-09
        ex.setQueueCapacity(1000);                                          // D-09
        ex.setThreadNamePrefix("chat-log-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());  // D-09
        ex.initialize();
        return ex;
    }
}
```

```java
// In ChatLogService — D-10
@Async(ChatLogAsyncConfig.CHAT_LOG_EXECUTOR)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void save(String question, ChatAnswerResponse response, GroundingStatus groundingStatus,
                 String conversationId, int promptTokens, int completionTokens, int responseTime,
                 String pipelineLogSnapshot) {   // <-- pre-snapshotted by caller (D-11)
    // body unchanged
}
```

```java
// In ChatService.doAnswer — D-11 snapshot discipline
String pipelineLog = String.join("\n", List.copyOf(logMessages));   // ← snapshot BEFORE handoff
chatLogService.save(question, response, groundingStatus, null,
                    promptTokens, completionTokens, responseTime, pipelineLog);
// Method returns immediately; log writes on chat-log-* thread.
```

**MDC propagation:** Spring Boot 4 preserves Slf4j MDC context across `@Async` via the auto-configured `TaskDecorator` only if configured. If structured logs need the correlation-id from request thread, add a `TaskDecorator` that captures `MDC.getCopyOfContextMap()`. `[ASSUMED]` Current v1.0 does not MDC-correlate chat-log writes; skip TaskDecorator unless planner finds a correlation-id in existing logs.

### Pattern 3: JHipster-style CacheConfiguration (reference-verified)

**What:** A single `@Configuration @EnableCaching` class, cache-name constants co-located with the consumer, per-cache config, TTL as safety ceiling (not freshness mechanism), write-path eviction for correctness.

**Reference file:** `D:/jhipster/src/main/java/com/vn/core/config/CacheConfiguration.java` `[VERIFIED: file confirmed present via Bash ls]`. Key quote (line 111-115):

> *"The TTL is set to 3600 s as a non-semantic safety ceiling only. Actual cache correctness comes from write-path eviction on every SecPermission create, update, or delete (D-02, D-03). The TTL prevents unbounded map growth if eviction is somehow missed in a deployment edge case; it is not intended as the freshness mechanism."*

**Adaptation for P7 (Caffeine instead of Hazelcast):**

```java
@Configuration
@EnableCaching
public class EmbeddingCacheConfig {

    // Cache-name constant at consumer — JHipster pattern 2
    public static final String EMBEDDING_CACHE = "embedding";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setCacheNames(List.of(EMBEDDING_CACHE));
        mgr.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)                         // D-18
            .expireAfterWrite(Duration.ofMinutes(30))    // D-18 — safety ceiling ONLY
            .recordStats());                             // D-19 — enables Micrometer binding
        return mgr;
    }

    // D-17 — write-path eviction on embedding-model change
    @Component
    @RequiredArgsConstructor
    static class EmbeddingCacheInvalidator {
        private final CacheManager cacheManager;

        @EventListener
        public void on(EmbeddingModelChangedEvent ev) {
            Cache c = cacheManager.getCache(EMBEDDING_CACHE);
            if (c != null) c.clear();
        }
    }
}
```

**Correctness reasoning (adapted from JHipster comment):** Cache key includes `modelId`, so write-path eviction is trivial — a model change → different key → old entries naturally orphaned. `EmbeddingModelChangedEvent.invalidateAll()` is belt-and-braces to bound memory after rotation. TTL=30min is a safety ceiling, not correctness; the 24h value in STACK.md was a generic suggestion — user locked 30min in D-18.

### Anti-Patterns to Avoid

- **Calling `this.save(...)` from within `ChatLogService`** — bypasses the `@Async` proxy. Always invoke from a different bean (`ChatService` → `ChatLogService` is already correct).
- **Mutating `logMessages` after handoff** — Pitfall 7 race. Snapshot BEFORE passing.
- **Using `@Cacheable` on `embed(String)`** — the return type is `float[]` (primitive array) which Spring Cache stores, but the dimension-mismatch guard (D-16) and the batch-vs-single fork are awkward to express via annotation. Stick with explicit decorator.
- **Adding `accent-stripping` "for fuzzier matching" in cache key normalization** — Vietnamese diacritics are semantically load-bearing for retrieval (D-15, deferred ideas).
- **Splitting `ChatPromptFactory.buildPrompt` into system/user messages** — that is P9 scope (D-20).
- **Removing `SYSTEM_CONTEXT_FALLBACK` or the keyword list** — P9 and P8 scope respectively (D-21, deferred ideas).
- **Adding null/`[]` tolerance fill in `AnswerComposer` for dropped scenario fields** — explicitly rejected in D-04. Frontend removes rendering branches in the same PR.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| In-JVM cache with stats | Custom `ConcurrentHashMap` + counters | Caffeine + `CacheMetricsAutoConfiguration` | Caffeine's `recordStats()` + Spring Boot's `CacheMetricsAutoConfiguration` auto-binds to Micrometer as `cache.gets`, `cache.puts`, `cache.evictions`, `cache.size` — zero extra `MeterBinder` code. `[CITED: spring-boot/reference/actuator/metrics.adoc]` |
| Async executor | Custom `ExecutorService` + manual shutdown | Spring `ThreadPoolTaskExecutor` + `@EnableAsync` | Spring wires lifecycle, exception handler (`AsyncUncaughtExceptionHandler`), and auto-exposes executor metrics to Micrometer. |
| SHA-256 of normalized text | Ad-hoc `MessageDigest` each call | `MessageDigest.getInstance("SHA-256")` is fine (JDK) — but centralize the helper | Not a library "don't hand-roll" — it's a JDK call. Just don't reimplement `Normalizer.normalize`. |
| Application event bus for cache invalidation | `Observer` pattern | Spring `ApplicationEventPublisher` + `@EventListener` | Already in use elsewhere; `AiModelProperties` reload path publishes → listener clears. Wiring is 5 lines. |
| Latency histograms | Custom `System.currentTimeMillis()` accumulator | `@Timed` or `Timer` from `io.micrometer.core` | Micrometer already on classpath via actuator. See §5. |

**Key insight:** Every "new" concern in P7 already has a mature Spring/Micrometer/Caffeine primitive. The phase's risk is not in primitive choice but in wiring discipline (`@Async` proxy, `@Primary` decorator ordering, snapshot-before-handoff).

## Runtime State Inventory

Phase 7 is a schema + code change only. No renames. No migrations. Checking each category:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | **Nothing to migrate.** `chat_log` table unchanged — only WHEN rows are written changes, not the row shape. `LegalAnswerDraft` is transient (no persistence). `ChatAnswerResponse.scenarioFacts` field removal doesn't break existing `chat_log.answer` TEXT (it's a rendered string, not JSON column). | None |
| Live service config | **None — verified.** No n8n / Datadog / Tailscale / Cloudflare Tunnel usage in this repo. `AiModelProperties` is YAML-only. | None |
| OS-registered state | **None — verified.** No Task Scheduler / launchd / systemd / pm2 registrations for the backend; Spring Boot runs as plain JVM. | None |
| Secrets/env vars | **None affected.** `OPENAI_API_KEY` unchanged. No new secrets. | None |
| Build artifacts / installed packages | Gradle `build/` will be stale after build.gradle dependency addition — normal. Frontend `node_modules` unaffected (no new npm deps). | `./gradlew clean build` once after dep add |

The "chat_log row-count == HTTP-200-count within 0.1%" (ROADMAP success criterion 2) is a BEHAVIOR test, not a migration. No existing rows need rewriting.

## Common Pitfalls

### Pitfall A: @Async self-invocation silently no-ops

**What goes wrong:** Method annotated `@Async` executes on the caller thread — zero error, no warning. Latency win disappears.

**Why it happens:** Spring `@Async` is proxy-based. Calling `this.save(...)` from inside the same bean (including via a helper method in the SAME bean) bypasses the CGLIB/JDK proxy.

**How to avoid:** `ChatService` → `ChatLogService.save` is cross-bean (verified `ChatService.java:49`). Do not introduce a wrapper method in `ChatLogService` that calls `this.save()`. If such a wrapper is needed, annotate the wrapper itself, not the helper.

**Warning signs:** Thread name in logs is still `http-nio-*` after the annotation lands. `chatLogExecutor` Micrometer metric `executor.completed` stays flat.

### Pitfall B: @Transactional(REQUIRES_NEW) without spring-tx proxy on async bean

**What goes wrong:** Async thread has no transaction → JPA save fails with `TransactionRequiredException` or silently detaches.

**Why it happens:** `@Async` starts a new thread. Thread has no inherited `TransactionSynchronization`. Without `@Transactional` on the async method, there is no transaction. With `@Transactional` but without `REQUIRES_NEW`, the (non-existent) outer transaction is joined → same result.

**How to avoid:** D-10 — both annotations together. Spring creates the proxy chain correctly: the outer proxy handles `@Async` (offload to executor), the inner handles `@Transactional(REQUIRES_NEW)` (starts a fresh tx on the async thread). Verify with a single `ChatLogServiceAsyncIT` following Spring-AI-docs-style pattern from Context7 (D-07).

**Warning signs:** `No EntityManager with actual transaction available for current thread` in async-thread logs.

### Pitfall 7 (from research/PITFALLS.md §Pitfall 7): Async save races pipeline-log mutation

**What goes wrong:** Request thread appends to `logMessages` AFTER handoff → async thread serializes a half-filled list, or `ConcurrentModificationException`.

**How to avoid:** D-11 — `List.copyOf(logMessages)` on request thread before passing. `List.copyOf` returns an immutable snapshot. Pass the snapshot (not the mutable list).

**Warning signs:** Trailing "Chat log:" lines missing from some rows; others complete. Intermittent — only under load.

### Pitfall 8 (from research/PITFALLS.md §Pitfall 8): Embedding cache dimension-mismatch after model swap

**What goes wrong:** Cached 1536-d vector served for a pgvector column rebuilt to 1024 dims → `PSQLException: expected X dimensions, got Y`.

**How to avoid:** D-15 keys cache by `modelId` (different model → different key, never collides). D-16 stores `(vec, dim)` tuple and asserts `dim == configuredPgvectorDim`, evicts on mismatch. D-17 `EmbeddingModelChangedEvent` clears the whole cache as belt-and-braces.

**Warning signs:** `PSQLException: expected 1536 dimensions, got 1024` immediately after admin model swap.

### Pitfall 9 (from research/PITFALLS.md §Pitfall 9): Slim schema frontend mismatch

**What goes wrong:** Backend drops fields; frontend still reads `answer.scenarioRule` → blank sections or React error.

**How to avoid:** D-04 — BE + FE land in same PR. See §7 for concrete FE files. Planner MUST check `frontend/types/api.ts` and `frontend/components/chat/message-bubble.tsx` + `scenario-accordion.tsx` in the same task wave as the backend change.

**Critical finding (§2.1 below):** The `scenarioRule/Outcome/Actions` fields in `LegalAnswerDraft` are **ALREADY DEAD CODE** in the v1.0 pipeline. `AnswerComposer.compose` reads only `safeDraft.scenarioFacts()` (line 79 of AnswerComposer.java); the other three are never accessed. Removing them from `LegalAnswerDraft` touches only `ChatPromptFactory.buildPrompt` (which instructs the LLM to emit them) — zero call-site impact in `AnswerComposer` or `ChatAnswerResponse`. The scenario-analysis UI uses `ChatAnswerResponse.scenarioAnalysis` (populated by `ScenarioAnswerComposer`, not the main `AnswerComposer`) — a SEPARATE path for the multi-turn scenario feature. The planner must verify whether D-03 intends to touch only `ChatPromptFactory` (leaving `ScenarioAnswerComposer` alone) or both.

### Pitfall C: Caffeine `recordStats()` omitted → Micrometer binds nothing

**What goes wrong:** Cache works fine, but `cache.gets` counters are all zero because Caffeine only publishes stats when explicitly enabled.

**How to avoid:** Always include `.recordStats()` in the builder (D-19 requires hit/miss metrics). Spring Boot's `CacheMetricsAutoConfiguration` auto-binds when `management.metrics.enable.cache=true` (default).

**Warning signs:** `curl /actuator/prometheus | grep cache_gets_total` returns zero lines.

### Pitfall D: `/actuator/prometheus` endpoint not exposed

**What goes wrong:** CONTEXT.md + research/SUMMARY.md assume `/actuator/prometheus` is scrapeable. `application.yaml:122` currently exposes `health,info,metrics` — **prometheus is NOT in the include list.** Micrometer would be there, but the HTTP endpoint is closed.

**How to avoid:** Add `prometheus` to `management.endpoints.web.exposure.include` in application.yaml, AND verify `micrometer-registry-prometheus` is on the classpath (runtime dep — probably needs adding to build.gradle). This is a Wave-0 task.

**Warning signs:** `curl localhost:8089/actuator/prometheus` → 404 Not Found.

## Code Examples

### Chitchat short-circuit (D-02, D-20)

```java
// Source: direct adaptation of existing ChatService.doAnswer structure (AUTHORITATIVE v1.0 source)
// Placement: immediately after logger.accept("User prompt: ...") in doAnswer.
if (isGreetingOrChitchat(question)) {
    logger.accept("Path: chitchat — skipping retrieval + LLM (P7)");
    ChatAnswerResponse chitchat = answerComposer.composeChitchat(question);  // new method, direct code
    String snap = String.join("\n", List.copyOf(logMessages));
    chatLogService.save(question, chitchat, GroundingStatus.GROUNDED, null,
                        0, 0, 0, snap);  // sync per D-12? NO — this is GROUNDED, not REFUSED; D-12 only exempts refusals. So async per D-10.
    return chitchat;
}
```

**Open question for discuss-phase:** D-12 exempts REFUSAL from async. Chitchat is NOT refusal. Per §7 integration-point analysis, chitchat should go through the async path (standard grounded save). Planner to confirm.

`isGreetingOrChitchat` is Claude's discretion (D-02 "exact shape is direct code"). Suggested heuristic: Vietnamese greetings regex (`^(xin chào|chào|hi|hello)\b`) + length-< 8-word quick path. Keep it conservative — this is NOT the P8 LLM intent classifier.

### Cache-key normalization (D-15)

```java
private static String normalize(String s) {
    // D-15: lowercase + NFC + trim. NO accent stripping (diacritics load-bearing).
    return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
        .trim()
        .toLowerCase(java.util.Locale.ROOT);
}

private static String sha256(String s) {
    try {
        byte[] h = java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(h);
    } catch (java.security.NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 must be available on JDK 25", e);
    }
}
```

### Micrometer observation (D-06, D-19)

Already-exposed metrics (Spring Boot auto-bound) sufficient for PERF-01 eyeball:

- `http_server_requests_seconds{uri="/api/chat",quantile="0.95"}` — E2E p95
- `cache_gets_total{cache="embedding",result="hit"}` / `{result="miss"}` — CACHE-02 hit rate
- `executor_pool_size{name="chatLogExecutor"}`, `executor_queued_tasks{name="chatLogExecutor"}` — backpressure

Manual smoke (D-06):

```bash
for q in "Xin chào" "Vượt đèn đỏ phạt bao nhiêu" "Thủ tục cấp lại GPLX"; do
  for i in 1 2 3; do
    curl -s -w "\n%{time_total}s\n" -XPOST localhost:8089/api/chat \
      -H 'Content-Type: application/json' \
      -d "{\"question\":\"$q\"}" > /dev/null
  done
done
curl -s localhost:8089/actuator/prometheus | grep -E 'http_server_requests.*0\.95|cache_gets_total\{cache="embedding"'
```

Eyeball p95 < 2.5s pre/post. No fixture file committed (D-05).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Per-method `@Cacheable` on embed calls | Explicit decorator bean over `EmbeddingModel` | Spring AI 1.0 (GA) | Decorator is Spring AI-idiomatic — every provider module exposes the same interface. `[CITED: /spring-projects/spring-ai embeddings.adoc]` |
| Synchronous log save with CompletableFuture sprinkle | `@EnableAsync` + `ThreadPoolTaskExecutor` + `@Async` | Spring 4.x canonical | Bounded queue + `CallerRunsPolicy` gives backpressure and avoids silent drops (Pitfall 7). |
| Caffeine key=text only | Key prefixed by `modelId` | Pitfall 8 experience (cross-project) | Model rotation doesn't poison cache. |

**Deprecated/outdated:**

- Ehcache 2.x for new caches — superseded by Caffeine in modern Spring Boot; `spring-boot-starter-cache` no longer auto-configures Ehcache 2.
- Building custom `MeterBinder` for cache stats — Spring Boot 3+ auto-binds any Caffeine cache with `recordStats()`. `[CITED: spring-boot/reference/actuator/metrics.adoc]`

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (junit-platform-launcher, from Spring Boot 4 BOM) |
| Config file | `build.gradle` (test task `tasks.named('test') { useJUnitPlatform() ... }`) — no separate config |
| Quick run command | `./gradlew test --tests "com.vn.traffic.chatbot.ai.embedding.*" --tests "com.vn.traffic.chatbot.chat.service.ChatServiceTest" -x integrationTest` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PERF-01 | Top-20 Vietnamese legal lookups p95 < 2.5s E2E | manual-only (D-05, D-06) | `curl` smoke per §Code Examples | — (no in-tree harness) |
| PERF-02 | Greetings short-circuit; no refusal template | unit (heuristic) + integration (ChatService) | `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.ChatServiceTest.chitchat*"` | ❌ Wave 0 — extend existing ChatServiceTest.java |
| PERF-03a | `ChatLogService.save` runs on `chatLogExecutor`, not request thread | integration (Spring context) | `./gradlew test --tests "com.vn.traffic.chatbot.chatlog.ChatLogServiceAsyncIT"` | ❌ Wave 0 — pattern from Context7 `/spring-projects/spring-boot` testing docs |
| PERF-03b | `List.copyOf` snapshot taken before handoff | unit | `./gradlew test --tests "ChatServiceTest.logSnapshotImmutability"` | ❌ Wave 0 |
| PERF-03c | `@Transactional(REQUIRES_NEW)` — async thread has own tx | integration with `@Transactional` rollback check | included in `ChatLogServiceAsyncIT` | ❌ Wave 0 |
| CACHE-02a | Cache hit on repeat normalized query | unit with Caffeine `CacheManager` test double | `./gradlew test --tests "CachingEmbeddingModelTest.hitOnRepeat"` | ❌ Wave 0 |
| CACHE-02b | Cache-key normalization: NFC + lowercase + trim, NO accent strip | unit (pure function) | `./gradlew test --tests "CachingEmbeddingModelTest.normalize*"` | ❌ Wave 0 |
| CACHE-02c | Dimension-mismatch guard evicts stale entry | unit | `./gradlew test --tests "CachingEmbeddingModelTest.dimensionMismatchEvicts"` | ❌ Wave 0 |
| CACHE-02d | `EmbeddingModelChangedEvent` clears cache | integration (Spring context) | `./gradlew test --tests "EmbeddingCacheConfigTest.eventClearsCache"` | ❌ Wave 0 |
| CACHE-02e | Micrometer `cache.gets{result=hit|miss}` exposed | integration with `/actuator/prometheus` smoke | `./gradlew test --tests "CacheMetricsSmokeIT"` | ❌ Wave 0 |
| D-03 | Slim schema — `LegalAnswerDraft` has 8 fields | compile-time (record test) + JSON serialization | `./gradlew test --tests "LegalAnswerDraftTest"` + existing `ChatContractSerializationTest.java` | ⚠ partial — extend existing |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "<scope>"` on the specific test class touched
- **Per wave merge:** `./gradlew test` (full suite)
- **Phase gate:** Full suite green + manual `curl` smoke documented (D-06) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModelTest.java` — covers CACHE-02a/b/c. Pure unit; mocks `EmbeddingModel` delegate + uses `ConcurrentMapCacheManager` test double.
- [ ] `src/test/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheConfigTest.java` — covers CACHE-02d. Full `@SpringBootTest` with `applicationEventPublisher.publishEvent(new EmbeddingModelChangedEvent(...))`.
- [ ] `src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogServiceAsyncIT.java` — covers PERF-03a/c. **Follow Spring AI testing pattern** per D-07; Context7 `/spring-projects/spring-boot` on `@SpringBootTest` + async verification (assert `Thread.currentThread().getName().startsWith("chat-log-")` inside the save method during test).
- [ ] `src/test/java/com/vn/traffic/chatbot/common/metrics/CacheMetricsSmokeIT.java` — covers CACHE-02e. Boot actuator web test client, embed a query twice, hit `/actuator/prometheus`, assert `cache_gets_total{cache="embedding",result="hit"} > 0`.
- [ ] Extend `ChatServiceTest.java` with chitchat short-circuit test (PERF-02) and log-snapshot test (PERF-03b).
- [ ] Extend `ChatContractSerializationTest.java` to assert `LegalAnswerDraft` JSON no longer contains `scenarioRule/Outcome/Actions` keys.

No framework install needed — JUnit 5 + spring-boot-starter-webmvc-test + spring-boot-starter-data-jpa-test already on classpath.

**Explicitly NOT created (per D-05):**
- `src/test/java/com/vn/traffic/chatbot/chat/perf/ChatLatencyBaselineIT.java`
- `src/test/resources/benchmark/top20-queries.json`
- Any custom `Timer`, `LoadGenerator`, or perf-harness utility class.

## Security Domain

Skipped — Phase 7 does not introduce authentication, authorization, input-validation surfaces, new network endpoints (beyond adding `prometheus` to the already-public actuator block), or cryptographic primitives. `security_enforcement` from project defaults: no new controllers; no user-submitted payload changes; no new secrets.

One hardening note for the planner (NOT a new requirement): `/actuator/prometheus` should remain unauthenticated for scraping in dev; in prod, add basic auth or network-level restriction — but that is operational concern, not P7 scope.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java (JDK toolchain 25) | Gradle build | ✓ | openjdk 25.0.2 (verified `java --version`) | — |
| Gradle wrapper | All build tasks | ✓ | via `./gradlew` | — |
| Node.js | Frontend build | ✓ | v24.14.0 (verified) | — |
| `curl` | Manual smoke (D-06) | ✓ | verified | — |
| PostgreSQL + pgvector | Runtime (unchanged from v1.0) | `[ASSUMED]` (not probed this session — v1.0 shipped with it) | — | — |
| OpenRouter API key | LLM + embedding calls | `[ASSUMED]` (env `OPENAI_API_KEY`) | — | — |
| `jhipster` reference repo | Documentation reference only | ✓ | `D:/jhipster/src/main/java/com/vn/core/config/CacheConfiguration.java` confirmed present | — |
| Prometheus scraper | PERF-01 observation | Not verified this session | — | Manual `curl /actuator/prometheus` + eyeball (D-06 explicitly permits this) |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** Prometheus scraper (eyeball `curl` per D-06).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Caffeine 3.2.0 is a current stable release compatible with Spring Boot 4 BOM | §Standard Stack | LOW — planner verifies via `./gradlew dependencies` in Wave 0; bump if needed. |
| A2 | `micrometer-registry-prometheus` is NOT already on classpath via `spring-boot-starter-actuator` | §Standard Stack / §5 | LOW — one `./gradlew dependencyInsight` probe resolves. |
| A3 | Auto-configured OpenRouter `OpenAiEmbeddingModel` is the only `EmbeddingModel` bean, so `@Primary` decorator resolves injection cleanly | §Pattern 1 | MEDIUM — if a second `EmbeddingModel` is ever added (e.g. for local smoke tests), `@Qualifier` retrofit needed. |
| A4 | Current v1.0 does not propagate MDC correlation IDs across `@Async` boundaries | §Pattern 2 | LOW — planner can add a `TaskDecorator` later if logs show missing correlation. |
| A5 | `AiModelProperties` reload path exists and can be made to publish `EmbeddingModelChangedEvent` | §Pattern 3, D-17 | MEDIUM — if there is NO existing reload path, D-17 is dormant (manual invalidation only). Planner: grep `AiModelProperties` for reload/refresh in Wave 0. |
| A6 | Chitchat (non-refusal) save should go async, not sync — D-12 only exempts refusals | §Code Examples | LOW — confirm with user in discuss-phase; one-line behavior. |
| A7 | `ChatPromptFactory.buildPrompt` currently instructs LLM to emit the 4 scenario fields in some branches — verify which branches need trimming in Wave-0 | §Code Examples, D-20 | LOW — direct read of the file resolves. |
| A8 | PostgreSQL + pgvector is running and reachable (not probed this session) | §Environment Availability | LOW — v1.0 already shipped with this. |
| A9 | Refactoring `CachingEmbeddingModel` won't break the existing Spring AI `VectorStore` wiring (pgvector consumer of `EmbeddingModel`) | §Pattern 1 | LOW — decorator IS the interface, so transparency is structural. A single boot smoke verifies. |

**Confirmations needed from user before/during planning:** A6 (chitchat sync vs async) is the only locked-decision-adjacent assumption.

## Open Questions

1. **Does D-03 intend to also remove `scenarioFacts` (the one surviving scenario field in `AnswerComposer.compose`) or only the other three?**
   - What we know: CONTEXT.md D-03 lists all 4; but `AnswerComposer` currently reads `safeDraft.scenarioFacts()` (passed to `ChatAnswerResponse`); `scenarioRule/Outcome/Actions` in `LegalAnswerDraft` are already dead code.
   - What's unclear: If `scenarioFacts` is also dropped, `ChatAnswerResponse.scenarioFacts` must be set to `List.of()` (or the field removed from the public DTO — breaking change coordinated with frontend per D-04).
   - Recommendation: Confirm in discuss-phase; default to "all 4 fields dropped from `LegalAnswerDraft`, AnswerComposer passes `List.of()` for scenarioFacts in the response DTO" — keeps public contract shape stable while honoring D-04's "no null tolerance fill" literally (`List.of()` ≠ `null`). Alternatively, drop the DTO field entirely.

2. **Does `ScenarioAnswerComposer` (the multi-turn scenario path, separate from the main `AnswerComposer`) need updating?**
   - What we know: `ScenarioAnalysisResponse` in frontend `types/api.ts` has `{facts, rule, outcome, actions}` — consumed via `response.scenarioAnalysis` (object), NOT via `response.scenarioRule` etc.
   - What's unclear: Whether the `LegalAnswerDraft` fields `scenarioRule/Outcome/Actions` ever feed `ScenarioAnalysisResponse` through `ScenarioAnswerComposer`, or whether the scenario analysis path has its own LLM call and its own draft type.
   - Recommendation: Planner reads `ScenarioAnswerComposer.java` and `ChatScenarioAnalysisIntegrationTest.java` in Wave 0 to confirm. If separate, D-03 only touches the main path and scenario analysis is untouched.

3. **Is `AiModelProperties` reload wired to publish `EmbeddingModelChangedEvent`, or does D-17 require adding both the event type AND publishing call-site?**
   - What we know: CONTEXT.md D-17 says "Event published by `AiModelProperties` reload path" — implying a path exists.
   - What's unclear: Whether the reload path exists in v1.0 or needs to be added.
   - Recommendation: Planner greps `AiModelProperties` for refresh/reload in Wave 0. If nothing exists, D-17's immediate value is limited to manual `applicationContext.publishEvent(...)` from admin code (future phase) — acceptable as wiring-only in P7.

4. **How is chitchat logged — GROUNDED, a new `CHITCHAT` status, or piggyback on REFUSED?**
   - What we know: P8 explicitly introduces an LLM-based intent classifier and new status. P7 is a code-level short-circuit.
   - What's unclear: Log storage — treating chitchat as GROUNDED dirties analytics; introducing a new enum value is P8 scope.
   - Recommendation: Log chitchat as GROUNDED with 0 documents + a pipeline-log marker "Path: chitchat short-circuit (P7)". Add a proper `CHITCHAT` enum in P8 alongside the classifier.

## Project Constraints (from CLAUDE.md)

Extracted actionable directives (frontend CLAUDE.md also loaded):

- `.planning/` files and code must be in English; chat with user in Vietnamese. → RESEARCH.md written in English. ✓
- Preserve core concepts from `jmix-ai-backend` → no P7 impact.
- Follow controller/service/API patterns similar to migrated shoes backend → P7 touches service + config layers only; no new controllers.
- Prefer REST-first backend changes → P7 adds only one operational endpoint (`/actuator/prometheus` exposure); no API changes.
- Keep traffic-condition integrations out of v1 → not relevant to P7.
- Preserve trusted-source ingestion, vector-store management, parameters, chat logs, and answer checks as first-class → P7 alters chat_log write timing but preserves the data; ingestion/vector-store untouched.
- Treat legal source provenance and answer grounding as critical → P7 chitchat short-circuit applies ONLY before grounding check; legal-path grounding unchanged. Removing scenario fields has no provenance impact.
- **Frontend AGENTS.md ("This is NOT the Next.js you know"):** read `node_modules/next/dist/docs/` before writing frontend code. → Planner MUST have the frontend-touching task include a `Read` of the relevant Next.js docs before modifying `message-bubble.tsx` or `types/api.ts`.
- **User memory — Spring AI tests follow official docs (Context7):** D-07 already encodes this.
- **User memory — solo dev, skip feature flags and custom test harnesses:** D-01/D-05/D-06 already encode this.

## Sources

### Primary (HIGH confidence)

- Context7 `/spring-projects/spring-ai` — `embeddings.adoc` (EmbeddingModel interface definition, OpenAiEmbeddingModel configuration, BeanOutputConverter JSON schema from records)
- Context7 `/spring-projects/spring-boot` — `task-execution-and-scheduling.adoc` (@EnableAsync, ThreadPoolTaskExecutor, applicationTaskExecutor, AsyncConfigurer)
- Context7 `/spring-projects/spring-boot` — `io/caching.adoc` (Caffeine provider, `spring.cache.caffeine.spec`, CaffeineCacheManager)
- Context7 `/spring-projects/spring-boot` — `actuator/metrics.adoc` (Cache metrics auto-binding, Micrometer overview)
- Project source (direct read): `ChatService.java`, `AnswerComposer.java`, `LegalAnswerDraft.java`, `ChatLogService.java`, `build.gradle`, `application.yaml`, `frontend/types/api.ts`, `frontend/components/chat/message-bubble.tsx`
- JHipster reference: `D:/jhipster/src/main/java/com/vn/core/config/CacheConfiguration.java` (verified file present; pattern comment on TTL-as-safety-ceiling on line 111-115 directly quoted)
- `.planning/research/SUMMARY.md`, `.planning/research/PITFALLS.md`, `.planning/research/ARCHITECTURE.md`, `.planning/research/STACK.md` (project-scoped, v1.1 authoritative)
- `.planning/phases/07-chat-latency-foundation/07-CONTEXT.md` (user-locked decisions, authoritative)

### Secondary (MEDIUM confidence)

- Spring AI 2.0.0-M4 OpenRouter OpenAI-compat routing — verified via STACK.md ADR, not re-probed

### Tertiary (LOW confidence)

- None used in P7 — every claim with non-LOW impact is verified above or flagged as `[ASSUMED]` in §Assumptions Log.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every library is already a project dep or a direct Context7-verified Spring Boot 4 primitive
- Architecture: HIGH — decorator pattern + `@Async` + JHipster CacheConfiguration are textbook; only Assumption A3 (bean wiring) needs one-boot verification
- Pitfalls: HIGH — Pitfalls 7/8/9 reproduce project-scoped research; A/B/C/D are canonical Spring pitfalls with Spring-AI-docs citations

**Research date:** 2026-04-17
**Valid until:** 2026-05-17 (30 days — stack is stable; only trigger is Spring AI 2.0.0-GA release which would warrant re-verification of embedding interface)

## RESEARCH COMPLETE
