# Feature Research — v1.1 Chat Performance & Spring AI Modular RAG

**Domain:** Vietnamese traffic-law RAG chatbot — performance and architecture migration milestone
**Researched:** 2026-04-17
**Confidence:** HIGH (grounded in v1.0 codebase reading + Spring AI modular RAG docs)

## Scope Note

This is a **subsequent-milestone** research doc. v1.0 features (Q&A, citations, multi-turn, admin pages, model dropdown) are shipped and NOT revisited here. The six v1.1 features are already named in the milestone brief; the job of this doc is to decide per feature which capabilities are table stakes, which are differentiators, and which are explicit non-goals, plus to pin down the user-visible behavior and dependencies.

> The v1.0 feature landscape previously captured in this file has been superseded. See `.planning/PROJECT.md` "Validated" requirements section for the shipped v1.0 feature list.

---

## Feature 1 — Chat Latency Quick Wins

**Goal:** Cut p95 under 2.5s for common lookups without architectural changes.

### Table Stakes

| Capability | Why Expected | Complexity | Notes |
|---|---|---|---|
| Async chat log persistence | Chat log write must not block the HTTP response; today `chatLogService.save(...)` runs inline at the end of `doAnswer` | SMALL | Move to `@Async` or `TaskExecutor`; fire-and-forget with error log on failure. Depends on existing `ChatLogService`. |
| Slim JSON schema for model output | Current schema forces 12 keys (`conclusion`, `answer`, `uncertaintyNotice`, `legalBasis`, `penalties`, `requiredDocuments`, `procedureSteps`, `nextSteps`, `scenarioFacts`, `scenarioRule`, `scenarioOutcome`, `scenarioActions`) even for a one-line greeting — wastes completion tokens | SMALL | Collapse to ~4 required keys; scenario fields only required when in case-analysis mode. Coordinates with Feature 4 (BeanOutputConverter). |
| Prompt trim | `ChatPromptFactory.buildPrompt` emits ~12 instruction lines of JSON schema boilerplate; most are redundant with `BeanOutputConverter` format instructions | SMALL | Delete lines that will be replaced by Feature 4. Keep grounding + citation rules. |
| Loosen grounding gate | Current `containsAnyLegalCitation` Vietnamese keyword list (line 254-268 of ChatService) causes false refusals on greetings and valid questions whose retrieved chunks don't contain the literal word "nghị định" etc. | SMALL | Remove keyword check entirely; rely on document count + similarity score threshold. This directly satisfies PERF-02 and ARCH-03. |
| User-visible behavior unchanged for grounded answers | Same answer quality, citation format, refusal message — only faster | n/a | Regression tests must assert identical output shape for shipped Q&A fixtures. |

### Differentiators

| Capability | Value | Complexity | Notes |
|---|---|---|---|
| Streaming response (SSE) | Perceived latency drop — user sees tokens in <500ms | MEDIUM | `client.prompt().stream()` is supported by Spring AI; but structured JSON output (Feature 4) streams as partial JSON, requires careful frontend handling. Recommended to defer until after Feature 4 ships. |
| Per-request timing breakdown in response headers | `X-Retrieval-Ms`, `X-LLM-Ms`, `X-Compose-Ms` for debugging/admin | SMALL | Useful for quality ops (v1.2); low risk. |

### Anti-Features

| Capability | Why Requested | Why Problematic | Alternative |
|---|---|---|---|
| Parallel retrieval + LLM warmup | "Fire LLM call before retrieval finishes" | LLM needs retrieved chunks in the prompt; only saves time if retrieval is slow (pgvector search is already <50ms on current corpus) | Measure first; optimize the actual bottleneck (LLM call = 80%+ of current latency). |
| Aggressive retrieval top-K reduction | "Retrieve only 2 docs to save LLM tokens" | Hurts grounding recall; legal answers often need 3-5 chunks for penalty + procedure + document | Keep `top-k=5`; trim via prompt, not retrieval. |
| Switch default model to a smaller local model | "Run llama-3 locally for speed" | Breaks OpenRouter single-gateway decision (2026-04-17); Vietnamese legal accuracy drops sharply on <8B params | Keep `openai/gpt-4o-mini` default; let heavy models remain opt-in. |
| Remove conversation history entirely | "Skip history to save tokens" | Breaks CHAT-02 (multi-turn) and CASE-03 (clarifying follow-ups) which are shipped v1.0 | Cap history at `MAX_HISTORY_MESSAGES=10` (already done); leave alone. |

### User-Visible Behavior

- Before: "Xin chào" → ~6s refusal ("không tìm thấy đủ căn cứ").
- After: "Xin chào" → <1s friendly chitchat reply from Feature 2, or fast refusal without keyword-gate delay.
- Before: common fine lookup → ~8s.
- After: same lookup → <2.5s p95.

---

## Feature 2 — GroundingGuardAdvisor (Refusal Policy as Advisor)

**Goal:** Move refusal logic out of `ChatService.doAnswer` into a Spring AI `CallAroundAdvisor`. Support a "chitchat mode" that bypasses retrieval for small talk.

### Table Stakes

| Capability | Why Expected | Complexity | Notes |
|---|---|---|---|
| `CallAroundAdvisor` implementation | Spring AI idiomatic; composable with `MessageChatMemoryAdvisor` and the upcoming `RetrievalAugmentationAdvisor` | MEDIUM | Implements `CallAroundAdvisor`/`StreamAroundAdvisor`; injected via `client.prompt().advisors(...)`. Depends on Spring AI 1.0.x advisor API. |
| Classification: chitchat vs legal | Decides whether to run retrieval + refusal gate | MEDIUM | Use lightweight heuristic (message length, punctuation, greeting patterns) OR a cheap LLM classifier call (gpt-4o-mini with 10-token output). NO keyword list — ARCH-03 forbids it. |
| Chitchat path bypass | Greetings/thanks/"bạn là ai" return canned friendly reply without retrieval or JSON schema | SMALL | Return minimal `ChatAnswerResponse` (conclusion only, empty citations). No refusal, no disclaimer. |
| Score-threshold refusal | Refusal decision based on retrieved doc count + `similarityThreshold` — not keyword signals | SMALL | Replaces `containsAnyLegalCitation`. Configurable via parameter set. |
| Refusal message config via parameter set | Already supported (`AnswerCompositionPolicy.getRefusalMessage`); advisor must use this | SMALL | Wire `ActiveParameterSetProvider` into the advisor. |

### Differentiators

| Capability | Value | Complexity | Notes |
|---|---|---|---|
| Admin-tunable classification threshold | Ops can shift chitchat/legal boundary without redeploy | SMALL | Expose as parameter set key `grounding.chitchatThreshold`. |
| Explainability in chat log | Log classification decision + reason ("chitchat: greeting pattern", "legal: retrieval returned 5 docs @ score>0.7") | SMALL | Enhances ADMIN-04. |
| Parameter-set-driven refusal "next steps" | Already in `AnswerCompositionPolicy.getRefusalNextSteps` | n/a | Preserve in the advisor path. |

### Anti-Features

| Capability | Why Requested | Why Problematic | Alternative |
|---|---|---|---|
| Vietnamese legal keyword list (v2) | "Just add more keywords" | Violates ARCH-03; creates an endless whack-a-mole; caused v1.0 false refusals | Score-threshold + LLM classifier. |
| Full LLM call to classify every message | "Use gpt-4o-mini to classify" | Adds 300-800ms to every request, even trivial ones | Cheap regex/heuristic first pass; LLM only when ambiguous. Or skip LLM entirely — trust retrieval count + score. |
| Hard-coded "I am a legal assistant" refusal | "Return a fixed Vietnamese string" | Removes admin tunability already shipped in v1.0 parameter sets | Keep `AnswerCompositionPolicy` integration. |
| Classifier that blocks off-topic legal questions (e.g. civil law) | "Refuse anything not traffic law" | Over-blocks; users ask adjacent questions (insurance, licenses) that are in-scope | Let retrieval/grounding do the filtering — if no traffic-law chunks match, refuse. |

### User-Visible Behavior

- "Xin chào, bạn khỏe không?" → friendly reply ("Xin chào! Tôi là trợ lý pháp luật giao thông...") in <1s, no refusal.
- "Bạn là ai?" → canned self-description, no retrieval.
- "Uống rượu lái xe bị phạt bao nhiêu?" → standard grounded answer.
- Vague question with no retrieval hits → refusal with same message as v1.0.

---

## Feature 3 — RetrievalAugmentationAdvisor + CitationPostProcessor

**Goal:** Replace manual `vectorStore.similaritySearch` + `citationMapper` + prompt assembly with Spring AI's `RetrievalAugmentationAdvisor` and a custom `DocumentPostProcessor` for inline citation labeling.

### Table Stakes

| Capability | Why Expected | Complexity | Notes |
|---|---|---|---|
| `RetrievalAugmentationAdvisor` wiring | Spring AI idiomatic modular RAG (ARCH-01) | MEDIUM | Replaces the `similaritySearch` → `buildPrompt` flow. Config: `VectorStoreDocumentRetriever` with existing `RetrievalPolicy` parameters. |
| Custom `DocumentPostProcessor` for citation labels | Preserves `[Nguồn 1]` inline format shipped in v1.0; advisor default has no such labeling | MEDIUM | Implement `DocumentPostProcessor` that rewrites `Document.metadata` adding `citationLabel`, then templates the augmented prompt with the label. Reuses existing `CitationMapper` logic. |
| `QueryAugmenter` for legal-context template | Preserves the Vietnamese system prompt structure currently in `ChatPromptFactory` | MEDIUM | `ContextualQueryAugmenter` with custom Vietnamese prompt template. |
| Empty-context handler | If retriever returns 0 docs, route to refusal path without LLM call | SMALL | `ContextualQueryAugmenter.allowEmptyContext(false)` + catch `EmptyContextException` → refusal. |
| Trust policy preserved | Existing `RetrievalPolicy.buildRequest` filters by trust tier — must survive the migration | SMALL | Reuse `RetrievalPolicy` inside the `VectorStoreDocumentRetriever` filter. |
| Citation output parity | `ChatAnswerResponse.citations` and `.sources` identical shape pre/post migration | MEDIUM | Regression tests against shipped fixtures. |

### Differentiators

| Capability | Value | Complexity | Notes |
|---|---|---|---|
| Query expansion via `MultiQueryExpander` | Better recall on short/ambiguous legal questions | MEDIUM | Optional — adds one LLM call; measure latency cost before enabling. Flag off by default. |
| Query compression with history | Collapses "điều đó thì sao" follow-ups into standalone queries | MEDIUM | `CompressionQueryTransformer` — useful for multi-turn but adds LLM call. Defer to v1.2 if latency budget tight. |
| Per-request retriever override | Admin can test alternate similarity thresholds on live traffic | SMALL | Parameter set integration — already partly supported. |

### Anti-Features

| Capability | Why Requested | Why Problematic | Alternative |
|---|---|---|---|
| `RewriteQueryTransformer` (LLM-based query rewrite) | "Cleaner queries = better retrieval" | Adds 500-1500ms LLM call per request — directly conflicts with PERF-01 | Rely on the user's phrasing; expand retrieval top-K if recall is low. |
| Re-ranking with cross-encoder | "Better ranking = better answers" | No Vietnamese legal cross-encoder available; adds ML dependency + latency | pgvector cosine + trust-tier filter is sufficient for v1.1. |
| Switching vector store to Weaviate/Pinecone | "Modular RAG deserves a modular store" | PostgreSQL + pgvector is shipped v1.0; migration is weeks of work with no user-visible benefit | Stay on pgvector; revisit only if scale >10M chunks. |
| Abandon `CitationMapper` in favor of Spring AI default source formatting | "Less code" | Spring AI default formats as raw `Document.content` — loses `origin`, `page`, `section`, `excerpt` structure that powers the UI citation panel | Keep `CitationMapper`; invoke from the custom `DocumentPostProcessor`. |

### User-Visible Behavior

- Identical `ChatAnswerResponse` JSON shape pre/post migration.
- Citation labels `[Nguồn 1]`, `[Nguồn 2]`... identical.
- Potentially slightly better answer quality if `ContextualQueryAugmenter` template is tuned; no regression is the hard requirement.

---

## Feature 4 — Structured Output via BeanOutputConverter

**Goal:** Drop the ~60-line manual JSON extraction in `ChatService.parseDraft`/`extractJson` and use Spring AI's `BeanOutputConverter<LegalAnswerDraft>`.

### Table Stakes

| Capability | Why Expected | Complexity | Notes |
|---|---|---|---|
| `BeanOutputConverter<LegalAnswerDraft>` bound to `ChatClient` | Spring AI idiomatic; auto-generates JSON schema instructions in the prompt | SMALL | `client.prompt().user(...).call().entity(LegalAnswerDraft.class)`. |
| `LegalAnswerDraft` record Jackson-compatible | Already a record; already Jackson-parsed — needs schema annotations if converter uses JSON Schema | SMALL | Add `@JsonPropertyDescription` where helpful; verify nullability. |
| Remove `extractJson` and `parseDraft` manual logic | Markdown-code-fence stripping and `{...}` extraction no longer needed — provider-enforced JSON mode handles it | SMALL | Delete ~40 LOC; keep `fallbackDraft` for genuine parse failures. |
| Fallback on parse failure | Models occasionally emit malformed JSON; `fallbackDraft` must still exist | SMALL | Wrap `.entity(...)` call in try/catch → `fallbackDraft`. |
| Schema parity with current `LegalAnswerDraft` | Same 12 fields until Feature 1's slim-schema change lands | n/a | If Feature 1 slims the schema, do it in same PR — don't ship two schema changes. |

### Differentiators

| Capability | Value | Complexity | Notes |
|---|---|---|---|
| Provider-native JSON mode | `response_format: {type: "json_object"}` on OpenAI/gpt-4o-mini guarantees valid JSON | SMALL | Check OpenRouter passthrough; Spring AI's `OpenAiChatOptions.responseFormat` handles this. Eliminates 95% of parse failures. |
| Per-mode schema (chitchat/legal/case) | Different `Bean` per route — chitchat returns `{reply}`, legal returns `LegalAnswerDraft`, case returns extended schema | MEDIUM | Enabled by advisor-based routing from Feature 2. |
| Schema version pinning | `@JsonPropertyOrder` + version field for future migrations | SMALL | Nice-to-have. |

### Anti-Features

| Capability | Why Requested | Why Problematic | Alternative |
|---|---|---|---|
| Custom `StructuredOutputConverter` beyond `BeanOutputConverter` | "We know JSON best" | Reinventing Spring AI's built-in; harder to maintain | Use `BeanOutputConverter`; escalate only if it can't express the schema. |
| XML/YAML output format | "More readable for admins" | No consumer needs it; adds conversion layer | Stay with JSON. |
| Tool calling instead of structured output | "Let the model call a `composeAnswer` tool" | Adds round-trips; structured output is the right primitive here | Tool calling may appear in v1.2 for live-data lookups, not answer shaping. |
| Streaming structured output to frontend | "Show the conclusion as soon as it's generated" | Partial JSON is hard to render safely; fields arrive out-of-order | Defer; use whole-response rendering. Reconsider if Feature 1 streaming ships. |

### User-Visible Behavior

- Users see zero change.
- Chat logs show fewer `parseDraft failed` warnings; fallback responses drop from ~3% of requests to <0.3%.

---

## Feature 5 — Prompt Caching + Embedding Cache

**Goal:** Reduce LLM token cost and latency by caching (a) the static system prompt block on provider side, and (b) embeddings for repeated queries locally.

### Table Stakes

| Capability | Why Expected | Complexity | Notes |
|---|---|---|---|
| OpenRouter `cache_control` on system block | Static system prompt (system context + clarification rules + schema) is identical across requests; Anthropic/OpenAI prompt caching via OpenRouter can save 50-90% of its tokens | MEDIUM | Requires sending `cache_control: {"type": "ephemeral"}` marker. Spring AI OpenAI client needs per-provider extra params plumbing. Verify via Context7 that Spring AI 1.0.x supports extra request fields on OpenAI chat options. |
| Caffeine embedding cache | Query embeddings are deterministic for identical input text; identical queries are common ("mức phạt nồng độ cồn", "phạt vượt đèn đỏ") | SMALL | `Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(24h)`. Wrap `EmbeddingModel` in a caching decorator. Hit rate expected 20-40%. |
| Cache key = normalized question text | Lowercase + trim + collapse whitespace; do NOT include user/session data | SMALL | Avoids cross-user leakage; all queries are non-PII. |
| Cache metrics | Hit/miss counters in Micrometer | SMALL | Needed to validate the cache is actually helping. |
| Config toggle | `app.chat.cache.embedding.enabled`, `app.chat.cache.prompt.enabled` | SMALL | Safe rollback path. |

### Differentiators

| Capability | Value | Complexity | Notes |
|---|---|---|---|
| Answer cache (not just embedding) | Fully cached identical questions return in <50ms | MEDIUM | Risky — must invalidate on knowledge base change; also coarser matching misses near-duplicate phrasing. Defer until embedding cache proves out. |
| Semantic cache via embedding similarity | "Near-duplicate questions hit cache" | HIGH | Requires a second vector index of prior Q→A pairs; complex invalidation; defer to v1.2. |
| Cache warming from chat log top-100 queries | Pre-populate cache at startup | SMALL | Nice-to-have; only matters after cold restarts. |
| Redis-backed cache | Survives restarts, shared across instances | MEDIUM | v1.1 is single-instance; Caffeine in-process is sufficient. Plan for Redis if/when horizontal scaling lands. |

### Anti-Features

| Capability | Why Requested | Why Problematic | Alternative |
|---|---|---|---|
| Cache full chat responses keyed by question | "Identical questions = identical answers" | Invalidation nightmare: knowledge base updates, parameter set changes, model switches all require flushes; answer personalization breaks | Embedding cache only in v1.1. |
| Persist cache to disk | "Don't lose warm cache on restart" | Adds a serialization layer; in-process Caffeine loss is acceptable for cost savings | Redis if persistence matters. |
| Share prompt cache across different models | "One cache to rule them all" | Anthropic/OpenAI prompt caches are provider-scoped; keys mismatch | Cache marker is per-model inherently — don't fight it. |
| Cache embedding by document ID (ingestion-side) | Already done by pgvector | No new work needed | n/a |

### User-Visible Behavior

- Repeat queries (same user or different) return ~200-500ms faster on embedding cache hit.
- With prompt caching, first LLM call in a session pays full price; subsequent calls pay ~10-50% of prompt token cost. User notices token bill drop (admin-visible), not latency significantly.
- Cache misses behave identically to today.

---

## Feature 6 — User-Managed API Key Admin

**Goal:** Admin UI to add/update/revoke provider API keys at runtime, encrypted at rest, displayed masked, with audit log.

### Table Stakes

| Capability | Why Expected | Complexity | Notes |
|---|---|---|---|
| CRUD API for per-provider keys | Keys identified by `(provider, modelId)` composite; current `AiModelProperties.ModelEntry` has an `apiKey` field — must become dynamic, not YAML-only | MEDIUM | New table `ai_provider_credential` (id, providerId, modelId, encryptedKey, createdAt, updatedAt, createdBy, status). |
| Encryption at rest | PII/secret handling; keys must never appear in plaintext in DB dump | MEDIUM | AES-GCM with a key-encryption-key from env (`app.security.kek`). Options: JDK `Cipher`, Spring Security `TextEncryptor`, or jasypt. Check Context7 for Spring Security 6.x `TextEncryptor` current API. |
| Masked display | UI shows `sk-...abcd` (first 3 + last 4); never returns full key over API after creation | SMALL | `GET` endpoints return masked; only the server-side key resolver sees plaintext. |
| Audit log | Who created/rotated/revoked a key, when, from what IP | SMALL | New table `ai_credential_audit` or reuse an existing audit infrastructure. |
| Runtime key resolution replaces YAML lookup | `chatClientMap` today is bootstrapped from YAML `app.ai.models[].apiKey`; must refresh when admin rotates a key | MEDIUM | Introduce `ChatClientFactory` that resolves keys via `ProviderCredentialService` on each `resolveClient(modelId)` call — or on credential-change events. |
| "Test connection" button | Admin clicks to verify key works before saving | SMALL | Lightweight chat or models-list API call; returns success/error. |
| Revoke/disable | Status flag; disabled key → provider disappears from user model dropdown | SMALL | Existing dropdown logic already filters on availability. |

### Differentiators

| Capability | Value | Complexity | Notes |
|---|---|---|---|
| Key rotation without dropped requests | In-flight requests finish with old key; new requests use new key | SMALL | Naturally solved by per-request resolution. |
| Multiple keys per provider (primary/fallback) | Failover if primary returns 401/429 | MEDIUM | Nice-to-have; defer to v1.2. |
| Per-key rate-limit/budget tracking | "Key X used $45 this month" | MEDIUM | Requires tracking usage rows; valuable for ops, not launch-blocking. |
| Key expiry + reminder | Notify admin when key is N days old | SMALL | Optional. |
| Env-var override | Dev/staging can still use env/YAML when DB empty | SMALL | Keep `AiModelProperties.apiKey` as fallback when no DB credential exists. |

### Anti-Features

| Capability | Why Requested | Why Problematic | Alternative |
|---|---|---|---|
| Show plaintext key to admin after creation | "Admin forgot the key, let them copy it again" | Any read path to plaintext expands the blast radius of a compromised admin account | One-time display on creation only; rotate if lost. |
| Per-user API keys (end users bring their own key) | "Let users use their own OpenAI quota" | Multiplies security surface; requires user-role model (already Out of Scope per PROJECT.md) | Admin-managed only in v1.1. |
| Store keys in Git secret manager | "Use GitHub/GitLab secrets" | Decouples from runtime; rotation requires redeploy; defeats the whole feature | DB + encryption. |
| Client-side decryption in admin UI | "End-to-end encryption" | No plaintext client flow needed — keys are typed once on create | Server-side encryption only. |
| Webhook on key change | "Notify other systems" | Over-engineering for single-instance v1.1 | Audit log is sufficient. |
| Custom crypto | "Roll our own cipher" | Always a mistake | Use JDK AES-GCM or Spring Security `TextEncryptor`. |

### User-Visible Behavior

**Admin side:**
- New admin page "API Keys": table of providers with masked keys, Add/Rotate/Revoke/Test buttons.
- Audit log sub-tab shows history.

**End-user side:**
- Invisible when keys are healthy.
- Model dropdown omits providers whose keys are missing/revoked/invalid.

---

## Feature Dependencies

```
Feature 1 (Quick Wins)
    ├── Loosen grounding gate ──enables──> Feature 2 (GroundingGuardAdvisor)
    └── Slim JSON schema ─────coordinates─> Feature 4 (BeanOutputConverter)

Feature 2 (GroundingGuardAdvisor) ──composes-with──> Feature 3 (RetrievalAugmentationAdvisor)

Feature 3 (RetrievalAugmentationAdvisor)
    ├── requires ──> Feature 4 (BeanOutputConverter) for clean advisor-based entity extraction
    └── preserves ──> existing CitationMapper, RetrievalPolicy, trust filtering

Feature 4 (BeanOutputConverter) ──replaces──> manual parseDraft/extractJson

Feature 5 (Caching)
    ├── Prompt cache ──depends-on──> stable system prompt (finalized after Features 1+3 land)
    └── Embedding cache ──independent──> can ship anytime

Feature 6 (API Key Admin)
    ├── requires ──> refactor of AiModelProperties.ModelEntry.apiKey from static YAML to dynamic resolver
    ├── requires ──> new ChatClientFactory replacing static chatClientMap bean
    └── independent of ──> Features 1-5 (pure admin/platform work)
```

### Dependency Notes

- **F1 → F2:** Removing the keyword gate in `ChatService.containsAnyLegalCitation` is the precondition for the advisor to own the grounding decision. Do F1 first or in the same PR.
- **F2 ↔ F3:** Both are advisors on the same `ChatClient.prompt()` chain. Order them `[MessageChatMemoryAdvisor, GroundingGuardAdvisor, RetrievalAugmentationAdvisor]`. The guard must run before retrieval so chitchat skips the vector store.
- **F3 → F4:** The `RetrievalAugmentationAdvisor` integrates most cleanly with `client.prompt().entity(...)` — structured output is the natural output path. Doing F3 without F4 means building a throwaway manual-parse bridge.
- **F4 coordinates with F1 slim schema:** Ship schema slim-down once, in the same PR as the converter migration, to avoid two schema churns.
- **F5 prompt cache:** The system prompt block must be stable — don't ship prompt caching until Features 1+3 have landed and the template is frozen.
- **F6 independent:** Can proceed in parallel. Key coupling: `chatClientMap` bean construction must change from eager-singleton-from-YAML to lazy-resolution-per-request. Plan this refactor as its own deliverable.

---

## MVP Definition (for v1.1)

### Launch With (v1.1)

- [ ] **F1** All four quick wins (async log, slim schema, prompt trim, loosen gate) — unblocks PERF-01 and PERF-02
- [ ] **F2** GroundingGuardAdvisor + chitchat mode without keyword matching — satisfies ARCH-01 + ARCH-03
- [ ] **F3** RetrievalAugmentationAdvisor with CitationPostProcessor preserving `[Nguồn n]` — satisfies ARCH-01
- [ ] **F4** BeanOutputConverter replacing parseDraft — satisfies ARCH-02
- [ ] **F5a** Embedding cache (Caffeine) — easy win
- [ ] **F5b** Prompt caching (OpenRouter cache_control) — satisfies CACHE-01 (verify Spring AI passthrough support via Context7 early)
- [ ] **F6** API key admin CRUD + encryption + masking + audit — satisfies ADMIN-07

### Add After Validation (v1.2)

- [ ] Streaming responses (SSE) — after structured output is stable
- [ ] Query compression / multi-query expansion — if retrieval recall observed to be a problem
- [ ] Multiple keys per provider with failover
- [ ] Per-key budget tracking
- [ ] Redis-backed caches (when horizontal scaling needed)

### Future Consideration (v2+)

- [ ] Semantic answer cache
- [ ] Per-user keys (requires user-role model — explicitly Out of Scope)
- [ ] Tool calling for live data

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---|---|---|---|
| F1 Quick Wins | HIGH (p95 under 2.5s) | LOW | P1 |
| F2 GroundingGuardAdvisor | HIGH (no false refusals on chitchat) | MEDIUM | P1 |
| F3 RetrievalAugmentationAdvisor | MEDIUM (invisible to users, enables future work) | MEDIUM-HIGH | P1 |
| F4 BeanOutputConverter | MEDIUM (fewer fallback responses) | LOW | P1 |
| F5a Embedding Cache | MEDIUM (faster repeats) | LOW | P1 |
| F5b Prompt Cache | MEDIUM (cost + small latency) | MEDIUM | P1 |
| F6 API Key Admin | HIGH (ops autonomy, no redeploys) | MEDIUM-HIGH | P1 |

All seven capabilities above are P1 — they're the explicit scope of v1.1. The matrix is therefore for cost/value framing, not for cutting scope.

---

## Open Questions for Requirement Definer

1. **F1 slim schema shape:** Should the chitchat path use a separate `ChitchatResponse` DTO or a minimal `ChatAnswerResponse` with most fields null? The frontend currently reads 12 fields.
2. **F2 chitchat classifier:** Heuristic-only (no LLM) acceptable, or do we budget one cheap classifier call?
3. **F3 empty-context behavior:** On `allowEmptyContext=false`, does the advisor throw or return a sentinel? Need to confirm with Spring AI 1.0.x API via Context7 at implementation time.
4. **F5b prompt caching:** Confirm via Context7 that Spring AI's OpenAI client exposes a way to inject `cache_control` markers into the request payload (it may require custom `ChatOptions` subclassing).
5. **F6 key-encryption-key (KEK):** Env var, HashiCorp Vault, or AWS KMS for the master key? v1.1 simplest = env var with rotation documented.
6. **F6 YAML `apiKey` field deprecation:** When F6 ships, does YAML remain a fallback, or is DB the single source of truth? Recommended: YAML fallback for local dev only.

---

## Sources

- D:/ai/traffic-law-chatbot/.planning/PROJECT.md (v1.1 Active requirements: PERF-01, PERF-02, ARCH-01/02/03, CACHE-01/02, ADMIN-07)
- D:/ai/traffic-law-chatbot/src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java (current refusal gate, parseDraft, chatLogService.save, chatClientMap resolution)
- D:/ai/traffic-law-chatbot/src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java (current system prompt structure + JSON schema boilerplate)
- D:/ai/traffic-law-chatbot/src/main/java/com/vn/traffic/chatbot/chat/service/AnswerCompositionPolicy.java (refusal message + next-steps already parameter-set-driven)
- D:/ai/traffic-law-chatbot/src/main/java/com/vn/traffic/chatbot/common/config/AiModelProperties.java (current static YAML key binding — refactor target for F6)
- Spring AI modular RAG docs: `RetrievalAugmentationAdvisor`, `DocumentPostProcessor`, `ContextualQueryAugmenter`, `BeanOutputConverter`, `CallAroundAdvisor` (verify current API via Context7 at build time)
- OpenRouter prompt caching: `cache_control` markers (verify current request shape via OpenRouter docs at build time)

---
*Feature research for: v1.1 Chat Performance & Spring AI Modular RAG*
*Researched: 2026-04-17*
