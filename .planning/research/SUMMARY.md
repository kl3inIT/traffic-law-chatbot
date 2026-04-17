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

Suggested phases: **7** (6 sequential on chat pipeline + 1 parallel on API-key admin). Phase numbering continues from v1.0 which ended at Phase 6 → v1.1 starts at Phase 7.

### Phase 7: Foundation & Latency Quick Wins
**Rationale:** Zero-risk prep unblocks everything downstream. Async chat-log save delivers immediate p95 improvement. Loosening keyword gate behind a flag is precondition for F2.
**Delivers:** p95 < 2.5s on common lookups, async executor, slim schema `?slim=1` canary, `app.chat.grounding.keywordGate=false` flag, feature-flag infra, refusal-rate baseline snapshot.
**Addresses:** F1 (PERF-01, PERF-02). **Avoids:** Pitfall 7, Pitfall 9.

### Phase 8: Embedding Cache (Caffeine)
**Rationale:** Transparent decorator; independent of advisor work; exercises Spring Cache abstraction on something that can't break grounding.
**Delivers:** `CachingEmbeddingModel` `@Primary` bean, JHipster-style `CacheConfiguration`, dimension-mismatch guard, Micrometer metrics, model-change invalidation hook.
**Uses:** Caffeine 3.2.0, `spring-boot-starter-cache`. **Implements:** CACHE-02. **Avoids:** Pitfall 8.

### Phase 9: Structured Output (BeanOutputConverter)
**Rationale:** Smallest isolated advisor work; validates `.entity()` + `StructuredOutputValidationAdvisor` before RAG depends on it.
**Delivers:** `.entity(LegalAnswerDraft.class)` replacing manual parsing, per-model `supportsStructuredOutput` capability flag, cross-model matrix test.
**Implements:** ARCH-02. **Avoids:** Pitfall 6.

### Phase 10: GroundingGuardAdvisor
**Rationale:** Depends on Phase 9 (classifier uses `.entity(IntentDecision.class)`) and Phase 7's keyword-gate flag. Unblocks Phase 11 by ensuring chitchat short-circuits before retrieval.
**Delivers:** `GroundingGuardInputAdvisor` + `GroundingGuardOutputAdvisor` pair, intent classifier (heuristic-first), chitchat canned replies, memory-advisor moved to `defaultAdvisors(...)`.
**Implements:** ARCH-01 (partial), ARCH-03 (full). **Avoids:** Pitfalls 1, 10, 3.

### Phase 11: Modular RAG Migration
**Rationale:** Centerpiece. Depends on Phase 10. Shrinks `ChatService.doAnswer` ~250→70 LOC.
**Delivers:** `RetrievalAugmentationAdvisor` wiring, `CitationPostProcessor`, `ContextualQueryAugmenter.allowEmptyContext(true)` with Vietnamese template, `FILTER_EXPRESSION` for trust-tier, identical output JSON shape, retirement of manual pipeline.
**Implements:** ARCH-01. **Avoids:** Pitfalls 2, 11, 13.

### Phase 12: Prompt Caching (OpenRouter `cache_control`)
**Rationale:** Last advisor phase — needs stable system block which doesn't exist until Phase 11. System/user message split in `ChatPromptFactory` is prerequisite.
**Delivers:** `PromptCachingAdvisor` at +500, `cache_control` via extra-body, provider-aware skip, `cached_tokens > 0` integration test.
**Implements:** CACHE-01. **Avoids:** Pitfalls 4, 12.

### Phase 13: User-Managed API Key Admin (parallel from Phase 7)
**Rationale:** Pure platform work; zero coupling to chat pipeline; encryption-from-commit-#1 non-negotiable.
**Delivers:** `api_key` + `api_key_audit` migrations (TIMESTAMPTZ, soft-delete), `ApiKeyService` + masked display + fingerprint, `ChatClientRegistry` + `@EventListener(ApiKeyRotatedEvent)`, `/api/admin/api-keys` CRUD, "Test connection" action, Next.js admin page, append-only audit role, Logback masking, `gitleaks` pre-commit, CI plaintext-grep gate.
**Uses:** `spring-security-crypto` 6.5.0, JPA `AttributeConverter`, existing Next.js stack. **Implements:** ADMIN-07. **Avoids:** Pitfalls 5, 14, 15, 16, 17, 18.

### Phase Ordering Rationale

- Phase 7 first — feature-flag infra + async executor + baseline snapshot are prerequisites.
- Phase 8 second — transparent win, de-risks Cache abstraction.
- Phase 9 before 10 — classifier uses `.entity(IntentDecision.class)`.
- Phase 10 before 11 — chitchat must short-circuit before retrieval.
- Phase 11 before 12 — cache breakpoints require stable system block.
- Phase 13 parallel with Phase 7-8 — zero coupling.

Highest-risk migration (Phase 11, keyword-gate removal + manual-RAG retirement) lands after Phases 9–10 validate the advisor pattern on lower-stakes surfaces.

### Research Flags

**Need deeper research at implementation kickoff:**
- **Phase 10:** Spring AI M4 `CallAdvisor`/`BaseAdvisor` exact signatures (Context7 at kickoff); Vietnamese chitchat heuristic A/B on 20-query set.
- **Phase 12:** `cache_control` extra-body serialization on M4 (likely `RestClient` interceptor fallback); verify via OpenRouter `generation`.
- **Phase 13:** `BytesEncryptor.stronger()` on Spring Security Crypto 6.5.0 + Spring Boot 4 / Java 25; `AttributeConverter` × Hibernate 7 dirty-tracking.

**Standard patterns — skip extra research:**
- Phase 7 (textbook Spring Boot), Phase 8 (JHipster reference pinned), Phase 9 (`BeanOutputConverter` verified), Phase 11 (all primitives Context7-verified).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Context7 + Spring Security official docs + JHipster reference; OpenRouter cache MEDIUM (WebSearch against official docs) |
| Features | HIGH | Grounded in direct v1.0 source reading |
| Architecture | HIGH | All Spring AI primitives Context7-verified |
| Pitfalls | HIGH | Backed by open issue threads |

**Overall confidence:** HIGH.

### Gaps to Address

- Spring AI M4 `cache_control` wire format — verify Phase 12 early via OpenRouter `cached_tokens`.
- Per-model `supportsStructuredOutput` matrix — one-time test across 8 cataloged models at Phase 9 kickoff.
- Empirical refusal-rate baseline — Phase 7 deliverable (pre/post comparison).
- Vietnamese chitchat classifier quality — Phase 10 spike on real chat logs.
- OpenRouter sticky-routing × per-user keys — Phase 13 kickoff via OpenRouter docs.

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
