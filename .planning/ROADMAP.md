# Roadmap: Vietnam Traffic Law Chatbot

**Created:** 2026-04-07
**Last updated:** 2026-04-17 — v1.1 planned (Phases 7–10)

## Milestones

- ✅ **v1.0 MVP** — Phases 1–06.1 (shipped 2026-04-15)
- 🟡 **v1.1 Chat Performance & Spring AI Modular RAG** — Phases 7–10 (planned 2026-04-17)

## Phases

<details>
<summary>✅ v1.0 MVP (9 phases, 38 plans) — SHIPPED 2026-04-15</summary>

- [x] Phase 1: Backend Foundation & Knowledge Base (4/4 plans)
- [x] Phase 01.1: Spring AI-first Ingestion Alignment (4/4 plans)
- [x] Phase 2: Grounded Legal Q&A Core (4/4 plans)
- [x] Phase 3: Multi-turn Case Analysis (3/3 plans)
- [x] Phase 4: Next.js Chat & Admin App (7/7 plans)
- [x] Phase 4.1: Backend Hardening, ETL & Use-Case Architecture (4/4 plans)
- [x] Phase 5: Quality Operations & Evaluation (4/4 plans)
- [x] Phase 6: Audit, Real-Data Validation & Stabilization (6/6 plans)
- [x] Phase 06.1: Multi-Provider AI Model Selection (2/2 plans)

</details>

### v1.1 — Chat Performance & Spring AI Modular RAG

- [ ] **Phase 7: Chat Latency Foundation** — Async log, slim schema, prompt trim, feature-flag infra, Caffeine embedding cache; latency/refusal baseline snapshot.
- [x] **Phase 8: Structured Output + GroundingGuardAdvisor** — `BeanOutputConverter` via `.entity()`, Input/Output grounding guard advisor pair, LLM intent classifier, retire keyword gate. (Complete 2026-04-18; 20/20 code-level SCs verified + live run 5/7 pass — 2 regression tests scope-deferred to Phase 9.)
- [ ] **Phase 9: Modular RAG + Prompt Caching** — `RetrievalAugmentationAdvisor` + `CitationPostProcessor`, trust-tier `FILTER_EXPRESSION`, `PromptCachingAdvisor` with OpenRouter `cache_control`.
- [ ] **Phase 10: User-Managed API Key Admin** — Encrypted `api_key` + `api_key_audit` tables, `ChatClientRegistry` runtime rotation, admin UI, masking, audit, security gates. (Parallelizable with Phase 7.)

## Phase Details

### Phase 7: Chat Latency Foundation
**Goal**: User receives common legal answers under 2.5s p95; chitchat and legal paths are measurable, and foundational infra (feature flags, async executor, embedding cache) is in place for later advisor work.
**Depends on**: Nothing (first v1.1 phase).
**Requirements**: PERF-01, PERF-02, PERF-03, CACHE-02.
**Success Criteria** (what must be TRUE):
  1. User receives chat answers for the top-20 canonical Vietnamese legal lookups in under 2.5s p95 end-to-end at the API layer (informal manual smoke per CONTEXT.md D-06 — baseline snapshot SUPERSEDED by solo-dev decision 2026-04-17).
  2. HTTP response for a chat request returns before the `chat_log` row write completes, and count(HTTP 200) equals count(chat_log rows) within ≤0.1% gap in a 1000-request load test.
  3. User greetings and non-legal small talk short-circuit past the grounding gate via direct code (`isGreetingOrChitchat` in ChatService) without triggering the refusal template; manual smoke confirms conversational reply (keyword-flag SUPERSEDED by CONTEXT.md D-02).
  4. Repeat embedding requests for the same normalized query text are served from an in-process Caffeine cache; hit/miss counters are exposed via Micrometer and the cache invalidates on embedding-model change (dimension-mismatch guard proven in a test).
  5. ~~Feature-flag infrastructure (`app.chat.v11.*`)~~ **SUPERSEDED by CONTEXT.md D-01 (solo-dev 2026-04-17): no feature-flag layer; rollback = `git revert`. Criterion dropped.**
**Plans**: 4 plans
Plans:
- [x] 07-01-PLAN.md — Wave 0: expose /actuator/prometheus, add Caffeine + cache-starter + prometheus-registry deps, author 4 Nyquist RED test scaffolds + CacheKeyNormalizer stub
- [x] 07-02-PLAN.md — Caffeine embedding cache: CachingEmbeddingModel @Primary decorator, EmbeddingCacheConfig (JHipster pattern, .recordStats()), EmbeddingModelChangedEvent + listener, dimension-mismatch guard
- [x] 07-03-PLAN.md — ChatService trio: ChatLogAsyncConfig + @Async @Transactional(REQUIRES_NEW) on ChatLogService.save + List.copyOf snapshots; LegalAnswerDraft slim schema (12→8 fields) + prompt trim + AnswerComposer update + frontend branch removal; Vietnamese chitchat short-circuit
- [x] 07-04-PLAN.md — Wave 2: manual smoke (curl loop + /actuator/prometheus scrape) + human-verify checkpoint; produces 07-SMOKE-REPORT.md per CONTEXT.md D-06

### Phase 8: Structured Output + GroundingGuardAdvisor
**Goal**: Chat responses use native structured output and refusal/chitchat policy is encapsulated in an advisor pair; no hardcoded Vietnamese keyword heuristic drives grounding decisions.
**Depends on**: Phase 7 (feature-flag infra, `.entity()` baseline, refusal/latency baseline).
**Requirements**: ARCH-02, ARCH-03, ARCH-04.
**Success Criteria** (what must be TRUE):
  1. `ChatService` produces `LegalAnswerDraft` via `.entity(LegalAnswerDraft.class)` backed by `BeanOutputConverter`; the legacy `parseDraft` / `extractJson` / `fallbackDraft` / markdown-fence code paths are deleted from `ChatService`.
  2. Per-model `supportsStructuredOutput` flag governs native vs. prompt-instruction mode; cross-model matrix test passes across all 8 cataloged OpenRouter models (Anthropic, OpenAI, Google, DeepSeek families) without schema 400s.
  3. A `GroundingGuardInputAdvisor` + `GroundingGuardOutputAdvisor` pair owns the refusal decision and can be disabled/replaced via configuration without editing `ChatService`; `MessageChatMemoryAdvisor` is attached via `defaultAdvisors(...)` on the client builder rather than per-call.
  4. User greeting or non-legal small talk is classified by an LLM intent classifier (`.entity(IntentDecision.class)`) and short-circuits to a canned conversational reply without vector retrieval; zero keyword-list references remain in production grounding paths (`containsAnyLegalCitation` + Vietnamese keyword list removed).
  5. 20-query Vietnamese regression suite passes at ≥95%, refusal rate for canonical legal queries stays within 10% of the Phase-7 baseline, and a two-turn conversation integration test confirms chat memory still works after the advisor chain lands.
**Plans**: 4 plans
Plans:
- [x] 08-01-PLAN.md — Wave 0: ModelEntry 4→5 arg migration (atomic: record + 9 tests + 3 YAML) + build.gradle spring-ai-test dep + liveTest task + 4 RED test stubs + 20-query VN fixture
- [x] 08-02-PLAN.md — Wave 1: GroundingGuard advisor pair + 3 NoOp placeholders + IntentClassifier service + IntentDecision record + LegalAnswerDraft @JsonClassDescription + ChatClientConfig defaultAdvisors wiring (full P9 chain order)
- [x] 08-03-PLAN.md — Wave 2: ChatService.doAnswer rewrite (IntentClassifier dispatch + .entity(LegalAnswerDraft.class) + conditional ENABLE_NATIVE_STRUCTURED_OUTPUT); delete all ARCH-03 targets; AnswerComposer.composeOffTopicRefusal; NoKeywordGateArchTest + ChatServiceDeletionArchTest
- [x] 08-04-PLAN.md — Wave 3: VietnameseRegressionIT (≥95% + refusal ±10% + two-turn memory) using RelevancyEvaluator + FactCheckingEvaluator directly (BasicEvaluationTest absent in 2.0.0-M4) + StructuredOutputMatrixIT across 8 models + IntentClassifierIT (LEGAL/CHITCHAT/OFF_TOPIC live) — code complete, live execution awaits OPENROUTER_API_KEY

### Phase 9: Modular RAG + Prompt Caching
**Goal**: User-visible answer shape is identical to v1.0 (same `[Nguồn n]` citations, same `ChatAnswerResponse` JSON) but produced through the Spring AI modular RAG advisor chain, with the static system block marked for OpenRouter prompt caching.
**Depends on**: Phase 8 (chitchat short-circuit must exist before retrieval refactor; structured output validated).
**Requirements**: ARCH-01, ARCH-05, CACHE-01.
**Success Criteria** (what must be TRUE):
  1. User chat requests flow through a `RetrievalAugmentationAdvisor` wired with `VectorStoreDocumentRetriever` (similarity threshold from `RetrievalPolicy`) and a custom `CitationPostProcessor`; raw `vectorStore.similaritySearch` in `ChatService` is removed and `ChatService.doAnswer` shrinks ≥60% in LOC.
  2. `[Nguồn n]` citation labels and the `ChatAnswerResponse` / `sources[]` / `citations[]` JSON contract are byte-for-byte preserved; regression fixtures from v1.0 pass without frontend changes.
  3. Empty-context behavior is handled by `ContextualQueryAugmenter.allowEmptyContext(true)` plus the Phase-8 guard; `FILTER_EXPRESSION` enforces `trust_tier IN (PRIMARY, SECONDARY)` + active-source gating, with a sample of 50 post-deploy chat logs showing zero citations to non-legal / untrusted sources in legal answers.
  4. The static system block is marked with `cache_control: {"type":"ephemeral","ttl":"1h"}`; an integration test against the OpenRouter `generation` endpoint asserts `cached_tokens > 0` on the second call for an Anthropic-family model, and the advisor safely skips for non-Anthropic providers.
  5. Advisor chain ordering is explicit and documented (`GuardIn → Memory → RAG → Cache → Validation → GuardOut`); a two-turn integration test shows chat memory is preserved and roles alternate correctly.
**Plans**: 2 plans
Plans:
- [ ] 09-01-PLAN.md — PR1: RetrievalAugmentationAdvisor + LegalQueryAugmenter + CitationPostProcessor + CitationStashAdvisor + FILTER_EXPRESSION wiring; ChatService shrink ≥60%; 20-query regression + refusal-parity + two-turn memory + CitationFormat byte-for-byte
- [ ] 09-02-PLAN.md — PR2: StructuredOutputValidationAdvisor swap (maxRepeatAttempts=1); delete NoOpValidationAdvisor; retry IT

### Phase 10: User-Managed API Key Admin
**Goal**: An operator can create, rotate, disable, test, and audit per-provider API keys through the admin UI at runtime, with keys encrypted at rest and plaintext never leaking. (Independent of Phases 7–9; can run in parallel with Phase 7.)
**Depends on**: Nothing (parallel with Phase 7; no coupling to the chat advisor chain).
**Requirements**: ADMIN-07, ADMIN-08, ADMIN-09, ADMIN-10, ADMIN-11, ADMIN-12.
**Success Criteria** (what must be TRUE):
  1. Admin user can create, view, rotate, disable, and soft-delete OpenRouter (and other provider) API keys through a Next.js admin page without a backend restart; admin API surface is `/api/admin/api-keys` with RHF/zod/shadcn forms on the frontend.
  2. Stored keys are encrypted with AES-256-GCM via `BytesEncryptor.stronger()` through a JPA `AttributeConverter`; plaintext never appears in logs, `toString()`, REST responses, DB dumps, or stack traces — verified by a CI grep gate for `sk-[A-Za-z0-9]{20,}` across logs/DB dump/git history, an ArchUnit rule on the plaintext field, a Logback masking converter, and a `gitleaks` pre-commit hook.
  3. Admin UI displays stored keys as a masked preview (first 4 + last 4) plus a short fingerprint; full plaintext is never retrievable through UI or API after save, and a "Test connection" button probes the key via a lightweight backend call returning success/failure without revealing plaintext.
  4. Every create / rotate / disable / delete operation writes an append-only row to `api_key_audit` containing actor, action, TIMESTAMPTZ, and fingerprint (never plaintext); the audit table has INSERT-only grant for the app role.
  5. API-key rotation takes effect for new chat requests within 60 seconds of the admin action without backend restart; `ChatClientRegistry` rebuilds affected `ChatClient` entries on `ApiKeyRotatedEvent` while in-flight requests complete on the previous key (verified by a rotate-and-fire integration test).
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Milestone | Plans | Status | Completed |
|-------|-----------|-------|--------|-----------|
| 1. Backend Foundation & Knowledge Base | v1.0 | 4/4 | Complete | 2026-04-08 |
| 01.1. Spring AI-first Ingestion Alignment | v1.0 | 4/4 | Complete | 2026-04-09 |
| 2. Grounded Legal Q&A Core | v1.0 | 4/4 | Complete | 2026-04-10 |
| 3. Multi-turn Case Analysis | v1.0 | 3/3 | Complete | 2026-04-11 |
| 4. Next.js Chat & Admin App | v1.0 | 7/7 | Complete | 2026-04-12 |
| 4.1. Backend Hardening, ETL & Use-Case Architecture | v1.0 | 4/4 | Complete | 2026-04-12 |
| 5. Quality Operations & Evaluation | v1.0 | 4/4 | Complete | 2026-04-13 |
| 6. Audit, Real-Data Validation & Stabilization | v1.0 | 6/6 | Complete | 2026-04-14 |
| 06.1. Multi-Provider AI Model Selection | v1.0 | 2/2 | Complete | 2026-04-15 |
| 7. Chat Latency Foundation | v1.1 | 4/4 | Awaiting verification | — |
| 8. Structured Output + GroundingGuardAdvisor | v1.1 | 3/4 | Executing | — |
| 9. Modular RAG + Prompt Caching | v1.1 | 0/2 | Planned | — |
| 10. User-Managed API Key Admin | v1.1 | 0/0 | Not started | — |

## Dependencies (v1.1)

- Phase 7 → Phase 8 (feature-flag infra and `.entity()` baseline from P7 are prerequisites for P8).
- Phase 8 → Phase 9 (chitchat short-circuit from P8 must exist before retrieval refactor in P9; intent classifier uses `.entity(IntentDecision.class)`).
- Phase 10 is independent and runs in parallel with Phase 7 (no coupling to chat pipeline).

## Research Flags (v1.1)

Informed by `.planning/research/SUMMARY.md` and `.planning/research/PITFALLS.md`:

- **Phase 7**: low-risk patterns (async executor, Caffeine — JHipster reference pinned). Pitfalls 7, 8, 9 mitigated in plan work.
- **Phase 8**: verify Spring AI M4 `CallAdvisor` / `BaseAdvisor` exact signatures via Context7 at kickoff; per-model `supportsStructuredOutput` matrix; Vietnamese chitchat classifier A/B on 20-query set. Pitfalls 1, 3, 6, 10.
- **Phase 9**: verify `cache_control` extra-body serialization on Spring AI M4 (likely `RestClient` interceptor fallback); assert `cached_tokens > 0` against OpenRouter `generation`. Pitfalls 2, 4, 11, 13.
- **Phase 10**: verify `BytesEncryptor.stronger()` on Spring Security Crypto 6.5.0 + Spring Boot 4 / Java 25; `AttributeConverter` × Hibernate 7 dirty-tracking; `ChatClientRegistry` rotation races. Pitfalls 5, 14, 15, 16, 17, 18.

## Coverage Validation

### v1.0 (archived)

| Category | Total | Mapped |
|----------|-------|--------|
| Chat Foundation | 4 | 4 |
| Legal Guidance | 4 | 4 |
| Case Analysis | 4 | 4 |
| Knowledge Sources | 6 | 6 |
| Admin Operations | 6 | 6 |
| Platform | 4 | 4 |
| **Total** | **28** | **28** |

### v1.1

| Category | Total | Mapped |
|----------|-------|--------|
| Performance (PERF) | 3 | 3 |
| Architecture (ARCH) | 5 | 5 |
| Caching (CACHE) | 2 | 2 |
| Administration (ADMIN) | 6 | 6 |
| **Total** | **16** | **16** |

**Unmapped requirements:** 0 ✓

---
*Last updated: 2026-04-17 — v1.1 roadmap created*
