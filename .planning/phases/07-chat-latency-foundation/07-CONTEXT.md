# Phase 7: Chat Latency Foundation - Context

**Gathered:** 2026-04-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver foundational chat-latency infrastructure so top-20 canonical Vietnamese legal lookups hit p95 < 2.5s end-to-end:

1. Async `chat_log` persistence (HTTP response returns before log row write completes).
2. Slim `LegalAnswerDraft` JSON schema (drop 4 scenario fields).
3. `app.chat.grounding.keywordGate=false` flag + `app.chat.v11.*` feature-flag infra togglable without redeploy.
4. Caffeine in-JVM embedding cache (JHipster `CacheConfiguration` pattern) with dimension-mismatch guard + Micrometer hit/miss metrics.
5. Reproducible pre/post baseline snapshot (latency + refusal-rate) for downstream P8/P9 comparison.

No advisor refactor, no modular RAG, no prompt caching in P7 — those land in P8/P9. Keyword gate is only *flagged off*; the hardcoded list itself is deleted in P8 (ARCH-03).

</domain>

<decisions>
## Implementation Decisions

### Feature-flag Infrastructure
- **D-01:** Use `@ConfigurationProperties` class `ChatV11Properties` binding `app.chat.v11.*` with Spring Boot Actuator `/actuator/refresh` for hot-reload. No DB-backed flag table, no Cloud Config, no admin UI in v1.1. Flip env var → POST `/actuator/refresh` = live toggle without redeploy (satisfies success criterion 5).
- **D-02:** Namespace = granular per-feature: `app.chat.v11.grounding.keywordGate`, `app.chat.v11.chatLog.async`, `app.chat.v11.embeddingCache.enabled`, `app.chat.v11.slimSchema`. Each subsequent phase (P8/P9/P10) adds its own leaf flag under the same root for per-feature rollback.
- **D-03:** Rollback verification in P7 UAT = flip `app.chat.v11.chatLog.async=false` and confirm sync path resumes within 1 refresh.

### Slim JSON Schema
- **D-04:** Drop 4 scenario fields from `LegalAnswerDraft` LLM schema: `scenarioFacts`, `scenarioRule`, `scenarioOutcome`, `scenarioActions`. Keep the remaining 8 core legal-answer fields.
- **D-05:** API contract = **update backend and frontend simultaneously**. No null/[] tolerance fill in `AnswerComposer`. The user explicitly deferred production-deploy tolerance concerns for v1.1 iteration speed. Frontend code removes the rendering branches for the 4 dropped fields in the same PR sequence.
- **D-06:** Gated behind `app.chat.v11.slimSchema` flag so pre-flip requests still produce the 12-field output if ever needed for diagnostics.

### Baseline Snapshot
- **D-07:** Mechanism = Spring integration test `ChatLatencyBaselineIT` (MockMvc or RestAssured) running the top-20 query fixture × N runs, computing p50 / p95 / p99 latency and refusal-rate. Exports `BASELINE.json` + `BASELINE.md` into `.planning/phases/07-chat-latency-foundation/`.
- **D-08:** Query fixture = `src/test/resources/benchmark/top20-queries.json` — committed to repo, deterministic, reused by P8/P9 for apples-to-apples comparison. Claude proposes the 20 queries drawn from Vietnamese traffic-law canonical patterns (red-light, helmet, license, speeding, alcohol, documents, procedures); user reviews before lock.
- **D-09:** Baseline runs **twice**: (1) pre-change snapshot with all `app.chat.v11.*=false`, (2) post-change snapshot with P7 flags flipped on. Both stored in the same file for diff.
- **D-10:** Refusal-rate captured alongside latency by counting `GroundingStatus == REFUSED` in the integration-test responses.

### Async chat-log Executor
- **D-11:** Dedicated `ThreadPoolTaskExecutor` bean `chatLogExecutor`: core=2, max=8, queue=1000, `CallerRunsPolicy`. Bounded backpressure — no silent log loss when DB slow (Pitfall 7).
- **D-12:** `ChatLogService.save(...)` annotated `@Async("chatLogExecutor") @Transactional(propagation=REQUIRES_NEW)`. Required because async thread inherits no transaction.
- **D-13:** Pipeline log snapshot = `List.copyOf(logMessages)` captured on the request thread **before** async handoff. Never mutate the captured list post-handoff (Pitfall 7 race).
- **D-14:** Refusal path stays **synchronous** — refusals are rare, sync cost is negligible, async refusal loss would hide grounding regressions from P8/P9 (Pitfall 2/3).
- **D-15:** Gated behind `app.chat.v11.chatLog.async=true`. Default off on first deploy, flipped on post-baseline.

### Caffeine Embedding Cache (Claude's Discretion)
- **D-16:** `CachingEmbeddingModel` decorator marked `@Primary` wrapping the OpenRouter `OpenAiEmbeddingModel`. Transparent to callers.
- **D-17:** Cache key = `embeddingModelId + ":" + sha256(normalizedText)` where normalization = lowercase + NFC Unicode + trim. No accent-stripping (Vietnamese diacritics are semantically load-bearing for retrieval).
- **D-18:** Dimension-mismatch guard: cache value stores `(float[] vector, int dim)`; consumer asserts `dim == configuredPgvectorDim` on hit, evicts on mismatch.
- **D-19:** Invalidate trigger: Spring `@EventListener(EmbeddingModelChangedEvent)` calls `cache.invalidateAll()`. Event published by `AiModelProperties` reload path.
- **D-20:** Cache config via JHipster pattern: `@EnableCaching`, per-cache `CaffeineCacheConfiguration` (maxSize=10_000, TTL=30min as safety ceiling — write-path eviction is the primary correctness mechanism).
- **D-21:** Micrometer metrics `cache.gets{cache="embedding",result=hit|miss}` exposed via `/actuator/prometheus`.

### Prompt Trim (Claude's Discretion)
- **D-22:** P7 prompt trim is conservative — remove only the 4 scenario-field instruction block from `ChatPromptFactory.buildPrompt`. Do NOT split system/user into cacheable/dynamic halves yet; that belongs in P9 (prompt caching).
- **D-23:** Keep D-13 safety fallback (`SYSTEM_CONTEXT_FALLBACK`) untouched. Retiring it belongs in P9 cleanup per Pitfall 11.

### Claude's Discretion
- Exact `ChatV11Properties` class layout and nested group structure.
- JPA / Hibernate details of `ChatLogService` async save (entity mapping stays the same, only the service method changes).
- Caffeine dependency versioning (align with Spring Boot 4 BOM).
- Exact wording of `BASELINE.md` human-readable report (table format with columns: query, p50, p95, p99, grounded/refused).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Research artifacts
- `.planning/research/SUMMARY.md` — v1.1 migration scope, locked ADRs, Phase 7 rationale.
- `.planning/research/PITFALLS.md` §Pitfall 7 (async chat-log race), §Pitfall 8 (embedding cache staleness), §Pitfall 9 (slim schema frontend mismatch).
- `.planning/research/ARCHITECTURE.md` — advisor chain target shape (for P8/P9 context, P7 should not disturb).
- `.planning/research/STACK.md` — Caffeine 3.2.0 + `spring-boot-starter-cache` dependency guidance.

### Roadmap & requirements
- `.planning/ROADMAP.md` §Phase 7 — 5 success criteria (authoritative for P7 UAT).
- `.planning/REQUIREMENTS.md` — PERF-01, PERF-02, PERF-03, CACHE-02 exact wording.
- `.planning/PROJECT.md` — trust-tier + source-grounded non-negotiables.

### External references
- JHipster `CacheConfiguration.java` at `D:/jhipster/src/main/java/com/vn/core/config/CacheConfiguration.java` — pattern reference for `@EnableCaching` + per-cache Caffeine config.
- Spring AI 2.0.0-M4 docs via Context7 `/spring-projects/spring-ai` — `OpenAiEmbeddingModel` decorator pattern.
- OpenRouter prompt caching guide — informational for P9 only; P7 does not touch prompt caching.
- Spring Boot Actuator `/refresh` endpoint docs — for `ChatV11Properties` hot-reload.

### Source files touched by P7
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` — remove synchronous `chatLogService.save(...)` call; adjust scenario-field omission in draft mapping.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatLogService.java` — add `@Async("chatLogExecutor") @Transactional(REQUIRES_NEW)` wrapper; add `List.copyOf` snapshot discipline.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java` — drop scenario-field instruction block (keep D-13 fallback).
- `src/main/java/com/vn/traffic/chatbot/chat/domain/LegalAnswerDraft.java` — remove 4 scenario fields (and downstream usages in AnswerComposer).
- `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java` — unchanged in P7 (advisor chain is P8/P9).
- `src/main/resources/application.yaml` — add `app.chat.v11.*` keys with safe defaults.
- `frontend/` — remove rendering branches for 4 scenario fields (coordinated with backend PR).
- New: `ChatV11Properties.java`, `ChatLogAsyncConfig.java` (executor bean), `CachingEmbeddingModel.java`, `EmbeddingCacheConfig.java`, `EmbeddingModelChangedEvent.java`, `ChatLatencyBaselineIT.java`, `src/test/resources/benchmark/top20-queries.json`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ChatLogService` — already the persistence boundary; only its save path changes.
- `AnswerComposer` — stays untouched; LLM drops 4 fields but composer already handles partial drafts defensively in several branches (verify during planning).
- `AiModelProperties` — already supports reload; embedding cache invalidation hooks into its change path.
- Spring Boot `TaskExecutionAutoConfiguration` — project already has a default `applicationTaskExecutor`; `chatLogExecutor` is a dedicated peer, not a replacement.

### Established Patterns
- `application.yaml`-driven config with per-module properties classes (e.g., existing `AiModelProperties`) — `ChatV11Properties` follows the same pattern.
- Micrometer metrics already wired (`/actuator/prometheus` exposed) — cache metrics plug in without new infra.
- Liquibase for schema changes — **P7 adds no tables**, so no migration needed.

### Integration Points
- `ChatService.doAnswer` is the single integration surface for async-log + slim-schema + prompt-trim changes.
- Embedding cache sits between `ChatService` and `VectorStore.similaritySearch` as a `@Primary EmbeddingModel` decorator — zero call-site changes.
- Feature flags read at runtime via `ChatV11Properties` injected into `ChatService`, `ChatLogService`, `CachingEmbeddingModel`.

</code_context>

<specifics>
## Specific Ideas

- JHipster `CacheConfiguration.java` is the pinned reference pattern — Claude to mirror structure (single `@EnableCaching`, per-cache `CaffeineCacheConfiguration`, write-path eviction, TTL as safety ceiling, cache-name constants co-located with consumer).
- User explicitly accepts simultaneous BE+FE change without production-deploy tolerance concerns — iteration speed > rollout safety for v1.1.
- User chose test-driven reproducible baseline (not manual curl, not external load tool) — optimize for P8/P9 diffability.

</specifics>

<deferred>
## Deferred Ideas

- DB-backed feature-flag table with admin UI — considered, rejected for v1.1 complexity. Revisit if operator flagging frequency grows.
- Profile-based v11 master switch — rejected in favor of granular per-feature flags for rollback.
- External k6 / JMeter harness — rejected in favor of Spring integration test; can be added later if cross-network variance becomes a concern.
- Runtime chat_log top-20 query sampling — rejected; fixture is deterministic and diffable.
- Prompt split into cacheable system + dynamic user — deferred to P9 (prompt caching).
- Deletion of `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` — deferred to P9 cleanup (Pitfall 11).
- Deletion of `containsAnyLegalCitation` + Vietnamese keyword list — deferred to P8 (ARCH-03); P7 only flag-gates it off.
- Accent-stripping in embedding-cache key normalization — rejected; Vietnamese diacritics are semantically load-bearing.

</deferred>

---

*Phase: 07-chat-latency-foundation*
*Context gathered: 2026-04-17*
</content>
</invoke>