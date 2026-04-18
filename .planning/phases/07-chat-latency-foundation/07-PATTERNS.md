# Phase 07: Chat Latency Foundation — Pattern Map

**Mapped:** 2026-04-17
**Files analyzed:** 12 (4 modified Java, 4 new Java, 1 YAML, 3 frontend, plus 5 new tests)
**Analogs found:** 11 strong matches / 12 files (1 file has no in-repo analog — JHipster reference supplies it)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` | service (orchestrator) | request-response | self (v1.0 `doAnswer` is already the reference — modify in place) | self |
| `src/main/java/com/vn/traffic/chatbot/chatlog/service/ChatLogService.java` | service (persistence) | async write / CRUD | `IngestionOrchestrator.runPipeline` (uses `@Async("ingestionExecutor")` cross-bean) | role-match + async |
| `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java` | service (pure helper) | transform | self (trim instruction lines 60-62) | self |
| `src/main/java/com/vn/traffic/chatbot/chat/service/LegalAnswerDraft.java` | domain (record) | data carrier | self (12-field record → 8-field record) | self |
| `src/main/resources/application.yaml` | config | boot-time settings | self (line 118-125 `management.endpoints.web.exposure.include`) | self |
| `src/main/java/com/vn/traffic/chatbot/chat/config/ChatLogAsyncConfig.java` (NEW) | config (executor bean) | event-driven | `common/config/AsyncConfig.java` (ingestionExecutor + `@EnableAsync`) | exact (role + shape) |
| `src/main/java/com/vn/traffic/chatbot/ai/embedding/CachingEmbeddingModel.java` (NEW) | service (decorator) | transform (cache-through) | no direct analog — Spring AI `EmbeddingModel` decorator; use RESEARCH.md §Pattern 1 | none in-repo |
| `src/main/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingCacheConfig.java` (NEW) | config (cache-manager bean) | boot-time | `D:/jhipster/.../config/CacheConfiguration.java` (`@EnableCaching`, per-cache config, TTL as safety ceiling) + in-repo `VectorStoreConfig.java` (bean shape) | role-match (external) |
| `src/main/java/com/vn/traffic/chatbot/ai/embedding/EmbeddingModelChangedEvent.java` (NEW) | event (ApplicationEvent) | pub-sub | no existing `ApplicationEvent` in repo — use Spring `ApplicationEvent` base class; listener pattern from JHipster `@EventListener` reference | none in-repo |
| `src/test/java/.../ai/embedding/CachingEmbeddingModelTest.java` (NEW) | test (pure unit) | transform | `chat/service/AnswerComposerTest.java` (pure unit + Mockito) | exact |
| `src/test/java/.../chatlog/ChatLogServiceAsyncIT.java` (NEW) | test (integration) | async verification | `common/config/AsyncConfigTest.java` + `chatlog/ChatLogServiceTest.java` | role-match |
| `frontend/types/api.ts`, `frontend/components/chat/message-bubble.tsx`, `frontend/components/chat/scenario-accordion.tsx` | frontend DTO + UI removal | transform + render | self (existing fields — delete branches referencing `scenarioRule/Outcome/Actions`) | self |

---

## Pattern Assignments

### `ChatLogAsyncConfig.java` (NEW — config, executor bean)

**Analog:** `src/main/java/com/vn/traffic/chatbot/common/config/AsyncConfig.java`

**IMPORTANT divergence:** AsyncConfig uses `SimpleAsyncTaskExecutor` + virtual threads (OK for I/O-bound OpenAI calls, unbounded concurrency=20). Phase 7 requires `ThreadPoolTaskExecutor` with bounded queue + `CallerRunsPolicy` (D-09 — deliberate backpressure). Do NOT copy the virtual-thread shape; copy only the `@EnableAsync` + `@Configuration` class skeleton + cache-name constant idiom.

**Class skeleton + @EnableAsync pattern** (AsyncConfig.java:27-50):
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("ingestion-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(20);
        return executor;
    }
    // ...
}
```

**NOTE on @EnableAsync:** Already present at `AsyncConfig.java:28`. Phase 7 must NOT add a second `@EnableAsync` (duplicates cause Spring to register `AsyncAnnotationBeanPostProcessor` twice — harmless but smelly). The new `ChatLogAsyncConfig` should NOT re-declare `@EnableAsync`. Instead, only define the bean:

```java
@Configuration
public class ChatLogAsyncConfig {
    public static final String CHAT_LOG_EXECUTOR = "chatLogExecutor";

    @Bean(name = CHAT_LOG_EXECUTOR)
    public ThreadPoolTaskExecutor chatLogExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("chat-log-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}
```

**Uncaught-handler coverage:** `AsyncConfig` already registers `getAsyncUncaughtExceptionHandler()` globally (AsyncConfig.java:66-70). Because that handler is on the `AsyncConfigurer` contract, it applies to ALL `@Async` invocations — including the new `chatLogExecutor`. No need to duplicate.

---

### `ChatLogService.java` (MODIFY — async write)

**Analog (`@Async` cross-bean invocation pattern):** `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`

**Imports pattern** (IngestionOrchestrator.java:25-27):
```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
```

**`@Async` method annotation pattern** (IngestionOrchestrator.java:60-63):
```java
@Async("ingestionExecutor")
public CompletableFuture<Void> runPipeline(UUID jobId) {
    // body — different bean calls into it, proxy works
}
```

**Adapt for ChatLogService (D-10):**
```java
// Currently (ChatLogService.java:29):
public void save(String question, ChatAnswerResponse response, GroundingStatus groundingStatus,
                 String conversationId, int promptTokens, int completionTokens, int responseTime,
                 String pipelineLog) {

// Target (P7):
@Async(ChatLogAsyncConfig.CHAT_LOG_EXECUTOR)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void save(String question, ChatAnswerResponse response, GroundingStatus groundingStatus,
                 String conversationId, int promptTokens, int completionTokens, int responseTime,
                 String pipelineLog) {
    // body unchanged — ChatLog.builder() + chatLogRepository.save(...) stay as-is (lines 32-43)
}
```

**Class-level annotations stay:** `@Service @RequiredArgsConstructor @Slf4j` (ChatLogService.java:22-24).

**Cross-bean call-site already correct:** `ChatService.java:49` holds `private final ChatLogService chatLogService;` — proxy invocation is safe (Pitfall A guarded).

---

### `ChatService.java` (MODIFY — orchestrator with chitchat short-circuit + snapshot + slim draft)

**Analog:** self. The existing `doAnswer` method (ChatService.java:86-191) is the authoritative shape.

**Snapshot pattern to ADD before every `chatLogService.save(...)` call-site** (D-11):

Current refusal branch (ChatService.java:131-139):
```java
ChatAnswerResponse refused = refusalResponse();
try {
    String pipelineLog = String.join("\n", logMessages);
    chatLogService.save(question, refused, GroundingStatus.REFUSED, null, 0, 0, 0, pipelineLog);
    // ...
```

**Change to:**
```java
ChatAnswerResponse refused = refusalResponse();
try {
    // D-11: snapshot BEFORE async handoff — prevents ConcurrentModificationException race (Pitfall 7)
    String pipelineLog = String.join("\n", List.copyOf(logMessages));
    chatLogService.save(question, refused, GroundingStatus.REFUSED, null, 0, 0, 0, pipelineLog);
    // ...
```

Apply same `List.copyOf(logMessages)` wrap at grounded call-site (ChatService.java:180-184).

**Chitchat short-circuit (D-02) — placement: immediately after `logger.accept("User prompt: ...")` (ChatService.java:94):**
```java
// D-02: Chitchat bypass — direct code, no flag. Rollback = git revert.
if (isGreetingOrChitchat(question)) {
    logger.accept("Path: chitchat — skipping retrieval + LLM (P7)");
    ChatAnswerResponse chitchat = answerComposer.composeChitchat(question);
    String pipelineLog = String.join("\n", List.copyOf(logMessages));
    chatLogService.save(question, chitchat, GroundingStatus.GROUNDED, null, 0, 0, 0, pipelineLog);
    return chitchat;
}
```

`isGreetingOrChitchat(...)` heuristic (Claude's discretion): Vietnamese greeting regex + short-length gate. Keep tiny.

**Drop scenario fields from `emptyDraft()` and `fallbackDraft()`:** Current signatures (ChatService.java:324-337 and 340-342) have 12 args ending with `...List.of(), null, null, List.of()`. After D-03, the `LegalAnswerDraft` record has 8 args; trim the tail four `List.of()/null` args.

---

### `ChatPromptFactory.java` (MODIFY — trim scenario instruction block, D-20)

**Analog:** self (ChatPromptFactory.java:60-62). The three lines mentioning `scenarioFacts, scenarioRule, scenarioOutcome, scenarioActions` are to be removed.

**Current lines to delete (verbatim, ChatPromptFactory.java:60, 62):**
```java
prompt.append("Tất cả các khóa conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps, scenarioFacts, scenarioRule, scenarioOutcome, scenarioActions phải luôn xuất hiện trong JSON.").append("\n");
// ...
prompt.append("Trả về JSON hợp lệ với đúng các khóa sau: conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps, scenarioFacts, scenarioRule, scenarioOutcome, scenarioActions.").append("\n");
```

**Replace with (scenario keys stripped):**
```java
prompt.append("Tất cả các khóa conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps phải luôn xuất hiện trong JSON.").append("\n");
// ...
prompt.append("Trả về JSON hợp lệ với đúng các khóa sau: conclusion, answer, uncertaintyNotice, legalBasis, penalties, requiredDocuments, procedureSteps, nextSteps.").append("\n");
```

**D-21 — DO NOT TOUCH:** `SYSTEM_CONTEXT_FALLBACK` (ChatPromptFactory.java:18-21) and `CLARIFICATION_RULES` (lines 23-28).

---

### `LegalAnswerDraft.java` (MODIFY — drop 4 fields, D-03)

**Analog:** self.

**Current (LegalAnswerDraft.java:5-19):**
```java
public record LegalAnswerDraft(
        String conclusion,
        String answer,
        String uncertaintyNotice,
        List<String> legalBasis,
        List<String> penalties,
        List<String> requiredDocuments,
        List<String> procedureSteps,
        List<String> nextSteps,
        List<String> scenarioFacts,
        List<String> scenarioRule,
        List<String> scenarioOutcome,
        List<String> scenarioActions
) {}
```

**Target (8 fields):**
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

**Call-site cascade:**
- `AnswerComposer.compose` reads `safeDraft.scenarioFacts()` at AnswerComposer.java:79 — per Research Open Question #1, pass `List.of()` literal instead of `safeDraft.scenarioFacts()` in the `ChatAnswerResponse` constructor (preserves public DTO shape; `List.of()` ≠ `null`, satisfies D-04 "no null tolerance fill").
- `AnswerComposer.java:49` fallback draft constructor — trim last 4 args to match new record shape.
- `ChatService.fallbackDraft()` (line 324) and `ChatService.emptyDraft()` (line 341) — trim last 4 args.
- `ScenarioAnswerComposer.java` — per Research Open Question #2, planner must Read this file in Wave-0 to confirm it does NOT touch these 4 fields before proceeding.

---

### `EmbeddingCacheConfig.java` (NEW — Caffeine cache manager, D-18)

**Analog:** `D:/jhipster/src/main/java/com/vn/core/config/CacheConfiguration.java` (external reference) + in-repo `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java` (bean shape).

**JHipster pattern to mirror (CacheConfiguration.java:19-21, 107-119):**
```java
@Configuration
@EnableCaching
public class CacheConfiguration {
    // ...
    // Quoted directive: "The TTL is set to 3600 s as a non-semantic safety ceiling only.
    // Actual cache correctness comes from write-path eviction ... The TTL prevents
    // unbounded map growth if eviction is somehow missed in a deployment edge case;
    // it is not intended as the freshness mechanism."
}
```

**Adapted (Caffeine, cache-name constant at consumer — JHipster pattern 2):**
```java
@Configuration
@EnableCaching
public class EmbeddingCacheConfig {

    public static final String EMBEDDING_CACHE = "embedding";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setCacheNames(List.of(EMBEDDING_CACHE));
        mgr.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)                      // D-18
            .expireAfterWrite(Duration.ofMinutes(30)) // D-18 safety ceiling ONLY
            .recordStats());                          // D-19 — enables Micrometer binding
        return mgr;
    }

    @Bean
    @Primary   // D-14 — override auto-configured OpenAiEmbeddingModel
    public EmbeddingModel cachingEmbeddingModel(EmbeddingModel delegate,
                                                 CacheManager cm,
                                                 AiModelProperties props,
                                                 @Value("${spring.ai.vectorstore.pgvector.dimensions:1536}") int dim) {
        return new CachingEmbeddingModel(delegate, cm,
            props.models().isEmpty() ? "unknown" : props.models().get(0).id(),
            dim);
    }
}
```

**`@Primary` pattern source:** `JacksonConfig.java:11-15`:
```java
@Bean
@Primary
public ObjectMapper objectMapper() {
    return new ObjectMapper();
}
```
Copy the annotation placement (above `@Bean`).

**Bean-shape reference (dependency injection of pre-built Spring AI bean):** `VectorStoreConfig.java:14-24` already injects `EmbeddingModel` into a `@Bean` method — the auto-configured OpenAI embedding model IS resolvable. Use the same constructor-injection style.

---

### `CachingEmbeddingModel.java` (NEW — decorator, D-14/D-15/D-16)

**Analog:** none in-repo. Use RESEARCH.md §Pattern 1 (Context7 verified) as the authoritative template. The core skeleton is already provided in RESEARCH.md §Pattern 1 (lines 242-292). Planner must copy it verbatim, with:
- cache-name constant from `EmbeddingCacheConfig.EMBEDDING_CACHE`
- normalize() per D-15 (Normalizer.Form.NFC + lowercase + trim — NO accent strip)
- SHA-256 helper per RESEARCH.md §Cache-key normalization code example
- dimension-mismatch eviction per D-16

**Package placement:** `com.vn.traffic.chatbot.ai.embedding` (new package — no existing `ai.embedding` directory in repo; create it).

---

### `EmbeddingModelChangedEvent.java` (NEW — ApplicationEvent, D-17)

**Analog:** no existing `ApplicationEvent` in repo (grep returned 0 matches for `ApplicationEvent|publishEvent|@EventListener`). Use canonical Spring shape:

```java
package com.vn.traffic.chatbot.ai.embedding;

import org.springframework.context.ApplicationEvent;

public class EmbeddingModelChangedEvent extends ApplicationEvent {
    private final String newModelId;

    public EmbeddingModelChangedEvent(Object source, String newModelId) {
        super(source);
        this.newModelId = newModelId;
    }

    public String newModelId() { return newModelId; }
}
```

**Listener pattern** (co-located in EmbeddingCacheConfig.java as static inner `@Component`, per RESEARCH.md §Pattern 3 lines 396-408):
```java
@Component
@RequiredArgsConstructor
static class EmbeddingCacheInvalidator {
    private final CacheManager cacheManager;

    @EventListener
    public void on(EmbeddingModelChangedEvent ev) {
        Cache c = cacheManager.getCache(EmbeddingCacheConfig.EMBEDDING_CACHE);
        if (c != null) c.clear();
    }
}
```

**Assumption A5 caveat:** `AiModelProperties` (AiModelProperties.java:18 — `record`) currently has no reload path. D-17 publishing call-site may need to be deferred if no refresh mechanism exists; the event type + listener are still wired in P7 (dormant until admin reload lands later).

---

### `application.yaml` (MODIFY — expose prometheus endpoint, Pitfall D)

**Analog:** self (application.yaml:118-125).

**Current (application.yaml:121-122):**
```yaml
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

**Target:**
```yaml
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

Add single token `prometheus`. If `./gradlew dependencyInsight --dependency micrometer-registry-prometheus` shows it's missing, also add to `build.gradle` dependencies: `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'`.

---

### `CachingEmbeddingModelTest.java` (NEW — pure unit, CACHE-02a/b/c)

**Analog:** `src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerTest.java`

**Imports + class-annotation pattern** (AnswerComposerTest.java:8-31):
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerComposerTest {
    private final AnswerComposer answerComposer = new AnswerComposer(fallbackPolicy());
    // ...
}
```

**Test-shape pattern** (AnswerComposerTest.java:33-56):
```java
@Test
void composeGroundedResponseUsesConclusionFirstAndIncludesDisclaimer() {
    ChatAnswerResponse response = answerComposer.compose(/* ... */);
    assertThat(response.answer()).startsWith("Kết luận:\n...");
}
```

Apply same style to CachingEmbeddingModelTest — construct `CachingEmbeddingModel` with a mocked `EmbeddingModel` delegate and a `ConcurrentMapCacheManager` test double (no `@SpringBootTest` needed — keep it pure unit per D-08).

---

### `ChatLogServiceAsyncIT.java` (NEW — async integration, PERF-03a/c)

**Analog (thin wave-0 pattern):** `src/test/java/com/vn/traffic/chatbot/common/config/AsyncConfigTest.java`
**Analog (service mocking style):** `src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogServiceTest.java`

**AsyncConfigTest pattern (AsyncConfigTest.java:12-29):**
```java
class AsyncConfigTest {
    @Test
    void asyncConfigImplementsAsyncConfigurer() {
        AsyncConfig config = new AsyncConfig();
        assertThat(config).isInstanceOf(AsyncConfigurer.class);
    }
}
```

**ChatLogServiceTest DI + ArgumentCaptor pattern (ChatLogServiceTest.java:22-53):**
```java
@ExtendWith(MockitoExtension.class)
class ChatLogServiceTest {
    @Mock private ChatLogRepository chatLogRepository;
    @InjectMocks private ChatLogService chatLogService;

    @Test
    void testLogPersistedAfterAnswer() {
        // ... Arrange
        chatLogService.save(...);
        ArgumentCaptor<ChatLog> captor = ArgumentCaptor.forClass(ChatLog.class);
        verify(chatLogRepository).save(captor.capture());
    }
}
```

**Adaptation for async IT:** Use `@SpringBootTest` to load real Spring context so `@Async` proxy + `@Transactional(REQUIRES_NEW)` are applied, and assert `Thread.currentThread().getName().startsWith("chat-log-")` inside the save invocation via a spy/@MockBean on `ChatLogRepository`. **Per D-07**, before writing this test, planner must Read Spring Boot `task-execution-and-scheduling.adoc` via Context7 `/spring-projects/spring-boot` for the canonical `@Async` test pattern.

---

### Frontend (`types/api.ts`, `message-bubble.tsx`, `scenario-accordion.tsx`)

**Analog:** self (same files). Action = delete branches referring to `scenarioRule/Outcome/Actions` — no new code pattern introduced.

**Pattern:** per Research §Recommended Project Structure (lines 225-229) + RESEARCH.md constraint ("frontend AGENTS.md — 'This is NOT the Next.js you know'"): planner must Read `frontend/node_modules/next/dist/docs/` before editing. No new component creation; deletions only.

---

## Shared Patterns

### Cross-bean `@Async` invocation (Pitfall A guard)

**Source:** `IngestionOrchestrator.java:60` (`@Async("ingestionExecutor")` on bean method) + `CheckRunner.java:34` (same). Both are called from OTHER beans, never via `this.*`.

**Apply to:** `ChatLogService.save` — called from `ChatService.doAnswer` (already cross-bean, ChatService.java:49 injects ChatLogService). Pattern requirement: DO NOT add an internal wrapper method in `ChatLogService` that calls `this.save(...)`.

### `@Service @RequiredArgsConstructor @Slf4j` class header

**Source:** `ChatLogService.java:22-24`, `IngestionOrchestrator.java:45-47`, `CheckRunner.java:23-25`.

**Apply to:** any new Spring-managed service (if planner adds one; `CachingEmbeddingModel` is NOT `@Service` — it's a `@Bean` declared in `EmbeddingCacheConfig`).

### `@Configuration` + constant-at-consumer (cache / executor names)

**Source:** `EmbeddingCacheConfig.EMBEDDING_CACHE` (new) mirrors JHipster `RequestPermissionSnapshot.PERMISSION_MATRIX_CACHE` (CacheConfiguration.java:117). Executor-name constant `ChatLogAsyncConfig.CHAT_LOG_EXECUTOR` mirrors same idea.

**Apply to:** all new config classes. Always expose cache/executor name as `public static final String` on the config class; consumers use the constant, never a magic string.

### Pure unit test (Mockito + JUnit 5)

**Source:** `AnswerComposerTest.java`, `ChatLogServiceTest.java`, `CrlfSanitizerTest.java`.

**Apply to:** `CachingEmbeddingModelTest` (CACHE-02a/b/c), any pure domain test. Per D-08, JUnit normal. NO `@SpringBootTest` for pure logic.

### Spring Boot integration test shape

**Source:** `SpringBootSmokeTest.java`, `ChatFlowIntegrationTest.java`, `ChatLogServiceTest` (unit but uses ArgumentCaptor pattern).

**Apply to:** `ChatLogServiceAsyncIT`, `EmbeddingCacheConfigTest` (CACHE-02d), `CacheMetricsSmokeIT`. Per D-07 Spring-AI-adjacent tests — consult Context7 `/spring-projects/spring-ai` + `/spring-projects/spring-boot` testing docs first.

---

## No Analog Found

| File | Role | Data Flow | Reason | Mitigation |
|------|------|-----------|--------|-----------|
| `CachingEmbeddingModel.java` | service (decorator over Spring AI) | transform | Repo has no existing decorator over a Spring AI model | Use RESEARCH.md §Pattern 1 verbatim (Context7-verified) |
| `EmbeddingModelChangedEvent.java` | ApplicationEvent | pub-sub | Repo has NO existing `ApplicationEvent`, `publishEvent`, or `@EventListener` (grep confirmed) | Canonical Spring shape; listener co-located per JHipster reference |

---

## Metadata

**Analog search scope:** `src/main/java/com/vn/traffic/chatbot/**`, `src/test/java/com/vn/traffic/chatbot/**`, `D:/jhipster/src/main/java/com/vn/core/config/CacheConfiguration.java`, `src/main/resources/application.yaml`.

**Grep probes run:**
- `@Primary` → 1 hit (JacksonConfig.java:12)
- `@Async` → 2 hits (IngestionOrchestrator.java:60, CheckRunner.java:34) + AsyncConfig javadoc
- `ThreadPoolTaskExecutor` → 0 production hits (only javadoc reference) — NEW pattern in P7
- `@EventListener|ApplicationEvent|publishEvent` → 0 hits — NEW pattern in P7
- `@EnableAsync` → 1 hit (AsyncConfig.java:28) — MUST NOT duplicate

**Key insights for planner:**
1. `@EnableAsync` already declared on `AsyncConfig` — `ChatLogAsyncConfig` must NOT redeclare it.
2. Existing `AsyncConfigurer` uncaught-handler (AsyncConfig.java:65-70) auto-covers the new `chatLogExecutor` — no duplication.
3. `VectorStoreConfig` injects `EmbeddingModel` as a constructor-arg (line 15) — when `@Primary CachingEmbeddingModel` bean lands, `VectorStoreConfig` WILL receive the decorated instance (per Spring's `@Primary` resolution). This is the intended transparency (Assumption A3). Verify with one boot smoke post-Wave-3.
4. `ChatLogService.save` is already cross-bean invoked (ChatService.java:49) — `@Async` proxy will engage correctly (Pitfall A guarded by existing code shape).
5. Scenario field removal: `AnswerComposer.java:79` is the only live-read site for `safeDraft.scenarioFacts()`. Research Open Question #1 must be resolved before trimming `LegalAnswerDraft`.

**Pattern extraction date:** 2026-04-17

---

## PATTERN MAPPING COMPLETE
