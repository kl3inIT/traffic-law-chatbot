# Phase 9: Modular RAG + Prompt Caching - Context

**Gathered:** 2026-04-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Swap the Phase-8 `NoOpRetrievalAdvisor` + `NoOpValidationAdvisor` placeholders for real Spring AI implementations while preserving v1.0's user-visible answer shape byte-for-byte (`[Nguồn n]` labels + `ChatAnswerResponse` / `sources[]` / `citations[]` JSON contract).

1. **Real retrieval advisor**: `RetrievalAugmentationAdvisor` wired with `VectorStoreDocumentRetriever` + custom `QueryAugmenter` (numbers retrieved docs `[Nguồn 1]…[Nguồn n]` inline in the augmented prompt, ports `ChatPromptFactory` behavior) + `CitationPostProcessor` as a `DocumentPostProcessor` that assigns `labelNumber` metadata pre-augmentation.
2. **FILTER_EXPRESSION**: reuses existing `RetrievalPolicy.RETRIEVAL_FILTER` literal — `approvalState == 'APPROVED' && trusted == 'true' && active == 'true'` — enforced via `VectorStoreDocumentRetriever`. Raw `vectorStore.similaritySearch` call in `ChatService.doAnswer` is removed.
3. **Empty-context handling**: `ContextualQueryAugmenter.allowEmptyContext(true)`; zero-citation refusal decision owned by the Phase-8 `GroundingGuardOutputAdvisor` using `AnswerComposer.composeRefusal()` verbatim (no wording drift).
4. **Real validation advisor**: `StructuredOutputValidationAdvisor` replaces `NoOpValidationAdvisor` with `maxRepeatAttempts = 1` (one retry on parse failure).
5. **`ChatService.doAnswer` shrink ≥60% LOC**: retrieval + citation mapping + prompt construction move into advisor chain; `doAnswer` orchestrates intent dispatch + `.entity()` call + response mapping only.
6. **Byte-for-byte preservation**: v1.0 regression fixtures for `[Nguồn n]` labels and `ChatAnswerResponse` JSON pass without frontend changes.
7. **Advisor chain order** (unchanged from P8 D-04): `GuardIn → Memory → RAG → [NoOpCache] → Validation → GuardOut`.

**Explicitly deviates from ROADMAP.md Phase 9 success criterion 4**: prompt caching (`cache_control`, `cached_tokens > 0` test) is **deferred out of Phase 9** (see D-07). `NoOpPromptCacheAdvisor` placeholder stays unchanged in the chain. `CACHE-01` requirement rolls to a later phase.

**Not in scope**: `PromptCachingAdvisor`, OpenRouter `cache_control` injection, provider-family detection for caching, `cached_tokens` integration test.

</domain>

<decisions>
## Implementation Decisions

### Retrieval & Trust-Tier Filter
- **D-01:** `VectorStoreDocumentRetriever.FILTER_EXPRESSION` = reuse `RetrievalPolicy.RETRIEVAL_FILTER` literal as-is (`approvalState == 'APPROVED' && trusted == 'true' && active == 'true'`). ROADMAP SC 3's wording (`trust_tier IN (PRIMARY, SECONDARY)`) is treated as intent already satisfied — PRIMARY+SECONDARY sources already map to `trusted=true` during ingestion; MANUAL_REVIEW maps to `trusted=false`. **No chunk-metadata backfill**, no Liquibase migration. If the trust-tier literal is needed in future, add it incrementally without blocking P9.
- **D-02:** `VectorStoreDocumentRetriever` consumes `topK` + `similarityThreshold` **per-request** via `Supplier<SearchRequest>` that calls `RetrievalPolicy.buildRequest(query, topK)` on every invocation. Preserves the `AiParameterSet` runtime-reload admin workflow without restart.
- **D-03:** ROADMAP SC 3's "50 post-deploy chat logs sample → zero citations to untrusted sources" = **manual SQL + eyeball review** against `chat_log` JOIN `citations` post-deploy. No new `CheckDef`, no automated harness, no fixture IT. Consistent with P7 D-06, P8 D-05a (solo dev, no custom perf/audit harnesses).

### CitationPostProcessor & Numbering
- **D-04:** **Custom `QueryAugmenter`** (extending `ContextualQueryAugmenter`) owns `[Nguồn n]` numbering. It ports the numbered-citations block currently in `ChatPromptFactory.formatCitation` / `buildPrompt` and embeds labeled docs in the augmented prompt. The ROADMAP-named `CitationPostProcessor` is implemented as a **`DocumentPostProcessor`** that assigns `labelNumber` metadata to each retrieved `Document` **pre-augmentation**, so the augmenter reads labels from metadata deterministically. No post-processing of model text output (model emits `[Nguồn n]` inline, matching the numbered list it was given).
- **D-05:** `CitationMapper.toCitations(documents)` runs **inside the advisor chain** (from the `DocumentPostProcessor` or `QueryAugmenter`). Results (`List<CitationResponse>`) are written to Spring AI advisor context; `ChatService` reads from advisor context post-call to build `ChatAnswerResponse`. Supports the ≥60% `doAnswer` LOC shrink.
- **D-06:** `AnswerComposer` signature/behavior — **Claude's discretion** during planning. Default: unchanged (receives text + `List<CitationResponse>` as today, just sourced from advisor context instead of `ChatService` locals). Byte-for-byte output parity must hold either way.

### Prompt Caching — DEFERRED OUT OF PHASE 9
- **D-07:** Prompt caching is **explicitly removed from Phase 9 scope**. `NoOpPromptCacheAdvisor` (P8 placeholder) stays no-op. ROADMAP Phase 9 success criterion 4 (`cache_control`, `cached_tokens > 0` IT, provider-family detection) is **dropped for P9** — deviation is intentional and user-approved on 2026-04-18, same pattern as P7 D-01 (feature-flag infra drop). `CACHE-01` requirement rolls forward to a later phase. All prompt-caching design (RestClient interceptor, cache-boundary placement, Anthropic family detection, integration test) → Deferred Ideas.

### Empty-Context & Refusal
- **D-08:** `ContextualQueryAugmenter.allowEmptyContext(true)`. When retrieval returns zero docs, `QueryAugmenter` still produces an augmented prompt (empty citations block); model replies; `GroundingGuardOutputAdvisor` (P8) detects zero-citation + non-chitchat intent and rewrites to `AnswerComposer.composeRefusal()` output. Single refusal source of truth — consistent with P8 D-06 (refusal template owned by GroundingGuard).
- **D-09:** Refusal text stays **byte-for-byte identical to v1.0**. `AnswerComposer.composeRefusal()` wording, disclaimer, and suggested-next-steps list (read from active parameter set via `AnswerCompositionPolicy`) are unchanged. No wording tightening in P9.

### Validation Advisor
- **D-10:** `StructuredOutputValidationAdvisor` replaces `NoOpValidationAdvisor` with `maxRepeatAttempts = 1` (single retry on parse failure). Catches rare `BeanOutputConverter` mismatches from prompt-instruction-mode providers (P8 D-03a). Closes the P8 deferred item.

### Migration Granularity
- **D-11:** **Two incremental PRs**:
  - **PR1** — `RetrievalAugmentationAdvisor` + `VectorStoreDocumentRetriever` + custom `QueryAugmenter` + `DocumentPostProcessor` (citation numbering) + `FILTER_EXPRESSION` wiring + `CitationMapper` move into advisor context + removal of `vectorStore.similaritySearch` from `ChatService`. **20-query Vietnamese regression suite + refusal-parity + two-turn memory test must pass** before PR1 merges (closes P8 deferred regression items).
  - **PR2** — `StructuredOutputValidationAdvisor` swap (NoOp → real, `maxRepeatAttempts=1`).
  - `NoOpPromptCacheAdvisor` stays as-is in both PRs (D-07).

### Carry-forward from Phase 7 / Phase 8
- **D-12:** No feature flags, no `@ConfigurationProperties` runtime knobs. Rollback = `git revert`. Inherits P7 D-01, P8 D-06.
- **D-13:** Advisor chain order stays `GuardIn → Memory → RAG → [NoOpCache] → Validation → GuardOut` exactly as wired in P8 D-04. P9 swaps implementations in place without re-ordering.
- **D-14:** `MessageChatMemoryAdvisor` stays attached via `defaultAdvisors(...)` on the `ChatClient` builder (P8 D-08).
- **D-15:** Spring AI test patterns sourced from Context7 `/spring-projects/spring-ai` — no custom perf/baseline harness, no WireMock (P7 D-07, P8 D-05a).

### Claude's Discretion
- Exact `QueryAugmenter` prompt template wording (Vietnamese; must preserve existing `ChatPromptFactory` instruction about `[Nguồn n]` nhãn trích dẫn nội dòng).
- Whether `CitationPostProcessor` is implemented via Spring AI's `DocumentPostProcessor` interface or as a custom post-retrieval step inside the custom `QueryAugmenter`.
- Advisor-context key names for passing `List<CitationResponse>` back to `ChatService`.
- Exact `order` numeric value for `RetrievalAugmentationAdvisor` and `StructuredOutputValidationAdvisor` (must respect the P8-documented chain order).
- `AnswerComposer` signature shape after the shrink (D-06).
- Whether `ChatPromptFactory` survives P9 or is fully absorbed into the custom `QueryAugmenter` (likely absorbed; confirm during planning).
- Deletion of `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` (inherited P7/P8 deferred cleanup; fair game now that RAG owns the prompt).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & requirements
- `.planning/ROADMAP.md` §Phase 9 — 5 success criteria. **SC 4 (prompt caching) is explicitly dropped for P9 per D-07**; the other 4 are authoritative for P9 UAT.
- `.planning/REQUIREMENTS.md` — `ARCH-01`, `ARCH-05` apply to P9; `CACHE-01` is deferred with D-07.
- `.planning/PROJECT.md` — trust-tier + source-grounded non-negotiables.

### Prior phase context (locked decisions carried forward)
- `.planning/phases/07-chat-latency-foundation/07-CONTEXT.md` — D-01 (no feature flags), D-07 (Spring AI test patterns via Context7).
- `.planning/phases/08-structured-output-groundingguardadvisor/08-CONTEXT.md` — D-04 (advisor chain order — P9 swaps implementations in place), D-05 (live IT pattern), D-06 (no runtime knobs), D-08 (`defaultAdvisors`), `NoOpRetrievalAdvisor` / `NoOpValidationAdvisor` / `NoOpPromptCacheAdvisor` placeholders.
- `.planning/STATE.md` — P8 deferred regression items (`twentyQueryRegressionSuiteAtLeast95Percent`, `refusalRateWithinTenPercentOfPhase7Baseline`) must close in P9 PR1 per D-11.

### Research artifacts
- `.planning/research/ARCHITECTURE.md` — advisor chain target shape.
- `.planning/research/SUMMARY.md` — v1.1 migration scope.
- `.planning/research/PITFALLS.md` — §Pitfall 1 (advisor order vs chat memory — P9 regression target), §Pitfall 2 (`ContextualQueryAugmenter.allowEmptyContext(true)` default trap — addressed by D-08), §Pitfall 3 (keyword-gate removal without replacement — already addressed by P8, verify P9 preserves layered defense).

### External references (Spring AI — Context7 `/spring-projects/spring-ai`)
- §api/retrieval-augmented-generation — `RetrievalAugmentationAdvisor`, `VectorStoreDocumentRetriever`, `ContextualQueryAugmenter`, `DocumentPostProcessor` interfaces.
- §api/advisors — `BaseAdvisor` ordering; `StructuredOutputValidationAdvisor` with `maxRepeatAttempts`.
- §api/chatclient — `defaultAdvisors(...)` wiring (unchanged from P8).
- §api/testing — `BasicEvaluationTest`, `RelevancyEvaluator`, `FactCheckingEvaluator` (reused from P8 D-05).

### Source files touched by P9

**PR1 (RAG + Citation + Filter):**
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` — remove `vectorStore.similaritySearch` call; shrink `doAnswer` ≥60% LOC; read `CitationResponse` list from advisor context instead of building inline.
- `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java` — swap `NoOpRetrievalAdvisor` for real `RetrievalAugmentationAdvisor`; wire `VectorStoreDocumentRetriever` with `Supplier<SearchRequest>` + custom `QueryAugmenter` + `DocumentPostProcessor`.
- `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpRetrievalAdvisor.java` — **delete** once real advisor lands.
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java` — likely **absorbed into custom `QueryAugmenter`** (see Claude's Discretion); `SYSTEM_CONTEXT_FALLBACK` cleanup candidate.
- `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java` — unchanged; consumed by the new `Supplier<SearchRequest>`.
- `src/main/java/com/vn/traffic/chatbot/chat/citation/CitationMapper.java` — moves into advisor flow (likely called from the new `DocumentPostProcessor` or custom `QueryAugmenter`).
- **New files:**
  - `src/main/java/com/vn/traffic/chatbot/chat/advisor/LegalQueryAugmenter.java` — custom `QueryAugmenter` with Vietnamese `[Nguồn n]` numbering template.
  - `src/main/java/com/vn/traffic/chatbot/chat/advisor/CitationPostProcessor.java` — `DocumentPostProcessor` that assigns `labelNumber` metadata + builds `CitationResponse` list.

**PR2 (Validation):**
- `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java` — swap `NoOpValidationAdvisor` for real `StructuredOutputValidationAdvisor(maxRepeatAttempts=1)`.
- `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpValidationAdvisor.java` — **delete** once real advisor lands.

**Tests (PR1, extend P8 `VietnameseRegressionIT`):**
- `src/test/java/com/vn/traffic/chatbot/chat/regression/VietnameseRegressionIT.java` — re-run with real retrieval; assert ≥95% pass and refusal-parity within 10% of P7 baseline (closes P8 deferred items).
- New: `src/test/java/com/vn/traffic/chatbot/chat/advisor/LegalQueryAugmenterTest.java` — unit test for `[Nguồn n]` numbering byte-for-byte parity vs `ChatPromptFactory` golden output.
- New: `src/test/java/com/vn/traffic/chatbot/chat/regression/CitationFormatRegressionIT.java` — v1.0 fixture replay asserting `ChatAnswerResponse` JSON contract unchanged.

**Not touched in P9:**
- `NoOpPromptCacheAdvisor.java` — stays (D-07).
- Any frontend file (`frontend/**`) — byte-for-byte API compat.
- Liquibase / chunk metadata schema (D-01, no `trust_tier` literal backfill).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RetrievalPolicy` (`src/main/java/.../retrieval/RetrievalPolicy.java`) — `RETRIEVAL_FILTER` constant + `buildRequest(query, topK)` method. Consumed directly by the new `Supplier<SearchRequest>` feeding `VectorStoreDocumentRetriever` (D-02).
- `CitationMapper.toCitations(documents)` — labels docs "Nguồn 1…n" in retrieval order. Relocates into the advisor flow (D-05) but logic is unchanged.
- `AnswerComposer.composeRefusal()` / `composeChitchat()` — refusal template read from `AnswerCompositionPolicy` (active parameter set). Stays verbatim (D-09).
- `ChatPromptFactory` — current home of the Vietnamese `[Nguồn n]` instruction block (line 56) and `formatCitation` helper. Likely absorbed into the custom `QueryAugmenter` (Claude's Discretion).
- P8 advisor chain scaffolding (`ChatClientConfig.defaultAdvisors(...)`) — swap targets already in place.
- P8 `VietnameseRegressionIT` / `StructuredOutputMatrixIT` / `IntentClassifierIT` (`@Tag("live")`) — re-usable test harness; extend rather than recreate.

### Established Patterns
- `AiParameterSet` runtime reload — `RetrievalPolicy` already reads `topK`/`similarityThreshold` per-request via `paramProvider`; the `Supplier<SearchRequest>` pattern slots in naturally.
- Chunk metadata schema: `approvalState`, `trusted`, `active` (string-valued in pgvector metadata). `trust_tier` literal lives only on `KbSource` / `SourceTrustPolicy` DB tables — **not on chunks** (informs D-01).
- `@SpringBootTest` + `@Tag("live")` + `@DisabledIfEnvironmentVariable` pattern from P8 — regression IT extends this.

### Integration Points
- `ChatService.doAnswer` collapses significantly: intent dispatch (P8) + `.entity(LegalAnswerDraft.class)` call + advisor-context reads for citations/sources + `AnswerComposer` invocation. All retrieval + prompt construction moves into the advisor chain.
- `ChatClientConfig` is the single wiring point — swap `NoOpRetrievalAdvisor` → real one + `NoOpValidationAdvisor` → real one.
- No DB migrations (D-01). No frontend changes (byte-for-byte API compat per ROADMAP SC 2).
- Embedding cache (P7 D-14, `CachingEmbeddingModel @Primary`) transparently benefits the new retriever — zero coordination needed.

</code_context>

<specifics>
## Specific Ideas

- `[Nguồn n]` label format is fixed: `"Nguồn " + labelNumber` (see `CitationMapper.toCitation`). The custom `QueryAugmenter` must emit this exact string; regression test (`CitationFormatRegressionIT`) gates byte-for-byte parity.
- Model-facing instruction "Mọi nhận định có căn cứ phải gắn nhãn trích dẫn nội dòng đúng định dạng [Nguồn n]; tuyệt đối không tự tạo nhãn ngoài danh sách được cung cấp." (currently `ChatPromptFactory` line 56) must survive the move into the custom `QueryAugmenter`.
- PR1 is the risk carrier — the 20-query Vietnamese regression suite (deferred from P8) closes here. Do NOT land PR1 unless it passes.
- PR2 (`StructuredOutputValidationAdvisor`) is low risk; can land same-day as PR1 if regression holds.
- `allowEmptyContext(true)` is non-negotiable (Pitfall 2) — without it, Spring AI's default generic refusal will leak into production, breaking v1.0 refusal wording parity.
- When planning tests for `RetrievalAugmentationAdvisor` / `StructuredOutputValidationAdvisor` / `ContextualQueryAugmenter`, read Spring AI §api/retrieval-augmented-generation via Context7 first (inherits P7 D-07 / P8 D-11).

</specifics>

<deferred>
## Deferred Ideas

### Deferred out of Phase 9 (per D-07)
- **`PromptCachingAdvisor`** — real implementation (currently `NoOpPromptCacheAdvisor`). All work deferred to a later phase:
  - OpenRouter `cache_control: {"type":"ephemeral","ttl":"1h"}` injection via RestClient interceptor (Pitfall 4 mitigation path).
  - Provider-family detection (Anthropic-only activation): model-id prefix check vs. new `ModelEntry` flag vs. `ProviderFamily` enum — all three options noted, pick during that phase.
  - Cache-boundary placement (system block only vs. system + instructions).
  - `cached_tokens > 0` integration test (`@Tag("live")`, Anthropic-only, OpenRouter `/generation` endpoint verification).
- **`CACHE-01` requirement** — rolls to a later phase (to be scheduled).
- **ROADMAP Phase 9 success criterion 4** — explicitly dropped for P9; will be reinstated when `PromptCachingAdvisor` work is picked up.

### Deferred from earlier phases (still deferred after P9)
- **Chunk-metadata `trust_tier` literal backfill** (per D-01) — only revisit if business needs differentiate PRIMARY vs SECONDARY at retrieval time beyond the current `trusted` boolean.
- **Prompt split into cacheable system + dynamic user halves** (P7 deferred) — depends on `PromptCachingAdvisor` work above.
- **Runtime-configurable GroundingGuard knobs** (`@ConfigurationProperties` for threshold, refusal template) — rejected (P8 D-06).
- **DB-backed model capability flags / admin UI** — rejected (P8 D-03).
- **Recorded fixture replay / WireMock** for regression tests — rejected (P7 D-05a, P8 D-05a).

</deferred>

---

*Phase: 09-modular-rag-prompt-caching*
*Context gathered: 2026-04-18*
