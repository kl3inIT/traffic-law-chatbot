# Project Research Summary

**Project:** Vietnam Traffic Law Chatbot
**Milestone:** v1.1 — Chat Performance & Spring AI Modular RAG
**Domain:** Brownfield migration of a shipped RAG chatbot from manual pipeline to Spring AI idiomatic advisors + user-managed API key admin
**Researched:** 2026-04-17
**Confidence:** HIGH (Spring AI APIs verified via Context7 against `/spring-projects/spring-ai`; OpenRouter cache semantics verified via official docs and multiple SDK issue threads; v1.0 baseline read directly from source)

## Executive Summary

v1.0 shipped a working Vietnamese traffic-law chatbot whose chat pipeline is a ~250-line manual orchestration in `ChatService.doAnswer`: direct `vectorStore.similaritySearch`, hardcoded `containsAnyLegalCitation` Vietnamese keyword gate, monolithic `ChatPromptFactory` system prompt, and lenient regex-driven JSON extraction. It works, but it is slow (p95 well above 2.5s on common lookups), brittle (markdown-fenced JSON edge cases, false refusals on greetings), and incompatible with v1.1 goals: idiomatic Spring AI modular RAG, user-managed API keys at runtime, and cross-cutting removal of keyword-based grounding heuristics.

The research converges on a tightly bounded migration: keep every v1.0 domain object that still pays rent (`CitationMapper`, `AnswerComposer`, `AnswerCompositionPolicy`, `LegalAnswerDraft`, `RetrievalPolicy`, `GroundingStatus`), replace the manual orchestration with a six-advisor Spring AI chain (`GroundingGuardIn → MessageChatMemory → RetrievalAugmentation → PromptCaching → StructuredOutputValidation → GroundingGuardOut`), swap manual JSON parsing for `BeanOutputConverter` via `.entity(LegalAnswerDraft.class)`, add a transparent Caffeine embedding-cache decorator, and bolt an additive DB-encrypted `api_key` table + `ChatClientRegistry` in front of the existing YAML model catalog.

Three ADRs are locked:
1. **Caffeine, not Redis/Hazelcast** — single-node; the Spring Cache abstraction lets us swap later with zero business-code change. JHipster `CacheConfiguration` (studied at `D:/jhipster/src/main/java/com/vn/core/config/CacheConfiguration.java`) is the reference pattern — single `@EnableCaching`, cache-name constants at the consumer, write-path eviction for correctness with TTL as safety ceiling, per-cache config.
2. **Single `OpenAiChatModel` via OpenRouter OpenAI-compat for all 8 models** — no `spring-ai-starter-model-anthropic` in v1.1, because explicit per-block `cache_control` still flows through OpenAI-compat to Anthropic upstreams, and multi-SDK is deferred to v1.2 if extended-thinking or 1h cache TTL becomes a real need.
3. **Hardcoded Vietnamese legal-keyword matching is removed** wholesale (ARCH-03) and replaced by similarity-score threshold + trust-tier metadata gate + optional LLM intent classifier.

The dominant risks are migration-flavored, not greenfield: advisor order collisions silently breaking `MessageChatMemoryAdvisor` (Spring AI #4170 still open on 1.0.1), `ContextualQueryAugmenter`'s default `allowEmptyContext=false` regressing refusal UX, OpenRouter `cache_control` being silently dropped on the OpenAI-compat path for non-Anthropic providers, plaintext API key leakage via `toString()` / exception logs / `pg_dump`, and async chat-log save races that drop pipeline entries. Each risk has a concrete verification: two-turn integration test with explicit `getOrder()` constants, 20-query Vietnamese regression set gated at ≥95% pass, `cached_tokens > 0` assertion against the OpenRouter generation endpoint, CI grep for `sk-[A-Za-z0-9]{20,}` across logs/DB dumps/git history, and HTTP-200-count vs `chat_log`-row-count parity under load. Feature flags on each advisor keep rollback to "flip flag + redeploy."

## Key Findings

### Recommended Stack

v1.0 stack is frozen; v1.1 is delta-only. Spring Boot 4.0.5 + Spring AI 2.0.0-M4 BOM + Java 25 + PostgreSQL+pgvector all stay. Frontend Next.js 16.2.3 / shadcn / react-hook-form 7.72 / zod 4 is already sufficient for the masked-key admin screen — zero new frontend deps. Three backend additions; no build-tool upgrades.

**Core additions:**
- **Caffeine 3.2.0 + spring-boot-starter-cache** — in-JVM LRU embedding cache behind Spring `CacheManager`; JHipster-style `CacheConfiguration` pattern; swappable to Redis/Hazelcast later with zero business-code change.
- **spring-security-crypto 6.5.0** (standalone, NOT full `spring-boot-starter-security`) — `BytesEncryptor.stronger()` for AES-256-GCM + PBKDF2 column-level encryption of stored API keys via a ~40-LOC JPA `AttributeConverter`. Chosen over Jasypt because Jasypt's Spring Boot 4 / Java 25 compatibility is unverified and its default cipher requires explicit hardening.
- **Spring AI modular-RAG primitives from existing BOM** (no new dep) — `RetrievalAugmentationAdvisor`, `VectorStoreDocumentRetriever`, `ContextualQueryAugmenter` (with `allowEmptyContext(true)`), `BeanOutputConverter` via `.entity()`, `StructuredOutputValidationAdvisor`, `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`.

**Explicitly rejected:** Jasypt, full `spring-boot-starter-security`, Redis/Hazelcast, Spring Vault / AWS KMS, custom `StructuredOutputConverter`, Postgres `pgcrypto` DB-side encryption, provider-specific Spring AI starters, Hibernate Envers for audit.

Full detail in `.planning/research/STACK.md`.

### Expected Features

Six v1.1 feature areas, all P1. User-visible behavior for grounded legal answers should be **identical** pre/post migration — this is an architecture swap, not a feature rewrite. User-visible wins are latency (p95 < 2.5s), chitchat support (no more false refusals on "Xin chào"), and admin autonomy (rotate API keys without redeploy).

**Must have (v1.1 scope — all P1):**
- **F1 Latency Quick Wins** — async chat-log persistence, slim `LegalAnswerDraft` JSON schema, prompt trim, loosen grounding gate (PERF-01, PERF-02).
- **F2 GroundingGuardAdvisor** — `CallAdvisor` Input (intent classify → chitchat short-circuit) + Output (final refusal override); no keyword list (ARCH-03).
- **F3 RetrievalAugmentationAdvisor + CitationPostProcessor** — replaces manual pipeline; preserves `[Nguồn n]` citations and trust-tier filtering (ARCH-01).
- **F4 BeanOutputConverter** — `.entity(LegalAnswerDraft.class)` + `StructuredOutputValidationAdvisor`; delete ~60 LOC (ARCH-02).
- **F5a Caffeine embedding cache** — key = `embeddingModelId + ":" + sha256(normalizedText)` (CACHE-02).
- **F5b OpenRouter prompt caching** — explicit `cache_control: {"type":"ephemeral","ttl":"1h"}` on static system block (CACHE-01).
- **F6 User-Managed API Key Admin** — `api_key` table AES-256-GCM encrypted, masked display, audit log, runtime key rotation (ADMIN-07).

**Should have (v1.2 — defer):** SSE streaming, query compression/expansion, multi-key failover, Redis-backed shared cache, `spring-ai-starter-model-anthropic` native route.

**Anti-features:** Parallel retrieval+LLM warmup, local/smaller default model, per-user end-user keys, full answer cache, semantic cache, tool calling, extended reasoning, cross-encoder reranking.

Full detail in `.planning/research/FEATURES.md`.

### Architecture Approach

The v1.1 target is a thin `ChatService.answer(...)` (shrinks from ~250 LOC to ~70) that does nothing but resolve the `ChatClient`, invoke `.prompt().advisors(chain).user(q).call().entity(LegalAnswerDraft.class)`, read citations/status/docs from the returned `AdvisorContext`, and hand off to the unchanged `AnswerComposer`.

**Advisor chain (ordered):**
1. `GroundingGuardInputAdvisor` (`HIGHEST_PRECEDENCE`) — intent classify; chitchat short-circuit; `FORCE_REFUSAL` flag for out-of-scope.
2. `MessageChatMemoryAdvisor` (`-500`) — moved from per-call to `defaultAdvisors(...)`; ephemeral sentinel conversation ID when none supplied.
3. `RetrievalAugmentationAdvisor` (`0`) — `VectorStoreDocumentRetriever` inherits `RetrievalPolicy`; `CitationPostProcessor` stashes `List<CitationResponse>` in advisor context.
4. `PromptCachingAdvisor` (`+500`) — mutates `SystemMessage` metadata; skips when provider ≠ Anthropic family.
5. `StructuredOutputValidationAdvisor` (`+1000`) — bounded retry (`maxRepeatAttempts=2`) on parse failure.
6. `GroundingGuardOutputAdvisor` (`LOWEST_PRECEDENCE`) — final `GroundingStatus` write; refusal-draft override.

**Kept as-is:** `CitationMapper`, `AnswerComposer`, `AnswerCompositionPolicy`, `LegalAnswerDraft`, `RetrievalPolicy`, `GroundingStatus`.
**Modified:** `ChatService.doAnswer` (~70% shrink), `ChatPromptFactory.buildPrompt` (split into cacheable `SystemMessage` + dynamic `UserMessage`), `ChatClientConfig` (key-resolution chain + rotation event listener).
**Retired:** `containsAnyLegalCitation` + keyword list, `determineGroundingStatus`, `parseDraft` + `extractJson` + `fallbackDraft`, raw `vectorStore.similaritySearch` in `ChatService`.
**New:** `GroundingGuardInputAdvisor`/`GroundingGuardOutputAdvisor`, `CitationPostProcessor`, `PromptCachingAdvisor`, `CachingEmbeddingModel`, `ChatClientRegistry`, `ApiKeyService` + `ApiKeyEntity` + `ApiKeyAudit`.

Full detail in `.planning/research/ARCHITECTURE.md`.

### Critical Pitfalls

1. **Advisor order collision breaks ChatMemory** — Spring AI #4170 still open on 1.0.1. Mitigation: explicit `getOrder()` constants, memory via `defaultAdvisors(...)`, two-turn Vietnamese integration test.
2. **`ContextualQueryAugmenter` default refuses legitimate queries** — `allowEmptyContext=false` hijacks refusal UX. Mitigation: `allowEmptyContext(true)`, push refusal into `GroundingGuardOutputAdvisor`, 20-query regression set ≥95%.
3. **Removing keyword gate without layered defence regresses grounding quality (legal liability)**. Mitigation: layered — raised `similarityThreshold` + `trust_tier` metadata gate + optional LLM classifier; keep `looksLikeLegalCitation` as monitoring log only for 2 weeks; split removal and threshold-tuning into separate PRs.
4. **OpenRouter `cache_control` silently dropped on OpenAI-compat for non-Anthropic** — pydantic-ai #4392, OpenRouterTeam/ai-sdk-provider #35, sst/opencode #1245. Mitigation: inject via extra-body / `RestClient` interceptor; verify `cached_tokens > 0` against OpenRouter `generation` endpoint.
5. **Plaintext API key leakage** via `toString()`, exception stacks, `pg_dump`, Actuator `/env`. Mitigation: encrypt at rest only (no plaintext column, no decrypted read path), `@JsonIgnore` + `@ToString.Exclude` + ArchUnit, Logback masking converter, audit stores fingerprints only, `gitleaks` pre-commit, CI plaintext grep.
6. **`BeanOutputConverter` strict schema breaks on non-OpenAI providers**. Mitigation: per-model `supportsStructuredOutput` flag; lenient fallback + prompt-instruction mode; `FAIL_ON_UNKNOWN_PROPERTIES=false`; cross-model matrix test.
7. **Async chat-log save races with request lifecycle**. Mitigation: `List.copyOf(logMessages)` snapshot BEFORE handoff; bounded `ThreadPoolTaskExecutor` with `CallerRunsPolicy`; `@Transactional(REQUIRES_NEW)`; keep refusal-path synchronous.

Full detail in `.planning/research/PITFALLS.md`.

## Implications for Roadmap

Suggested phases: **4** (3 sequential on chat pipeline + 1 parallel on API-key admin). Phase numbering continues from v1.0 which ended at Phase 6 → v1.1 uses Phases 7–10.

### Phase 7: Chat Latency Foundation
**Rationale:** Zero-risk prep unblocks everything downstream. Async chat-log save delivers immediate p95 improvement. Loosening keyword gate behind a flag is precondition for Phase 8. Caffeine embedding cache is a transparent decorator independent of advisor work, so it belongs in the same foundation phase.
**Delivers:** p95 < 2.5s on common lookups, async chat-log executor, slim `LegalAnswerDraft` JSON schema, `app.chat.grounding.keywordGate=false` flag, feature-flag infra, refusal-rate/latency baseline snapshot, `CachingEmbeddingModel` `@Primary` decorator with JHipster-style `CacheConfiguration`, dimension-mismatch guard, Micrometer hit/miss metrics.
**Uses:** Caffeine 3.2.0, `spring-boot-starter-cache`, existing Spring Boot async executor.
**Implements:** PERF-01, PERF-02, PERF-03, CACHE-02. **Avoids:** Pitfalls 7 (async race), 8 (cache staleness), 9 (frontend tolerant-render first).

### Phase 8: Structured Output + GroundingGuardAdvisor
**Rationale:** `BeanOutputConverter`'s `.entity()` pattern is the smallest isolated advisor work — validating it first lets the LLM intent classifier in the GroundingGuardAdvisor reuse the same pattern. Keyword gate is deleted here (Phase 7 already flag-gated it off). Chitchat short-circuit lands here so it exists before the Phase 9 RAG refactor.
**Delivers:** `.entity(LegalAnswerDraft.class)` backed by `BeanOutputConverter` replacing `parseDraft` + `extractJson` + `fallbackDraft`; `StructuredOutputValidationAdvisor` with `maxRepeatAttempts=2`; per-model `supportsStructuredOutput` capability flag with cross-model matrix test; `GroundingGuardInputAdvisor` + `GroundingGuardOutputAdvisor` pair (Input = intent classify + chitchat short-circuit; Output = final refusal override); heuristic-first Vietnamese intent classifier on 20-query regression set ≥95%; `MessageChatMemoryAdvisor` moved from per-call to `defaultAdvisors(...)`; deletion of `containsAnyLegalCitation` + keyword list.
**Implements:** ARCH-02, ARCH-03, ARCH-04. **Avoids:** Pitfalls 1 (advisor order + ChatMemory), 3 (keyword-gate layered defence), 6 (strict schema across providers), 10 (classifier quality).

### Phase 9: Modular RAG + Prompt Caching
**Rationale:** Centerpiece. Depends on Phase 8 (chitchat must short-circuit before retrieval triggers; `.entity()` baseline is established). Shrinks `ChatService.doAnswer` ~250→70 LOC. Prompt caching is bundled here because `cache_control` breakpoints require the stable system block that only emerges after the RAG migration splits prompt into cacheable system + dynamic user parts.
**Delivers:** `RetrievalAugmentationAdvisor` with `VectorStoreDocumentRetriever` (inherits `RetrievalPolicy`), `CitationPostProcessor` (preserves `[Nguồn n]` labels), `ContextualQueryAugmenter.allowEmptyContext(true)` with Vietnamese template, `FILTER_EXPRESSION` for `trust_tier IN ('PRIMARY','SECONDARY')` metadata gate, retirement of raw `vectorStore.similaritySearch` in `ChatService`, byte-for-byte preservation of `ChatAnswerResponse` JSON contract; `PromptCachingAdvisor` at order `+500` with `cache_control: {"type":"ephemeral","ttl":"1h"}` via extra-body, provider-aware skip when provider ≠ Anthropic family, `cached_tokens > 0` integration test.
**Implements:** ARCH-01, ARCH-05, CACHE-01. **Avoids:** Pitfalls 2 (allowEmptyContext), 4 (cache_control silently dropped), 11 (JSON contract drift), 12 (provider-specific caching semantics), 13 (manual-pipeline retirement regressions).

### Phase 10: User-Managed API Key Admin (parallel with Phase 7)
**Rationale:** Pure platform work; zero coupling to the chat pipeline; encryption-from-commit-#1 is non-negotiable. Can start parallel with Phase 7.
**Delivers:** `api_key` + `api_key_audit` Liquibase migrations (TIMESTAMPTZ, soft-delete, append-only role), JPA `AttributeConverter` backed by `BytesEncryptor.stronger()` (AES-256-GCM + PBKDF2), `ApiKeyService` with masked display (first4+last4+fingerprint) and "Test connection" probe, `ChatClientRegistry` + `@EventListener(ApiKeyRotatedEvent)` rebuilding the client map atomically, `/api/admin/api-keys` CRUD, Next.js admin page on existing RHF/zod/shadcn stack, Logback masking converter, ArchUnit boundary rules, `gitleaks` pre-commit hook, CI plaintext-grep gate (`sk-[A-Za-z0-9]{20,}`).
**Uses:** `spring-security-crypto` 6.5.0, existing Next.js 16 / shadcn / react-hook-form 7.72 / zod 4 stack.
**Implements:** ADMIN-07, ADMIN-08, ADMIN-09, ADMIN-10, ADMIN-11, ADMIN-12. **Avoids:** Pitfalls 5 (plaintext leakage), 14 (rotation race), 15 (audit completeness), 16 (gitleaks / secret detection), 17 (masked display), 18 (cached ChatClient staleness after rotation).

### Phase Ordering Rationale

- Phase 7 first — feature-flag infra + async executor + baseline snapshot + transparent embedding cache are all prerequisites for later work and deliver immediate p95 improvement.
- Phase 8 before 9 — `.entity()` pattern validated before RAG depends on it; chitchat short-circuit must exist before retrieval refactor.
- Phase 9 last of the chat pipeline — cache breakpoints require the stable system block that only emerges post-RAG migration.
- Phase 10 parallel with Phase 7 — zero coupling to chat pipeline; API-key admin is additive.

Highest-risk migration (Phase 9, keyword-gate fallout + manual-RAG retirement) lands after Phase 8 validates the advisor pattern and delivers chitchat short-circuit on a lower-stakes surface.

### Research Flags

**Need deeper research at implementation kickoff:**
- **Phase 8:** Spring AI M4 `CallAdvisor`/`BaseAdvisor` exact signatures (Context7 at kickoff); Vietnamese chitchat heuristic A/B on 20-query set; per-model `supportsStructuredOutput` matrix test across 8 OpenRouter models.
- **Phase 9:** `cache_control` extra-body serialization on M4 (likely `RestClient` interceptor fallback); verify via OpenRouter `generation` endpoint (`cached_tokens > 0`).
- **Phase 10:** `BytesEncryptor.stronger()` on Spring Security Crypto 6.5.0 + Spring Boot 4 / Java 25; `AttributeConverter` × Hibernate 7 dirty-tracking; OpenRouter sticky-routing × per-credential.

**Standard patterns — skip extra research:**
- Phase 7 (textbook Spring Boot `@Async` + Caffeine + JHipster `CacheConfiguration` reference pinned).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Context7 + Spring Security official docs + JHipster reference; OpenRouter cache MEDIUM (WebSearch against official docs) |
| Features | HIGH | Grounded in direct v1.0 source reading |
| Architecture | HIGH | All Spring AI primitives Context7-verified |
| Pitfalls | HIGH | Backed by open issue threads |

**Overall confidence:** HIGH.

### Gaps to Address

- Spring AI M4 `cache_control` wire format — verify Phase 9 early via OpenRouter `cached_tokens`.
- Per-model `supportsStructuredOutput` matrix — one-time test across 8 cataloged models at Phase 8 kickoff.
- Empirical refusal-rate baseline — Phase 7 deliverable (pre/post comparison).
- Vietnamese chitchat classifier quality — Phase 8 spike on real chat logs.
- OpenRouter sticky-routing × per-credential — Phase 10 kickoff via OpenRouter docs.

## Sources

### Primary (HIGH)
- `/spring-projects/spring-ai` (Context7, 2.0.0-M4)
- Spring AI official docs: advisors, RAG, chat-memory, structured-output
- Spring Security Crypto docs
- Project source: `ChatService.java`, `ChatClientConfig.java`, `ChatPromptFactory.java`, `AnswerComposer.java`, `AnswerCompositionPolicy.java`, `AiModelProperties.java`, `CitationMapper.java`, `LegalAnswerDraft.java`, `RetrievalPolicy.java`, `build.gradle`, `application.yaml`, `frontend/package.json`, `.planning/PROJECT.md`
- JHipster reference: `D:/jhipster/src/main/java/com/vn/core/config/CacheConfiguration.java`

### Secondary (MEDIUM)
- OpenRouter Prompt Caching guide
- spring-projects/spring-ai#4170, #1601, #4212
- OpenRouterTeam/ai-sdk-provider#35
- pydantic/pydantic-ai#4392
- sst/opencode#1245
- sultanov.dev — column-level encryption with Spring Data JPA
- Baeldung — Spring Boot Jasypt

### Tertiary (LOW)
- Jasypt Spring Boot GitHub (used only to confirm non-fit decision)

---
*Research synthesized: 2026-04-17*
*Ready for roadmap: yes*
*Milestone: v1.1 — Chat Performance & Spring AI Modular RAG*
