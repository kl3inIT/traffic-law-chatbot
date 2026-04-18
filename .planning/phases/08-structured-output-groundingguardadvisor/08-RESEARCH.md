# Phase 8: Structured Output + GroundingGuardAdvisor - Research

**Researched:** 2026-04-18
**Domain:** Spring AI 2.0.0-M4 idiomatic chat pipeline (structured output, advisor chain, LLM evaluation)
**Confidence:** HIGH (Context7 `/spring-projects/spring-ai` primary source; source files verified on disk)

## Summary

Phase 8 is a **code-only refactor** inside the Spring AI abstraction layer. All framework choices are locked (Spring AI 2.0.0-M4 via `spring-ai-starter-model-openai`, single `OpenAiChatModel` per cataloged model routed through OpenRouter). Phase 7 shipped the slim `LegalAnswerDraft` record, async chat-log save, a manual Vietnamese chitchat regex, and the `containsAnyLegalCitation` Phase-7-era stopgap. Phase 8 replaces the manual JSON parsing in `ChatService` with `.entity(LegalAnswerDraft.class)`, wires the full Phase-9 advisor order (`GuardIn → Memory → RAG(no-op) → Cache(no-op) → Validation(no-op) → GuardOut`) with pass-through placeholders, adds an LLM intent classifier that reuses the same chat model, and deletes all Vietnamese keyword heuristics. No DB migration, no new runtime config surface, no feature flags.

The non-obvious risks are: (1) OpenRouter-proxied `response_format: json_schema` support is heterogeneous across the 8 cataloged models — the per-model `supportsStructuredOutput` flag (D-03) exists precisely to isolate provider-specific 400s; (2) `ModelEntry` record extension from 4 → 5 constructor args touches **7 production + test files** with hardcoded call-sites, same risk pattern that produced commit `f72440b`; (3) the intent classifier adds a serial LLM roundtrip, so the p7 p95 ceiling (2.5s) is now the constraint on classifier prompt size and temperature.

**Primary recommendation:** Drop all new advisors as `BaseAdvisor` (sealed; provides `adviseCall` + `adviseStream` bridge). Use `BaseAdvisor.HIGHEST_PRECEDENCE + N` for the P9 ordering slots. Keep `IntentClassifier` as a **service** called from `ChatService.doAnswer` before entering the advisor chain — not an advisor — so a `CHITCHAT` or `OFF_TOPIC` result short-circuits without ever running the chain. Per-call `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` is applied conditionally based on the resolved `ModelEntry.supportsStructuredOutput()`.

---

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Intent classifier reuses the same OpenRouter chat model as the main answer (1 extra roundtrip). No cheap side model.
- **D-02:** On classifier error/timeout/malformed response → assume `LEGAL`.
- **D-03:** `supportsStructuredOutput` = 5th arg on `ModelEntry` record. Hardcoded per model in YAML. Default `false` for un-annotated entries.
- **D-03a:** `supportsStructuredOutput=true` → `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` + OpenRouter `response_format: json_schema`. `false` → `BeanOutputConverter` prompt-instruction mode.
- **D-04:** Phase 8 wires the **full Phase-9 target order** with no-op placeholders: `GuardIn → Memory → [RAG no-op] → [Cache no-op] → [Validation no-op] → GuardOut`.
- **D-05:** 20-query Vietnamese regression suite extends `BasicEvaluationTest` from `spring-ai-test`. Uses `RelevancyEvaluator` + `FactCheckingEvaluator`. Tagged `@Tag("live")` + `@DisabledIfEnvironmentVariable` on `OPENROUTER_API_KEY`.
- **D-05a:** No WireMock / recorded-fixture replay. No custom perf harness.
- **D-05b:** Cross-model structured-output matrix test iterates all 8 `ModelEntry` rows. `@Tag("live")`.
- **D-05c:** Two-turn chat-memory integration test in same test class.
- **D-06:** No `@ConfigurationProperties`, no runtime knobs. Thresholds/templates/prompts hardcoded. Enable/disable = code change + `git revert`.
- **D-07:** Delete from `ChatService`: `CHITCHAT_PATTERN`, `isGreetingOrChitchat`, `containsAnyLegalCitation`, `looksLikeLegalCitation`, `containsLegalSignal`, `parseDraft`, `extractJson`, `fallbackDraft`, the `hasLegalCitation` branch.
- **D-08:** `MessageChatMemoryAdvisor` attached via `defaultAdvisors(...)` on the `ChatClient` builder — not per-call.
- **D-09:** `IntentDecision` = record with `enum Intent { CHITCHAT, LEGAL, OFF_TOPIC }` + optional `double confidence`. `OFF_TOPIC` short-circuits to a distinct canned off-topic refusal template. `CHITCHAT` → `AnswerComposer.composeChitchat()`.
- **D-10:** No feature flags. Rollback = `git revert`. (Inherits P7 D-01.)
- **D-11:** Spring AI test patterns sourced from Context7 `/spring-projects/spring-ai`. No improvised test harness. (Inherits P7 D-07.)

### Claude's Discretion

- Exact `BeanOutputConverter` wiring in `ChatService` (fluent `.entity()` path vs. explicit builder).
- Advisor interface choice (`BaseAdvisor` vs. `CallAdvisor` vs. `StreamAdvisor`).
- JSON-schema prompt-instruction text for `supportsStructuredOutput=false` models.
- Exact numeric `order` values for advisor chain slots (must preserve documented P9 sequence).
- Intent classifier Vietnamese prompt wording.
- `IntentDecision` as record vs. sealed interface (record preferred for `.entity()` ergonomics).

### Deferred Ideas (OUT OF SCOPE)

- `RetrievalAugmentationAdvisor` (swap for `NoOpRetrievalAdvisor` — Phase 9).
- `CitationPostProcessor` (citations still flow through existing path — Phase 9).
- `FILTER_EXPRESSION` trust-tier gating — Phase 9.
- `PromptCachingAdvisor` with OpenRouter `cache_control` — Phase 9; `NoOpPromptCacheAdvisor` holds the slot.
- `StructuredOutputValidationAdvisor` with `maxRepeatAttempts` — Phase 9; `NoOpValidationAdvisor` holds the slot.
- Runtime-configurable GroundingGuard knobs — rejected for P8 per D-06.
- Dedicated cheap intent-classifier model (Haiku 4.5 / Flash) — rejected for P8 per D-01.
- DB-backed `supportsStructuredOutput` / admin UI — rejected for P8 per D-03.
- Prompt split into cacheable system + dynamic user — Phase 9.
- Deletion of `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` — Phase 9 cleanup.
- WireMock / recorded fixture replay — rejected per D-05a.

---

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ARCH-02 | `.entity(LegalAnswerDraft.class)` via `BeanOutputConverter`; delete manual JSON parsing | §2 (BeanOutputConverter), §3 (cross-model matrix) |
| ARCH-03 | No hardcoded keyword heuristics; classification via similarity / metadata / LLM | §6 (deletion audit), §5 (intent classifier) |
| ARCH-04 | `GroundingGuardInputAdvisor` + `GroundingGuardOutputAdvisor`, togglable without editing `ChatService` | §4 (advisor shape), §8 (advisor pitfalls) |

---

## Project Constraints (from CLAUDE.md)

- Backend: Java 25, Spring REST. Spring AI 2.0.0-M4 (verified in `build.gradle` line 24).
- All `.planning/` files and code in English; chat user-facing copy in Vietnamese.
- Reference pattern: shoes/jmix-ai-backend controller/service structure.
- Spring AI as ingestion/orchestration base — no custom frameworks.
- Solo dev, no production traffic — skip feature flags, skip custom test harnesses.
- Spring AI tests MUST follow official Context7 `/spring-projects/spring-ai` patterns.
- Planning files English; commit planning docs before advancing workflow stage (memory rule).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Intent classification | Backend service (`chat/intent/`) | Spring AI `.entity()` | Runs **before** advisor chain to short-circuit; not an advisor (D-01 — 1 extra roundtrip, no chain re-entry) |
| Grounding refusal decision | Spring AI advisor (`chat/advisor/`) | `ChatService` (reads result) | ARCH-04 — config-togglable without editing service |
| Structured draft parsing | Spring AI `BeanOutputConverter` | `ChatService` (consumes record) | ARCH-02 — fluent `.entity(Class)` API owns this |
| Conversation memory | Spring AI `MessageChatMemoryAdvisor` | `ChatClient` builder | D-08 — attached via `defaultAdvisors(...)` |
| Per-model capability routing | `AiModelProperties` / `ChatClientConfig` | `ChatService` (reads resolved flag) | D-03 — YAML-bound, reload-safe |
| No-op placeholder advisors | Spring AI `BaseAdvisor` | `ChatClientConfig` | D-04 — holds P9 slots, pass-through behavior |
| Vietnamese regression eval | `spring-ai-test` `BasicEvaluationTest` | `@Tag("live")` integration test | D-05 — canonical pattern, LLM-as-judge |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-ai-bom` | 2.0.0-M4 | Dependency alignment | **[VERIFIED: `build.gradle` line 24-29]** Already pinned |
| `spring-ai-starter-model-openai` | managed by BOM | OpenRouter OpenAI-compat ChatClient | **[VERIFIED: `build.gradle` line 52]** Current chat path |
| `spring-ai-advisors-vector-store` | managed by BOM | `RetrievalAugmentationAdvisor` (P9); not invoked in P8 but must remain on classpath for NoOp typing | **[VERIFIED: `build.gradle` line 54]** |
| `spring-ai-rag` | managed by BOM | `QueryAugmenter` / `DocumentRetriever` interfaces needed by P9 NoOp shape | **[VERIFIED: `build.gradle` line 56]** |

### Supporting (added in P8)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-ai-test` | 2.0.0-SNAPSHOT (BOM-aligned to M4) | `BasicEvaluationTest`, `RelevancyEvaluator`, `FactCheckingEvaluator` | Test-scope only; D-05 regression + cross-model matrix |

**Dependency declaration (test scope):**
```groovy
testImplementation 'org.springframework.ai:spring-ai-test'
```
**[CITED: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-test/pom.xml]** — `groupId=org.springframework.ai`, `artifactId=spring-ai-test`. Version resolves from `spring-ai-bom` — do not hardcode.

**Version verification:** `spring-ai-bom:2.0.0-M4` is pinned in `build.gradle` line 24. The matching `spring-ai-test` artifact ships in the same BOM (no version override needed). `BasicEvaluationTest` requires evaluation prompt templates on the classpath at `classpath:/prompts/spring/test/evaluation/` — **[CITED: Spring AI `spring-ai-test/README.md`]** — the artifact ships these prompts; no additional resource authoring needed.

### Alternatives Considered
| Instead of | Could Use | Tradeoff | Rejected Because |
|------------|-----------|----------|------------------|
| `spring-ai-test` `BasicEvaluationTest` | Custom JUnit + hand-rolled assertion helpers | Full control, no LLM-as-judge dependency | D-05 + P7 D-07 lock official Spring AI test patterns |
| Intent classifier as `CallAdvisor` | Short-circuit inside chain | Single call site | D-01 keeps it a service — short-circuits BEFORE chain entry so `CHITCHAT` skips the entire advisor stack (including memory), matching the Phase-7 behavior |
| `IntentDecision` sealed interface with variants | Exhaustive pattern match | Type-safe dispatch | Record + enum preferred (Claude's discretion; `.entity()` works best with records) |

**Installation (test scope only):**
```groovy
dependencies {
    testImplementation 'org.springframework.ai:spring-ai-test'
}
```

---

## 1. Problem Framing — what "planning well" requires

To produce an actionable plan, we need decisive answers for these implementation micro-questions:

1. **How does `.entity(Class)` choose native vs. prompt mode?** (Answer: § 2)
2. **What is the exact fluent call shape that threads `ENABLE_NATIVE_STRUCTURED_OUTPUT` conditionally?** (Answer: § 2)
3. **Which of the 8 cataloged models need `supportsStructuredOutput=false`?** (Answer: § 3 — matrix with evidence)
4. **Which advisor interface does each new slot implement, and what does a minimal `BaseAdvisor` look like?** (Answer: § 4)
5. **Where does the intent classifier live — advisor or service — and how does it short-circuit?** (Answer: § 5)
6. **Which exact lines in `ChatService.java` get deleted? Any external call-sites?** (Answer: § 6 — line-anchored audit)
7. **Which files need `ModelEntry` 4→5 arg updates?** (Answer: § 7 — exhaustive list)
8. **How is `MessageChatMemoryAdvisor` wired correctly under `defaultAdvisors(...)` with no `conversationId` collision?** (Answer: § 4.3)
9. **What advisor pitfalls apply to a `GuardIn → Memory → [RAG] → [Cache] → [Validation] → GuardOut` chain?** (Answer: § 8)
10. **What GAV + skeleton does the `BasicEvaluationTest` regression require?** (Answer: § 9)
11. **Which signals are observable to prove each P8 success criterion?** (Answer: `## Validation Architecture`)

---

## 2. Spring AI `.entity(Class)` + `BeanOutputConverter`

### 2.1 Fluent API (native mode)

**[CITED: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/structured-output-converter.adoc]**

```java
// Per-call, native JSON schema (OpenRouter response_format: json_schema)
LegalAnswerDraft draft = chatClient.prompt()
    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
    .user(question)
    .call()
    .entity(LegalAnswerDraft.class);
```

### 2.2 Prompt-instruction mode (no advisor param)

```java
// Fallback: BeanOutputConverter injects format instructions + schema into prompt text
LegalAnswerDraft draft = chatClient.prompt()
    .user(question)
    .call()
    .entity(LegalAnswerDraft.class);
```

`.entity(Class)` without the advisor uses `BeanOutputConverter` which:
- Generates a JSON schema from the record at converter construction (via Jackson `JsonSchemaGenerator`).
- Appends format-instruction text to the final user message.
- Parses the raw text response (tolerant of markdown fences only if explicitly configured — the built-in path is **strict**).

### 2.3 Conditional selection pattern (P8)

```java
ChatClient.ChatClientRequestSpec spec = client.prompt().user(prompt);
if (entry.supportsStructuredOutput()) {
    spec = spec.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT);
}
LegalAnswerDraft draft = spec.call().entity(LegalAnswerDraft.class);
```

The existing per-call chain attaches `MessageChatMemoryAdvisor` today (ChatService.java:180-184). Under D-08, memory moves to `defaultAdvisors(...)` and is **not** re-attached per call. The `ENABLE_NATIVE_STRUCTURED_OUTPUT` advisor param is safe to add per-call without duplicating memory — it is an `AdvisorParams` marker, not a `MessageChatMemoryAdvisor` instance.

### 2.4 Schema generation from `LegalAnswerDraft`

Current record (`src/main/java/com/vn/traffic/chatbot/chat/service/LegalAnswerDraft.java` — **note: NOT in `chat/domain/` as CONTEXT.md line 101 states; actual location is `chat/service/`**):

```java
public record LegalAnswerDraft(
        String conclusion,
        String answer,
        String uncertaintyNotice,
        List<String> legalBasis,
        List<String> penalties,
        List<String> requiredDocuments,
        List<String> procedureSteps,
        List<String> nextSteps
) {}
```

**[CITED: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/openai-chat.adoc]** — to produce a schema where OpenAI's `strict: true` mode works, **every field must be annotated `@JsonProperty(required = true, value = "...")`** OR the generator treats them as optional. The current record has no annotations — result: generated schema marks all 8 fields optional. For Vietnamese legal answers this is arguably correct (`uncertaintyNotice` legitimately absent in confident answers), but the **[VERIFIED: Pitfall 6]** warning applies: non-OpenAI models (Claude, Gemini, DeepSeek) may hallucinate extra fields if the schema is not strict.

**Planner decision:** leave `LegalAnswerDraft` unannotated for P8 (matches CONTEXT.md line 125 "ready for `.entity()` without further changes"). Add `@JsonClassDescription` at the record level + `@JsonPropertyDescription` on each field to improve classifier/generator guidance **[CITED: `api/tools.adoc`]**. Do NOT add `@JsonProperty(required = true)` because this flips Jackson's deserialization contract and will reject the `fallbackDraft` shape that Phase 7 still writes through the refusal path (relevant only until §6 deletion lands — sequence the record annotation commit AFTER the deletion commit).

### 2.5 `IntentDecision` record

**[ASSUMED]** Shape Claude may propose (subject to user confirmation via /gsd-discuss-phase re-entry if desired):

```java
@JsonClassDescription("Intent classification of a user chat message")
public record IntentDecision(
    @JsonPropertyDescription("LEGAL=traffic-law question; CHITCHAT=greeting/social; OFF_TOPIC=unrelated domain")
    Intent intent,
    @JsonPropertyDescription("Classifier confidence in [0.0, 1.0]; 0 if unsure")
    double confidence
) {
    public enum Intent { CHITCHAT, LEGAL, OFF_TOPIC }
}
```

Classifier call:
```java
IntentDecision decision = client.prompt()
    .system(INTENT_CLASSIFIER_SYSTEM_PROMPT_VIETNAMESE)
    .user(question)
    .call()
    .entity(IntentDecision.class);
```

---

## 3. Structured-Output Cross-Model Matrix

**OpenRouter routes `response_format: json_schema` only to upstreams that natively support it.** Evidence below is compiled from OpenRouter provider notes + community reports + Spring AI Pitfall 6 research.

| # | ModelEntry ID | Provider family | `response_format: json_schema` via OpenRouter | Recommended `supportsStructuredOutput` | Evidence |
|---|---------------|-----------------|-----------------------------------------------|----------------------------------------|----------|
| 1 | `anthropic/claude-sonnet-4.6` | Anthropic | Supported (Anthropic tool-use JSON mode translation) | `true` | [CITED: OpenRouter structured-outputs docs; Anthropic has native JSON tool-use since Claude 3.5] |
| 2 | `anthropic/claude-opus-4.6` | Anthropic | Supported | `true` | [CITED: same mechanism as Sonnet] |
| 3 | `anthropic/claude-haiku-4.5` | Anthropic | Supported | `true` | [CITED: same] |
| 4 | `openai/gpt-5.1` | OpenAI | Native (this is where the feature originated) | `true` | [CITED: OpenAI Structured Outputs API] |
| 5 | `openai/gpt-4o-mini` | OpenAI | Native since gpt-4o-2024-08-06 | `true` | [CITED: OpenAI docs — json_schema strict mode GA'd 2024-08] |
| 6 | `google/gemini-3.1-pro` | Google | Supported (Gemini `responseSchema` field) | `true` | [ASSUMED — Gemini 3.x line advertises responseSchema; requires live matrix test to confirm routing via OpenRouter] |
| 7 | `deepseek/deepseek-v3.2` | DeepSeek | Partial / inconsistent reports | `false` | [CITED: Pitfall 6 — "DeepSeek V3.2, fall back to non-native mode"; community reports schema 400s on nested arrays] |
| 8 | `anthropic/claude-sonnet-4.5:extended` | Anthropic (1M context variant) | Same as base Sonnet | `true` | [ASSUMED — extended-context variant shares the tool-use JSON implementation] |

**Verification requirement (D-05b):** the P8 cross-model matrix test (`StructuredOutputMatrixIT`) iterates all 8 entries and asserts `.entity(LegalAnswerDraft.class)` returns non-null without 400. Any `true` above that actually 400s → flip that entry to `false` in `application.yaml`. **Do not treat the table above as authoritative without the matrix run.**

**YAML shape (D-03):**
```yaml
app:
  ai:
    models:
      - id: anthropic/claude-sonnet-4.6
        display-name: "Claude Sonnet 4.6"
        base-url: ${OPENROUTER_BASE_URL:https://openrouter.ai/api}
        api-key: ${OPENROUTER_API_KEY:dummy}
        supports-structured-output: true   # NEW
      # ... 7 more
      - id: deepseek/deepseek-v3.2
        display-name: "DeepSeek V3.2"
        base-url: ${OPENROUTER_BASE_URL:https://openrouter.ai/api}
        api-key: ${OPENROUTER_API_KEY:dummy}
        supports-structured-output: false  # NEW — prompt-instruction fallback
```

Spring Boot kebab-case → camelCase binding maps `supports-structured-output` → `supportsStructuredOutput` record arg **[CITED: Spring Boot `@ConfigurationProperties` relaxed binding]**.

---

## 4. Advisor Interface Choice + Ordering

### 4.1 Interface choice per new advisor slot

**[CITED: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/advisors.adoc]**

| Slot | Interface | Reason |
|------|-----------|--------|
| `GroundingGuardInputAdvisor` | `CallAdvisor` | Short-circuits on `FORCE_REFUSAL` context; no streaming need (P8 is non-streaming per v1.1 deferred list) |
| `GroundingGuardOutputAdvisor` | `CallAdvisor` | Post-processes on the way out; no streaming need |
| `NoOpRetrievalAdvisor` | `BaseAdvisor` | P9 will swap to `RetrievalAugmentationAdvisor` which is already a `BaseAdvisor`; matching the shape now avoids re-typing |
| `NoOpPromptCacheAdvisor` | `BaseAdvisor` | P9 `PromptCachingAdvisor` mutates metadata on both request and response — `BaseAdvisor` bridge pattern fits |
| `NoOpValidationAdvisor` | `BaseAdvisor` | P9 `StructuredOutputValidationAdvisor` from Spring AI is `BaseAdvisor` with recursive chain copy |
| `IntentClassifier` | **Not an advisor** | Plain `@Service`; invoked directly from `ChatService.doAnswer` before entering the advisor chain (D-01) |

### 4.2 Minimal `CallAdvisor` skeleton

**[CITED: `api/advisors.adoc` — `SimpleLoggerAdvisor`]**

```java
public final class GroundingGuardInputAdvisor implements CallAdvisor {
    public static final String FORCE_REFUSAL = "chat.guard.forceRefusal";

    @Override public String getName() { return "GroundingGuardInputAdvisor"; }
    @Override public int getOrder() { return BaseAdvisor.HIGHEST_PRECEDENCE + 100; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        // Decision already made by IntentClassifier; advisor only enforces.
        // In P8, this is a thin shim — P9 extends with trust-tier precheck.
        return chain.nextCall(req);
    }
}
```

### 4.3 `defaultAdvisors(...)` wiring in `ChatClientConfig`

**[CITED: `api/advisors.adoc`]**

```java
@Bean
public Map<String, ChatClient> chatClientMap(
        AiModelProperties modelProperties,
        ChatMemory chatMemory,   // Spring-AI-provided bean (JDBC-backed per build.gradle line 51)
        GroundingGuardInputAdvisor guardIn,
        NoOpRetrievalAdvisor noOpRag,
        NoOpPromptCacheAdvisor noOpCache,
        NoOpValidationAdvisor noOpValidation,
        GroundingGuardOutputAdvisor guardOut) {
    // ... per-ModelEntry loop ...
    ChatClient client = ChatClient.builder(chatModel)
        .defaultAdvisors(
            guardIn,                                           // HIGHEST_PRECEDENCE + 100
            MessageChatMemoryAdvisor.builder(chatMemory).build(),  // HIGHEST_PRECEDENCE + 200
            noOpRag,                                           // HIGHEST_PRECEDENCE + 300
            noOpCache,                                         // HIGHEST_PRECEDENCE + 500
            noOpValidation,                                    // HIGHEST_PRECEDENCE + 1000
            guardOut                                           // LOWEST_PRECEDENCE - 100
        )
        .build();
}
```

**Conversation ID at request time:** per Spring AI's documented pattern **[CITED: `api/advisors.adoc`]**, pass `ChatMemory.CONVERSATION_ID` as an advisor param per call:
```java
spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
```
This does NOT re-attach the advisor — it parameterizes the already-attached one. Safe with D-08.

### 4.4 `IntentClassifier` as service (not advisor)

```java
@Service
@RequiredArgsConstructor
public class IntentClassifier {
    private final Map<String, ChatClient> chatClientMap;
    private final AiModelProperties props;

    public IntentDecision classify(String question, String modelId) {
        try {
            ChatClient client = resolveClient(modelId);
            return client.prompt()
                .system(INTENT_SYSTEM_VI)
                .user(question)
                .call()
                .entity(IntentDecision.class);
        } catch (Exception e) {
            // D-02 failure policy — assume LEGAL
            return new IntentDecision(Intent.LEGAL, 0.0);
        }
    }
}
```

`ChatService.doAnswer` flow:
```
question → IntentClassifier.classify(...)
  ├─ CHITCHAT    → AnswerComposer.composeChitchat() → save log → return
  ├─ OFF_TOPIC   → composeOffTopicRefusal()        → save log → return
  └─ LEGAL       → client.prompt()...entity(LegalAnswerDraft.class)
                     (runs GuardIn → Memory → NoOps → GuardOut)
                   → AnswerComposer.compose(...)    → save log → return
```

---

## 5. Intent Classifier Design

- **Runs before advisor chain entry** — per D-01; one extra roundtrip per request on the same model.
- **Failure policy is "assume LEGAL"** — per D-02; catches timeout, parse error, null `Intent`.
- **No caching in P8** — CONTEXT.md defers all caching to P9; the Caffeine embedding cache from P7 does not apply here.
- **Prompt wording is Claude's Discretion** — must be Vietnamese-first. Iterate against the 20-query regression suite until ≥95% pass.
- **Short-circuit paths preserve existing exit points:**
  - `CHITCHAT` → `AnswerComposer.composeChitchat()` (P7 method still present)
  - `OFF_TOPIC` → new method `AnswerComposer.composeOffTopicRefusal()` (**new in P8**) — distinct from the grounding-refusal template
  - `LEGAL` → enter advisor chain

**Latency budget:** intent call must complete in ~300ms median with `gpt-4o-mini`-class models (1 extra roundtrip on top of the main answer roundtrip). This fits inside P7's 2.5s p95 ceiling but leaves **no slack** — if the matrix test shows any model taking > 1s for the intent step, revisit D-01.

---

## 6. ARCH-03 Deletion Audit (line-anchored)

### 6.1 `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`

Delete these exact regions:

| Lines | Item | Deletion action |
|-------|------|-----------------|
| 30 (import) | `import java.util.Locale;` | Remove if no other usage |
| 32 (import) | `import java.util.regex.Pattern;` | Remove |
| 56-65 | `CHITCHAT_PATTERN` constant | Remove entire block |
| 108-122 | Chitchat short-circuit `if (isGreetingOrChitchat(question)) { … }` block | Replace with `IntentClassifier` dispatch |
| 143-146 | citation/source extraction (stays in LEGAL path; move inside advisor chain in P9) | **Keep for P8** — advisors are no-op; `ChatService` still calls `citationMapper.toCitations` directly |
| 153-154 | `boolean hasLegalCitation = …` + `logger.accept("Legal citation check: …")` | Remove both lines |
| 156 | `if (groundingStatus == REFUSED || !hasLegalCitation)` | Change to `if (groundingStatus == REFUSED)` |
| 202-205 | `LegalAnswerDraft draft = parseDraft(…); logger.accept("Draft parsed: …");` | Replace with `.entity(LegalAnswerDraft.class)` call |
| 185-200 | raw `ChatResponse` → `modelPayload` → `getText()` path | Replace with `.entity(LegalAnswerDraft.class)`; token/latency capture moves to advisor or Micrometer observation |
| 261-267 | `containsAnyLegalCitation` + `safeCitations` helpers | Remove `containsAnyLegalCitation`; **keep `safeCitations` if used elsewhere** (verify) |
| 269-299 | `looksLikeLegalCitation` + `containsLegalSignal` + 14-keyword list | Remove entirely |
| 301-316 | `parseDraft(...)` method | Remove |
| 318-341 | `extractJson(...)` method | Remove |
| 343-361 | `isGreetingOrChitchat(...)` method + javadoc | Remove |
| 363-384 | `fallbackDraft(...)` method | Remove |
| 43 (field) | `private final ObjectMapper objectMapper;` | Remove — only consumer was `parseDraft` |
| 3-4 (imports) | `com.fasterxml.jackson.databind.DeserializationFeature; ObjectMapper;` | Remove |

**Post-deletion expected line count:** `ChatService.java` currently 389 lines → target ~150 lines (D-07 language "delete" applied; ARCH-01 "≥60% LOC reduction" is P9 — P8 accepts the partial ~60% reduction as a step toward that goal).

### 6.2 External call-site scan

**[VERIFIED via Grep]** — these production+test paths are the only non-docs references to the deletion targets:

- `containsAnyLegalCitation` / `isGreetingOrChitchat` / `CHITCHAT_PATTERN` / `looksLikeLegalCitation` → **zero external callers** (all uses are `private` inside `ChatService`). Planning docs reference them but don't import.
- `parseDraft` / `extractJson` / `fallbackDraft` → same; all private.
- `ChatServiceChitchatTest.java` — test asserts chitchat short-circuit behavior. **Must be rewritten** to assert `IntentClassifier`-driven short-circuit (or deleted if its coverage is subsumed by the new regression suite).

**Conclusion:** ARCH-03 deletion is fully internal to `ChatService` — no ripple into other production classes.

### 6.3 Adjacent files (no deletion, but must be read)

- `AnswerComposer.java` — verify `composeChitchat()` exists (CONTEXT.md line 123 says yes) and add `composeOffTopicRefusal()`.
- `ChatPromptFactory.buildPrompt` — unchanged in P8 (P7 already trimmed it; further trim belongs to P9 system/user split).
- `chatPromptFactory` reference in `ChatService` — keep; the LEGAL path still builds a prompt string today (advisor-chain prompt augmentation is P9).

---

## 7. `ModelEntry` 4→5 arg Migration Plan

**Current signature (`AiModelProperties.java:33`):**
```java
public record ModelEntry(String id, String displayName, String baseUrl, String apiKey) {}
```

**Target signature:**
```java
public record ModelEntry(String id, String displayName, String baseUrl, String apiKey, boolean supportsStructuredOutput) {}
```

### 7.1 Exhaustive call-site list (verified via Grep)

| # | File | Line | Call shape | Update |
|---|------|------|------------|--------|
| 1 | `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java` | 71 | `new ModelEntry("claude-sonnet-4-6", "Claude Sonnet 4.6", "", "")` | Append `, true` |
| 2 | `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java` | 72 | `new ModelEntry("claude-haiku-4-5-20251001", "Claude Haiku 4.5", "", "")` | Append `, true` |
| 3 | `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java` | 78-79 | Same pattern, 2 entries | Append `, true` each |
| 4 | `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceChitchatTest.java` | 60-61 | Same pattern, 2 entries | **File may be deleted per §6.2** — if kept, append `, true` |
| 5 | `src/test/java/com/vn/traffic/chatbot/chat/config/ChatClientConfigTest.java` | 27-29 | 3 entries, uses `null, null` | Append `, true` each |
| 6 | `src/test/java/com/vn/traffic/chatbot/chat/api/AllowedModelsControllerTest.java` | 37-39 | 3 entries | Append `, true` each |
| 7 | `src/test/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheContextLoadTest.java` | 119-120 | 1 entry, multi-line | Append `, true` |
| 8 | `src/test/java/com/vn/traffic/chatbot/ai/config/AiModelPropertiesTest.java` | 22-24 | 3 entries | Append `, true` each |
| 9 | `src/test/java/com/vn/traffic/chatbot/checks/LlmSemanticEvaluatorTest.java` | 43-44 | 2 entries | Append `, true` each |
| 10 | `src/main/resources/application.yaml` | 70-107 | 8 `- id: …` entries | Add `supports-structured-output: true` (or `false` for DeepSeek per §3) |

**Total:** 9 test files × 17 `new ModelEntry(...)` invocations + 1 YAML file × 8 entries.

### 7.2 Production code call-sites

**[VERIFIED via Grep]** — zero non-test production files construct `ModelEntry` directly. Binding is via `@ConfigurationProperties` reflection. Safe.

### 7.3 Risk — recent history

Commit `f72440b chore(tests): update ModelEntry instantiations to 4-arg signature` (**[CITED: `07-01-SUMMARY.md` line 105]**) shows this exact record extension was done in P7 Wave 0 with 15 compile errors across 6 test files. The 4→5 migration will produce a similar error cloud unless all 9 files above are updated in the **same commit** as the `AiModelProperties.java` record change.

**Planner recommendation:** single atomic commit `chore(models): extend ModelEntry with supportsStructuredOutput (4→5 args)` that touches:
- `AiModelProperties.java`
- All 9 test files listed above
- `application.yaml`

And runs `./gradlew compileTestJava` to verify zero errors **before** any P8 advisor work begins.

---

## 8. Advisor Chain Pitfalls

Adapted from `.planning/research/PITFALLS.md` §1-6, 10-12, filtered to P8 scope.

| # | Pitfall | Manifests As | Mitigation |
|---|---------|--------------|------------|
| 1 | **Equal order values** | Spring AI's `Ordered` sorts arbitrarily on ties — chain order becomes non-deterministic | Every advisor uses a unique constant: `HIGHEST_PRECEDENCE + N` with N at 100, 200, 300, 500, 1000, and `LOWEST_PRECEDENCE - 100` for GuardOut |
| 2 | **`MessageChatMemoryAdvisor` double-attach** | Per-call `.advisors(memory)` + `defaultAdvisors(memory)` → messages appended twice | D-08 + §4.3 — move to `defaultAdvisors(...)` only; per-call uses `a.param(ChatMemory.CONVERSATION_ID, id)`, not `.advisors(...)` |
| 3 | **Role alternation error on turn 2+** | OpenRouter 400 "roles must alternate user/assistant/user…" — Spring AI issue #4170 regression | Two-turn integration test (D-05c) is mandatory; tests first + second turn with memory default-attached |
| 4 | **`ChatClientRequest` mutation in advisor** | Subsequent advisors see stale prompt text | Use `req.mutate()` / return a new `ChatClientRequest` via `chain.nextCall(modifiedReq)`; never mutate in place |
| 5 | **Stream vs. Call advisor mix** | `Flux` subscription race if a Call advisor is in a chain used via `.stream()` | P8 is non-streaming only; if a future advisor must support both, implement both `CallAdvisor` + `StreamAdvisor` like `SimpleLoggerAdvisor` does |
| 6 | **No-op advisor that swallows context** | `adviseCall` returns a fresh `ChatClientResponse` without delegating → chain short-circuits | All NoOp advisors MUST call `chain.nextCall(req)` and return its result unmodified (verified in § 4.2 skeleton) |
| 7 | **`conversationId=null` in advisor-default mode** | `MessageChatMemoryAdvisor` needs a CONVERSATION_ID param; without it, new/ephemeral convo | Pass `a.param(CONVERSATION_ID, conversationId != null ? conversationId : "ephemeral-" + UUID.randomUUID())` |
| 8 | **Guard advisors can't see intent** | GuardIn/Out are after `IntentClassifier` (service) — they don't know CHITCHAT vs LEGAL | Non-issue: CHITCHAT/OFF_TOPIC short-circuit BEFORE advisor chain; only LEGAL ever reaches GuardIn/Out |
| 9 | **`ENABLE_NATIVE_STRUCTURED_OUTPUT` + DeepSeek 400** | Schema rejected by upstream | D-03a + supportsStructuredOutput=false for DeepSeek; matrix test verifies |
| 10 | **Advisor ordering drift when P9 swaps NoOps** | If NoOps use different order values than P9 replacements, chain re-orders unexpectedly | § 4.3 uses the exact Phase-9 target ordering; P9 swaps implementations in place at same `order` values |

---

## 9. Evaluation Approach (D-05)

### 9.1 Dependency
```groovy
testImplementation 'org.springframework.ai:spring-ai-test'
```
GAV: `org.springframework.ai:spring-ai-test` — version managed by `spring-ai-bom:2.0.0-M4` in `build.gradle` line 24.

### 9.2 `BasicEvaluationTest` skeleton

**[CITED: https://github.com/spring-projects/spring-ai/blob/main/spring-ai-test/README.md]**

```java
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class VietnameseRegressionIT extends BasicEvaluationTest {

    @Autowired ChatService chatService;
    @Autowired Map<String, ChatClient> chatClientMap;
    @Autowired AiModelProperties props;

    private static final List<QaPair> REGRESSION_SET = List.of(
        new QaPair("Vượt đèn đỏ phạt bao nhiêu với xe máy?", "nghị định 100/2019"),
        new QaPair("Không có giấy phép lái xe bị phạt thế nào?", "điều 21"),
        // … 18 more Vietnamese canonical queries …
    );

    record QaPair(String question, String expectedCitationHint) {}

    @Test
    void twentyQueryRegressionSuite() {
        int passed = 0;
        for (QaPair qa : REGRESSION_SET) {
            ChatAnswerResponse resp = chatService.answer(qa.question(), props.chatModel());
            // RelevancyEvaluator (context = citations text, answer = resp.answer())
            if (isRelevantAndGrounded(qa.question(), resp)) passed++;
        }
        assertThat(passed * 100.0 / REGRESSION_SET.size()).isGreaterThanOrEqualTo(95.0);
    }
}
```

### 9.3 `RelevancyEvaluator` usage (per query)

**[CITED: `api/testing.adoc`]**

```java
RelevancyEvaluator evaluator = new RelevancyEvaluator(ChatClient.builder(chatModel));
EvaluationRequest er = new EvaluationRequest(
    question,
    contextDocuments,   // citations' source text
    answer              // resp.answer()
);
EvaluationResponse eval = evaluator.evaluate(er);
assertThat(eval.isPass()).isTrue();
```

### 9.4 `FactCheckingEvaluator` usage (grounding / no-hallucinated-citations)

**[CITED: `api/testing.adoc`]**

```java
FactCheckingEvaluator fce = new FactCheckingEvaluator(ChatClient.builder(chatModel));
EvaluationRequest er = new EvaluationRequest(citationContext, List.of(), answerClaim);
assertFalse(fce.evaluate(er).isPass() == false);  // claim must be supported by cited context
```

### 9.5 Cross-model matrix (D-05b)

```java
@Test
void structuredOutputAcrossAllCatalogedModels() {
    for (AiModelProperties.ModelEntry e : props.models()) {
        assertThatCode(() -> {
            ChatAnswerResponse r = chatService.answer("Vượt đèn đỏ phạt bao nhiêu?", e.id());
            assertThat(r.answer()).isNotBlank();
        }).as("model=%s", e.id()).doesNotThrowAnyException();
    }
}
```

### 9.6 Two-turn memory (D-05c)

```java
@Test
void twoTurnConversationMemoryWorks() {
    String convId = UUID.randomUUID().toString();
    var r1 = chatService.answer("Vượt đèn đỏ phạt bao nhiêu?", props.chatModel(), convId);
    var r2 = chatService.answer("Còn xe máy thì sao?", props.chatModel(), convId);
    // Turn 2 must succeed (no role-alternation 400) AND reference turn-1 context
    assertThat(r2.answer()).isNotBlank();
    // (optional) assert chat_log shows role alternation
}
```

---

## Architecture Patterns

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                         ChatController (unchanged)                      │
└───────────────────────────┬────────────────────────────────────────────┘
                            ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    ChatService.doAnswer  (~150 LOC post-P8)             │
│   1. IntentClassifier.classify(question, modelId)                       │
│      ├─ CHITCHAT    → AnswerComposer.composeChitchat()  [exit]          │
│      ├─ OFF_TOPIC   → composeOffTopicRefusal()          [exit]          │
│      └─ LEGAL       ↓                                                   │
│   2. retrieve citations (via existing vectorStore + CitationMapper;     │
│      moves into RetrievalAugmentationAdvisor in P9)                     │
│   3. If documents empty → refusal                                       │
│   4. client.prompt().advisors(conditional_native).user(prompt)          │
│                     .call().entity(LegalAnswerDraft.class)              │
│   5. AnswerComposer.compose(...)                                        │
│   6. chatLogService.save (async, from P7)                               │
└───────────────────────────┬────────────────────────────────────────────┘
                            ▼ (only in step 4 — LEGAL path)
┌────────────────────────────────────────────────────────────────────────┐
│                     ChatClient defaultAdvisors chain                    │
│  [HP+100]  GroundingGuardInputAdvisor      (pass-through in P8)         │
│  [HP+200]  MessageChatMemoryAdvisor        (JDBC-backed memory)         │
│  [HP+300]  NoOpRetrievalAdvisor            (→ P9: RetrievalAugAdvisor)  │
│  [HP+500]  NoOpPromptCacheAdvisor          (→ P9: PromptCachingAdvisor) │
│  [HP+1000] NoOpValidationAdvisor           (→ P9: StructuredOutputValAd)│
│  [LP-100]  GroundingGuardOutputAdvisor     (P8: records status in ctx)  │
└───────────────────────────┬────────────────────────────────────────────┘
                            ▼
                     OpenRouter / OpenAI-compat
                     (per-model ChatClient via ModelEntry)
```

### Component Responsibilities

| File | Phase 8 responsibility |
|------|------------------------|
| `chat/service/ChatService.java` | Orchestrate: intent → retrieve → `.entity()` → compose → log. Shrinks ~60% |
| `chat/service/LegalAnswerDraft.java` | `BeanOutputConverter` target record (no structural change) |
| `chat/intent/IntentClassifier.java` | **NEW** — same-model LLM classifier; fails to `LEGAL` |
| `chat/intent/IntentDecision.java` | **NEW** — record with `Intent` enum + confidence |
| `chat/advisor/GroundingGuardInputAdvisor.java` | **NEW** — thin `CallAdvisor`; P9 home for trust-tier precheck |
| `chat/advisor/GroundingGuardOutputAdvisor.java` | **NEW** — thin `CallAdvisor`; records `GroundingStatus` in response context |
| `chat/advisor/placeholder/NoOpRetrievalAdvisor.java` | **NEW** — `BaseAdvisor`, pass-through at order HP+300 |
| `chat/advisor/placeholder/NoOpPromptCacheAdvisor.java` | **NEW** — `BaseAdvisor`, pass-through at order HP+500 |
| `chat/advisor/placeholder/NoOpValidationAdvisor.java` | **NEW** — `BaseAdvisor`, pass-through at order HP+1000 |
| `chat/config/ChatClientConfig.java` | Wire `defaultAdvisors(...)` chain; read `supportsStructuredOutput` for per-call flag (on the call-site in `ChatService`, not here) |
| `common/config/AiModelProperties.java` | `ModelEntry` gains 5th arg `boolean supportsStructuredOutput` |
| `src/main/resources/application.yaml` | Add `supports-structured-output: true\|false` per model (§3) |

### Recommended project structure
```
src/main/java/com/vn/traffic/chatbot/chat/
├── advisor/
│   ├── GroundingGuardInputAdvisor.java
│   ├── GroundingGuardOutputAdvisor.java
│   └── placeholder/
│       ├── NoOpRetrievalAdvisor.java
│       ├── NoOpPromptCacheAdvisor.java
│       └── NoOpValidationAdvisor.java
├── intent/
│   ├── IntentClassifier.java
│   └── IntentDecision.java
├── config/
│   └── ChatClientConfig.java          (modified — defaultAdvisors wiring)
└── service/
    ├── ChatService.java               (shrunk — deletes per §6.1)
    ├── LegalAnswerDraft.java          (unchanged shape)
    └── AnswerComposer.java            (modified — add composeOffTopicRefusal)
```

### Anti-Patterns to Avoid
- **Attaching `MessageChatMemoryAdvisor` per-call** (Pitfall #2) — use `defaultAdvisors(...)`; per-call only passes `CONVERSATION_ID` param.
- **Setting two advisors to the same `order`** (Pitfall #1) — sort order is arbitrary on ties.
- **Putting the intent classifier as a `CallAdvisor`** — D-01 makes it a service so short-circuit paths never enter the chain; making it an advisor would force every CHITCHAT/OFF_TOPIC request through Memory+NoOps for no reason.
- **Mutating `ChatClientRequest` in place** — always use `req.mutate().build()` or just pass forward.
- **Annotating `LegalAnswerDraft` fields `@JsonProperty(required=true)` in P8** — breaks compatibility with Phase-7 fallback draft paths still present during the transition commit.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON extraction from model payload | Custom markdown-fence stripper + Jackson lenient config | `.entity(Class)` + `BeanOutputConverter` | Spring AI handles schema + parse + native vs prompt mode |
| Intent classification | Vietnamese regex / keyword list | LLM `.entity(IntentDecision.class)` | ARCH-03 mandates; regex fails on dialect/typo variance |
| Advisor chain ordering | Ad-hoc integers | `BaseAdvisor.HIGHEST_PRECEDENCE + N` constants | Documented idiom; P9 swap preserves ordering |
| Memory wiring per call | Manual `requestSpec.advisors(memoryAdvisor)` | `defaultAdvisors(memory)` + `a.param(CONVERSATION_ID, id)` | Single-attach + param is the official pattern; avoids Pitfall #2 |
| Evaluation test framework | Custom assertion helpers | `BasicEvaluationTest` + `RelevancyEvaluator` + `FactCheckingEvaluator` | D-05 + D-11 lock this |
| Cross-model support matrix | Parallel capability map | 5th arg on `ModelEntry` | D-03 — single source of truth already tied to catalog |

---

## Common Pitfalls

See §8. Summary of the "must-test" surface:

- Advisor order uniqueness (`@Test` — boot-time log assertion recommended).
- Two-turn memory works (D-05c).
- Structured output works across all 8 models (D-05b).
- Classifier failure → LEGAL (unit test — mock the ChatClient to throw; assert `IntentDecision(LEGAL, 0.0)`).
- Zero keyword-string survivals in production code (ArchUnit rule or a CI grep against the 14 strings in current `containsLegalSignal`).

---

## Code Examples

### Conditional native structured output

**[CITED: Spring AI `api/chatclient.adoc`]**
```java
ChatClient.ChatClientRequestSpec spec = client.prompt()
    .user(prompt);
if (entry.supportsStructuredOutput()) {
    spec = spec.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT);
}
if (conversationId != null && !conversationId.isBlank()) {
    spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
}
LegalAnswerDraft draft = spec.call().entity(LegalAnswerDraft.class);
```

### `ChatClientConfig` with full chain

**[CITED: Spring AI `api/advisors.adoc`]**
```java
ChatClient client = ChatClient.builder(chatModel)
    .defaultAdvisors(
        guardIn,
        MessageChatMemoryAdvisor.builder(chatMemory).build(),
        noOpRetrieval,
        noOpPromptCache,
        noOpValidation,
        guardOut
    )
    .build();
```

### `BasicEvaluationTest` extension

**[CITED: Spring AI `spring-ai-test/README.md`]**
```java
@SpringBootTest
public class VietnameseRegressionIT extends BasicEvaluationTest {
    @Test
    public void testAnswerAccuracy() {
        String q = "Vượt đèn đỏ phạt bao nhiêu với xe máy?";
        String a = chatService.answer(q, "openai/gpt-4o-mini").answer();
        evaluateQuestionAndAnswer(q, a, true);
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual JSON extraction (`parseDraft`/`extractJson`/`fallbackDraft`) | `.entity(Class)` + `BeanOutputConverter` | Spring AI 1.0 → 2.0.0-M4 | ARCH-02; ~80 LOC deleted |
| Hardcoded keyword gate (`containsAnyLegalCitation`) | LLM intent classifier + score threshold | Spring AI 2.0 modular RAG | ARCH-03; 38 LOC deleted |
| Per-call advisor attach (`MessageChatMemoryAdvisor` only when `conversationId != null`) | `defaultAdvisors(...)` + per-call `CONVERSATION_ID` param | Spring AI 1.0 release | D-08 |
| `OpenAiChatOptions.responseFormat(JSON_SCHEMA)` set on the ChatClient bean | `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` per-call | Spring AI 2.0 advisor unification | Per-model conditional control without bean rebuild |

**Deprecated/outdated:**
- Direct `chatResponse()` + manual parsing — still works but is the Phase-7 pattern being deleted.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `google/gemini-3.1-pro` supports `response_format: json_schema` via OpenRouter | §3 row 6 | Matrix test 400s — flip to `false` in YAML; no code change |
| A2 | `anthropic/claude-sonnet-4.5:extended` shares the base Sonnet tool-use JSON path | §3 row 8 | Same — flip to `false` |
| A3 | `IntentDecision` record shape with `Intent` enum + `double confidence` | §2.5 | User's D-09 explicitly allows this; low risk |
| A4 | 300ms median classifier latency on `gpt-4o-mini`-class | §5 | If higher, p95 may breach 2.5s ceiling — revisit D-01 |
| A5 | `BasicEvaluationTest` classpath prompts are bundled in `spring-ai-test:2.0.0-M4` | §9.1 | If absent, the test will fail on prompt loading — need to author prompt templates in `test/resources/prompts/spring/test/evaluation/` |
| A6 | `IntentClassifier` failure on malformed JSON propagates as `Exception` (not silent null) | §4.4, §5 | If it silently returns null `Intent`, the null-handling path must be explicit — unit test mandatory |

---

## Open Questions

1. **Does `.entity(Class)` without `ENABLE_NATIVE_STRUCTURED_OUTPUT` inject schema into the system prompt or the user prompt?**
   - What we know: `BeanOutputConverter` generates format instructions; appended to the call.
   - What's unclear: exact injection site — matters for P9 prompt caching (cacheable system block must remain stable).
   - Recommendation: live-trace one request during Wave 0 with `SimpleLoggerAdvisor` attached; document the actual site; not a P8 blocker.

2. **Does the `spring-ai-test:2.0.0-M4` artifact physically ship the evaluation prompts?**
   - What we know: README says `classpath:/prompts/spring/test/evaluation/` is required.
   - What's unclear: whether they are inside the jar or expected to be authored per-project.
   - Recommendation: add a Wave-0 probe task `./gradlew dependencies` + jar inspection; if absent, copy from upstream repo to `src/test/resources/`.

3. **Should `AnswerComposer.composeOffTopicRefusal()` share wording with the grounding refusal?**
   - What we know: D-09 says distinct from chitchat.
   - What's unclear: user hasn't specified wording.
   - Recommendation: Claude's discretion (Vietnamese); propose: "Câu hỏi này nằm ngoài phạm vi luật giao thông Việt Nam. Vui lòng hỏi về luật giao thông để được hỗ trợ."

4. **Is there a test tag convention in this repo already for `@Tag("live")`?**
   - What we know: CONTEXT.md mandates `@Tag("live")` + env-disable.
   - What's unclear: Whether `./gradlew test` already excludes this tag by default or whether a `Test` task config is needed.
   - Recommendation: Wave-0 audit of `build.gradle` — if no filter exists, add `useJUnitPlatform { excludeTags 'live' }` to the default `test` task so CI skips without `OPENROUTER_API_KEY`.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + `spring-ai-test` `BasicEvaluationTest` |
| Config file | `build.gradle` (`testImplementation 'org.springframework.ai:spring-ai-test'` to add) |
| Quick run command | `./gradlew test` (excludes `@Tag("live")`) |
| Full suite command | `./gradlew test -PincludeLiveTests` (flag enables `@Tag("live")` — **Wave 0 must add this property wiring**) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| ARCH-02 | `.entity(LegalAnswerDraft.class)` produces non-null record across all 8 models | integration (live) | `./gradlew test --tests StructuredOutputMatrixIT -PincludeLiveTests` | ❌ Wave 0 |
| ARCH-02 | `parseDraft` / `extractJson` / `fallbackDraft` no longer exist in `ChatService.java` | ArchUnit | `./gradlew test --tests ChatServiceDeletionArchTest` | ❌ Wave 0 |
| ARCH-03 | Zero Vietnamese keyword strings survive in production code | grep/ArchUnit | `./gradlew test --tests NoKeywordGateArchTest` | ❌ Wave 0 |
| ARCH-03 | `IntentClassifier` returns `LEGAL` on error/timeout/malformed | unit | `./gradlew test --tests IntentClassifierTest` | ❌ Wave 0 |
| ARCH-03 | `IntentClassifier` correctly classifies 20-query Vietnamese regression (≥95%) | integration (live) | `./gradlew test --tests VietnameseRegressionIT -PincludeLiveTests` | ❌ Wave 0 |
| ARCH-04 | `GroundingGuardInputAdvisor` + `GroundingGuardOutputAdvisor` registered via `defaultAdvisors` | Spring context test | `./gradlew test --tests ChatClientConfigTest` | ✅ exists (extend) |
| D-05c | Two-turn conversation memory works post-advisor-chain | integration (live) | `./gradlew test --tests VietnameseRegressionIT.twoTurnMemory -PincludeLiveTests` | ❌ Wave 0 |
| D-04 | Advisor chain order is `GuardIn → Memory → NoOpRAG → NoOpCache → NoOpValidation → GuardOut` | startup log assertion | `./gradlew test --tests AdvisorChainOrderingTest` | ❌ Wave 0 |
| P7-parity | Refusal rate stays within 10% of P7 baseline | integration (live, manual review) | `./gradlew test --tests VietnameseRegressionIT.refusalRateWithinBaseline -PincludeLiveTests` | ❌ Wave 0 |

### Observable Signals (Nyquist per success criterion)

| Success Criterion | Observable Signal | Where |
|-------------------|-------------------|-------|
| SC1: `.entity()` replaces manual parsing | Post-P8 grep for `parseDraft\|extractJson\|fallbackDraft` in `src/main` returns 0 | CI grep step + ArchUnit |
| SC2: `supportsStructuredOutput` governs mode | Per-request log `native_structured_output=true\|false` tagged | `SimpleLoggerAdvisor` at HP+50 + Micrometer tag |
| SC3: Guard advisor pair owns refusal | Startup log prints advisor chain order once | `@PostConstruct` in `ChatClientConfig` logs resolved advisor names+orders |
| SC4: LLM intent classifier; zero keyword-list | `IntentClassifier` per-call log `intent=LEGAL\|CHITCHAT\|OFF_TOPIC`; ArchUnit rule asserts no Vietnamese keyword string literals outside `intent/` package | Log + ArchUnit |
| SC5: 20-query ≥95%, refusal rate ±10%, two-turn OK | `VietnameseRegressionIT` asserts all three | `@Tag("live")` integration test |

### Sampling Rate
- **Per task commit:** `./gradlew test` (fast suite, no live)
- **Per wave merge:** `./gradlew test -PincludeLiveTests` (requires `OPENROUTER_API_KEY`)
- **Phase gate:** Full suite + manual smoke review of `chat_log.pipeline_log` for 10 canonical queries before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/.../chat/regression/VietnameseRegressionIT.java` — covers ARCH-02/03/04, D-05c (extends `BasicEvaluationTest`)
- [ ] `src/test/java/.../chat/regression/StructuredOutputMatrixIT.java` — covers ARCH-02, D-05b
- [ ] `src/test/java/.../chat/intent/IntentClassifierTest.java` — unit, D-02 failure policy
- [ ] `src/test/java/.../chat/advisor/GroundingGuardAdvisorTest.java` — structural unit
- [ ] `src/test/java/.../chat/advisor/AdvisorChainOrderingTest.java` — startup log assertion
- [ ] `src/test/java/.../chat/archunit/NoKeywordGateArchTest.java` — grep rule
- [ ] `src/test/java/.../chat/archunit/ChatServiceDeletionArchTest.java` — classes deleted
- [ ] `build.gradle` — add `testImplementation 'org.springframework.ai:spring-ai-test'`
- [ ] `build.gradle` — add `-PincludeLiveTests` property + `useJUnitPlatform { excludeTags 'live' }` by default
- [ ] (If Open Question 2 confirms) `src/test/resources/prompts/spring/test/evaluation/` prompts

---

## Security Domain

`.planning/config.json` not re-read in this session; Phase 8 is code-only, touches no secrets, no new endpoints, no new persistence. Applicable ASVS categories below are minimal.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | — (no new auth surface) |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | partial | User question is already validated upstream; classifier prompt-injection is a known LLM risk but mitigated by model-side policies (no tool calling in P8) |
| V6 Cryptography | no | — |

### Known Threat Patterns for Spring AI / OpenRouter chain

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection inside user question → classifier outputs wrong intent | Tampering | D-02 failure policy: assume `LEGAL` on anomalous output → passes to grounding gate (safety net) |
| Schema 400s leaking model response text into logs | Information Disclosure | Existing Logback masking (if any); no new risk vs. P7 |
| Intent classifier called on every request → latency + cost surface | DoS | Single-model reuse (D-01); no external service added; rate-limit surface unchanged from P7 |

---

## Sources

### Primary (HIGH confidence)
- Context7 `/spring-projects/spring-ai` — `api/structured-output-converter.adoc` (BeanOutputConverter + `.entity()`)
- Context7 `/spring-projects/spring-ai` — `api/chatclient.adoc` (`AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`, `defaultAdvisors`)
- Context7 `/spring-projects/spring-ai` — `api/advisors.adoc` (`CallAdvisor`, `BaseAdvisor`, `Ordered`, `SimpleLoggerAdvisor`)
- Context7 `/spring-projects/spring-ai` — `api/advisors-recursive.adoc` (`StructuredOutputValidationAdvisor`, `ToolCallAdvisor` ordering reference)
- Context7 `/spring-projects/spring-ai` — `api/testing.adoc` (`RelevancyEvaluator`, `FactCheckingEvaluator`, `EvaluationRequest`)
- `spring-ai-test/README.md` — `BasicEvaluationTest`, `evaluateQuestionAndAnswer`
- WebFetch `github.com/spring-projects/spring-ai/blob/main/spring-ai-test/pom.xml` — GAV verification
- Source files verified: `ChatService.java`, `ChatClientConfig.java`, `AiModelProperties.java`, `LegalAnswerDraft.java`, 9 test files (Grep-verified call-sites), `application.yaml`, `build.gradle`

### Secondary (MEDIUM confidence)
- `.planning/research/PITFALLS.md` Pitfalls 1, 2, 3, 6, 10, 11
- `.planning/research/ARCHITECTURE.md` §3.1-3.3 (advisor shape precedents)
- `.planning/phases/07-chat-latency-foundation/07-CONTEXT.md` (D-01, D-07 carried forward)

### Tertiary (LOW confidence — flagged in Assumptions Log)
- OpenRouter per-model `response_format: json_schema` support for Gemini 3.1 Pro and Claude Sonnet 4.5:extended — requires matrix-test verification

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified in `build.gradle` + Context7
- Advisor shape: HIGH — Context7 canonical code verbatim
- Cross-model matrix: MEDIUM — 6/8 HIGH-confidence, 2/8 assumptions flagged (A1, A2)
- Deletion audit: HIGH — every line anchor Grep-verified
- ModelEntry migration: HIGH — 9 files + 17 call-sites Grep-verified
- Evaluation approach: HIGH — `spring-ai-test` + `BasicEvaluationTest` pattern quoted from README verbatim
- Pitfalls: HIGH — inherited from project research + Context7-verified advisor semantics

**Research date:** 2026-04-18
**Valid until:** 2026-05-18 (Spring AI 2.0.0-M4 stable; re-verify on Spring AI 2.0 GA release)
