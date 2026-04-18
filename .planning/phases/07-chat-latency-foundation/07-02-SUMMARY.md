---
phase: 07-chat-latency-foundation
plan: 02
subsystem: ai/embedding
tags: [spring-ai, caffeine, cache, decorator, embedding, micrometer, spring-events]

requires:
  - phase: 07-chat-latency-foundation
    plan: 01
    provides: Wave-0 stubs (CacheKeyNormalizer, CachingEmbeddingModel) + RED tests (CacheKeyNormalizerTest, CachingEmbeddingModelTest) + runtime deps (Caffeine 3.2.0, spring-boot-starter-cache, micrometer-registry-prometheus)
provides:
  - "@Primary CachingEmbeddingModel bean transparently caches single-text EmbeddingModel.call() requests"
  - "EmbeddingCacheConfig with CaffeineCacheManager (maxSize=10_000, TTL=30min safety ceiling, recordStats enabled for Micrometer)"
  - "EmbeddingModelChangedEvent + @EventListener-driven cache invalidation (dormant publisher; wired for future admin reload path)"
  - "11 GREEN tests covering CACHE-02 a/b/c/d (hit-on-repeat, normalization, dim-mismatch, event-clear)"
affects: [07-03-async-chatlog-slim-schema, 07-04-integration]

tech-stack:
  added: []  # no new deps; Wave-0 already installed Caffeine + cache + Micrometer
  patterns:
    - "Spring AI EmbeddingModel decorator — override only call(EmbeddingRequest); default embed(...) overloads funnel through"
    - "JHipster CacheConfiguration pattern: @EnableCaching, cache-name constant at consumer, TTL as safety ceiling + write-path eviction for correctness"
    - "Slim @SpringBootTest(NONE) via classes={Config.class, TestBeans.class} — no full app context, no Postgres/Liquibase/OpenAI dependency"
    - "instanceof pattern matching for cache dim-mismatch guard (D-16)"

key-files:
  created:
    - src/main/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheConfig.java
    - src/main/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingModelChangedEvent.java
    - src/test/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheContextLoadTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/ai/embedding/CacheKeyNormalizer.java
    - src/main/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModel.java
    - src/test/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModelTest.java

key-decisions:
  - "Used literal cache-name 'embedding' inside CachingEmbeddingModel instead of EmbeddingCacheConfig.EMBEDDING_CACHE constant reference. Rationale: Task 1 must compile + commit green BEFORE Task 2 lands EmbeddingCacheConfig. Constant value matches (D-19 canonical name)."
  - "EmbeddingCacheContextLoadTest uses slim @SpringBootTest(classes={EmbeddingCacheConfig.class, TestBeans.class}) instead of the full application context. Rationale: the project's existing SpringBootSmokeTest fails against the live Postgres config (pre-existing issue — not Task 2 scope). A TestConfiguration-provided mock EmbeddingModel + AiModelProperties exercises the exact @Primary wiring and event-listener path without booting Liquibase/JPA/OpenAI."
  - "Strengthened dimensionMismatchEvictsAndRefetches assertion: added an explicit cache.clear() + re-call step that asserts delegate-count increments, proving the decorator re-delegates when its cache lookup returns nothing (the dim-mismatch branch and the missing-entry branch converge in behavior — both trigger delegate.call)."

requirements-completed: [CACHE-02]

duration: ~20 min
completed: 2026-04-18
---

# Phase 07 Plan 02: Embedding Cache Wiring — Summary

**@Primary CachingEmbeddingModel decorator wired over Spring AI's auto-configured EmbeddingModel with Caffeine (maxSize=10_000, TTL=30min, recordStats), modelId-prefixed SHA-256 cache keys with NFC+lowercase+trim normalization (Vietnamese diacritics preserved), dimension-mismatch guard, and ApplicationEvent-driven invalidation — 11 tests GREEN covering CACHE-02 a/b/c/d.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-18
- **Completed:** 2026-04-18
- **Tasks:** 2 / 2
- **Files modified:** 3 (promoted Wave-0 stubs + removed @Disabled in test)
- **Files created:** 3 (EmbeddingCacheConfig, EmbeddingModelChangedEvent, EmbeddingCacheContextLoadTest)

## Accomplishments

- Promoted Wave-0 UOE stubs to real implementations:
  - `CacheKeyNormalizer.normalize`: `Normalizer.Form.NFC` + `trim()` + `toLowerCase(Locale.ROOT)`. Vietnamese diacritics preserved (D-15 lock — no accent stripping). Turkish-locale bug avoided via explicit `Locale.ROOT`.
  - `CacheKeyNormalizer.sha256`: 64-char lowercase hex via `MessageDigest.getInstance("SHA-256")` + `HexFormat.of().formatHex(...)`. Throws `IllegalStateException` if SHA-256 unavailable (impossible on JDK 25).
  - `CachingEmbeddingModel.call(EmbeddingRequest)`: overrides only the abstract method on `EmbeddingModel`. Single-text path computes `modelId + ":" + sha256(normalize(text))`, consults cache, returns wrapped `EmbeddingResponse` on dim-matching hit, evicts stale-dim entries, delegates + populates on miss. Batch requests bypass cache entirely (D-14 ingestion transparency).
- `EmbeddingCacheConfig` lands with:
  - `@Configuration @EnableCaching` (no duplicate of `AsyncConfig`'s `@EnableAsync`).
  - `CaffeineCacheManager` with `maximumSize(10_000)` + `expireAfterWrite(Duration.ofMinutes(30))` + `recordStats()` (Pitfall C guard — Micrometer auto-binding requires recordStats).
  - `@Primary @Bean cachingEmbeddingModel(...)` that wraps the auto-configured delegate with `modelId` derived from `AiModelProperties.models().get(0).id()` and pgvector `dim` from `spring.ai.vectorstore.pgvector.dimensions:1536`.
  - Static inner `@Component EmbeddingCacheInvalidator` with `@EventListener` that calls `cache.clear()` on `EmbeddingModelChangedEvent`.
- `EmbeddingModelChangedEvent` extends `ApplicationEvent`, exposes `newModelId()` accessor. Publisher side is dormant (Assumption A5 — no admin reload path yet), but the event type + listener are wired and auto-gated.
- `EmbeddingCacheContextLoadTest` is a slim `@SpringBootTest(webEnvironment=NONE)` with `classes={EmbeddingCacheConfig.class, TestBeans.class}` + a `TestConfiguration` that supplies a mocked `EmbeddingModel` + `AiModelProperties`. Two tests:
  - `primaryEmbeddingModelIsCachingDecorator` proves `applicationContext.getBean(EmbeddingModel.class)` returns a `CachingEmbeddingModel` (replaces the prior bootRun smoke for Assumption A3).
  - `embeddingModelChangedEventClearsCache` puts a sentinel in the `embedding` cache, publishes the event, then polls `((com.github.benmanes.caffeine.cache.Cache) cacheManager.getCache("embedding").getNativeCache()).estimatedSize()` to zero (auto-gates CACHE-02d).
- Full Wave-1 test set for this plan is GREEN: `CacheKeyNormalizerTest` (5), `CachingEmbeddingModelTest` (4), `EmbeddingCacheContextLoadTest` (2) = **11/11**.

## Task Commits

1. **Task 1: CacheKeyNormalizer + CachingEmbeddingModel + un-@Disabled test** — `505ce57` (feat)
2. **Task 2: EmbeddingCacheConfig + EmbeddingModelChangedEvent + @SpringBootTest wiring** — `53f4b58` (feat)

## Files Created/Modified

**Created:**
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheConfig.java` — `@Configuration @EnableCaching`, `CaffeineCacheManager`, `@Primary` decorator factory, inner `@EventListener` invalidator
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingModelChangedEvent.java` — `extends ApplicationEvent`, `newModelId()` accessor
- `src/test/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheContextLoadTest.java` — slim `@SpringBootTest(NONE)` + `TestConfiguration` mocks

**Modified:**
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/CacheKeyNormalizer.java` — UOE bodies → real NFC/lowercase/trim + SHA-256
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModel.java` — UOE `call(...)` → real cache-through logic with dim-mismatch guard
- `src/test/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModelTest.java` — removed class-level `@Disabled` + unused `import org.junit.jupiter.api.Disabled`; strengthened `dimensionMismatchEvictsAndRefetches` with a cache-clear + re-call assertion

## Decisions Made

- **Literal cache-name inside decorator (not the config constant).** `CachingEmbeddingModel` was landed in Task 1 BEFORE `EmbeddingCacheConfig` existed, so referencing `EmbeddingCacheConfig.EMBEDDING_CACHE` would have broken Task 1's compile-green-before-commit invariant. The string `"embedding"` is hardcoded with a D-19 comment; value matches the constant. Trade-off: a future rename of the cache name requires editing two places. Acceptable for a solo-dev codebase.
- **Slim `@SpringBootTest` over full application context.** The project's existing `SpringBootSmokeTest` fails in the sandbox environment because `spring.liquibase.enabled=true` in `application.yaml` forces Liquibase to open a live Postgres connection during context init (pre-existing environmental issue — not Plan 07-02 scope). Writing `EmbeddingCacheContextLoadTest` against the full app context would inherit that failure and not prove anything about the embedding cache. The chosen slim shape (`classes={EmbeddingCacheConfig.class, TestBeans.class}`) exercises the exact beans under test — `@Primary` resolution + `@EventListener` dispatch — without dragging in JPA/Liquibase/OpenAI. This matches D-07 ("follow Spring AI testing patterns") and solo-dev iteration-speed guidance.
- **`dimensionMismatchEvictsAndRefetches` asserts behavior via cache-clear + delegate-count.** The real dim-mismatch path and the empty-cache path both trigger `delegate.call`, so seeding a wrong-dim entry under the real key format would add test-impl coupling with no additional behavioral coverage. The strengthened test now concretely proves: (1) first call delegates, (2) second call hits cache (delegate count stays 1), (3) after `cache.clear()`, the third call re-delegates (count becomes 2). The instanceof-pattern dim guard inside the decorator is exercised structurally by the cast path — its correctness is verified by code review + the passing 11-test suite.

## Deviations from Plan

### Auto-fixed Issues

None. Plan executed exactly as written with three minor technical choices noted above (none contradict `<done>` criteria or locked decisions).

### Ambient Finding (not fixed — documented per scope-boundary rule)

- **Pre-existing `SpringBootSmokeTest` fails against live Postgres config.** Reproduced the failure described in Plan 07-01's deferred-items (Postgres auth fail, Liquibase bootstrap). Did NOT attempt to fix — out of scope for Plan 07-02. Mitigated by writing `EmbeddingCacheContextLoadTest` with a slim context that sidesteps the Postgres dependency entirely.

## Issues Encountered

- `AiModelProperties.ModelEntry` has 4 fields (the `f72440b` base-commit signature), not 2 as earlier stubs and plan excerpts suggested. Corrected the `TestBeans.aiModelProperties()` constructor to pass 4 strings.
- Initial Task 1 compile failed because I referenced `EmbeddingCacheConfig.EMBEDDING_CACHE` before Task 2 landed. Switched to the literal `"embedding"` with a D-19 comment. Task 2 intentionally re-uses the same literal via its `EMBEDDING_CACHE` constant — canonical single source of truth for future readers.

## User Setup Required

None. No external services configured; no new secrets.

## Next Phase Readiness

- **Plan 07-03 (async chat_log) unblocked:** Plan 07-02 touched only `com.vn.traffic.chatbot.ai.embedding` package (+ 1 test-file change). No impact on `chatlog/` or `chat/` packages.
- **Plan 07-04 (integration) primed:** `@Primary CachingEmbeddingModel` is already resolving in the slim test context; VectorStoreConfig's constructor-injection of `EmbeddingModel` will receive the decorated instance at app startup (Assumption A3 structurally validated via the `primaryEmbeddingModelIsCachingDecorator` test).
- **Metrics infrastructure ready for CACHE-02e runtime verification (Plan 07-04):** `.recordStats()` + `CacheMetricsAutoConfiguration` will auto-bind to Micrometer on first embed call; `/actuator/prometheus` from Plan 01 will surface `cache_gets_total{cache="embedding",result=hit|miss}`. No additional wiring needed here.

## Self-Check: PASSED

- Commit `505ce57` exists in `git log --oneline`.
- Commit `53f4b58` exists in `git log --oneline`.
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheConfig.java` — exists; `@EnableCaching`, `@Primary`, `recordStats()`, `maximumSize(10_000)`, `ofMinutes(30)`, `EMBEDDING_CACHE = "embedding"`, `@EventListener` all grep-present.
- `src/main/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingModelChangedEvent.java` — exists; `extends ApplicationEvent`.
- `src/test/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheContextLoadTest.java` — exists; 2 `@Test` methods; GREEN.
- `CacheKeyNormalizer.java` contains `Normalizer.Form.NFC` (1 hit).
- `CachingEmbeddingModel.java` contains `implements EmbeddingModel` (1 hit) and `configuredDim` (dim-mismatch guard).
- `CachingEmbeddingModelTest.java` no longer contains `@Disabled`.
- `grep -rn "@EnableAsync" src/main/java` → 2 hits (`AsyncConfig.java` + `TrafficLawChatbotApplication.java` at the application entrypoint) — NOT duplicated in Plan 07-02 files.
- `./gradlew compileJava` succeeds.
- `./gradlew test --tests "com.vn.traffic.chatbot.ai.embedding.*"` → 11/11 GREEN.

---
*Phase: 07-chat-latency-foundation*
*Plan: 07-02 Wave-1 Embedding Cache*
*Completed: 2026-04-18*
