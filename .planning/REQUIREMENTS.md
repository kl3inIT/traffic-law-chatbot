# Milestone v1.1 Requirements — Chat Performance & Spring AI Modular RAG

**Milestone:** v1.1
**Goal:** Cut chat latency 3-5× and migrate the manual RAG pipeline to Spring AI idiomatic (modular RAG + structured output + custom advisors). Eliminate hardcoded keyword matching. Add user-managed API key administration.
**Date:** 2026-04-17

## v1.1 Requirements

### Performance (PERF)

- [ ] **PERF-01**: User receives chat answers for common lookups with p95 latency under 2.5 seconds measured end-to-end at the API layer
- [ ] **PERF-02**: User greeting or non-legal small talk never triggers the refusal template; the system responds conversationally without retrieval
- [ ] **PERF-03**: User experiences no blocking wait for chat log persistence; log writes happen asynchronously after the response is returned

### Architecture (ARCH)

- [ ] **ARCH-01**: Chat pipeline uses Spring AI idiomatic advisors — the chat flow is orchestrated through the Spring AI advisor chain (modular RAG via `RetrievalAugmentationAdvisor` plus custom `CallAdvisor` components) rather than manual orchestration inside `ChatService`
- [x] **ARCH-02**: Structured output is produced via `BeanOutputConverter` through `.entity(LegalAnswerDraft.class)`; manual JSON extraction, markdown-fence stripping, and lenient Jackson fallback are removed from `ChatService`
- [x] **ARCH-03**: No hardcoded keyword-matching heuristics drive chat grounding decisions; classification relies on similarity scores, document metadata, or LLM-based judgment
- [x] **ARCH-04**: Refusal policy is encapsulated in a dedicated `GroundingGuardAdvisor` component (Input + Output pair) that can be tuned, disabled, or replaced via configuration without editing `ChatService`
- [ ] **ARCH-05**: Citation mapping and `[Nguồn n]` labeling are preserved during the modular RAG migration with no change to the response JSON contract consumed by the frontend

### Caching (CACHE)

- [ ] **CACHE-01**: Static system-prompt content is marked for OpenRouter prompt caching via `cache_control` breakpoints; caching status is observable through `cached_tokens` in response metadata
- [ ] **CACHE-02**: Repeat embedding requests for the same normalized query text are served from an in-process cache; cache hit/miss metrics are exposed via Micrometer

### Administration (ADMIN)

- [ ] **ADMIN-07**: Admin user can create, view, rotate, disable, and soft-delete OpenRouter (or other provider) API keys through an admin UI without redeploying the backend
- [ ] **ADMIN-08**: Stored API keys are encrypted at rest with AES-256-GCM; plaintext API keys never appear in logs, `toString()`, REST responses, database dumps, or stack traces
- [ ] **ADMIN-09**: Admin user sees only a masked preview of stored keys (first 4 + last 4 characters) plus a short fingerprint for identification; the full plaintext is never retrievable through the UI or API after save
- [ ] **ADMIN-10**: Every create, rotate, disable, or delete operation on an API key writes an audit entry (actor, action, timestamp, fingerprint) to an append-only `api_key_audit` table
- [ ] **ADMIN-11**: API key rotation takes effect for new chat requests within 60 seconds of the admin action without a backend restart (in-flight requests may complete on the previous key)
- [ ] **ADMIN-12**: Admin can test a saved API key from the UI (single button → backend probe call → success/failure indicator) without exposing the plaintext key

## Future Requirements (v1.2+)

Deferred to subsequent milestones:

- SSE / streaming chat responses (chunk-by-chunk delivery to UI)
- Advanced query transformation (rewrite, translation, multi-query expansion) via Spring AI `QueryTransformer` and `QueryExpander`
- Multi-key failover (pool of API keys per provider with auto-fallback on rate limit)
- Redis-backed or Hazelcast-backed shared cache for multi-replica deployments
- Native Anthropic API routing via `spring-ai-starter-model-anthropic` (extended thinking, 1h cache TTL, native tool-use format)
- Cross-encoder reranking (Cohere, BGE) for topK > 10 retrievals
- Full answer cache by question-hash (not just embedding cache)
- Semantic / fuzzy cache for near-duplicate questions

## Out of Scope

Explicitly excluded from v1.1:

- **Benchmark-as-a-phase** — latency checks are rolled into each phase's UAT; no standalone benchmarking phase
- **Multi-user RBAC / permission model** — API key admin is single-operator; no per-user key scoping
- **End-user API keys** — users of the chat app do not bring their own keys; only operators configure keys
- **Provider-specific Spring AI starters** (`spring-ai-starter-model-anthropic`, Vertex AI Gemini, etc.) — single `OpenAiChatModel` via OpenRouter OpenAI-compat endpoint remains the only path in v1.1
- **Spring Vault / AWS KMS / cloud secret manager** — env-var-derived master key is sufficient for single-node deployment
- **Full `spring-boot-starter-security`** — only the standalone `spring-security-crypto` module is used for encryption primitives
- **Redis / Hazelcast as the cache backend** — Caffeine is the v1.1 choice; Spring Cache abstraction keeps the swap path open
- **Hardcoded keyword lists as a fallback grounding heuristic** — removal is mandatory (ARCH-03), not optional
- **Changes to the public chat response JSON contract** — frontend must not require updates for the advisor migration
- **PDF/Word ingestion pipeline changes** — KNOW-01..06 remain as validated in v1.0

## Requirement Quality Notes

- All requirements are **user-centric** ("User can X" / "Admin user can Y") or **observable system behaviors** ("system does Z and it is measurable").
- All requirements are **atomic** — one capability per REQ-ID.
- Cross-cutting principle (ARCH-03 — minimize hardcoded keyword matching) is expressed as a single requirement rather than scattered as "and must not use keyword matching" clauses throughout, keeping each other requirement clean.

## Traceability

| REQ-ID | Phase | Success Criterion |
|--------|-------|-------------------|
| PERF-01 | Phase 7 | Top-20 canonical legal lookups served in <2.5s p95 end-to-end (baseline + post-change snapshot) |
| PERF-02 | Phase 7 | Greetings / non-legal small talk pass the loosened grounding gate (`keywordGate=false`) without hitting the refusal template; refusal-rate baseline captured |
| PERF-03 | Phase 7 | HTTP response returns before chat-log persistence; HTTP-200 count equals chat_log row count within ≤0.1% gap under 1000-request load |
| ARCH-01 | Phase 9 | Chat flow runs through `RetrievalAugmentationAdvisor` + custom advisors; raw `vectorStore.similaritySearch` in `ChatService` removed; `doAnswer` LOC ≥60% smaller |
| ARCH-02 | Phase 8 | `.entity(LegalAnswerDraft.class)` backed by `BeanOutputConverter` replaces `parseDraft`/`extractJson`/`fallbackDraft`; cross-model matrix passes |
| ARCH-03 | Phase 8 | LLM intent classifier + score-threshold grounding replace the Vietnamese keyword list; `containsAnyLegalCitation` + keyword list deleted |
| ARCH-04 | Phase 8 | `GroundingGuardInputAdvisor` + `GroundingGuardOutputAdvisor` pair owns refusal policy and is togglable via config without editing `ChatService` |
| ARCH-05 | Phase 9 | `[Nguồn n]` citation labels and `ChatAnswerResponse` JSON contract byte-for-byte preserved; v1.0 regression fixtures pass without frontend changes |
| CACHE-01 | Phase 9 | Static system block carries `cache_control: {"type":"ephemeral","ttl":"1h"}`; integration test asserts `cached_tokens > 0` on second call for an Anthropic-family model |
| CACHE-02 | Phase 7 | Caffeine embedding cache serves repeat normalized queries; Micrometer hit/miss metrics exposed; dimension-mismatch guard verified |
| ADMIN-07 | Phase 10 | Admin creates / views / rotates / disables / soft-deletes keys through `/api/admin/api-keys` + Next.js admin page without backend restart |
| ADMIN-08 | Phase 10 | AES-256-GCM at rest via `BytesEncryptor.stronger()`; CI grep + ArchUnit + Logback masking + gitleaks all green |
| ADMIN-09 | Phase 10 | UI shows masked preview (first 4 + last 4 + fingerprint); plaintext never retrievable via UI/API after save |
| ADMIN-10 | Phase 10 | Append-only `api_key_audit` row on every create/rotate/disable/delete with actor, action, TIMESTAMPTZ, fingerprint only |
| ADMIN-11 | Phase 10 | Rotation takes effect within 60s without restart; `ChatClientRegistry` rebuilds on `ApiKeyRotatedEvent`; in-flight requests complete on old key |
| ADMIN-12 | Phase 10 | "Test connection" button probes saved key via backend with success/failure indicator, never exposing plaintext |

---
*Defined: 2026-04-17 — traceability filled 2026-04-17*
