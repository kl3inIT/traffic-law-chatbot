# Phase 8: Structured Output + GroundingGuardAdvisor - Context

**Gathered:** 2026-04-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace Phase-7-era stopgaps with Spring AI primitives:

1. **Structured output**: `ChatService` produces `LegalAnswerDraft` via `.entity(LegalAnswerDraft.class)` backed by `BeanOutputConverter`. The legacy `parseDraft` / `extractJson` / `fallbackDraft` / markdown-fence paths are deleted.
2. **Per-model capability flag**: `supportsStructuredOutput` governs native vs. prompt-instruction mode across all 8 cataloged OpenRouter models (Anthropic, OpenAI, Google, DeepSeek).
3. **Advisor pair**: `GroundingGuardInputAdvisor` + `GroundingGuardOutputAdvisor` own refusal policy, configurable/disable-able without editing `ChatService`.
4. **LLM intent classifier**: `.entity(IntentDecision.class)` short-circuits chitchat past retrieval. All hardcoded Vietnamese keyword matching (`containsAnyLegalCitation`, `CHITCHAT_PATTERN`, `isGreetingOrChitchat`) is deleted from production grounding paths (ARCH-03).
5. **Default advisors**: `MessageChatMemoryAdvisor` is attached via `defaultAdvisors(...)` on the `ChatClient` builder (not per-call).
6. **Regression guarantee**: 20-query Vietnamese regression suite ≥95%, refusal rate within 10% of Phase-7 baseline, two-turn memory integration test passes.

**Not in scope** (deferred to Phase 9): `RetrievalAugmentationAdvisor`, `CitationPostProcessor`, `FILTER_EXPRESSION` trust-tier gating, `PromptCachingAdvisor` with OpenRouter `cache_control`. Phase 8 wires these as no-op advisor placeholders so Phase 9 swaps implementations without re-ordering.

</domain>

<decisions>
## Implementation Decisions

### Intent Classifier
- **D-01:** Intent classifier reuses the **same OpenRouter chat model** as the main answer call — 1 extra roundtrip per request. No dedicated cheap side model, no separate `ModelEntry`, no separate API key. Consistent with single-catalog philosophy; revisit only if p95 latency regresses outside Phase-7's 2.5s ceiling.
- **D-02:** On classifier error/timeout/malformed response → **assume `LEGAL`**. The request falls through to the grounding gate, which is safer than dropping to chitchat (dropping would mask legal queries during classifier outages).
- **D-09:** `IntentDecision` record shape: `enum Intent { CHITCHAT, LEGAL, OFF_TOPIC }` + optional `double confidence`. `OFF_TOPIC` (e.g., "tell me about the stock market") short-circuits to a canned off-topic refusal template (distinct from chitchat). `CHITCHAT` short-circuits to `AnswerComposer.composeChitchat()`. `LEGAL` proceeds through grounding gate + retrieval.

### Model Capability Flag
- **D-03:** `supportsStructuredOutput` = **5th arg on `ModelEntry` record** (`String id, String displayName, String baseUrl, String apiKey, boolean supportsStructuredOutput`). Hardcoded per model in `application-dev.yaml` / `application-prod.yaml`, code-reviewed. No DB-backed capability table, no admin UI — consistent with P7 D-01 (solo-dev, no feature-flag infra).
- **D-03a:** When `supportsStructuredOutput=false`, the `BeanOutputConverter` path uses prompt-instruction mode (schema embedded in system prompt). When `true`, use `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` via the OpenRouter `response_format: json_schema` channel. Cross-model matrix test covers both branches.

### Advisor Chain Wiring
- **D-04:** Wire the **full Phase-9 target order** in Phase 8 with no-op placeholders for RAG/Cache/Validation: `GuardIn → Memory → [RAG no-op] → [Cache no-op] → [Validation no-op] → GuardOut`. Placeholders are `BaseAdvisor` implementations that pass through unchanged with `order` assigned per the P9 spec. Phase 9 swaps implementations in place without re-ordering.
- **D-08:** `MessageChatMemoryAdvisor` attached via `defaultAdvisors(...)` on the `ChatClient` builder — per ROADMAP success criterion 3 and reference pattern in Spring AI `§api/chatclient`.

### Testing Approach (Spring AI Reference Patterns)
- **D-05:** 20-query Vietnamese regression suite extends **`BasicEvaluationTest`** from `spring-ai-test` module (reference: Context7 `/spring-projects/spring-ai` §api/testing). Uses `RelevancyEvaluator` for answer quality and `FactCheckingEvaluator` for grounding/no-hallucinated-citations assertions. Lives in `@SpringBootTest` tagged `@Tag("live")` + `@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")` so CI skips without a key; dev runs manually with real OpenRouter.
- **D-05a:** No WireMock / recorded-fixture replay. No custom perf/baseline harness. Consistent with P7 D-06/D-07.
- **D-05b:** Cross-model structured-output matrix test iterates all 8 `ModelEntry` rows and asserts `.entity(LegalAnswerDraft.class)` returns a non-null record without schema 400s — tagged `@Tag("live")` alongside the Vietnamese suite.
- **D-05c:** Two-turn chat-memory integration test (per ROADMAP success criterion 5) lives in the same test class, uses the full advisor chain, asserts role alternation + context carryover.

### GroundingGuard Configuration Surface
- **D-06:** **No `@ConfigurationProperties`, no runtime knobs.** Similarity threshold stays hardcoded in `RetrievalPolicy`. Refusal template is a constant in `GroundingGuardOutputAdvisor`. Enable/disable via code change + `git revert`. Intent classifier system prompt is a constant in `IntentClassifier`. Consistent with P7 D-01 (no feature-flag layer, solo dev, rollback = revert).

### Deletions (ARCH-03 cleanup)
- **D-07:** On P8 execute, **delete** from `ChatService.java`:
  - `CHITCHAT_PATTERN` regex (line 58)
  - `isGreetingOrChitchat(String)` helper
  - `containsAnyLegalCitation(List<CitationResponse>)` + the Vietnamese keyword list it consumes
  - `parseDraft(...)` / `extractJson(...)` / `fallbackDraft(...)` methods
  - The `hasLegalCitation` boolean and its branches
  
  These are Phase-7-era stopgaps replaced by `IntentClassifier` + `.entity()` + advisor pair. Zero keyword-list references must remain in production grounding paths.

### Carry-forward from Phase 7
- **D-10:** No feature flags (`app.chat.v11.*` etc.). Rollback = `git revert`. Inherits P7 D-01.
- **D-11:** Spring AI test patterns sourced from Context7 `/spring-projects/spring-ai` — not improvised. Inherits P7 D-07.

### Claude's Discretion
- Exact `BeanOutputConverter` wiring in `ChatService` (fluent `.entity()` path vs. explicit builder).
- Internal structure of each advisor (`BaseAdvisor` vs. `CallAdvisor` vs. `StreamAdvisor`) — pick per Spring AI §api/advisors reference.
- JSON-schema instruction text injected when `supportsStructuredOutput=false`.
- Exact `order` values for advisor chain slots (must be consistent with the documented P9 order, but numeric gaps are Claude's choice).
- Intent classifier prompt wording (Vietnamese) — iterate until 20-query regression passes.
- Whether `IntentDecision` is a record or sealed-interface-with-variants (record preferred for `.entity()` ergonomics).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & requirements
- `.planning/ROADMAP.md` §Phase 8 — 5 success criteria (authoritative for P8 UAT).
- `.planning/REQUIREMENTS.md` — ARCH-02, ARCH-03, ARCH-04 exact wording.
- `.planning/PROJECT.md` — trust-tier + source-grounded non-negotiables.

### Prior phase context (locked decisions carried forward)
- `.planning/phases/07-chat-latency-foundation/07-CONTEXT.md` — D-01 (no feature flags), D-02 (P7 chitchat stopgap, P8 must delete), D-07 (Spring AI test patterns via Context7).

### Research artifacts
- `.planning/research/ARCHITECTURE.md` — advisor chain target shape.
- `.planning/research/SUMMARY.md` — v1.1 migration scope, advisor-chain rationale.
- `.planning/research/PITFALLS.md` — review for advisor-chain and structured-output pitfalls before planning.

### External references (Spring AI — Context7 `/spring-projects/spring-ai`)
- §api/structured-output-converter — `BeanOutputConverter`, `.entity(Class)` fluent API.
- §api/chatclient — `defaultAdvisors(...)` builder pattern; `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`.
- §api/advisors — `BaseAdvisor` / `CallAdvisor` interfaces, advisor ordering.
- §api/advisors-recursive — `StructuredOutputValidationAdvisor` pattern (reference for P8 validation placeholder shape).
- §api/testing — `BasicEvaluationTest`, `RelevancyEvaluator`, `FactCheckingEvaluator` — **mandatory reference for D-05**.
- §api/chat/prompt-engineering-patterns — system prompting with structured entity mapping (reference for `IntentDecision` classifier prompt).

### Source files touched by P8
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` — rewrite `doAnswer`; delete `parseDraft` / `extractJson` / `fallbackDraft` / `containsAnyLegalCitation` / `CHITCHAT_PATTERN` / `isGreetingOrChitchat`.
- `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java` — add `defaultAdvisors(...)` with full P9 chain ordering (no-op placeholders for RAG/Cache/Validation).
- `src/main/java/com/vn/traffic/chatbot/common/config/AiModelProperties.java` — `ModelEntry` record gains 5th arg `boolean supportsStructuredOutput`.
- `src/main/java/com/vn/traffic/chatbot/chat/domain/LegalAnswerDraft.java` — confirm `@JsonClassDescription` and field-level `@JsonPropertyDescription` for `BeanOutputConverter` schema generation.
- `src/main/resources/application-dev.yaml`, `application-prod.yaml` — add `supportsStructuredOutput: true|false` per `ai.models.[i]` entry.
- **New files:**
  - `chat/advisor/GroundingGuardInputAdvisor.java`
  - `chat/advisor/GroundingGuardOutputAdvisor.java`
  - `chat/advisor/placeholder/NoOpRetrievalAdvisor.java` (P9 slot)
  - `chat/advisor/placeholder/NoOpPromptCacheAdvisor.java` (P9 slot)
  - `chat/advisor/placeholder/NoOpValidationAdvisor.java` (P9 slot)
  - `chat/intent/IntentClassifier.java`
  - `chat/intent/IntentDecision.java` (record with enum + confidence)
- **New tests:**
  - `chat/advisor/GroundingGuardAdvisorTest.java` (structural, unit)
  - `chat/intent/IntentClassifierIT.java` (`@Tag("live")`)
  - `chat/regression/VietnameseRegressionIT.java` extends `BasicEvaluationTest` (`@Tag("live")`)
  - `chat/regression/StructuredOutputMatrixIT.java` (`@Tag("live")`, iterates 8 models)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AnswerComposer.composeChitchat()` — already exists from P7 D-02; `IntentClassifier` reuses it on `CHITCHAT` path.
- `AnswerComposer` — unchanged; advisor pair is orthogonal to answer composition.
- `LegalAnswerDraft` — slim 8-field schema from P7 D-03; ready for `.entity()` without further changes.
- `AiModelProperties` with reload support — extending `ModelEntry` record to 5 args requires updating all constructor call-sites (flagged P7 commit `f72440b chore(tests): update ModelEntry instantiations to 4-arg signature` shows recent movement on this record).
- `RetrievalPolicy` — houses the similarity threshold that advisors read (no move required in P8).

### Established Patterns
- `@SpringBootTest` + Micrometer metrics wired — regression suite plugs in without new infra.
- `chatLogExecutor` + `@Async` from P7 — unchanged.
- OpenRouter client setup (`ChatClientConfig`) — advisor chain attaches here via `defaultAdvisors(...)`.

### Integration Points
- `ChatService.doAnswer` collapses significantly — intent classification + grounding decisions move into advisors; `doAnswer` orchestrates prompt → `.entity()` → response mapping.
- `ChatClientConfig` becomes the single wiring point for the advisor chain.
- `AiModelProperties` feeds the per-model capability flag into the structured-output mode switch.
- No DB migrations — P8 is code-only.

</code_context>

<specifics>
## Specific Ideas

- Advisor pair naming follows Spring AI convention: `GroundingGuardInputAdvisor` (pre-retrieval, can short-circuit to refusal) + `GroundingGuardOutputAdvisor` (post-model, validates grounding claims).
- Intent classifier prompt is Vietnamese-first; English fallback only if the classifier itself fails to parse Vietnamese reliably (unlikely for current OpenRouter catalog).
- Full P9 advisor order is documented in `ROADMAP.md` §Phase 9 success criterion 5 as `GuardIn → Memory → RAG → Cache → Validation → GuardOut` — P8 mirrors this exactly with no-op placeholders for RAG/Cache/Validation.
- Spring AI `spring-ai-test` module dependency must be added in the P8 plan (test scope only).
- `supportsStructuredOutput` default for un-annotated `ModelEntry` entries = `false` (prompt-instruction mode is the universal fallback).

</specifics>

<deferred>
## Deferred Ideas

- **`RetrievalAugmentationAdvisor`** swap for `NoOpRetrievalAdvisor` — Phase 9.
- **`CitationPostProcessor`** — Phase 9 (citations in P8 still flow through the existing `ChatService` path, just routed through the advisor chain).
- **`FILTER_EXPRESSION` trust-tier gating** (`trust_tier IN (PRIMARY, SECONDARY)`) — Phase 9.
- **`PromptCachingAdvisor`** with OpenRouter `cache_control` — Phase 9; the `NoOpPromptCacheAdvisor` placeholder holds the slot.
- **`StructuredOutputValidationAdvisor`** with `maxRepeatAttempts` — Phase 9; the `NoOpValidationAdvisor` placeholder holds the slot.
- **Runtime-configurable GroundingGuard knobs** (threshold, refusal template, intent prompt via `@ConfigurationProperties`) — rejected for P8 per D-06; revisit if/when project moves to production with live traffic.
- **Dedicated cheap intent-classifier model** (Haiku 4.5 / Flash) — rejected for P8 per D-01; revisit if p95 regresses.
- **DB-backed `supportsStructuredOutput`** / admin UI for capability flags — rejected for P8 per D-03; revisit alongside Phase 10 api-key admin if needed.
- **Prompt split into cacheable system + dynamic user** — Phase 9 (inherited from P7 deferred).
- **Deletion of `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK`** — Phase 9 cleanup (inherited from P7 deferred).
- **Recorded fixture replay / WireMock** for regression tests — rejected per D-05a.

</deferred>

---

*Phase: 08-structured-output-groundingguardadvisor*
*Context gathered: 2026-04-18*
