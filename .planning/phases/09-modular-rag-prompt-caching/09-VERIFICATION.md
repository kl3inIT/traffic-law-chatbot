---
phase: 09-modular-rag-prompt-caching
verified: 2026-04-18T00:00:00Z
status: human_needed
score: 9/13 must-haves verified (automated); 4 require live regression run
overrides_applied: 0
re_verification:
  previous_status: none
  previous_score: none
  gaps_closed: []
  gaps_remaining: []
  regressions: []
deferred:
  - truth: "Static system block is marked with cache_control (type=ephemeral, ttl=1h); cached_tokens > 0 IT passes on Anthropic-family model; advisor safely skips for non-Anthropic"
    addressed_in: "Post-Phase-9 (unscheduled — no explicit later phase claims CACHE-01)"
    evidence: "09-CONTEXT.md D-07 explicitly drops ROADMAP SC 4 from Phase 9 with user approval 2026-04-18; CACHE-01 rolls forward. NoOpPromptCacheAdvisor preserved byte-identical. ROADMAP Phase 10 scope (ADMIN-*) does NOT cover CACHE-01 — deferral has no assigned phase."
human_verification:
  - test: "Run ./gradlew liveTest --tests \"*VietnameseRegressionIT\" with OPENROUTER_API_KEY set"
    expected: "≥19/20 queries pass (≥95%); refusal rate within 10% absolute of P7 baseline; two-turn memory assertion GREEN"
    why_human: "Live LLM-backed IT; requires OPENROUTER_API_KEY. Executor environment lacks the key per 09-02-SUMMARY. Closes P8 deferred items + validates SC 5."
  - test: "Run ./gradlew liveTest --tests \"*CitationFormatRegressionIT\" with OPENROUTER_API_KEY set"
    expected: "v1.0 fixture replay asserts ChatAnswerResponse JSON byte-for-byte parity for [Nguồn n], citations[], sources[]"
    why_human: "Live LLM call against real advisor chain. Validates ROADMAP SC 2 (ARCH-05) byte-for-byte JSON contract."
  - test: "Run ./gradlew liveTest --tests \"*EmptyContextRefusalIT\" with OPENROUTER_API_KEY set"
    expected: "Nonsense query returns exactly AnswerComposer.composeRefusal() wording; citations/sources empty; no Spring AI generic refusal leaks through"
    why_human: "Live retrieval roundtrip validates allowEmptyContext=true + GroundingGuardOutputAdvisor ownership (T-9-02, ROADMAP SC 3)."
  - test: "Manual 50-sample post-deploy log review (chat_log JOIN citations) confirming zero citations to non-legal / untrusted sources"
    expected: "Zero MANUAL_REVIEW / untrusted chunk IDs surfacing as citations across 50 sampled answers"
    why_human: "D-03 explicitly defers this to manual SQL + eyeball review post-deploy; no automated harness by design (solo-dev simplicity)."
---

# Phase 9: Modular RAG + Prompt Caching Verification Report

**Phase Goal:** User-visible answer shape is identical to v1.0 (same `[Nguồn n]` citations, same `ChatAnswerResponse` JSON) but produced through the Spring AI modular RAG advisor chain, with the static system block marked for OpenRouter prompt caching.
**Verified:** 2026-04-18
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (merged ROADMAP SCs + PLAN frontmatter)

| #   | Truth | Status | Evidence |
| --- | ----- | ------ | -------- |
| 1   | User chat flow runs through RetrievalAugmentationAdvisor; ChatService contains zero `vectorStore.similaritySearch` | VERIFIED | `ChatClientConfig.java:78-88` wires real `RetrievalAugmentationAdvisor` bean at order +300; grep of `ChatService.java` for `vectorStore|similaritySearch|ChatPromptFactory|chatPromptFactory|buildPrompt|citationMapper.toCitations` returns zero matches |
| 2   | ChatService.doAnswer ≥60% LOC reduction vs P8 baseline | VERIFIED | 09-01-SUMMARY reports 134→29 LOC (78% reduction); actual `doAnswer` method body (lines 71-99) ~28 lines confirms |
| 3   | `[Nguồn n]` labels + `ChatAnswerResponse`/sources[]/citations[] JSON byte-for-byte identical to v1.0 fixtures | UNCERTAIN | Unit proof present (`LegalQueryAugmenterTest`, `CitationMapper.INLINE_LABEL_PREFIX` single source); live byte-for-byte IT (`CitationFormatRegressionIT`) requires OPENROUTER_API_KEY — deferred to human |
| 4   | FILTER_EXPRESSION enforces RetrievalPolicy.RETRIEVAL_FILTER literal on every retrieval | VERIFIED | `RetrievalPolicy.java:14` defines the literal; `PolicyAwareDocumentRetriever.retrieve()` calls `retrievalPolicy.buildRequest(text, topK)` per call, which at `RetrievalPolicy.java:41` calls `.filterExpression(RETRIEVAL_FILTER)` — safety-critical filter applied every request |
| 5   | Empty-context retrieval produces augmented prompt (allowEmptyContext=true); GroundingGuardOutputAdvisor owns refusal verbatim | VERIFIED (unit) / UNCERTAIN (live) | `LegalQueryAugmenter.java:58` sets `.allowEmptyContext(true)`; `EmptyContextRefusalIT` exists but requires live key |
| 6   | 20-query Vietnamese regression ≥95%, refusal rate within 10% of P7, two-turn memory passes | UNCERTAIN | `VietnameseRegressionIT.java` exists with two-turn coverage; live run deferred to human (no OPENROUTER_API_KEY in CI) |
| 7   | NoOpPromptCacheAdvisor preserved unchanged in advisor chain | VERIFIED | File present at `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpPromptCacheAdvisor.java`; wired at +500 in `ChatClientConfig.java:160` (D-07) |
| 8   | ChatClientConfig wires StructuredOutputValidationAdvisor (outputType=LegalAnswerDraft.class, maxRepeatAttempts=1) | VERIFIED | `ChatClientConfig.java:97-104` bean exactly matches |
| 9   | NoOpValidationAdvisor.java deleted | VERIFIED | File absent; `grep -rn NoOpValidationAdvisor src/main` returns only historical comments |
| 10  | NoOpRetrievalAdvisor.java deleted | VERIFIED | File absent |
| 11  | ChatPromptFactory.java deleted | VERIFIED | File absent from `src/main/java/com/vn/traffic/chatbot/chat/service/`; no source references |
| 12  | Advisor chain order: GuardIn(+100) → Memory(+200) → RAG(+300) → CitationStash(+310) → NoOpPromptCache(+500) → Validation(+1000) → GuardOut(-100) | VERIFIED | `ChatClientConfig.java:155-163` defaultAdvisors listing + `:171-185` order-log assertions |
| 13  | Validation advisor retry semantics (maxRepeatAttempts=1 → exactly 2 chain calls on bad→good) | VERIFIED | `StructuredOutputValidationAdvisorIT.java` has `verify(chain, times(2)).nextCall(...)` assertion per 09-02-SUMMARY (`eba118f`, `2f13c9f`) |

**Score:** 9/13 VERIFIED programmatically; 4 UNCERTAIN requiring live OPENROUTER_API_KEY regression run.

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | CACHE-01 / ROADMAP Phase 9 SC 4 — `cache_control` + `cached_tokens > 0` IT + provider-family detection | Post-Phase-9 (unscheduled) | 09-CONTEXT.md D-07 explicitly drops SC 4 with user approval 2026-04-18; CACHE-01 rolls forward. NoOpPromptCacheAdvisor preserved byte-identical. **Warning:** No later phase currently claims CACHE-01 — escalation noted below. |

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `src/main/java/com/vn/traffic/chatbot/chat/advisor/LegalQueryAugmenter.java` | Custom QueryAugmenter, allowEmptyContext=true | VERIFIED | 128 LOC, implements `QueryAugmenter`, Vietnamese [Nguồn n] template + documentFormatter |
| `src/main/java/com/vn/traffic/chatbot/chat/advisor/CitationPostProcessor.java` | DocumentPostProcessor assigning labelNumber 1..n | VERIFIED | Implements `DocumentPostProcessor`; immutable-metadata via HashMap copy |
| `src/main/java/com/vn/traffic/chatbot/chat/advisor/CitationStashAdvisor.java` | CallAdvisor publishing citations/sources to context | VERIFIED | Order +310; reads `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT` from context AND metadata (defensive) |
| `src/main/java/com/vn/traffic/chatbot/chat/advisor/PolicyAwareDocumentRetriever.java` | Per-call DocumentRetriever honoring RetrievalPolicy | VERIFIED (deviation from plan — documented in 09-01-SUMMARY Deviation #2) |
| `src/main/java/com/vn/traffic/chatbot/chat/advisor/context/ChatAdvisorContextKeys.java` | Context-key constants | VERIFIED |
| `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java` | Real RAA + Validation advisor bean-graph | VERIFIED |
| `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpRetrievalAdvisor.java` | DELETED | VERIFIED (absent) |
| `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpValidationAdvisor.java` | DELETED | VERIFIED (absent) |
| `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java` | DELETED | VERIFIED (absent) |
| `src/main/java/com/vn/traffic/chatbot/chat/advisor/placeholder/NoOpPromptCacheAdvisor.java` | PRESERVED UNCHANGED (D-07) | VERIFIED (present) |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `ChatClientConfig.retrievalAugmentationAdvisor` | `RetrievalPolicy.RETRIEVAL_FILTER` | `PolicyAwareDocumentRetriever` → `retrievalPolicy.buildRequest(q, topK)` → `.filterExpression(RETRIEVAL_FILTER)` | WIRED | Deviation from plan (used custom retriever instead of VectorStoreDocumentRetriever.Builder — Pitfall 5); filter literal still enforced per call |
| `CitationPostProcessor` | `Document.metadata["labelNumber"]` | HashMap copy + `.put(LABEL_NUMBER_METADATA, i+1)` | WIRED | |
| `CitationStashAdvisor` | `ChatClientResponse.context()[CITATIONS_KEY, SOURCES_KEY]` | `chain.nextCall(req)` → read `DOCUMENT_CONTEXT` → `CitationMapper.toCitations/toSources` → `resp.context().put(...)` | WIRED | |
| `ChatService.doAnswer` | `ChatClientResponse.context()[CITATIONS_KEY]` | `readList(resp, ChatAdvisorContextKeys.CITATIONS_KEY)` | WIRED | `ChatService.java:91-92` |
| `ChatClientConfig.validationAdvisor` | `LegalAnswerDraft.class` | `.outputType(LegalAnswerDraft.class)` | WIRED | `ChatClientConfig.java:100` |
| `ChatClientConfig.validationAdvisor` | `HIGHEST_PRECEDENCE + 1000` | `.advisorOrder(Ordered.HIGHEST_PRECEDENCE + 1000)` | WIRED | `ChatClientConfig.java:102` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `ChatService.doAnswer` → `citations` | `resp.context().get(CITATIONS_KEY)` | `CitationStashAdvisor.adviseCall` (populated post-RAG) | Yes, citations flow from real `VectorStore.similaritySearch` via `PolicyAwareDocumentRetriever` → `CitationPostProcessor` → `CitationMapper.toCitations` | FLOWING |
| `ChatService.doAnswer` → `sources` | `resp.context().get(SOURCES_KEY)` | same advisor; `CitationMapper.toSources(citations)` | Yes | FLOWING |
| `LegalQueryAugmenter.augment` | `documents` param | `PolicyAwareDocumentRetriever.retrieve` → real `VectorStore` | Yes (per-request policy-built SearchRequest) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Non-live test suite green | `./gradlew test` (reported in 09-02-SUMMARY) | 205/205 pass | PASS |
| ChatService identifier deletion | Grep 6 forbidden identifiers in `ChatService.java` | 0 matches | PASS |
| Deleted-file absence | `ls` on 3 deleted paths | all not-found | PASS |
| Advisor chain 7-slot wiring | Read `ChatClientConfig.chatClientMap` defaultAdvisors | 7 advisors in documented order | PASS |
| Live regression (20-query VN) | `./gradlew liveTest --tests "*VietnameseRegressionIT"` | Requires OPENROUTER_API_KEY | SKIP — human |
| Byte-for-byte fixture replay | `./gradlew liveTest --tests "*CitationFormatRegressionIT"` | Requires OPENROUTER_API_KEY | SKIP — human |
| Empty-context refusal | `./gradlew liveTest --tests "*EmptyContextRefusalIT"` | Requires OPENROUTER_API_KEY | SKIP — human |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| ARCH-01 | 09-01, 09-02 | Spring AI idiomatic advisor chain — modular RAG via RAA + custom CallAdvisor components; no manual orchestration in ChatService | SATISFIED | Real RAA wired; `ChatService.doAnswer` shrunk 78%; zero forbidden identifiers; `ChatServiceShrinkArchTest` GREEN; only 1 NoOp remaining (prompt-cache, deferred) |
| ARCH-05 | 09-01 | Citation mapping + `[Nguồn n]` preserved; ChatAnswerResponse JSON contract byte-for-byte; no frontend changes | SATISFIED (code) / NEEDS HUMAN (live fixture) | Unit parity proven (`LegalQueryAugmenterTest`, INLINE_LABEL_PREFIX single source); live `CitationFormatRegressionIT` awaits OPENROUTER_API_KEY |
| CACHE-01 | 09-01, 09-02 plan frontmatter does NOT claim CACHE-01 | Static system block `cache_control` + `cached_tokens > 0` IT | **DEFERRED** | 09-CONTEXT D-07 explicitly drops from Phase 9 with user approval (2026-04-18); NoOpPromptCacheAdvisor preserved. **Escalation: no later phase currently claims CACHE-01** (ROADMAP Phase 10 is admin scope only). |

**Requirements phase-map check:** REQUIREMENTS.md maps CACHE-01 to Phase 9, but the phase explicitly and with user approval deferred it (D-07). The phase plans omit CACHE-01 from `requirements:` frontmatter consistent with the deferral. Not orphaned inside the phase, but **CACHE-01 has no successor phase assigned** — user decision needed.

### Anti-Patterns Found

None found. Scan of the 7 created/modified advisor files returned zero TODO/FIXME/placeholder markers, zero empty handlers, and zero hardcoded-empty-return stubs that reach rendering/response (the `List.of()` defaults in `CitationStashAdvisor.extractDocuments` and `ChatService.readList` are defensive fallbacks after attempting real retrieval, not stubs).

### Human Verification Required

1. **20-query Vietnamese regression (VietnameseRegressionIT)** — requires `OPENROUTER_API_KEY`
   - Run: `./gradlew liveTest --tests "*VietnameseRegressionIT"`
   - Expected: ≥19/20 pass (≥95%); refusal rate within 10% of P7 baseline; two-turn memory GREEN
   - Why human: Live LLM-backed IT; executor environment has no key. Closes P8 deferred items and validates ROADMAP SC 5.

2. **CitationFormatRegressionIT byte-for-byte JSON parity** — requires `OPENROUTER_API_KEY`
   - Run: `./gradlew liveTest --tests "*CitationFormatRegressionIT"`
   - Expected: `ChatAnswerResponse` JSON for canonical queries matches v1.0 fixtures byte-for-byte (citations[], sources[], `[Nguồn n]` inline labels)
   - Why human: Validates ROADMAP SC 2 / ARCH-05 contract preservation.

3. **EmptyContextRefusalIT verbatim refusal** — requires `OPENROUTER_API_KEY`
   - Run: `./gradlew liveTest --tests "*EmptyContextRefusalIT"`
   - Expected: Nonsense-query response equals `AnswerComposer.composeRefusal()` exactly; citations/sources empty
   - Why human: Validates allowEmptyContext=true + GroundingGuardOutputAdvisor refusal ownership (T-9-02, ROADMAP SC 3).

4. **Post-deploy 50-sample trust-tier audit** (D-03, ROADMAP SC 3 tail)
   - Action: Manual SQL join of `chat_log` × `citations` across 50 sampled legal answers
   - Expected: Zero citations to MANUAL_REVIEW / untrusted chunks
   - Why human: D-03 explicitly decided against automated harness (solo-dev simplicity).

### Gaps Summary

No code-level gaps. Phase 9 implementation is structurally complete and idiomatic:

- Real `RetrievalAugmentationAdvisor` + `StructuredOutputValidationAdvisor` wired; both NoOp placeholders deleted.
- `ChatService.doAnswer` shrunk 78% (134 → 29 LOC) — exceeds ≥60% target.
- `ChatPromptFactory` absorbed into `LegalQueryAugmenter` and deleted.
- `RETRIEVAL_FILTER` enforced per call via `PolicyAwareDocumentRetriever` (Pitfall 5 defense — well-documented deviation).
- `NoOpPromptCacheAdvisor` preserved byte-identical per D-07.
- 205/205 non-live tests pass.

**Status is `human_needed` (not `passed`) because:**
- 4 live LLM-backed regressions cannot run without `OPENROUTER_API_KEY`; these validate ROADMAP SC 2, SC 3, SC 5 and P8-deferred regression closure. 09-02-SUMMARY explicitly flags this: "To be re-run post-merge as part of Phase-9 closeout."

**Escalation note for CACHE-01:** The requirement is documented as deferred (D-07, user-approved 2026-04-18), but no successor phase in ROADMAP currently claims it. Phase 10 scope is ADMIN-* only. Milestone v1.1 will not fully close without a follow-up plan or an explicit roll-to-v1.2 decision. Recommend the user either (a) schedule a late-v1.1 "CACHE-01 close-out" phase, or (b) move CACHE-01 to the v1.2 deferred list in REQUIREMENTS.md.

### Live-Run UAT Gaps (amended 2026-04-18)

| Gap | Disposition | Evidence |
|-----|-------------|----------|
| G1 (extra top-level fields `confidence`/`intent`/`note`) | CLOSED under Plan 09-03 | Prompt-side fix in `LegalQueryAugmenter.buildPromptTemplate` (commit `45baf54`); DB `ai_parameter_set.system_prompt` audit 2026-04-18 — clean, no extra-field instructions. |
| G2 (list fields returned as bare strings) | CLOSED under Plan 09-03 | Same commit adds `phải là mảng JSON` rule + one-shot example enforcing JSON arrays for `legalBasis`, `penalties`, `requiredDocuments`, `procedureSteps`, `nextSteps`. |
| G3 (retry budget insufficient) | CLOSED indirectly | Closure of G1/G2 means first-attempt responses validate; `maxRepeatAttempts=1` remains per D-10. |
| G4 (`SPRING_AI_CHAT_MEMORY.conversation_id` VARCHAR(36) overflow) | CLOSED under Plan 09-04 | `ChatService.buildMemoryConversationId` now returns bare 36-char `UUID.randomUUID().toString()`; regression test `ChatServiceEphemeralConversationIdTest` guards against re-introduction; `PITFALLS.md` Pitfall 10 updated. |
| G5 (IntentClassifier misroute via shared advisor chain) | DEFERRED to Plan 09-05 | Root cause confirmed in `ChatClientConfig.java:154-164`: every `chatClientMap` entry carries `validationAdvisor` bound to `LegalAnswerDraft`; `IntentClassifier.classify(...).entity(IntentDecision.class)` fails `BeanOutputConverter` → `catch` falls back to `LEGAL` per D-02. Fix scope ~30–50 LOC across `ChatClientConfig` + `IntentClassifier` (sibling `intentChatClientMap` without default advisors); exceeds Plan 09-04 Task 2 scope budget (>1 file). Functional correctness preserved via D-02 fallback; user impact is latency/cost on chitchat paths, not refusal behaviour. |
| G6 (`Phase7Baseline.REFUSAL_RATE_PERCENT = NaN`) | DEFERRED under Plan 09-04 — Phase7Baseline.REFUSAL_RATE_PERCENT stays NaN pending Plan 08-04 Task 1 backfill. `refusalRateWithinTenPercentOfPhase7Baseline` is expected-RED in the live re-run and is NOT a Phase-9 blocker. See `Phase7Baseline.java` Javadoc "Plan 09-04" TODO for the code-side cross-reference. |
| G7 (retrieval/grounding quality — 16/20 VN regression `fact=false`) | OPEN — NEW carry-over identified in live re-run 2026-04-18 | Live `twentyQueryRegressionSuiteAtLeast95Percent` returned 4/20. Failure distribution: 13× `relevancy=true fact=false` (LLM answers with retrieved context but fact-check judge rejects), 1× `relevancy=false fact=true` (Q11), 2× `relevancy=false fact=false` (Q15, Q18). Orthogonal to modular-RAG refactor (ARCH-01/ARCH-05 wiring is correct) — likely root causes: (a) retrieval quality (topK=5, similarityThreshold=0.25 too loose/too strict), (b) judge prompt over-strict vs real legal wording, (c) KB coverage gaps for specific Q-statements. Requires dedicated investigation plan (tentative 09-06). NOT a Phase-9 code-correctness blocker but blocks SC 5 (≥95% pass rate). |

### Live Run Results (2026-04-18)

| Test | Result | Notes |
|------|--------|-------|
| `twoTurnConversationMemoryWorks` | GREEN | G4 VARCHAR(36) fix validated live |
| `CitationFormatRegressionIT` | GREEN | ARCH-05 byte-for-byte JSON parity confirmed |
| `EmptyContextRefusalIT` | GREEN | T-9-02 / SC 3 verbatim refusal confirmed |
| `refusalRateWithinTenPercentOfPhase7Baseline` | RED-as-expected | NaN baseline; G6 deferred; NOT a blocker |
| `twentyQueryRegressionSuiteAtLeast95Percent` | 4/20 | New gap G7 — retrieval/grounding quality, not modular-RAG wiring |

---

_Verified: 2026-04-18_
_Verifier: Claude (gsd-verifier)_
