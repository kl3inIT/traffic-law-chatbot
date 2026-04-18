# Phase 8: Structured Output + GroundingGuardAdvisor - Pattern Map

**Mapped:** 2026-04-18
**Files analyzed:** 7 new + 5 modified production + 9 modified tests + 4 new tests = 25
**Analogs found:** 22 / 25 (3 files have no direct analog — Spring AI advisor surfaces, new to this codebase)

---

## File Classification

### New production files

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `chat/advisor/GroundingGuardInputAdvisor.java` | advisor (CallAdvisor) | request-response interceptor | **no existing advisor** — fallback: `common/aop/LoggingAspect.java` (around-advice shape) + RESEARCH §4.2 skeleton | no-analog / external-ref |
| `chat/advisor/GroundingGuardOutputAdvisor.java` | advisor (CallAdvisor) | request-response interceptor | **no existing advisor** — same as above | no-analog / external-ref |
| `chat/advisor/placeholder/NoOpRetrievalAdvisor.java` | advisor (BaseAdvisor placeholder) | pass-through | **no existing advisor** — RESEARCH §4.2 skeleton | no-analog / external-ref |
| `chat/advisor/placeholder/NoOpPromptCacheAdvisor.java` | advisor (BaseAdvisor placeholder) | pass-through | same | no-analog / external-ref |
| `chat/advisor/placeholder/NoOpValidationAdvisor.java` | advisor (BaseAdvisor placeholder) | pass-through | same | no-analog / external-ref |
| `chat/intent/IntentClassifier.java` | service (@Service) | request-response LLM roundtrip | `chat/service/ChatService.java` (resolveClient + ChatClient.prompt pattern) | role-match (same style @Service + ChatClient.prompt) |
| `chat/intent/IntentDecision.java` | domain (record with enum + Jackson annotations) | value object | `chat/service/LegalAnswerDraft.java` (plain record for `.entity()`) | exact |

### Modified production files

| File | Role | Data Flow | Closest Analog (self) | Match Quality |
|------|------|-----------|-----------------------|---------------|
| `chat/service/ChatService.java` | service (rewrite `doAnswer`; delete P7 stopgaps) | CRUD + orchestration | self; current file is its own best reference for untouched sections (resolveClient, determineGroundingStatus, logging) | self |
| `chat/config/ChatClientConfig.java` | config (@Configuration) | bean factory | self — inject advisors + `defaultAdvisors(...)` | self |
| `common/config/AiModelProperties.java` | config (@ConfigurationProperties record) | value object | self — append 5th arg to inner record | self |
| `chat/service/LegalAnswerDraft.java` | domain (record) | value object | self — add `@JsonClassDescription` + `@JsonPropertyDescription` (note: research confirms file lives in `chat/service/`, not `chat/domain/` as CONTEXT.md line 101 claims) | self |
| `src/main/resources/application.yaml` | config (YAML) | bean binding | self — add `supports-structured-output` key to each of 8 `ai.models[*]` entries | self |

### New test files

| New Test | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `chat/advisor/GroundingGuardAdvisorTest.java` | test (unit / structural) | assertion | `chat/config/ChatClientConfigTest.java` (standalone JUnit, no Spring context) | role-match |
| `chat/intent/IntentClassifierIT.java` | test (@Tag("live") integration) | LLM live | **no `@Tag("live")` analog in codebase**; closest shape: `common/SpringBootSmokeTest.java` (@SpringBootTest) | partial — pattern must be imported from RESEARCH §9 |
| `chat/regression/VietnameseRegressionIT.java` extends `BasicEvaluationTest` | test (live eval) | LLM live | **no analog** — new capability (spring-ai-test); closest shape: `common/SpringBootSmokeTest.java` | no-analog / external-ref |
| `chat/regression/StructuredOutputMatrixIT.java` | test (live matrix) | LLM live × 8 models | same | no-analog / external-ref |

### Modified test files (ModelEntry 4→5 arg migration — single atomic commit)

| # | Test File | Line(s) | Action |
|---|-----------|---------|--------|
| 1 | `chat/service/ChatServiceTest.java` | 71-72 | append `, true` to each `new ModelEntry(...)` |
| 2 | `chat/service/ChatServiceChitchatTest.java` | 60-61 | file may be deleted (see §6.2 of RESEARCH); if kept, append `, true` |
| 3 | `chat/config/ChatClientConfigTest.java` | 27-29 | append `, true` to 3 entries |
| 4 | `chat/ChatFlowIntegrationTest.java` | 78-79 | append `, true` to 2 entries |
| 5 | `chat/api/AllowedModelsControllerTest.java` | 37-39 | append `, true` to 3 entries |
| 6 | `ai/embedding/EmbeddingCacheContextLoadTest.java` | 119-120 | append `, true` to 1 multi-line entry |
| 7 | `ai/config/AiModelPropertiesTest.java` | 22-24 | append `, true` to 3 entries |
| 8 | `checks/LlmSemanticEvaluatorTest.java` | 43-44 | append `, true` to 2 entries |

---

## Pattern Assignments

### `chat/intent/IntentClassifier.java` (service, request-response)

**Analog:** `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`

**Class-level annotations + constructor pattern** (ChatService.java:35-51):
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final Map<String, ChatClient> chatClientMap;
    private final AiModelProperties aiModelProperties;
    // ...
}
```
Copy: `@Slf4j @Service @RequiredArgsConstructor` + inject `Map<String, ChatClient> chatClientMap` + `AiModelProperties aiModelProperties`.

**ChatClient resolution pattern** (ChatService.java:232-251):
```java
private ChatClient resolveClient(String requestedModelId) {
    if (requestedModelId != null && chatClientMap.containsKey(requestedModelId)) {
        return chatClientMap.get(requestedModelId);
    }
    if (requestedModelId != null && !requestedModelId.isBlank()) {
        log.warn("Unrecognized modelId '{}', falling back to default '{}'",
                requestedModelId, aiModelProperties.chatModel());
    }
    ChatClient fallback = chatClientMap.get(aiModelProperties.chatModel());
    if (fallback == null) {
        // ... falls back to first available
    }
    return fallback;
}
```
Copy this method verbatim into `IntentClassifier` (or extract to a shared helper if preferred).

**`.entity()` call shape** (RESEARCH §4.4):
```java
public IntentDecision classify(String question, String modelId) {
    try {
        ChatClient client = resolveClient(modelId);
        return client.prompt()
            .system(INTENT_SYSTEM_VI)
            .user(question)
            .call()
            .entity(IntentDecision.class);
    } catch (Exception e) {
        return new IntentDecision(IntentDecision.Intent.LEGAL, 0.0);  // D-02
    }
}
```

**Error handling / failure policy:** on any exception (timeout, parse error, null), return `new IntentDecision(LEGAL, 0.0)` per D-02. Log at `warn` level (same pattern as ChatService.java:237 `log.warn(...)`).

---

### `chat/intent/IntentDecision.java` (domain, value object)

**Analog:** `src/main/java/com/vn/traffic/chatbot/chat/service/LegalAnswerDraft.java`

**Current LegalAnswerDraft shape** (LegalAnswerDraft.java:5-15):
```java
package com.vn.traffic.chatbot.chat.service;

import java.util.List;

public record LegalAnswerDraft(
        String conclusion,
        String answer,
        String uncertaintyNotice,
        List<String> legalBasis,
        // ...
) {}
```

**Target shape** (RESEARCH §2.5, D-09):
```java
package com.vn.traffic.chatbot.chat.intent;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

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

No existing codebase records use `@JsonClassDescription` yet — this is the first. Confirm import is `com.fasterxml.jackson.annotation.*` (same Jackson package used by `JacksonConfig.java`).

---

### `chat/advisor/GroundingGuardInputAdvisor.java` (advisor, CallAdvisor)

**Analog:** none in codebase. Use RESEARCH.md §4.2 skeleton (CITED from Spring AI `api/advisors.adoc`).

**Skeleton** (RESEARCH §4.2, lines 304-317):
```java
public final class GroundingGuardInputAdvisor implements CallAdvisor {
    public static final String FORCE_REFUSAL = "chat.guard.forceRefusal";

    @Override public String getName() { return "GroundingGuardInputAdvisor"; }
    @Override public int getOrder() { return BaseAdvisor.HIGHEST_PRECEDENCE + 100; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        return chain.nextCall(req);
    }
}
```

**Class-level annotation pattern for Spring-managed advisor bean** — follow `AnswerComposer.java:14-16`:
```java
@Component
@RequiredArgsConstructor
public class AnswerComposer {
```
If the advisor needs injected dependencies (e.g., `AnswerCompositionPolicy` for refusal template), use `@Component @RequiredArgsConstructor`. Otherwise register explicitly as `@Bean` in `ChatClientConfig`.

**Order value:** `BaseAdvisor.HIGHEST_PRECEDENCE + 100` (see §Shared Patterns below).

**Pitfall 4 (request mutation):** never mutate `ChatClientRequest` in place — use `req.mutate()` and return the mutated instance via `chain.nextCall(mutatedReq)`.

---

### `chat/advisor/GroundingGuardOutputAdvisor.java` (advisor, CallAdvisor)

**Analog:** same as GuardIn. Use `CallAdvisor` interface, but `order = BaseAdvisor.LOWEST_PRECEDENCE - 100`.

**Refusal template constant** — house as `private static final String REFUSAL_TEMPLATE = "..."` directly in the advisor class per D-06 (no `@ConfigurationProperties`). Follow `ChatPromptFactory.java:18-21` pattern for constant-as-field:
```java
private static final String SYSTEM_CONTEXT_FALLBACK =
        "Ban la tro ly hoi dap phap luat giao thong Viet Nam.\n" +
        "...";
```

---

### `chat/advisor/placeholder/NoOpRetrievalAdvisor.java` (advisor, BaseAdvisor placeholder)

**Analog:** none. Use RESEARCH §4.2 skeleton shape; pass-through only.

**Pattern:** `adviseCall(req, chain) → chain.nextCall(req)` unmodified. Order = `BaseAdvisor.HIGHEST_PRECEDENCE + 300`.

**Pitfall 6 (no-op swallowing):** MUST call `chain.nextCall(req)` and return its result unchanged. Do NOT construct a fresh `ChatClientResponse`.

Same pattern applies to `NoOpPromptCacheAdvisor` (order 500) and `NoOpValidationAdvisor` (order 1000).

---

### `chat/service/ChatService.java` (service, rewrite `doAnswer`)

**Analog:** self (current file, keeping `resolveClient`, `determineGroundingStatus`, `safeDocuments`, `safeCitations`, logging pattern, async chatLogService save).

**Deletion audit** — from RESEARCH §6.1:

| Lines to delete | Item |
|-----------------|------|
| 3-4 | `import DeserializationFeature; ObjectMapper;` |
| 30 | `import java.util.Locale;` (if unused after) |
| 32 | `import java.util.regex.Pattern;` |
| 43 | `private final ObjectMapper objectMapper;` field |
| 56-65 | `CHITCHAT_PATTERN` constant |
| 108-122 | `if (isGreetingOrChitchat(question))` block → replace with IntentClassifier dispatch |
| 153-154 | `hasLegalCitation` var + log line |
| 156 | change `groundingStatus == REFUSED \|\| !hasLegalCitation` to `groundingStatus == REFUSED` |
| 185-205 | raw `ChatResponse` → `modelPayload` → `parseDraft` path → replace with `.entity(LegalAnswerDraft.class)` |
| 261-263 | `containsAnyLegalCitation` method |
| 269-299 | `looksLikeLegalCitation` + `containsLegalSignal` + 14-keyword list |
| 301-316 | `parseDraft(...)` |
| 318-341 | `extractJson(...)` |
| 343-361 | `isGreetingOrChitchat(...)` |
| 363-384 | `fallbackDraft(...)` |

**`.entity()` replacement pattern** (RESEARCH §2.3):
```java
ChatClient client = resolveClient(modelId);
AiModelProperties.ModelEntry entry = resolveEntry(modelId);
ChatClient.ChatClientRequestSpec spec = client.prompt().user(prompt);
if (entry.supportsStructuredOutput()) {
    spec = spec.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT);
}
if (conversationId != null && !conversationId.isBlank()) {
    spec = spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));  // §4.3 — param-only, no re-attach
}
LegalAnswerDraft draft = spec.call().entity(LegalAnswerDraft.class);
```

**Keep untouched** (lines stay as-is, verified unaffected by deletions):
- `resolveClient(...)` (232-251)
- `determineGroundingStatus(...)` (253-255)
- `safeDocuments(...)` (257-259)
- `safeCitations(...)` (265-267) — verify still used; delete if orphaned
- `emptyDraft()` / `refusalResponse()` (223-225, 386-388)
- async chatLogService save pattern (112-120, 159-168, 209-218) — pipelineLog snapshot BEFORE handoff (Pitfall 7)

**Pre-advisor-chain dispatch pattern** (RESEARCH §4.4):
```
question → IntentClassifier.classify(...)
  ├─ CHITCHAT    → AnswerComposer.composeChitchat() → save log → return
  ├─ OFF_TOPIC   → AnswerComposer.composeOffTopicRefusal() → save log → return   (NEW method — AnswerComposer)
  └─ LEGAL       → retrieval → prompt build → .entity(LegalAnswerDraft.class) → compose → return
```

**Note:** `AnswerComposer` needs a new `composeOffTopicRefusal()` method distinct from `composeChitchat()` (AnswerComposer.java:24-45) and distinct from grounding-refusal template (AnswerComposer.java:53-73). Use same `ChatAnswerResponse` constructor shape.

---

### `chat/config/ChatClientConfig.java` (config, bean factory)

**Analog:** self (ChatClientConfig.java full file, ~92 lines).

**Current bean factory loop** (ChatClientConfig.java:57-86) — extend the `ChatClient.builder(chatModel).build()` at line 84 into:
```java
ChatClient client = ChatClient.builder(chatModel)
    .defaultAdvisors(
        guardIn,                                                    // HIGHEST_PRECEDENCE + 100
        MessageChatMemoryAdvisor.builder(chatMemory).build(),       // + 200
        noOpRag,                                                    // + 300
        noOpCache,                                                  // + 500
        noOpValidation,                                             // + 1000
        guardOut                                                    // LOWEST_PRECEDENCE - 100
    )
    .build();
map.put(entry.id(), client);
```
(RESEARCH §4.3, lines 324-345)

**Bean method signature change** — inject the new advisors:
```java
@Bean
public Map<String, ChatClient> chatClientMap(
        AiModelProperties modelProperties,
        ChatMemory chatMemory,
        GroundingGuardInputAdvisor guardIn,
        NoOpRetrievalAdvisor noOpRag,
        NoOpPromptCacheAdvisor noOpCache,
        NoOpValidationAdvisor noOpValidation,
        GroundingGuardOutputAdvisor guardOut) {
    // ... existing loop body, modified ChatClient build step ...
}
```

**Unchanged patterns to preserve** (ChatClientConfig.java):
- `@Slf4j @Configuration` class annotation (line 31-33)
- `@Value("${spring.ai.openai.api-key:none}") private String apiKey;` (35-36)
- `@Autowired(required=false) ObservationRegistry / RetryTemplate` (38-42)
- `SimpleClientHttpRequestFactory` + 10min timeouts (50-52)
- Per-entry `OpenAiApi` builder (62-66)
- `OpenAiChatOptions.builder().model(entry.id()).build()` (68-70)
- Retry + observation registry wiring (76-81)
- Log line format (85): `log.info("Registered ChatClient model={} baseUrl={}", entry.id(), modelBaseUrl);`
- `Collections.unmodifiableMap(map)` return (90)

---

### `common/config/AiModelProperties.java` (config, record)

**Analog:** self (AiModelProperties.java full file, 34 lines).

**Current `ModelEntry` signature** (line 33):
```java
public record ModelEntry(String id, String displayName, String baseUrl, String apiKey) {}
```

**Target signature** (D-03):
```java
public record ModelEntry(String id, String displayName, String baseUrl, String apiKey, boolean supportsStructuredOutput) {}
```

**Javadoc update:** extend the `@param` block at lines 27-31 with:
```java
 * @param supportsStructuredOutput whether this model supports native JSON schema
 *                                 via OpenRouter response_format (D-03a). false =
 *                                 BeanOutputConverter prompt-instruction fallback.
```

---

### `chat/service/LegalAnswerDraft.java` (domain, record)

**Analog:** self.

**Current** (15 lines, zero annotations). **Target** (RESEARCH §2.4): add class-level `@JsonClassDescription` + field-level `@JsonPropertyDescription`. Do NOT add `@JsonProperty(required = true)` — RESEARCH §2.4 flags this as breaking `fallbackDraft` shape during the transitional period.

**Sequencing constraint:** add record annotations AFTER the ChatService deletion commit lands (fallbackDraft is gone, no conflict).

---

### `src/main/resources/application.yaml` (config, YAML)

**Analog:** self (application.yaml, 8 model entries at lines 72-107).

**Pattern per entry** — current:
```yaml
  - id: anthropic/claude-sonnet-4.6
    display-name: "Claude Sonnet 4.6"
    base-url: ${OPENROUTER_BASE_URL:https://openrouter.ai/api}
    api-key: ${OPENROUTER_API_KEY:dummy}
```

Target (append 5th field):
```yaml
  - id: anthropic/claude-sonnet-4.6
    display-name: "Claude Sonnet 4.6"
    base-url: ${OPENROUTER_BASE_URL:https://openrouter.ai/api}
    api-key: ${OPENROUTER_API_KEY:dummy}
    supports-structured-output: true     # NEW
```

**Per-model values** (RESEARCH §3 matrix):

| Model id | `supports-structured-output` |
|----------|------------------------------|
| `anthropic/claude-sonnet-4.6` | `true` |
| `anthropic/claude-opus-4.6` | `true` |
| `anthropic/claude-haiku-4.5` | `true` |
| `openai/gpt-5.1` | `true` |
| `openai/gpt-4o-mini` | `true` |
| `google/gemini-3.1-pro` | `true` (verify via matrix test D-05b) |
| `deepseek/deepseek-v3.2` | **`false`** (Pitfall 9) |
| `anthropic/claude-sonnet-4.5:extended` | `true` |

Applies equally to `application-dev.yaml` and `application-prod.yaml` (currently no `ai.models` entries there per filesystem check; if they override, mirror the same keys).

---

### `chat/advisor/GroundingGuardAdvisorTest.java` (test, unit/structural)

**Analog:** `src/test/java/com/vn/traffic/chatbot/chat/config/ChatClientConfigTest.java`

**Test class shape** (ChatClientConfigTest.java:19-38):
```java
class ChatClientConfigTest {

    private AiModelProperties buildModelProperties() {
        return new AiModelProperties(
                "http://localhost:20128",
                "claude-sonnet-4-6",
                "claude-haiku-4-5-20251001",
                List.of(
                        new AiModelProperties.ModelEntry("gpt-5.4", "GPT-5.4", null, null),
                        // ...
                )
        );
    }

    @Test
    void chatClientMapHasExactlyThreeKeys() {
        // ...
        assertThat(map).hasSize(3);
    }
}
```

Copy: package-private class + `@Test` methods + AssertJ `assertThat(...)` (no Spring context, no `@ExtendWith`). For advisor structural tests, assert `getOrder()` values, `getName()` strings, and `adviseCall(mockReq, mockChain)` delegates to `chain.nextCall(req)` for NoOps.

---

### `chat/intent/IntentClassifierIT.java` and regression ITs (test, live)

**Analog:** `src/test/java/com/vn/traffic/chatbot/common/SpringBootSmokeTest.java` (only `@SpringBootTest` example) + RESEARCH §9.2 skeleton.

**Skeleton** (RESEARCH §9.2, lines 529-547):
```java
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class VietnameseRegressionIT extends BasicEvaluationTest {

    @Autowired ChatService chatService;
    @Autowired Map<String, ChatClient> chatClientMap;
    @Autowired AiModelProperties props;

    record QaPair(String question, String expectedCitationHint) {}

    @Test
    void twentyQueryRegressionSuite() {
        int passed = 0;
        for (QaPair qa : REGRESSION_SET) {
            // RelevancyEvaluator + FactCheckingEvaluator assertions
        }
    }
}
```

Apply same `@SpringBootTest + @Tag("live") + @DisabledIfEnvironmentVariable` triad to `IntentClassifierIT` and `StructuredOutputMatrixIT`. Codebase has **no existing `@Tag("live")` class** — this introduces the pattern. Document in each test's javadoc that CI skips without `OPENROUTER_API_KEY`.

**Gradle dependency** (build.gradle, add to existing testImplementation block):
```groovy
testImplementation 'org.springframework.ai:spring-ai-test'   // version via BOM
```

---

## Shared Patterns

### Advisor ordering (apply to all 5 new advisors)
**Source:** RESEARCH §4.3 + §8 Pitfall 1 + Pitfall 10.
**Apply to:** `GroundingGuardInputAdvisor`, `NoOpRetrievalAdvisor`, `NoOpPromptCacheAdvisor`, `NoOpValidationAdvisor`, `GroundingGuardOutputAdvisor`.
```java
// Pin exact numeric offsets so P9 can swap implementations at same order values.
// GuardIn             → BaseAdvisor.HIGHEST_PRECEDENCE + 100
// MessageChatMemory   → BaseAdvisor.HIGHEST_PRECEDENCE + 200
// NoOpRetrieval       → BaseAdvisor.HIGHEST_PRECEDENCE + 300
// NoOpPromptCache     → BaseAdvisor.HIGHEST_PRECEDENCE + 500
// NoOpValidation      → BaseAdvisor.HIGHEST_PRECEDENCE + 1000
// GuardOut            → BaseAdvisor.LOWEST_PRECEDENCE  - 100
```

### Pass-through contract for NoOp advisors
**Source:** RESEARCH §8 Pitfall 6.
**Apply to:** all 3 `NoOp*Advisor` files.
```java
@Override
public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
    return chain.nextCall(req);   // never construct a fresh response; never short-circuit
}
```

### `@Service / @Component + @RequiredArgsConstructor` dependency injection
**Source:** `ChatService.java:35-38`, `AnswerComposer.java:14-18`, `RetrievalPolicy.java:8-19`.
**Apply to:** `IntentClassifier`, `GroundingGuardInputAdvisor` (if @Component), `GroundingGuardOutputAdvisor` (if @Component).
```java
@Slf4j
@Service              // or @Component for advisors
@RequiredArgsConstructor
public class XxxService {
    private final Foo foo;
    private final Bar bar;
}
```

### Async chat-log save pattern (preserve in rewritten `doAnswer`)
**Source:** `ChatService.java:112-120`, `159-168`, `209-218`.
**Apply to:** CHITCHAT, OFF_TOPIC, and LEGAL paths in rewritten `doAnswer`. Snapshot `pipelineLog` BEFORE async handoff (Pitfall 7).
```java
try {
    String pipelineLog = String.join("\n", List.copyOf(logMessages));   // CRITICAL: snapshot
    chatLogService.save(question, response, groundingStatus, null,
            promptTokens, completionTokens, responseTime, pipelineLog);
} catch (Exception ex) {
    log.warn("Failed to persist chat log entry: {}", ex.getMessage());
}
```

### YAML kebab-case → record camelCase binding
**Source:** Spring Boot relaxed binding (RESEARCH §3, cited in Spring Boot docs).
**Apply to:** `application.yaml` + `AiModelProperties.ModelEntry`.
`supports-structured-output` → `supportsStructuredOutput` — no additional `@JsonProperty` or `@ConfigurationPropertiesBinding` needed.

### `ModelEntry` test instantiation (single-commit migration)
**Source:** commit `f72440b` from P7 Wave 0 (`07-01-SUMMARY.md` line 105).
**Apply to:** all 9 test files listed in the Modified Test Files table.
**Rule:** update `AiModelProperties.java` record + all 9 test files + `application.yaml` in one atomic commit, then run `./gradlew compileTestJava` to verify zero errors BEFORE starting advisor work. This mirrors the P7 Wave-0 pattern that already produced 15 compile errors when done piecemeal.

---

## No Analog Found

| File | Role | Data Flow | Why no analog |
|------|------|-----------|---------------|
| `GroundingGuardInputAdvisor.java` / `GroundingGuardOutputAdvisor.java` | advisor (CallAdvisor) | request-response interceptor | Codebase has zero Spring AI advisors today (grep confirms only `ChatService.java` references `MessageChatMemoryAdvisor`). Planner must use RESEARCH §4.2 + Spring AI `api/advisors.adoc` `SimpleLoggerAdvisor` reference. |
| `NoOp*Advisor.java` (×3) | advisor (BaseAdvisor placeholder) | pass-through | Same — new interface surface. Use RESEARCH §4.2 skeleton and Pitfall-6 rule. |
| `VietnameseRegressionIT.java` extends `BasicEvaluationTest` | test (spring-ai-test eval) | LLM-as-judge | No `BasicEvaluationTest` subclass exists; no test uses `RelevancyEvaluator` / `FactCheckingEvaluator`. Planner must use RESEARCH §9.2 + Spring AI `spring-ai-test/README.md`. |

Planner should cite RESEARCH.md directly in the action sections for these files rather than pointing to a codebase analog.

---

## Metadata

**Analog search scope:**
- `src/main/java/com/vn/traffic/chatbot/chat/**` (34 files)
- `src/main/java/com/vn/traffic/chatbot/common/**` (17 files)
- `src/main/java/com/vn/traffic/chatbot/retrieval/**`
- `src/test/java/**` (52 files)
- `src/main/resources/application*.yaml` (3 files)

**Files scanned:** ~75 production + tests + YAML

**Grep queries run:**
- `new ModelEntry\(|new AiModelProperties\.ModelEntry\(` → 9 test files × 17 invocations (verified)
- `BaseAdvisor|CallAdvisor|StreamAdvisor|Advisor` → only `ChatService.java` uses `MessageChatMemoryAdvisor` (confirms no existing advisor implementations)
- `@Tag\(|@EnabledIfEnvironmentVariable|@DisabledIfEnvironmentVariable` → zero matches (new pattern for this codebase)
- `@SpringBootTest` → 6 existing classes (smoke-test shape, none `@Tag("live")`)

**Pattern extraction date:** 2026-04-18
