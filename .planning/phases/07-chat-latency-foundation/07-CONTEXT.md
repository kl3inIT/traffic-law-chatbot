# Phase 7: Chat Latency Foundation - Context

**Gathered:** 2026-04-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver foundational chat-latency infrastructure so top-20 canonical Vietnamese legal lookups hit p95 < 2.5s end-to-end:

1. Async `chat_log` persistence (HTTP response returns before log row write completes).
2. Slim `LegalAnswerDraft` JSON schema (drop 4 scenario fields).
3. Loosen grounding gate at code level (`containsAnyLegalCitation` bypassed for chitchat path) — **no feature-flag infrastructure**. Solo dev, iteration speed > runtime toggle. Rollback = `git revert`.
4. Caffeine in-JVM embedding cache (JHipster `CacheConfiguration` pattern) with dimension-mismatch guard + Micrometer hit/miss metrics.
5. Micrometer-based latency observation (existing `/actuator/prometheus`) — **no custom baseline test harness**. Ad-hoc manual smoke on a handful of Vietnamese queries pre/post is sufficient.

No advisor refactor, no modular RAG, no prompt caching in P7 — those land in P8/P9. Keyword gate removal (full deletion of hardcoded list) is P8 (ARCH-03).

**Explicitly deviates from ROADMAP.md success criterion 5** (feature-flag infra) — user decision on 2026-04-17: solo dev does not need runtime toggle; roadmap criterion superseded.
**Explicitly deviates from ROADMAP.md success criterion 1** phrasing (baseline snapshot) — replaced with informal manual check; no `ChatLatencyBaselineIT`.

</domain>

<decisions>
## Implementation Decisions

### Feature-flag Infrastructure — REMOVED
- **D-01:** No `ChatV11Properties`, no `app.chat.v11.*` namespace, no Actuator `/refresh` wiring, no DB-backed flag table. User decision (solo dev, pre-production): rollback via `git revert` + rebuild is acceptable; runtime toggle overhead is not worth it. Subsequent phases (P8/P9/P10) will also skip feature-flag infra unless user re-opens the decision.
- **D-02:** Keyword gate loosening in P7 = direct code change in `ChatService` (short-circuit greetings/chitchat past `containsAnyLegalCitation`). No config flag. Full deletion of the keyword list is still P8 scope.

### Slim JSON Schema
- **D-03:** Drop 4 scenario fields from `LegalAnswerDraft` LLM schema: `scenarioFacts`, `scenarioRule`, `scenarioOutcome`, `scenarioActions`. Keep the remaining core legal-answer fields.
- **D-04:** Update backend and frontend simultaneously. No null/[] tolerance fill in `AnswerComposer`; frontend removes rendering branches for the 4 dropped fields in the same PR sequence. User explicitly deferred production-deploy tolerance concerns for v1.1 iteration speed.

### Testing Approach (Explicit)
- **D-05:** No `ChatLatencyBaselineIT`. No `src/test/resources/benchmark/top20-queries.json` fixture. No custom load/perf harness.
- **D-06:** Latency observation = existing Micrometer + `/actuator/prometheus` + ad-hoc manual smoke on a handful of Vietnamese queries (e.g., `curl` a few times pre/post change, eyeball p95). No structured baseline snapshot file.
- **D-07:** When tests ARE written for Spring AI components later (advisor, `ChatClient`, `EmbeddingModel`, `BeanOutputConverter`, etc.), planner MUST first read Spring AI reference docs via Context7 `/spring-projects/spring-ai` and follow the official test pattern. Do not invent custom test harnesses.
- **D-08:** Pure domain logic (e.g., `CitationMapper`, `AnswerComposer`, Java-only helpers) — unit tests OK with JUnit as normal.

### Async chat-log Executor
- **D-09:** Dedicated `ThreadPoolTaskExecutor` bean `chatLogExecutor`: core=2, max=8, queue=1000, `CallerRunsPolicy`. Bounded backpressure — no silent log loss when DB slow (Pitfall 7).
- **D-10:** `ChatLogService.save(...)` annotated `@Async("chatLogExecutor") @Transactional(propagation=REQUIRES_NEW)`. Required because async thread inherits no transaction.
- **D-11:** Pipeline log snapshot = `List.copyOf(logMessages)` captured on the request thread **before** async handoff. Never mutate the captured list post-handoff (Pitfall 7 race).
- **D-12:** Refusal path stays **synchronous** — refusals are rare, sync cost is negligible, async refusal loss would hide grounding regressions from P8/P9 (Pitfall 2/3).
- **D-13:** Async-log change is direct, no flag gate (per D-01). Rollback = revert.

### Caffeine Embedding Cache (Claude's Discretion)
- **D-14:** `CachingEmbeddingModel` decorator marked `@Primary` wrapping the OpenRouter `OpenAiEmbeddingModel`. Transparent to callers.
- **D-15:** Cache key = `embeddingModelId + ":" + sha256(normalizedText)` where normalization = lowercase + NFC Unicode + trim. No accent-stripping (Vietnamese diacritics are semantically load-bearing for retrieval).
- **D-16:** Dimension-mismatch guard: cache value stores `(float[] vector, int dim)`; consumer asserts `dim == configuredPgvectorDim` on hit, evicts on mismatch.
- **D-17:** Invalidate trigger: Spring `@EventListener(EmbeddingModelChangedEvent)` calls `cache.invalidateAll()`. Event published by `AiModelProperties` reload path.
- **D-18:** Cache config via JHipster pattern: `@EnableCaching`, per-cache `CaffeineCacheConfiguration` (maxSize=10_000, TTL=30min as safety ceiling — write-path eviction is the primary correctness mechanism).
- **D-19:** Micrometer metrics `cache.gets{cache="embedding",result=hit|miss}` exposed via `/actuator/prometheus`.

### Prompt Trim (Claude's Discretion)
- **D-20:** P7 prompt trim is conservative — remove only the 4 scenario-field instruction block from `ChatPromptFactory.buildPrompt`. Do NOT split system/user into cacheable/dynamic halves yet; that belongs in P9 (prompt caching).
- **D-21:** Keep `SYSTEM_CONTEXT_FALLBACK` untouched. Retiring it belongs in P9 cleanup per Pitfall 11.

### Claude's Discretion
- JPA / Hibernate details of `ChatLogService` async save (entity mapping stays the same, only the service method changes).
- Caffeine dependency versioning (align with Spring Boot 4 BOM).
- Exact shape of the chitchat short-circuit in `ChatService` (direct code, no flag).

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

### Source files touched by P7
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` — swap synchronous `chatLogService.save(...)` for async call; short-circuit greetings past `containsAnyLegalCitation`; drop scenario-field usage from draft mapping.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatLogService.java` — add `@Async("chatLogExecutor") @Transactional(REQUIRES_NEW)` wrapper; add `List.copyOf` snapshot discipline.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java` — drop scenario-field instruction block (keep `SYSTEM_CONTEXT_FALLBACK`).
- `src/main/java/com/vn/traffic/chatbot/chat/domain/LegalAnswerDraft.java` — remove 4 scenario fields (and downstream usages in AnswerComposer).
- `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java` — unchanged in P7 (advisor chain is P8/P9).
- `src/main/resources/application.yaml` — minor config (no `app.chat.v11.*` namespace).
- `frontend/` — remove rendering branches for 4 scenario fields (coordinated with backend PR).
- New: `ChatLogAsyncConfig.java` (executor bean), `CachingEmbeddingModel.java`, `EmbeddingCacheConfig.java`, `EmbeddingModelChangedEvent.java`. No `ChatV11Properties.java`, no `ChatLatencyBaselineIT.java`, no `top20-queries.json` fixture.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ChatLogService` — already the persistence boundary; only its save path changes.
- `AnswerComposer` — stays untouched; LLM drops 4 fields but composer already handles partial drafts defensively in several branches (verify during planning).
- `AiModelProperties` — already supports reload; embedding cache invalidation hooks into its change path.
- Spring Boot `TaskExecutionAutoConfiguration` — project already has a default `applicationTaskExecutor`; `chatLogExecutor` is a dedicated peer, not a replacement.

### Established Patterns
- Micrometer metrics already wired (`/actuator/prometheus` exposed) — cache metrics plug in without new infra.
- Liquibase for schema changes — **P7 adds no tables**, so no migration needed.

### Integration Points
- `ChatService.doAnswer` is the single integration surface for async-log + slim-schema + prompt-trim + chitchat short-circuit.
- Embedding cache sits between `ChatService` and `VectorStore.similaritySearch` as a `@Primary EmbeddingModel` decorator — zero call-site changes.
- No feature flags wired — behavior changes are direct code changes.

</code_context>

<specifics>
## Specific Ideas

- JHipster `CacheConfiguration.java` is the pinned reference pattern — Claude to mirror structure (single `@EnableCaching`, per-cache `CaffeineCacheConfiguration`, write-path eviction, TTL as safety ceiling, cache-name constants co-located with consumer).
- User explicitly accepts simultaneous BE+FE change without production-deploy tolerance concerns — iteration speed > rollout safety for v1.1.
- Solo dev + pre-production: rollback = `git revert` + rebuild. No runtime toggle required.
- Spring AI tests must follow official reference docs via Context7 `/spring-projects/spring-ai`. No custom perf / baseline harnesses.

</specifics>

<deferred>
## Deferred Ideas

- **All feature-flag infrastructure** (`ChatV11Properties`, Actuator `/refresh`, DB-backed flag table, admin UI, profile-based switch, granular per-feature flags) — user explicitly rejected for solo-dev iteration speed. Revisit if/when project moves to production with live traffic.
- **`ChatLatencyBaselineIT` + `top20-queries.json` fixture + `BASELINE.md` report** — rejected; Micrometer + manual smoke is sufficient. No custom perf harness.
- **External k6 / JMeter load harness** — rejected; same reason.
- Prompt split into cacheable system + dynamic user — deferred to P9 (prompt caching).
- Deletion of `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` — deferred to P9 cleanup (Pitfall 11).
- Deletion of `containsAnyLegalCitation` + Vietnamese keyword list — deferred to P8 (ARCH-03); P7 only short-circuits greetings around it in code.
- Accent-stripping in embedding-cache key normalization — rejected; Vietnamese diacritics are semantically load-bearing.

</deferred>

---

*Phase: 07-chat-latency-foundation*
*Context gathered: 2026-04-17*
</content>
</invoke>