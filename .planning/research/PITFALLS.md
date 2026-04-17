# Pitfalls Research

**Domain:** Spring AI modular RAG migration + user-managed API key admin (v1.0 → v1.1 brownfield)
**Researched:** 2026-04-17
**Confidence:** HIGH for Spring AI advisor + BeanOutputConverter behavior (Context7 + GitHub issues); HIGH for OpenRouter cache_control quirks (official docs + multiple SDK issue threads); MEDIUM for exact Vietnamese-legal grounding regressions (project-specific, inferred from current `ChatService` code).

## Scope Reminder

v1.0 shipped the chatbot with a **manual RAG pipeline** in `ChatService.doAnswer`:
- Similarity search → hardcoded `containsAnyLegalCitation` keyword gate → `ChatPromptFactory.buildPrompt` (monolithic Vietnamese system prompt) → `chatClient.prompt().user(prompt).call()` → manual JSON extraction from markdown-wrapped payload → `LegalAnswerDraft`.
- `MessageChatMemoryAdvisor` is attached **per-call** only when `conversationId` is supplied.
- Chat log save, citation mapping, and refusal path are inline in the service.

v1.1 wants to replace this with Spring AI idiomatic modular RAG (`RetrievalAugmentationAdvisor` + custom `QueryAugmenter` / `DocumentPostProcessor` + `GroundingGuardAdvisor`), switch to `BeanOutputConverter`, add prompt caching via OpenRouter `cache_control`, embedding cache (Caffeine), and user-managed API keys.

Every pitfall below is scoped to that specific transition.

---

## Critical Pitfalls

### Pitfall 1: Advisor order collision breaks ChatMemory after RAG migration

**What goes wrong:**
After migrating to `RetrievalAugmentationAdvisor`, the app throws `conversation roles must alternate user/assistant/user/assistant...` on the second turn, or silently sends retrieved context *before* the system message, causing the model to ignore grounding rules. Both `MessageChatMemoryAdvisor` (today attached ad-hoc in `ChatService.doAnswer` line 151-154) and `RetrievalAugmentationAdvisor` mutate the user message; if their `getOrder()` values are equal or unintentionally swapped, the retrieved context can be appended *after* history rather than merged into the current turn.

**Why it happens:**
- Spring AI advisor ordering is a stack: lower order = runs first on request, last on response. Equal order values produce arbitrary sort order ([Spring AI advisor docs](https://docs.spring.io/spring-ai/reference/api/advisors.html)).
- Known open issue: [spring-projects/spring-ai#4170](https://github.com/spring-projects/spring-ai/issues/4170) — `MessageChatMemoryAdvisor` still reproduces role-alternation errors under 1.0.1 when combined with a system prompt advisor. Regression of #2216.
- In v1.0, the team built the prompt manually, so no advisor chain existed — this class of bug is brand new to v1.1.

**How to avoid:**
- Set explicit orders and comment them: `RetrievalAugmentationAdvisor` early (e.g. `Ordered.HIGHEST_PRECEDENCE + 100`), `MessageChatMemoryAdvisor` after it (`+ 200`), `GroundingGuardAdvisor` last on input / first on output (`Ordered.LOWEST_PRECEDENCE - 100`).
- Attach advisors via `ChatClient.Builder.defaultAdvisors(...)` at bean config time, **not** per-call inside `ChatService.doAnswer`. The per-call `requestSpec.advisors(...)` pattern currently used for memory is fragile and must be retired.
- Integration test: two-turn Vietnamese conversation (`"Vượt đèn đỏ phạt bao nhiêu?"` → `"Còn với xe máy thì sao?"`) asserting the second turn sees both retrieved docs AND prior turn.

**Warning signs:**
- 400 from OpenRouter with `roles must alternate` on turn 2+.
- Second-turn responses lose citations present in turn 1.
- `chat_log.pipeline_log` shows user message text duplicated.

**Phase to address:**
Phase 2 (Modular RAG migration) — add the integration test **before** removing manual prompt build.

---

### Pitfall 2: `ContextualQueryAugmenter` default refuses legitimate legal queries with thin retrieval

**What goes wrong:**
`RetrievalAugmentationAdvisor` defaults to **refusing to answer when context is empty** ([Spring AI RAG docs](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)). Vietnamese legal questions often pull 0 chunks above the `similarityThreshold` for phrasing variations ("xe máy vượt đèn đỏ" vs ingested "phương tiện giao thông cơ giới") — today's manual pipeline refuses correctly with `REFUSED` status. After migration, the advisor may refuse *before* `GroundingGuardAdvisor` can apply the v1.1 "loosened" policy (chitchat mode, PERF-02), regressing both latency *and* UX.

**Why it happens:**
Default `ContextualQueryAugmenter` has `allowEmptyContext=false`. Developers copy-paste the "naive RAG flow" snippet from the docs and ship it.

**How to avoid:**
- Configure `ContextualQueryAugmenter.builder().allowEmptyContext(true).promptTemplate(legalGroundingTemplate).build()` and move the refusal *decision* into `GroundingGuardAdvisor` where it can distinguish greetings ("xin chào") from unanswerable legal queries.
- Keep `groundingStatus` semantics: ensure `GroundingGuardAdvisor` writes `GROUNDED | REFUSED | CHITCHAT` to advisor context so `chat_log` and `AnswerComposer` still work.
- Regression suite: 20 canonical Vietnamese queries that passed v1.0 must still pass v1.1. Gate on ≥ 95% pass rate.

**Warning signs:**
- Refusal rate jumps above the v1.0 baseline (query `SELECT grounding_status, COUNT(*) FROM chat_log ... GROUP BY 1` before/after).
- User-visible refusal text changes from your composed refusal to Spring AI's generic "I cannot answer based on context".

**Phase to address:**
Phase 2 (Modular RAG migration). Block merge until regression suite passes.

---

### Pitfall 3: Removing `containsAnyLegalCitation` gate without replacement regresses grounding quality

**What goes wrong:**
The v1.0 code at `ChatService.java:231-268` refuses when no citation text contains "nghị định | luật | điều | khoản | giao thông | ...". ARCH-03 calls to drop this gate. If it is removed and replaced only by similarity threshold, spurious retrieval hits (e.g. a trusted source mentioning traffic incidentally in an unrelated decree) will be cited with authoritative confidence — a **legal liability** given the project's "source-grounded" promise in PROJECT.md.

**Why it happens:**
Keyword gating is easy to delete; the safety net it provided is invisible until a bad answer ships.

**How to avoid:**
Replace with layered defence, not pure removal:
1. Raise `similarityThreshold` (measure with the existing 20-query regression set).
2. Add document metadata gate in `DocumentPostProcessor`: require `document.metadata["trust_tier"] IN ('PRIMARY','SECONDARY')` AND `document.metadata["doc_type"] IN ('decree','law','circular')`.
3. Optional LLM classifier advisor (one cheap `openai/gpt-4o-mini` call) that tags the query as `LEGAL | CHITCHAT | OFF_TOPIC` and short-circuits.
4. Keep `looksLikeLegalCitation` as a post-retrieval *sanity check* that logs (not refuses) when 0/N citations contain legal markers — monitoring signal, not gate.

**Warning signs:**
- QA run: sample of 50 random `chat_log` entries — any citing a non-legal source in a legal answer is a fail.
- `LEGAL-01` answer-check run rate drops below v1.0 baseline.

**Phase to address:**
Phase 2 (Modular RAG migration). Do NOT remove the keyword gate in the same PR that introduces score-threshold tuning — split into two PRs so regressions are attributable.

---

### Pitfall 4: OpenRouter `cache_control` silently no-ops on non-Anthropic providers and on the OpenAI-compatible path

**What goes wrong:**
Prompt caching is enabled for the static system block (CACHE-01), latency looks better for Anthropic models in staging, then default traffic (which is `openai/gpt-4o-mini` per PROJECT.md) shows no latency improvement. Worse, when switching models via the v1.0 UI dropdown, the cache key changes and every model switch invalidates it. Even worse: Spring AI's `OpenAiChatModel`-based OpenRouter integration may **silently drop** `cache_control` breakpoints because the OpenAI schema does not know that field.

**Why it happens:**
- OpenAI caches **automatically** (no `cache_control` needed); Anthropic/Gemini require **explicit** per-block `cache_control` breakpoints ([OpenRouter prompt caching](https://openrouter.ai/docs/guides/best-practices/prompt-caching)).
- Known issue: OpenRouter SDKs inheriting OpenAI base silently drop `CachePoint` content items ([pydantic-ai #4392](https://github.com/pydantic/pydantic-ai/issues/4392), [OpenRouter ai-sdk #35](https://github.com/OpenRouterTeam/ai-sdk-provider/issues/35)).
- Reported: "OpenRouter seems to cache the system message and maybe the first message, but never updates what is cached. As the conversation gets longer, OpenRouter becomes an order of magnitude more expensive than Anthropic direct." ([opencode #1245](https://github.com/sst/opencode/issues/1245)).
- Default TTL 5 minutes; low-QPS traffic misses more than it hits.

**How to avoid:**
- Inject `cache_control: {"type":"ephemeral"}` via a `ChatClient` request customizer that writes it into the raw JSON content block (Spring AI typed options likely won't serialize it — use the extra-body / additional-params escape hatch).
- Verify end-to-end with OpenRouter `generation` endpoint: inspect `cache_discount`/`cached_tokens` in usage; add an integration test that asserts `cached_tokens > 0` on the second call.
- Set `"ttl":"1h"` on the static system block for low-QPS traffic.
- Keep cache breakpoints **at most 4** (Anthropic limit) and always at the boundary between static (system+instructions) and dynamic (citations+question).
- Model-switch invalidation: accept it. Do not cache across different provider/model pairs. Document that the cache benefits the *common* Gemini/Anthropic paths, not `gpt-4o-mini` (which auto-caches anyway).

**Warning signs:**
- OpenRouter dashboard shows flat cache-hit rate after deploy.
- p95 latency improves for Claude/Gemini calls but not for the default `gpt-4o-mini` — this is expected; only flag if nothing improves.
- Cost-per-request did not drop despite cache logs saying "hit".

**Phase to address:**
Phase 3 (Chat performance) — integration test must verify real cache hits before declaring CACHE-01 done.

---

### Pitfall 5: Plaintext API key leakage via logs, exception messages, and audit trail

**What goes wrong:**
ADMIN-07 introduces user-managed API keys. A key leaks via one of:
- `log.info("Using model {} with props {}", modelId, props)` where `props.toString()` includes the key.
- `@Slf4j` default exception log prints the Spring `HttpClientErrorException` whose body echoed the `Authorization` header back.
- Liquibase changelog diff or `pg_dump` of `chat_log.pipeline_log` containing the raw key because `ChatService` logged `"Unrecognized modelId '{}'"` plus the full request context (line 207).
- `git commit` including a `.env.local` that a developer used for testing.
- Audit log stores the old value of the key on update (for "diff" purposes) in plaintext.

**Why it happens:**
Spring's default `toString()` for records/lombok `@Data` exposes all fields. Error stacks from HTTP clients (Reactor Netty especially) log request headers at DEBUG. `ObjectMapper` will happily serialize the API key into JSON responses if the DTO is shared between admin-read and admin-detail paths.

**How to avoid:**
- **Never** store the plaintext. Encrypt at rest using AES-256-GCM. Recommended: Spring Security Crypto `Encryptors.stronger(password, salt)` with PBKDF2-derived key; master key from env var `APP_KEK` (never `application.yml`) ([Spring Security Crypto docs](https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html)).
- Entity field `apiKeyCipher` (bytea) + `apiKeyFingerprint` (SHA-256 hex, used for dedup/audit). DTO exposes only `maskedKey` (first 4 + last 4 + fingerprint).
- Add a Jackson `@JsonIgnore` on the cipher field; add a unit test that serializes the entity and asserts the cipher and plaintext are absent.
- Override `toString()` / add Lombok `@ToString.Exclude` on the plaintext field; enforce with ArchUnit rule.
- Register a Logback `PatternLayout` masking converter that regex-strips `sk-[a-zA-Z0-9_-]{20,}` and `Bearer [A-Za-z0-9._-]+` from every log line as defence in depth.
- Audit log entries store `{action, userId, providerId, fingerprintBefore, fingerprintAfter, maskedBefore, maskedAfter}` — **never** the decrypted value.
- Pre-commit hook (`gitleaks` or `trufflehog`) to catch accidental commits.

**Warning signs:**
- grep test in CI: `grep -rE 'sk-[A-Za-z0-9]{20}' logs/ test-results/` returns results.
- Spring Actuator `/env` endpoint visible without auth OR shows any property containing "apiKey".
- `SELECT * FROM audit_log WHERE details::text ~ 'sk-'` returns rows.

**Phase to address:**
Phase 4 (API key admin) — security test is the definition of done; no feature flag rollout without it.

---

### Pitfall 6: BeanOutputConverter strict schema breaks on OpenRouter non-OpenAI providers

**What goes wrong:**
ARCH-02 replaces the hand-rolled JSON extraction in `ChatService.extractJson` (lines 288-311) with `BeanOutputConverter<LegalAnswerDraft>` + OpenAI `ResponseFormat.JSON_SCHEMA`. Works perfectly on `openai/gpt-4o-mini`. As soon as a user picks Claude Sonnet or Gemini via the dropdown, requests 400 with "response_format not supported" or return plain text that fails strict parsing — regressing the multi-model promise of the app.

**Why it happens:**
- OpenAI Structured Outputs (`response_format: json_schema, strict: true`) is an OpenAI-only feature. OpenRouter passes it through only when the backing provider supports it. Anthropic, DeepSeek, older Gemini endpoints handle it differently or not at all.
- `BeanOutputConverter` emits `additionalProperties: false` and marks every `@JsonProperty(required=true)` — any provider not implementing OpenAI's JSON-schema subset will fail or hallucinate.
- Current code tolerates markdown-wrapped JSON (`extractJson` strips ```` ```json ````) — that tolerance disappears with `BeanOutputConverter.convert()` which throws on malformed input.

**How to avoid:**
- Per-model capability matrix in `AiModelProperties`: `supportsStructuredOutput: true|false`. Only set `responseFormat(JSON_SCHEMA, schema)` for providers with `true`.
- Fallback path: when `supportsStructuredOutput=false`, append `FormatProvider.getFormat()` instructions into the prompt AND keep `extractJson` as a safety net wrapped around `BeanOutputConverter.convert()`. Catch `JsonProcessingException` and fall back to `fallbackDraft(...)` (already implemented).
- Test matrix: run the same 10 canonical queries across every model in `app.ai.models` and assert the DTO parses.
- Keep `FAIL_ON_UNKNOWN_PROPERTIES=false` in the converter's ObjectMapper — otherwise providers that return extra fields in the JSON blow up.

**Warning signs:**
- Support tickets of the form "chat fails with Claude but works with GPT".
- `chat_log.grounding_status = 'REFUSED'` spikes on a specific `model_id`.
- `parseDraft failed` warnings clustered by model.

**Phase to address:**
Phase 2 (Modular RAG migration) — ship behind a per-model flag; default flag to `false` and only flip providers after the matrix test passes.

---

### Pitfall 7: Async chat-log save races with request lifecycle and loses the pipeline log

**What goes wrong:**
To hit PERF-01 (p95 < 2.5s), a common quick win is to make `chatLogService.save(...)` async (`@Async` or `CompletableFuture`). Then:
- Log entries are missing when a request errors before the async task fires.
- Pipeline log is captured from a `List<String> logMessages` that is mutated during the request — if mutation continues after `join`, the async task serializes a partial list.
- Transaction propagation breaks: `@Async` creates a new thread with no transaction; the save fails silently.
- Order of save vs. response flush gets inverted and the admin-panel chat log viewer shows stale rows.

**Why it happens:**
The current `logMessages` pattern in `ChatService.doAnswer` (line 87-91) is a captured-by-reference `ArrayList` passed through the whole method. Moving the final `save` off the request thread without copying the list is a classic race.

**How to avoid:**
- Snapshot `List<String> pipelineSnapshot = List.copyOf(logMessages)` **before** handing off to async.
- Use `@Async("chatLogExecutor")` with a dedicated bounded `ThreadPoolTaskExecutor` (e.g. core=2, max=8, queue=1000, `CallerRunsPolicy`) so a log-save backlog never silently drops entries.
- Wrap with `@Transactional(propagation=REQUIRES_NEW)` on the async method.
- Add a latency dashboard that separates **user-observed** latency (controller in → response out) from **log-save** latency. CACHE metrics must come from the former.
- Do **not** make the refusal-path save async — refusals are rare, logging them sync costs nothing, and async-refusal loss would hide regressions from Pitfall 2/3.

**Warning signs:**
- Row-count mismatch: count of HTTP 200s from the chat controller over a window vs. `chat_log` rows in the same window > 0.1% gap.
- Pipeline log rows with trailing "Chat log:" lines missing (indicates the async thread captured the list before the sync code appended them).
- Admin panel reports stale timestamps.

**Phase to address:**
Phase 3 (Chat performance).

---

### Pitfall 8: Caffeine embedding cache becomes stale after ingestion / model swap

**What goes wrong:**
CACHE-02 caches embedding vectors for repeat queries. After an ingestion run that changes the corpus, the cache still maps "vượt đèn đỏ" → the *query* embedding (fine) but downstream retrieval uses the new corpus. That's OK — until the **embedding model** changes (e.g. the user reconfigures OpenRouter to a different provider for `text-embedding-3-small` → `embed-v3`). Cached 1536-d vectors now mismatch the new 1024-d pgvector column, crashing the similarity search with a dimension error, or worse, returning garbage rankings.

**Why it happens:**
Caching keyed on query text only, not on `(query, embeddingModelId, modelVersion)`. Reload of `AiModelProperties` at runtime swaps the model under the cache.

**How to avoid:**
- Cache key = `embeddingModelId + ":" + sha256(query)`.
- Cache value includes vector dimension; assert dimension matches pgvector column before use — if not, evict.
- On `AiModelProperties` reload or on admin "rotate embedding model" action, invalidate the entire embedding cache (`cache.invalidateAll()`).
- TTL (5-15 min) as a belt-and-braces hedge against stale entries.
- **Do not** cache retrieval *results* (document lists). Ingestion, trust-tier flips, or source deactivation must take effect immediately — the project's whole trust story depends on it.

**Warning signs:**
- `VectorStore` throws `org.postgresql.util.PSQLException: expected X dimensions, got Y`.
- Retrieval quality drops cliff-edge after an admin changes a parameter set.
- Cache hit rate is suspiciously high (> 80%) for a diverse traffic shape — probably keying only on a normalised form and collapsing distinct queries.

**Phase to address:**
Phase 3 (Chat performance).

---

### Pitfall 9: Slim JSON schema drops fields the Next.js frontend still renders

**What goes wrong:**
Part of PERF-01 plan is "slim JSON schema" — drop optional fields like `scenarioFacts`, `scenarioRule`, `scenarioOutcome`, `scenarioActions` from the model's response schema to save tokens and latency. Deploy backend, frontend still ships code that reads `answer.scenarioRule` — result: blank sections, `undefined`-dereference, or React error boundary crashes.

**Why it happens:**
`ChatPromptFactory.buildPrompt` (line 60) currently **mandates** all 12 keys in every response. The DTO `ChatAnswerResponse`/`LegalAnswerDraft` is shared implicitly with the frontend via OpenAPI or hand-written TS types. Shrinking the schema requires coordinated frontend change.

**How to avoid:**
- Contract-first: change the TypeScript type first, deploy frontend tolerant of missing fields (render only when present), *then* slim the backend schema.
- Keep the response DTO stable even if the LLM schema is slim — in `AnswerComposer`, fill dropped fields with `null` / `[]` so the API surface is unchanged.
- Add a Playwright smoke test that renders a scenario response and asserts no console errors.
- Feature-flag the slim schema per request (`?slim=1`) to canary.

**Warning signs:**
- Frontend Sentry/console errors spike post-deploy.
- Blank card sections in the chat UI.
- Admin "Answer Checks" fail for scenario-shaped outputs because an expected section is missing.

**Phase to address:**
Phase 3 (Chat performance) — coordinate with Phase 4 (frontend admin changes).

---

## Moderate Pitfalls

### Pitfall 10: `MessageChatMemoryAdvisor` per-call attach in `doAnswer` creates inconsistent behaviour

**What goes wrong:**
Current code attaches `MessageChatMemoryAdvisor` only when `conversationId != null` (line 150-154). In the new modular-RAG advisor chain, the `ChatClient` bean will likely have default advisors. Attaching memory **again** per-call duplicates it → messages are appended twice; or missing it when `conversationId` is null silently bypasses the chain.

**How to avoid:**
Always attach `MessageChatMemoryAdvisor` as a default advisor on the client bean. When `conversationId` is null, pass a sentinel conversation ID (e.g. `"ephemeral-" + UUID`) or use a no-op `ChatMemory` bean. Never call `.advisors(...)` on a per-request basis for memory.

**Phase to address:** Phase 2.

---

### Pitfall 11: Hardcoded D-13 prompt fallback drifts from advisor-managed prompt

**What goes wrong:**
`ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` (line 18-21) and the inlined JSON/citation rules (lines 53-63) are "safety-critical hardcoded fallback" per comment. After modular RAG, these rules move into the `QueryAugmenter` prompt template. Developers update one but not the other; the `ActiveParameterSetProvider.getString("systemPrompt", ...)` path becomes stale.

**How to avoid:**
- Single source of truth: extract the Vietnamese instructions to a `prompts/` resource bundle loaded by both the legacy fallback and the new augmenter template.
- Delete `ChatPromptFactory` once `RetrievalAugmentationAdvisor` is live — do not keep dead code "just in case". Git history is the fallback.
- Architecture test: assert no class outside `com.vn.traffic.chatbot.chat.prompt` references the Vietnamese system-prompt string literals.

**Phase to address:** Phase 2 (end of phase cleanup).

---

### Pitfall 12: OpenRouter `cache_control` + tool calling interaction

**What goes wrong:**
If tool calling is later added (even for simple retrieval helpers), `cache_control` breakpoints on the system block interact with tool definitions — Anthropic requires the cached prefix to be byte-identical including tools list. Any dynamic tool ordering invalidates the cache.

**How to avoid:**
- Sort the tools list deterministically by name before serialization.
- Pin tool definitions to a stable version string; bump only on actual tool-schema change.
- Do not mix `BeanOutputConverter` JSON-schema response format **and** tool calling in the same request — Spring AI may silently prefer one, and Anthropic rejects certain combinations.

**Phase to address:** Phase 2 (design note) — avoid adding tools in v1.1.

---

### Pitfall 13: `ContextualQueryAugmenter` rewrites the user query and breaks Vietnamese diacritics

**What goes wrong:**
Some query-augmenter configurations perform LLM-based query rewriting for retrieval. Rewriting Vietnamese text with an English-biased prompt template strips diacritics or translates ("vượt đèn đỏ" → "run a red light") — pgvector similarity collapses because the corpus is Vietnamese.

**How to avoid:**
- Use a rewriter prompt explicitly in Vietnamese, instructing preservation of diacritics and legal terminology.
- Prefer *expansion* (add synonyms) over *rewriting* (replace wording).
- A/B test: rewriter on vs. off over the 20-query regression set.

**Phase to address:** Phase 2 (if query rewriter is introduced; otherwise skip).

---

### Pitfall 14: Prompt cache leaks content across users via shared system block

**What goes wrong:**
If per-user API keys (ADMIN-07) are used but the prompt cache is keyed by provider + model only, two different tenants share cache entries. With OpenRouter, a cache write by user A could be billed to user B, or worse, their provider routing is sticky and now crosses auth contexts.

**How to avoid:**
- When using user-managed keys, include `userId` (or a hash of the API key fingerprint) in the sticky-routing header (`X-OpenRouter-Session` or equivalent) so cache pools are not shared.
- Audit OpenRouter bill against per-user token counts weekly until confident.

**Phase to address:** Phase 4 (API key admin) — test co-occurs with Phase 3 caching.

---

### Pitfall 15: API key rotation does not invalidate active ChatClient beans

**What goes wrong:**
User rotates their OpenRouter key in admin UI; new key stored encrypted. The `ChatClient` bean cached in `chatClientMap` still holds the old `RestClient` with the old `Authorization` header. Requests fail with 401 until app restart.

**How to avoid:**
- Do not cache `ChatClient` by model ID in a `Map<String, ChatClient>` bean (as today in `ChatService` line 39) when the API key is user-scoped and mutable.
- Build the `ChatClient` per-request from a factory that reads the latest decrypted key, OR evict the cached entry on the rotate event (Spring `ApplicationEvent`).
- Test: rotate key, immediately issue a chat request, assert success without restart.

**Phase to address:** Phase 4.

---

## Minor Pitfalls

### Pitfall 16: Liquibase migration for `api_keys` table ordering

Adding the `api_keys` table after 15 existing migrations — forgetting to include the `trust_tier`-style enum constraint or to add `ON DELETE CASCADE` for `user_id` foreign key. Mitigation: review with the team's existing migration checklist; add an `archived_at` soft-delete column from day 1.

**Phase to address:** Phase 4.

### Pitfall 17: Timezone mismatch in audit log

Using `LocalDateTime.now()` instead of `Instant` or `OffsetDateTime` in the audit table makes post-hoc correlation with OpenRouter billing (UTC) painful. Always `TIMESTAMPTZ` in Postgres.

**Phase to address:** Phase 4.

### Pitfall 18: Masked display off-by-one

Showing `sk-****abcd` by truncating to the last 4 chars — but OpenRouter keys have a prefix (`sk-or-v1-...`); stripping only the last 4 of a 40-char key is reasonable, but showing the *first* 6 plus the last 4 leaks structure if the prefix is predictable. Show last-4 only; prefer a stable random fingerprint for the UI.

**Phase to address:** Phase 4.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Keep `containsAnyLegalCitation` keyword gate post-migration | Zero-risk grounding safety net preserved | Hidden behaviour; new scoring strategies never get measured fairly | Acceptable **only** as a monitoring log, not as a gate. Delete after 2 weeks of clean regression data. |
| Async chat log save without snapshot | Easy `@Async` annotation; p95 drops instantly | Race conditions drop ~1% of logs, impossible to reproduce later | Never acceptable — always snapshot the list first |
| Cache `ChatClient` by modelId (v1.0 pattern) | Simple DI | Breaks user-managed keys, model swap, key rotation | Acceptable only when the API key is global/env-sourced. Must be rebuilt for ADMIN-07. |
| Store plaintext key "temporarily" during migration | Fast to ship | One production leak → catastrophic | Never acceptable — encrypt from commit #1 |
| Use single cache breakpoint at end of system prompt | One breakpoint, simple | Misses the dynamic-static boundary; cache churns | Acceptable in v1.1; revisit if cache hit < 50% |
| Inline Vietnamese prompt strings in Java source | Ships today | Translation drift, hard to A/B test | Acceptable **only** if centralised in one file; forbidden if scattered |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| OpenRouter (Anthropic) | Assuming `cache_control` works like OpenAI auto-cache | Add explicit `{"type":"ephemeral"}` breakpoints at 1-4 boundaries; verify via `cached_tokens` in usage |
| OpenRouter (OpenAI) | Sending `cache_control` and expecting cost drop | OpenAI caches automatically; `cache_control` is ignored — set expectations in dashboards |
| OpenRouter (Bedrock/Vertex backing Anthropic) | Using top-level `cache_control` | Only explicit per-block `cache_control` is supported via these backends |
| Spring AI `ChatMemory` + RAG | Attaching both advisors with default order | Explicit `getOrder()` values, integration test for two-turn conversation |
| pgvector | Changing embedding model without reindexing | Dimension check + mandatory re-embed job; fail loudly on mismatch |
| Jasypt | Storing the encryptor password in `application.yml` | Env var `JASYPT_ENCRYPTOR_PASSWORD` only, never in VCS |
| Next.js response typing | Changing backend DTO fields unilaterally | OpenAPI-generated TS types in CI; fail build on drift |
| Liquibase | Editing an already-applied changeset | Always add a new changeset; use `validCheckSum` only for documented emergencies |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Caching retrieval results (not embeddings) | Trust-tier flips don't take effect | Cache only embeddings, never document lists | Any time content changes |
| Cold advisor chain on first request | Cold-start p95 > 8s | Pre-warm `ChatClient` + at least one no-op call on `ApplicationReadyEvent` | Low-traffic periods / after deploy |
| Unbounded `ChatMemory` growth | OOM or runaway token bills at turn 30+ | Use `MessageWindowChatMemory` with window=10-20 messages (matches `MAX_HISTORY_MESSAGES`) | Any long-running thread |
| Synchronous `chunkInspectionService.getRetrievalReadinessCounts()` on every refusal (line 130) | Refusal latency > answer latency | Cache counts for 60s or call async | When refusal rate > 10% |
| Over-caching prompt with dynamic timestamps | Cache-miss every request | Put dynamic content (citations, date, user question) **after** the cache breakpoint | Any clock-sensitive prompt |
| Similarity threshold tuned on English corpus then deployed on Vietnamese | Retrieval recall cliff on diacritic variants | Tune threshold per embedding model + per language on 100+ held-out queries | First Vietnamese-only deploy |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Returning decrypted key in admin API responses | Leak via browser cache, HAR files, proxy logs | Return masked form only; add a one-time reveal endpoint with short TTL and audit-logged access |
| Logging the full `chat_log.pipeline_log` on exception | Key in `Authorization` header could appear in stack trace | Masking Logback converter regex; redact headers in HTTP client |
| Storing key in same table as user profile without row-level encryption | Any SELECT * exposes it | Separate `api_keys` table, restricted role, encrypted column |
| No rate limit on "reveal key" endpoint | Automated scraping if session hijacked | 1 reveal per key per minute per user; email on reveal |
| Audit log writable by app role | Tamper/cover tracks | Append-only role on `audit_log` table; app has INSERT-only grant |
| CORS `Access-Control-Allow-Origin: *` on admin endpoints | CSRF exfiltrates keys | Lock to known origins; require same-site cookie + CSRF token |
| Returning `500` stack traces to frontend | Stack mentions encryption classes, leaks architecture | Global `@ControllerAdvice` sanitising; correlation ID only |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Refusing friendly greetings ("Xin chào") after grounding gate loosens | Users think bot is broken | `GroundingGuardAdvisor` detects chitchat and responds conversationally without retrieval |
| Silent model-switch when user-chosen model fails auth | Confusing bills, unexpected answers | Explicit error "Your API key for <model> is invalid" with link to admin |
| "Key saved successfully" before encryption completes | User thinks key is safe, request later fails | Return only after encrypted + fingerprint written, with roundtrip decrypt test |
| Cache-hit answer differs slightly from cache-miss answer for same query | Users lose trust in consistency | Deterministic generation (temperature=0) for cached paths, or accept variation and mark cached responses explicitly |
| Latency improvements hide regressions in grounding quality | "Fast but wrong" is worse than "slow and right" for a legal assistant | Dashboard combines p95 + answer-check pass rate; block deploy if grounding drops |

## "Looks Done But Isn't" Checklist

- [ ] **Modular RAG:** Advisor chain integration test covers 2-turn conversation + empty-context path + chitchat path — verify `GroundingStatus` transitions still match v1.0 ground truth.
- [ ] **BeanOutputConverter:** Matrix test across every configured model in `app.ai.models` — verify structured output works or falls back cleanly.
- [ ] **Prompt caching:** OpenRouter dashboard shows non-zero `cached_tokens` for at least one Anthropic model — verify the `cache_control` header actually survived serialization.
- [ ] **Embedding cache:** Cache key includes embedding-model ID — verify dimension mismatch is caught before `VectorStore` call.
- [ ] **API key admin:** grep in CI for plaintext `sk-...` across logs, DB dumps, git history, Actuator output — zero matches.
- [ ] **Masked display:** DTO serialization unit test asserts plaintext field is absent.
- [ ] **Key rotation:** Rotate key → next request succeeds without restart — verify no stale `ChatClient` cached.
- [ ] **Audit log:** Rotate key → audit row exists with `fingerprintBefore != fingerprintAfter`, no plaintext in `details`.
- [ ] **Chat-log async save:** Count(HTTP 200) == count(chat_log rows) in a 1000-request load test.
- [ ] **Slim JSON schema:** Playwright render test for every layout variant — zero `undefined` accesses.
- [ ] **Keyword gate removal:** 20-query Vietnamese regression set passes at ≥ 95%, refusal rate within 10% of v1.0 baseline.
- [ ] **Grounding regression:** Sample 50 random post-deploy chat logs manually — zero cite non-legal sources in a legal answer.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Advisor ordering breaks memory | LOW | Revert to per-call advisor attach; add integration test; re-migrate with explicit orders |
| Grounding regression after keyword-gate removal | MEDIUM | Re-enable gate as monitoring-only; bisect which change caused it (threshold? metadata filter? classifier?); re-deploy fixed |
| Prompt cache no-op | LOW | Inspect raw OpenRouter request in logs; add `cache_control` via extra-body; verify `cached_tokens` |
| API key plaintext leak | HIGH | Immediate: rotate all affected users' keys (admin action). Sanitize logs / DB dumps. Post-mortem. Never recoverable in VCS — rewrite history only as last resort. |
| BeanOutputConverter strict-mode 400s | LOW | Per-model `supportsStructuredOutput=false`; fall back to prompt-instruction mode + lenient parsing |
| Embedding cache dim mismatch | MEDIUM | Evict cache; re-embed corpus with new model; verify dimension + threshold; canary 10% |
| Async chat log loss | MEDIUM | Switch to sync for diagnosis; add snapshot + REQUIRES_NEW; replay lost events from application access log if available |
| Next.js renders undefined after schema slim | LOW | Server-side keep DTO stable, set dropped fields to null/[]; frontend tolerant rendering |

## Pitfall-to-Phase Mapping

Assumes a 4-phase v1.1 structure: **Phase 1** = foundation/instrumentation; **Phase 2** = Modular RAG migration; **Phase 3** = Chat performance & caching; **Phase 4** = API key admin.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. Advisor order collision | Phase 2 | Two-turn integration test with explicit orders |
| 2. ContextualQueryAugmenter default refusal | Phase 2 | Regression suite ≥ 95% on 20 Vietnamese queries |
| 3. Keyword gate regression | Phase 2 | Manual 50-sample grounding audit post-deploy |
| 4. OpenRouter cache_control no-op | Phase 3 | `cached_tokens > 0` assertion per provider |
| 5. API key plaintext leak | Phase 4 | CI grep, Actuator audit, DB dump scan |
| 6. BeanOutputConverter strict failure | Phase 2 | Per-model matrix test |
| 7. Async chat log race | Phase 3 | Load test row-count parity |
| 8. Embedding cache staleness | Phase 3 | Dimension-mismatch guard test; invalidate-on-model-change test |
| 9. Slim JSON frontend mismatch | Phase 3 (backend) + Phase 4 (frontend) | Playwright render test |
| 10. Per-call memory advisor inconsistency | Phase 2 | Architecture test — no `.advisors(memory)` in service layer |
| 11. Prompt fallback drift | Phase 2 cleanup | Architecture test — single source of truth |
| 12. Cache + tool calling | Phase 2 (design doc) | N/A — avoid in v1.1 |
| 13. Query rewriter diacritic loss | Phase 2 | A/B retrieval quality on 20 queries |
| 14. Cache cross-tenant leak | Phase 4 | Bill reconciliation by user for 1 week |
| 15. Stale ChatClient after key rotation | Phase 4 | Rotate-and-fire integration test |
| 16. Liquibase migration hygiene | Phase 4 | Code review checklist |
| 17. Audit log timezone | Phase 4 | Column type is `TIMESTAMPTZ` |
| 18. Masked display structure leak | Phase 4 | UI review |

## Sources

- [Spring AI Advisors API reference](https://docs.spring.io/spring-ai/reference/api/advisors.html) — advisor ordering semantics (HIGH)
- [Spring AI Retrieval Augmented Generation reference](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html) — RetrievalAugmentationAdvisor, ContextualQueryAugmenter defaults (HIGH)
- [Spring AI Chat Memory reference](https://docs.spring.io/spring-ai/reference/api/chat-memory.html) — MessageChatMemoryAdvisor usage (HIGH)
- [Spring AI Structured Output reference](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) — BeanOutputConverter behaviour (HIGH)
- [Spring AI OpenAI Chat reference](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html) — JSON-schema response format, strict mode (HIGH)
- [spring-projects/spring-ai#4170](https://github.com/spring-projects/spring-ai/issues/4170) — ChatMemory ordering regression under 1.0.1 (HIGH)
- [spring-projects/spring-ai#1601](https://github.com/spring-projects/spring-ai/issues/1601) — MessageChatMemoryAdvisor empty user message (MEDIUM)
- [spring-projects/spring-ai#4212](https://github.com/spring-projects/spring-ai/issues/4212) — Memory injected but model doesn't use it (MEDIUM)
- [OpenRouter Prompt Caching guide](https://openrouter.ai/docs/guides/best-practices/prompt-caching) — provider-specific cache_control behaviour, TTLs, breakpoint limits (HIGH)
- [OpenRouterTeam/ai-sdk-provider#35](https://github.com/OpenRouterTeam/ai-sdk-provider/issues/35) — Anthropic cache silent drop through OpenAI-compatible path (MEDIUM)
- [pydantic/pydantic-ai#4392](https://github.com/pydantic/pydantic-ai/issues/4392) — OpenRouterChatModel dropping CachePoint items (MEDIUM)
- [sst/opencode#1245](https://github.com/sst/opencode/issues/1245) — Anthropic caching degrades through OpenRouter over long conversations (MEDIUM)
- [Spring Security Crypto Module reference](https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html) — AES-GCM via Encryptors.stronger (HIGH)
- [Jasypt Spring Boot GitHub](https://github.com/ulisesbocchio/jasypt-spring-boot) — property encryption, AES-256-GCM since 3.0.5 (MEDIUM)
- Project file `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` — current manual pipeline (AUTHORITATIVE for v1.0 baseline)
- Project file `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java` — hardcoded Vietnamese prompts and D-13 fallback (AUTHORITATIVE)
- Project file `.planning/PROJECT.md` — v1.1 milestone scope (AUTHORITATIVE)

---
*Pitfalls research for: Vietnam Traffic Law Chatbot v1.1 — Spring AI modular RAG + API key admin migration*
*Researched: 2026-04-17*
