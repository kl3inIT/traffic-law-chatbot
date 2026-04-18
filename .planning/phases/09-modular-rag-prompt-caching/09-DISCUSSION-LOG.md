# Phase 9: Modular RAG + Prompt Caching - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `09-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-04-18
**Phase:** 09-modular-rag-prompt-caching
**Areas discussed:** Retrieval & trust-tier filter, CitationPostProcessor design, Prompt caching mechanics, Empty-context + validation + migration

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| Retrieval & trust-tier filter | FILTER_EXPRESSION shape, similarity threshold source, top-K, 50-log sample | ✓ |
| CitationPostProcessor design | [Nguồn n] preservation, CitationMapper home, AnswerComposer fate | ✓ |
| Prompt caching mechanics | cache_control injection, provider-family detection, cache boundary, integration test | ✓ |
| Empty-context + validation + migration | allowEmptyContext + refusal ownership, validation advisor activation, PR granularity | ✓ |

---

## Retrieval & Trust-Tier Filter

### Q1: FILTER_EXPRESSION literal

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse existing (Recommended) | FILTER_EXPRESSION = RetrievalPolicy.RETRIEVAL_FILTER as-is | ✓ |
| Add trust_tier to metadata | Extend chunk metadata + backfill + Liquibase | |
| Hybrid | Keep existing literal + add trust_tier going forward (no backfill) | |

**User's choice:** Reuse existing — chunk metadata `trusted` boolean already encodes PRIMARY+SECONDARY (trusted=true) vs MANUAL_REVIEW (trusted=false). ROADMAP wording is intent already satisfied.

### Q2: topK + similarityThreshold binding

| Option | Description | Selected |
|--------|-------------|----------|
| Per-request via Supplier (Recommended) | Supplier<SearchRequest> calls RetrievalPolicy.buildRequest on every invocation | ✓ |
| Fixed at bean init | Read once at ChatClientConfig bean init | |
| You decide | Claude picks based on Spring AI API shape | |

**User's choice:** Per-request via Supplier — preserves `AiParameterSet` runtime-reload admin workflow.

### Q3: 50-log post-deploy sample verification

| Option | Description | Selected |
|--------|-------------|----------|
| Manual SQL + eyeball (Recommended) | One-off SQL, 50 rows, eyeball post-deploy | ✓ |
| Automated CheckDef | New CheckDef running periodically via CheckRunner | |
| Integration test on fixture | @Tag('live') test firing 50 fixture queries | |

**User's choice:** Manual SQL + eyeball — solo dev, pre-production; consistent with P7 D-06, P8 D-05a.

---

## CitationPostProcessor Design

### Q1: [Nguồn n] numbering ownership

| Option | Description | Selected |
|--------|-------------|----------|
| Custom QueryAugmenter (Recommended) | QueryAugmenter numbers docs inline; CitationPostProcessor = DocumentPostProcessor pre-augmentation | ✓ |
| Post-process model output | Rewrite model's raw text after the call | |
| Both (numbered augmenter + validator) | QueryAugmenter numbers + PostProcessor validates output | |

**User's choice:** Custom QueryAugmenter.

### Q2: CitationMapper home

| Option | Description | Selected |
|--------|-------------|----------|
| Inside advisor, read back via context (Recommended) | Advisor writes List<CitationResponse> into context; ChatService reads | ✓ |
| Stay in ChatService | ChatService still calls CitationMapper on returned Documents | |
| Into AnswerComposer | AnswerComposer absorbs CitationMapper | |

**User's choice:** Inside advisor, read back via context.

### Q3: AnswerComposer fate

| Option | Description | Selected |
|--------|-------------|----------|
| Unchanged (Recommended) | Same signature, same behavior, just sourced from advisor context | |
| Minor refactor | Takes a richer advisor-result bundle | |
| Leave to Claude | Claude picks during planning | ✓ |

**User's choice:** Leave to Claude — default remains unchanged; planner has flexibility.

---

## Prompt Caching Mechanics — DEFERRED

### Q1–Q4 (cache injection path, scope, family detection, IT shape)

**User's choice on all four questions:** "don't need cache in this phase"

### Confirmation Q

| Option | Description | Selected |
|--------|-------------|----------|
| Confirm — defer caching | P9 scope reduces to modular RAG only; CACHE-01 rolls forward; NoOpPromptCacheAdvisor unchanged; ROADMAP SC 4 dropped for P9 | ✓ |
| Reconsider — keep caching | Keep caching in scope | |

**User's choice:** Confirm — defer caching. Explicit deviation from ROADMAP Phase 9 SC 4, same pattern as P7 D-01 (feature-flag infra drop). Recorded as D-07.

---

## Empty-Context + Validation + Migration

### Q1: Empty-context refusal ownership

| Option | Description | Selected |
|--------|-------------|----------|
| GroundingGuardOutputAdvisor (Recommended) | Detects zero-citation + non-chitchat intent → AnswerComposer refusal | ✓ |
| Custom prompt template in augmenter | Model produces refusal itself | |
| ChatService post-advisor check | ChatService inspects citation count post-advisor | |

**User's choice:** GroundingGuardOutputAdvisor — single refusal source of truth.

### Q2: Refusal wording

| Option | Description | Selected |
|--------|-------------|----------|
| Byte-for-byte identical (Recommended) | AnswerComposer.composeRefusal() verbatim | ✓ |
| Tighten wording | Update for clarity now | |

**User's choice:** Byte-for-byte identical.

### Q3: StructuredOutputValidationAdvisor activation

| Option | Description | Selected |
|--------|-------------|----------|
| Activate, maxRepeatAttempts=1 (Recommended) | Real validator with one retry | ✓ |
| Keep NoOp, defer | Leave placeholder, handle via BeanOutputConverter exceptions | |
| Activate, no retry | Log + fail fast | |

**User's choice:** Activate with maxRepeatAttempts=1.

### Q4: Migration granularity

| Option | Description | Selected |
|--------|-------------|----------|
| Two incremental PRs (Recommended) | PR1 = RAG+Citation+Filter (regression-gated); PR2 = Validation | ✓ |
| One big PR | All active swaps together | |
| Three PRs | One advisor per PR | |

**User's choice:** Two incremental PRs — PR1 gated by 20-query regression + refusal parity + two-turn memory (closes P8 deferred items).

---

## Claude's Discretion

- `QueryAugmenter` prompt template wording (must preserve existing Vietnamese `[Nguồn n]` instruction).
- Whether `CitationPostProcessor` is implemented via `DocumentPostProcessor` interface or inside the custom `QueryAugmenter`.
- Advisor-context key names for passing `List<CitationResponse>` back to `ChatService`.
- Exact `order` numeric values within the P8-documented chain order.
- `AnswerComposer` signature after the `doAnswer` shrink.
- Whether `ChatPromptFactory` survives P9 or is absorbed into custom `QueryAugmenter`.
- Deletion of `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` (inherited P7/P8 deferred).

## Deferred Ideas

See `09-CONTEXT.md` §deferred for full list. Highlights:

- **All prompt-caching work** — `PromptCachingAdvisor`, `cache_control` injection, provider-family detection, cache-boundary placement, `cached_tokens` IT. Rolls to a later phase per D-07.
- **`CACHE-01` requirement** — rolls forward; to be scheduled.
- **ROADMAP Phase 9 success criterion 4** — explicitly dropped for P9; will be reinstated when caching is picked up.
- **Chunk-metadata `trust_tier` literal backfill** — not needed; `trusted` boolean already encodes the tier gate.
