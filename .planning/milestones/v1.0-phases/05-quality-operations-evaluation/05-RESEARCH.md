# Phase 5: Quality Operations & Evaluation — Research

**Researched:** 2026-04-12
**Domain:** LLM observability + answer evaluation (Spring Boot backend + Next.js admin UI)
**Confidence:** HIGH — all findings are verified against codebase source files or jmix reference implementation

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Each log entry stores: `question`, `answer`, `sources` (comma-separated), `promptTokens`, `completionTokens`, `responseTime`, `groundingStatus` (enum column), `conversationId`, `createdDate`.
- **D-02:** One log entry per chatbot answer. `ChatService.answer()` retrofitted to persist after composing response.
- **D-03:** `groundingStatus` is a dedicated enum-backed column. Enables filtering without parsing sources.
- **D-04:** Chat logs are append-only. No delete or soft-delete endpoint.
- **D-05:** Log list uses DataTable pattern: paginated, sortable by date desc, filterable by date range and grounding status. Text search on `question` via LIKE.
- **D-06:** Log detail: question, full answer, citations panel, grounding status badge, token counts, response time.
- **D-07:** Text search applies to `question` column via LIKE query. Sufficient for v1.
- **D-08:** `CheckDef` fields: `question`, `referenceAnswer`, `category`, `active`. Matches jmix shape.
- **D-09:** Admin UI CRUD form only for CheckDef. No bulk import in v1.
- **D-10:** Check runs are on-demand only — admin clicks "Run checks". Backend runs all active CheckDef entries asynchronously and stores results. No scheduler.
- **D-11:** Each `CheckRun` captures a reference to or snapshot of the active `AiParameterSet` at run time.
- **D-12:** Results UI: run history list (date, overall score, parameter set ref). Run detail: per-check results table (question, reference answer, actual answer, score).
- **D-13:** Evaluator uses LLM-as-judge via Spring AI. Returns 0–1 semantic similarity score. Evaluator model configurable independently.
- **D-14:** Both `chatModel` and `evaluatorModel` are new fields on existing `AiParameterSet`. Independent settings.
- **D-15:** Allowed model list is a hardcoded enum/constant in the backend. Admin picks from dropdown.

### Claude's Discretion

- `ChatLog` entity package location (e.g., `chat/domain/` or `chat/log/`)
- Exact `groundingStatus` enum name and mapping from `GroundingStatus` already in `ChatService`
- `CheckRun` / `CheckDef` / `Check` entity package layout
- AiParameterSet Liquibase migration column names for `chatModel` and `evaluatorModel`
- LLM-as-judge prompt wording for the evaluator
- Whether `CheckRun` stores the full AiParameterSet snapshot as JSON or just a foreign-key reference
- Thread safety and error handling in the async check runner
- Frontend DataTable column selection and filter component reuse

### Deferred Ideas (OUT OF SCOPE)

- Scheduled / automatic check runs
- Score trend charts / dashboards
- Bulk import of check definitions
- Chat log deletion / retention policy
- Multi-provider model registry
- Per-check grading rubrics
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| ADMIN-04 | Admin can review chat logs for past conversations | ChatLog entity design, ChatService retrofit, log list/detail REST + UI patterns |
| ADMIN-05 | Admin can define and run answer checks to evaluate chatbot quality | CheckDef/CheckRun/Check entity design, async check runner with virtual threads, LLM-as-judge evaluator, check admin UI |
</phase_requirements>

---

## Summary

Phase 5 is an operational hardening phase: retrofit the existing `ChatService.answer()` path to produce a persistent audit log entry, then build an evaluation harness (CheckDef + CheckRun + Check) that exercises the chat pipeline against known question/answer pairs using an LLM-as-judge. The jmix-ai-backend is the direct blueprint: every entity shape, the executor pattern in `CheckRunner`, and the evaluator contract in `ExternalEvaluatorImpl` have been read and are used as the design baseline.

The critical path is: ChatLog entity + Liquibase migration → ChatService retrofit → REST endpoints → UI. Everything downstream (checks, evaluator) depends on the chat pipeline continuing to work identically; the retrofit must be a non-breaking additive side-effect, not a refactor of the answer path.

`AiParameterSet` currently has `name`, `active`, `content`, `created_at`, `updated_at`. Two new columns (`chat_model`, `evaluator_model`) are added via a Liquibase changeSet numbered 008. The existing `AiParameterSetService` and frontend form are extended, not replaced.

**Primary recommendation:** Ship backend entities + ChatService log hook first (Wave 1), then check engine + evaluator (Wave 2), then all six admin UI screens (Wave 3). Blocking dependency: logs must exist before log UI; checks depend on logs (check runner calls ChatService).

---

## Standard Stack

### Core (all already on classpath — no new dependencies required)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | matches Spring Boot version in pom.xml | Entity persistence, repositories for ChatLog, CheckDef, CheckRun, Check | Already used by all entities in codebase |
| Liquibase | matches Spring Boot | Schema migrations for 4 new tables + 2 AiParameterSet columns | Project-standard migration tool; changesets 001–007 already exist |
| Spring `@Async` + `SimpleAsyncTaskExecutor` | Spring Boot (virtual threads) | Async check runner on virtual threads | Already configured in `AsyncConfig`; `ingestionExecutor` is the pattern |
| Spring AI `ChatClient` / `ChatModel` | matches project Spring AI version | LLM-as-judge evaluator call | Already used in `ChatService` for the main chat path |
| TanStack Query v5 | matches frontend package.json | All API calls from admin UI screens | Established pattern; `use-parameters.ts` is the reference |
| TanStack Table v8 | matches frontend | DataTable for chat log list, check run list, check result table | `components/admin/data-table.tsx` already built |
| shadcn/ui (Sheet, Checkbox, Separator) | installed per components.json | Row-expansion sheet in check run detail, CheckDef active toggle, parameters form divider | All confirmed installed (UI-SPEC component inventory) |
| react-hook-form + zod | installed per parameters page | CheckDef CRUD form validation | Already used in `parameters/page.tsx` |

### No New Dependencies
[VERIFIED: codebase scan] All libraries required for Phase 5 are already on the classpath or installed in the frontend. No `npm install` or `pom.xml` additions are needed.

---

## Architecture Patterns

### Backend Package Layout

Follow the existing domain-first package structure:

```
src/main/java/com/vn/traffic/chatbot/
├── chatlog/                         # NEW — ChatLog domain
│   ├── domain/
│   │   └── ChatLog.java
│   ├── repo/
│   │   └── ChatLogRepository.java
│   ├── service/
│   │   └── ChatLogService.java
│   └── api/
│       ├── ChatLogAdminController.java
│       └── dto/
│           ├── ChatLogResponse.java
│           └── ChatLogDetailResponse.java
├── checks/                          # NEW — Check engine domain
│   ├── domain/
│   │   ├── CheckDef.java
│   │   ├── CheckRun.java
│   │   └── Check.java
│   ├── repo/
│   │   ├── CheckDefRepository.java
│   │   ├── CheckRunRepository.java
│   │   └── CheckRepository.java
│   ├── service/
│   │   ├── CheckDefService.java
│   │   ├── CheckRunService.java
│   │   └── CheckRunner.java         # Async orchestrator
│   ├── evaluator/
│   │   ├── SemanticEvaluator.java   # Interface (like ExternalEvaluator)
│   │   └── LlmSemanticEvaluator.java  # Spring AI implementation
│   └── api/
│       ├── CheckDefAdminController.java
│       ├── CheckRunAdminController.java
│       └── dto/
│           ├── CheckDefResponse.java
│           ├── CheckRunResponse.java
│           ├── CheckRunDetailResponse.java
│           └── CheckResultResponse.java
└── parameter/
    └── domain/
        └── AllowedModel.java        # NEW — hardcoded enum of allowed model IDs
```

### Frontend File Layout

```
frontend/
├── app/(admin)/
│   ├── chat-logs/
│   │   ├── page.tsx                 # Chat Log list (DataTable + filters)
│   │   └── [id]/page.tsx            # Chat Log detail (read-only)
│   ├── checks/
│   │   ├── page.tsx                 # CheckDef CRUD (master-detail)
│   │   └── runs/
│   │       ├── page.tsx             # CheckRun history list (DataTable)
│   │       └── [id]/page.tsx        # CheckRun detail (summary + per-check DataTable)
│   └── parameters/page.tsx          # EXTENDED: add chatModel + evaluatorModel selects
├── hooks/
│   ├── use-chat-logs.ts             # NEW
│   ├── use-check-defs.ts            # NEW
│   └── use-check-runs.ts            # NEW
├── lib/api/
│   ├── chat-logs.ts                 # NEW
│   ├── check-defs.ts                # NEW
│   └── check-runs.ts                # NEW
└── types/api.ts                     # EXTENDED: add ChatLog*, CheckDef*, CheckRun*, AllowedModel types
```

### Pattern 1: ChatLog Entity (Adapted from jmix ChatLog)

The jmix `ChatLog` uses `@Lob String content` for the full answer text. This project splits that into `question` + `answer` separate columns (per D-01), uses `OffsetDateTime createdDate`, and adds `groundingStatus` (enum column) not present in jmix. The `conversationId` column maps to thread context. Token counts and response time come from the Spring AI usage metadata.

Key field mapping from jmix to this project:
- `content` (jmix: combined log messages) → `question` + `answer` (separate `TEXT` columns)
- `sources` → retained as comma-separated citation references (TEXT)
- Add `grounding_status VARCHAR(30)` — new, not in jmix
- Keep `prompt_tokens`, `completion_tokens`, `response_time` — identical

```java
// Source: verified against jmix ChatLog.java + 05-CONTEXT.md D-01
@Entity
@Table(name = "chat_log", indexes = {
    @Index(name = "idx_chat_log_created_date", columnList = "created_date"),
    @Index(name = "idx_chat_log_grounding_status", columnList = "grounding_status"),
    @Index(name = "idx_chat_log_conversation_id", columnList = "conversation_id")
})
public class ChatLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sources", columnDefinition = "TEXT")
    private String sources;

    @Enumerated(EnumType.STRING)
    @Column(name = "grounding_status", length = 30)
    private GroundingStatus groundingStatus;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "response_time")
    private Integer responseTime;

    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private OffsetDateTime createdDate;
}
```

`GroundingStatus` is already defined in `ChatService` as an inner enum (`GROUNDED`, `LIMITED_GROUNDING`, `REFUSED`). Extract it to a top-level class in the `chatlog` domain or `chat/domain` package so both `ChatService` and `ChatLog` can reference it.

### Pattern 2: ChatService Retrofit (log hook)

The retrofit inserts a single call **after** `answerComposer.compose()` returns, before returning to the controller. It is a non-blocking side-effect: if the save throws, the log failure must NOT break the chat response. Use try-catch to swallow log persistence errors.

```java
// Source: verified against ChatService.java + jmix ChatLogManager.java
public ChatAnswerResponse answer(String question) {
    // ... existing retrieval, grounding, compose logic unchanged ...
    ChatAnswerResponse response = answerComposer.compose(groundingStatus, draft, citations, sources);

    // Retrofit: persist log entry as side-effect (non-blocking, non-blocking failure)
    try {
        long startedAt = /* capture before compose */ System.currentTimeMillis();
        chatLogService.save(question, response, groundingStatus, /* conversationId */ null);
    } catch (Exception ex) {
        log.warn("Failed to persist chat log entry: {}", ex.getMessage());
    }
    return response;
}
```

**Token count capture:** Spring AI `ChatResponse` exposes `Usage` via `response.getMetadata().getUsage()`. The current `ChatService` calls `chatClient.prompt().user(prompt).call().content()` — this discards the metadata. To capture tokens, switch from `.content()` to `.chatResponse()` and read `getMetadata().getUsage()`:

```java
// Source: [ASSUMED] based on Spring AI ChatClient API — verify against Context7 if uncertain
ChatResponse chatResponse = chatClient.prompt().user(prompt).call().chatResponse();
String modelPayload = chatResponse.getResult().getOutput().getText();
Usage usage = chatResponse.getMetadata().getUsage();
int promptTokens = (int) usage.getPromptTokens();
int completionTokens = (int) usage.getGenerationTokens();
```

**Response time capture:** Record `System.currentTimeMillis()` before the `chatClient.prompt()...call()` and subtract after. Store difference in milliseconds as `Integer responseTime`.

**conversationId:** The current `ChatService.answer(String question)` signature does not accept a conversation/thread ID. For log grouping by thread, the controller must pass the threadId through to the service. This requires a signature change: `answer(String question, String conversationId)` or an overload. Confirm whether multi-turn (Phase 3) already threads conversation ID through the call stack.

[VERIFIED: ChatService.java] The current `answer()` method takes only `String question`. Phase 3 (multi-turn) will introduce threadId. If Phase 3 is not yet shipped, `conversationId` in ChatLog will be null until the signature is extended. This is acceptable for v1: the ChatLog still captures the exchange; thread grouping is added when the threadId is available.

### Pattern 3: Check Entity Design (Adapted from jmix)

The jmix `CheckDef` uses `@Lob String answer` for the reference answer. This project renames it `referenceAnswer` per D-08 to be unambiguous in DTOs alongside `actualAnswer` from `Check`.

The jmix `CheckRun` stores `parameters` as a `@Lob String` (raw YAML snapshot). For this project, the decision (D-11, Claude's Discretion) is to store either:
- **Option A (FK reference):** `parameter_set_id UUID` foreign key to `ai_parameter_set` — simple, but loses history if the parameter set is deleted
- **Option B (JSON snapshot):** `parameter_set_snapshot TEXT` — JSON of the parameter set name + key fields at run time

**Recommendation (Claude's discretion):** Use a hybrid: store `parameter_set_id` as a nullable FK **and** `parameter_set_name VARCHAR(255)` as a snapshot of the name at run time. This supports display ("Bộ tham số: Default v2") even if the set is later deleted, without storing full YAML. The `parameters` full-YAML column from jmix is omitted (reduces bloat).

The jmix `Check` entity uses `@ManyToOne CheckDef` and `@ManyToOne CheckRun`. Use the same bidirectional FK approach — `check_def_id` and `check_run_id` FK columns on the `check_result` table.

**Table naming:** Do not use `check` as a table name — it is a reserved SQL keyword in PostgreSQL. Use `check_result` or `check_item`. The jmix codebase works around this with `@Table(name = "CHECK_")` and `@Entity(name = "Check_")`. In this project, use `check_result` to avoid the reserved-word issue entirely.

```java
// Source: verified against jmix Check.java + PostgreSQL reserved word constraint
@Entity
@Table(name = "check_result", indexes = {
    @Index(name = "idx_check_result_check_def", columnList = "check_def_id"),
    @Index(name = "idx_check_result_check_run", columnList = "check_run_id")
})
public class CheckResult {
    // ... id, createdDate ...
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_def_id")
    private CheckDef checkDef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_run_id")
    private CheckRun checkRun;

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "reference_answer", columnDefinition = "TEXT")
    private String referenceAnswer;

    @Column(name = "actual_answer", columnDefinition = "TEXT")
    private String actualAnswer;

    @Column(name = "score")
    private Double score;

    @Column(name = "log", columnDefinition = "TEXT")
    private String log; // evaluator rationale
}
```

`CheckRun` shape:

```java
@Entity
@Table(name = "check_run")
public class CheckRun {
    // ... id, createdDate ...
    @Column(name = "average_score")
    private Double averageScore;

    @Column(name = "parameter_set_id", columnDefinition = "uuid")
    private UUID parameterSetId;          // FK snapshot (nullable — set may be deleted)

    @Column(name = "parameter_set_name", length = 255)
    private String parameterSetName;      // name snapshot for display

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private CheckRunStatus status;        // RUNNING, COMPLETED, FAILED

    @Column(name = "check_count")
    private Integer checkCount;
}
```

`CheckRunStatus` enum: `RUNNING`, `COMPLETED`, `FAILED`. Required for frontend polling logic (UI-SPEC interaction pattern: poll while any run has `status === 'RUNNING'`).

### Pattern 4: Async Check Runner (Virtual Threads)

The jmix `CheckRunner` creates a fixed-thread pool via `Executors.newFixedThreadPool(parallelism)` per invocation. This project already has `SimpleAsyncTaskExecutor` with virtual threads configured in `AsyncConfig`. Use `@Async("ingestionExecutor")` on the `CheckRunner.runAll()` method — the REST endpoint triggers it and returns a `CheckRun` ID immediately; the runner completes asynchronously.

```java
// Source: verified against AsyncConfig.java + jmix CheckRunner.java pattern
@Service
@RequiredArgsConstructor
public class CheckRunner {

    private final CheckRunRepository checkRunRepository;
    private final CheckDefRepository checkDefRepository;
    private final CheckResultRepository checkResultRepository;
    private final ChatService chatService;
    private final SemanticEvaluator evaluator;

    @Async("ingestionExecutor")
    public void runAll(UUID checkRunId) {
        CheckRun run = checkRunRepository.findById(checkRunId).orElseThrow();
        List<CheckDef> activeDefs = checkDefRepository.findByActiveTrue();

        if (activeDefs.isEmpty()) {
            run.setAverageScore(0.0);
            run.setStatus(CheckRunStatus.FAILED);
            checkRunRepository.save(run);
            return;
        }

        // Virtual threads handle I/O concurrency automatically — no explicit pool needed
        // Use CompletableFuture with the virtual-thread executor, or serial loop
        // (serial is simpler; virtual threads inside ChatService calls still benefit)
        List<CheckResult> results = new ArrayList<>();
        for (CheckDef def : activeDefs) {
            results.add(runSingle(def, run));
        }

        double avg = results.stream()
            .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
            .average().orElse(0.0);

        checkResultRepository.saveAll(results);
        run.setAverageScore(avg);
        run.setCheckCount(results.size());
        run.setStatus(CheckRunStatus.COMPLETED);
        checkRunRepository.save(run);
    }
}
```

**Note on parallelism:** The jmix pattern uses a fixed thread pool per run. For this project, since virtual threads are configured system-wide, running checks serially within the `@Async` method is simpler and still benefits from virtual-thread non-blocking I/O in the underlying Spring AI calls. If parallel check execution within a run is needed, `CompletableFuture.supplyAsync(task, ingestionExecutor)` can be used directly.

**Transaction boundary:** The `@Async` method runs in a new transaction context. Each `checkResultRepository.save()` should be transactional. Use `@Transactional` on `runAll()` or save in a `@Transactional` inner service to avoid detached entity errors.

### Pattern 5: LLM-as-Judge Evaluator

The jmix `ExternalEvaluatorImpl` constructs an `OpenAiChatModel` directly from API key + model string. This project uses the Spring AI `ChatClient` bean already configured with the project's AI provider. The evaluator should use the same `ChatClient` infrastructure but optionally with different model options injected from `AiParameterSet.evaluatorModel`.

```java
// Source: verified against jmix ExternalEvaluatorImpl.java + Spring AI ChatModel pattern
// [ASSUMED] exact Spring AI ChatOptions API — verify with Context7 if Spring AI version changed
public interface SemanticEvaluator {
    double evaluate(String referenceAnswer, String actualAnswer);
}

@Component
public class LlmSemanticEvaluator implements SemanticEvaluator {

    private static final String SYSTEM_PROMPT = """
        Bạn đánh giá độ tương đồng ngữ nghĩa giữa câu trả lời tham chiếu và câu trả lời thực tế.
        Cho điểm từ 0 đến 1 theo tiêu chí:
        - Độ chính xác ngữ nghĩa so với tham chiếu: 60%
        - Đầy đủ các điểm chính: 30%
        - Trừ điểm nếu có mâu thuẫn, thông tin sai, hoặc nội dung không liên quan: 10%
        - Nếu ngôn ngữ không khớp, áp dụng hệ số phạt mạnh.

        Trả về JSON hợp lệ (không có markdown fences):
        {"score": <số 0..1>, "verdict": "PASS"|"PARTIAL"|"FAIL", "rationale": "giải thích ngắn", "languageMatch": true|false}
        """;

    private final ChatClient chatClient;

    public LlmSemanticEvaluator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public double evaluate(String referenceAnswer, String actualAnswer) {
        try {
            String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("Câu trả lời tham chiếu:\n" + referenceAnswer
                    + "\n\nCâu trả lời thực tế:\n" + actualAnswer)
                .call()
                .content();
            return parseScore(content);
        } catch (Exception ex) {
            log.error("Evaluator failed: {}", ex.getMessage(), ex);
            return 0.0;
        }
    }
}
```

**Model selection:** When `AiParameterSet.evaluatorModel` is set, the evaluator should use it. Spring AI supports per-request model override via `ChatOptions`. The exact API for overriding model per call depends on the Spring AI version in the pom — verify with Context7 before implementing. [ASSUMED: `ChatClient.prompt().options(options).call()` where options specifies a model override — confirm API.]

**Score parsing:** Same JSON extraction pattern as jmix: regex for `\{.*\}` with `Pattern.DOTALL`, then Jackson parse. Apply language-mismatch penalty (cap at 0.2 if `languageMatch == false`).

**Language note:** The evaluator prompt should be Vietnamese-first since reference answers are in Vietnamese. The jmix system prompt is English; adapt it to Vietnamese per the project's Vietnamese-first constraint.

### Pattern 6: AiParameterSet Extension

The existing `AiParameterSet` entity has `name`, `active`, `content`, `created_at`, `updated_at`. Add two new nullable String columns:

```java
// Source: verified against AiParameterSet.java
@Column(name = "chat_model", length = 200)
private String chatModel;          // e.g., "claude-3-5-sonnet-20241022"

@Column(name = "evaluator_model", length = 200)
private String evaluatorModel;     // e.g., "claude-3-haiku-20240307"
```

Both are nullable — existing parameter sets have no model selection until admin updates them. The system falls back to the Spring AI auto-configured model when null.

`AllowedModel` enum (hardcoded constant list, D-15):

```java
// Source: [ASSUMED] — model list to confirm with project's AI provider config
public enum AllowedModel {
    CLAUDE_3_5_SONNET("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet"),
    CLAUDE_3_5_HAIKU("claude-3-5-haiku-20241022", "Claude 3.5 Haiku"),
    CLAUDE_3_OPUS("claude-3-opus-20240229", "Claude 3 Opus");
    // ... extend as needed

    private final String modelId;
    private final String displayName;
}
```

The backend exposes an endpoint (`GET /api/v1/admin/allowed-models`) returning the enum values as a list. Frontend dropdowns load from this endpoint.

### Pattern 7: REST API Design

New paths follow existing `ApiPaths` constant convention:

```java
// Source: verified against ApiPaths.java existing patterns
// Chat logs
public static final String CHAT_LOGS = ADMIN_BASE + "/chat-logs";
public static final String CHAT_LOG_BY_ID = CHAT_LOGS + "/{logId}";

// Check definitions
public static final String CHECK_DEFS = ADMIN_BASE + "/check-defs";
public static final String CHECK_DEF_BY_ID = CHECK_DEFS + "/{defId}";

// Check runs
public static final String CHECK_RUNS = ADMIN_BASE + "/check-runs";
public static final String CHECK_RUN_BY_ID = CHECK_RUNS + "/{runId}";
public static final String CHECK_RUNS_TRIGGER = ADMIN_BASE + "/check-runs/trigger";
public static final String CHECK_RUN_RESULTS = CHECK_RUN_BY_ID + "/results";

// Allowed models
public static final String ALLOWED_MODELS = ADMIN_BASE + "/allowed-models";
```

**Chat log list endpoint:** `GET /api/v1/admin/chat-logs?page=0&size=20&sort=createdDate,desc&groundingStatus=REFUSED&dateFrom=2026-01-01&dateTo=2026-12-31&question=camera`

Returns `PageResponse<ChatLogResponse>` — reuse the existing `PageResponse<T>` type already in `common/api/PageResponse.java`.

**Chat log detail:** `GET /api/v1/admin/chat-logs/{logId}` — returns `ChatLogDetailResponse` with full answer, sources list (split by comma), citations (if stored), grounding status.

**Check run trigger:** `POST /api/v1/admin/check-runs/trigger` — creates `CheckRun` in RUNNING state, triggers `@Async` runner, returns `CheckRunResponse` with the new run ID immediately (HTTP 202 Accepted).

**Check run results:** `GET /api/v1/admin/check-runs/{runId}/results` — returns list of `CheckResultResponse` for the run.

### Pattern 8: Frontend Hook Pattern

Follow the established pattern from `use-parameters.ts` / `use-trust-policy.ts`:

```typescript
// Source: verified against hooks/use-parameters.ts
// New: hooks/use-chat-logs.ts
export function useChatLogs(filters: ChatLogFilters) {
  return useQuery({
    queryKey: queryKeys.chatLogs(filters),
    queryFn: () => fetchChatLogs(filters),
  });
}

// New: hooks/use-check-runs.ts — with auto-poll when any run is RUNNING
export function useCheckRuns() {
  const { data, ...rest } = useQuery({
    queryKey: queryKeys.checkRuns,
    queryFn: fetchCheckRuns,
    refetchInterval: (query) => {
      const runs = query.state.data;
      return runs?.some((r: CheckRunResponse) => r.status === 'RUNNING') ? 5000 : false;
    },
  });
  return { data, ...rest };
}
```

### Anti-Patterns to Avoid

- **Do not throw in the ChatService log hook:** If `chatLogService.save()` throws, swallow the error. The chat response must return regardless.
- **Do not use `check` as a table name:** It is reserved in PostgreSQL. Use `check_result` or `check_item`.
- **Do not run check evaluator calls synchronously in the REST handler:** The trigger endpoint returns 202 immediately. The runner is `@Async`.
- **Do not create a new thread pool in `CheckRunner`:** Use `@Async("ingestionExecutor")` — do not replicate the jmix `Executors.newFixedThreadPool()` pattern; virtual threads are already configured.
- **Do not store full YAML in `CheckRun.parameters` column:** The jmix approach stores the full YAML snapshot, which can be large. This project stores `parameterSetId` + `parameterSetName` only.
- **Do not hardcode English in the evaluator prompt:** All user-facing strings and evaluator prompts must be Vietnamese-first.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Paginated query with filters | Custom SQL string builder | Spring Data JPA `Pageable` + `@Query` with params | Type-safe, tested, handles edge cases |
| JSON score extraction from LLM response | Regex from scratch | Adapt jmix `extractJsonObject()` pattern (Pattern.DOTALL matcher) | Already proven; handles leading/trailing text |
| Async method execution | New `ExecutorService` inside service | `@Async("ingestionExecutor")` on service method | Virtual threads already configured |
| Frontend polling | setInterval + manual fetch | TanStack Query `refetchInterval` callback | Stops automatically when terminal state reached |
| Form validation | Manual error state | react-hook-form + zod (already used in parameters page) | Consistent with established pattern |

---

## Liquibase Migration Plan

Next changeset is **008**. This phase requires four changesets (or one changeset with multiple operations):

### 008-1: Add chatModel + evaluatorModel to ai_parameter_set

```xml
<changeSet id="008-1" author="gsd-plan">
    <comment>Add chat_model and evaluator_model to ai_parameter_set for Phase 5 model selection</comment>
    <addColumn tableName="ai_parameter_set">
        <column name="chat_model" type="VARCHAR(200)"/>
        <column name="evaluator_model" type="VARCHAR(200)"/>
    </addColumn>
</changeSet>
```

### 008-2: Create chat_log table

```xml
<changeSet id="008-2" author="gsd-plan">
    <createTable tableName="chat_log">
        <column name="id" type="uuid" defaultValueComputed="gen_random_uuid()">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="conversation_id" type="VARCHAR(255)"/>
        <column name="question" type="TEXT"/>
        <column name="answer" type="TEXT"/>
        <column name="sources" type="TEXT"/>
        <column name="grounding_status" type="VARCHAR(30)"/>
        <column name="prompt_tokens" type="INTEGER"/>
        <column name="completion_tokens" type="INTEGER"/>
        <column name="response_time" type="INTEGER"/>
        <column name="created_date" type="TIMESTAMPTZ" defaultValueComputed="now()">
            <constraints nullable="false"/>
        </column>
    </createTable>
    <!-- indexes on grounding_status and created_date for filter queries -->
    <createIndex indexName="idx_chat_log_created_date" tableName="chat_log">
        <column name="created_date"/>
    </createIndex>
    <createIndex indexName="idx_chat_log_grounding_status" tableName="chat_log">
        <column name="grounding_status"/>
    </createIndex>
    <createIndex indexName="idx_chat_log_conversation_id" tableName="chat_log">
        <column name="conversation_id"/>
    </createIndex>
</changeSet>
```

### 008-3: Create check_def, check_run, check_result tables

```xml
<changeSet id="008-3" author="gsd-plan">
    <createTable tableName="check_def">
        <column name="id" type="uuid" defaultValueComputed="gen_random_uuid()">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="question" type="TEXT"><constraints nullable="false"/></column>
        <column name="reference_answer" type="TEXT"><constraints nullable="false"/></column>
        <column name="category" type="VARCHAR(255)"/>
        <column name="active" type="BOOLEAN" defaultValueBoolean="true">
            <constraints nullable="false"/>
        </column>
        <column name="created_at" type="TIMESTAMPTZ" defaultValueComputed="now()">
            <constraints nullable="false"/>
        </column>
        <column name="updated_at" type="TIMESTAMPTZ" defaultValueComputed="now()">
            <constraints nullable="false"/>
        </column>
    </createTable>

    <createTable tableName="check_run">
        <column name="id" type="uuid" defaultValueComputed="gen_random_uuid()">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="average_score" type="DOUBLE PRECISION"/>
        <column name="parameter_set_id" type="uuid"/>
        <column name="parameter_set_name" type="VARCHAR(255)"/>
        <column name="status" type="VARCHAR(20)"><constraints nullable="false"/></column>
        <column name="check_count" type="INTEGER"/>
        <column name="created_date" type="TIMESTAMPTZ" defaultValueComputed="now()">
            <constraints nullable="false"/>
        </column>
    </createTable>

    <createTable tableName="check_result">
        <column name="id" type="uuid" defaultValueComputed="gen_random_uuid()">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="check_def_id" type="uuid"/>
        <column name="check_run_id" type="uuid"><constraints nullable="false"/></column>
        <column name="question" type="TEXT"/>
        <column name="reference_answer" type="TEXT"/>
        <column name="actual_answer" type="TEXT"/>
        <column name="score" type="DOUBLE PRECISION"/>
        <column name="log" type="TEXT"/>
        <column name="created_date" type="TIMESTAMPTZ" defaultValueComputed="now()">
            <constraints nullable="false"/>
        </column>
    </createTable>

    <createIndex indexName="idx_check_result_run" tableName="check_result">
        <column name="check_run_id"/>
    </createIndex>
    <addForeignKeyConstraint
        baseTableName="check_result"
        baseColumnNames="check_run_id"
        referencedTableName="check_run"
        referencedColumnNames="id"
        constraintName="fk_check_result_run"
        onDelete="CASCADE"/>
</changeSet>
```

---

## Common Pitfalls

### Pitfall 1: Token Count Not Captured
**What goes wrong:** Switching from `chatClient.prompt().call().content()` to `chatClient.prompt().call().chatResponse()` changes the call chain. The `content()` shortcut discards usage metadata. If left as-is, `promptTokens` and `completionTokens` are always null in logs.
**Why it happens:** Developers assume `content()` is equivalent to `chatResponse().getResult().getOutput().getText()` — it is functionally equivalent for the answer text but loses the metadata.
**How to avoid:** Capture `ChatResponse chatResponse = chatClient.prompt()...call().chatResponse()` and extract both the content string and the usage metadata from it.
**Warning signs:** Null token columns in chat_log after first log entries appear.

### Pitfall 2: `check` as Table Name — Reserved Keyword
**What goes wrong:** `@Table(name = "check")` causes SQL syntax errors in PostgreSQL because `check` is a reserved keyword used in `CHECK` constraints.
**Why it happens:** Jmix works around it with `CHECK_` (underscore suffix). If the underscore trick is forgotten, the migration succeeds (Liquibase quotes it) but JPA queries using JPQL may fail depending on dialect.
**How to avoid:** Use `check_result` as the table name from the start.
**Warning signs:** `ERROR: syntax error at or near "check"` in application startup SQL logs.

### Pitfall 3: Async Check Runner — Detached Entity
**What goes wrong:** `CheckRun` is loaded in the REST handler (one transaction), passed to the `@Async` method (new thread, new transaction). Setting fields on the detached entity and calling `save()` may throw `DetachedObjectException` or silently do nothing depending on session state.
**Why it happens:** `@Async` creates a new thread; the JPA session from the HTTP request is not shared with the async thread.
**How to avoid:** In the async method, load the `CheckRun` by ID (fresh from DB) rather than accepting the entity object. Pass `UUID checkRunId`, not the entity.
**Warning signs:** `CheckRun` rows with `status = RUNNING` that never transition to `COMPLETED` despite logs showing the runner finished.

### Pitfall 4: Chat Log Breaking Chat Response
**What goes wrong:** If `chatLogService.save()` throws (e.g., DB connection issue) and the exception is not caught, the user receives a 500 error for an otherwise valid chat response.
**Why it happens:** The log persistence is injected into the answer path without error isolation.
**How to avoid:** Wrap the log save call in `try { ... } catch (Exception ex) { log.warn(...) }`. The chat response is independent of log persistence success.
**Warning signs:** Users receiving 500 errors with stack traces pointing to `ChatLogService.save()`.

### Pitfall 5: Check Runner Blocking on LLM Calls
**What goes wrong:** If check definitions are many and each LLM evaluation call takes 2–5 seconds, a serial loop blocks the ingestion executor thread for the entire run duration.
**Why it happens:** The `@Async` method runs on a virtual thread (fine), but a serial loop with blocking calls means checks run sequentially (slow for large check sets).
**How to avoid:** For v1 with a small number of check definitions (< 20), serial is acceptable. If parallelism within a run is later needed, use `CompletableFuture.supplyAsync()` with the `ingestionExecutor`. Document the serial decision so it is not a surprise.
**Warning signs:** Check runs taking very long for moderate numbers of active check defs.

### Pitfall 6: AiParameterSet chatModel/evaluatorModel Null When Defaulting
**What goes wrong:** When `chatModel` is null (existing parameter sets before migration), `ChatService` should fall back to the Spring AI auto-configured model. If the code does `options.model(paramSet.getChatModel())` without a null check, a NullPointerException or an invalid API request results.
**Why it happens:** Nullable column, no fallback guard.
**How to avoid:** Add `if (chatModel != null) { options.model(chatModel); }` or use `Optional.ofNullable(paramSet.getChatModel()).ifPresent(options::model)`.
**Warning signs:** NullPointerException in `ChatService` after parameter set migration is applied.

### Pitfall 7: Frontend Polling Not Stopping
**What goes wrong:** The check run list polls every 5 seconds even after all runs are in COMPLETED or FAILED state, causing unnecessary API load.
**Why it happens:** `refetchInterval` set to a fixed number rather than a conditional function.
**How to avoid:** Use the TanStack Query `refetchInterval` callback form: `(query) => query.state.data?.some(r => r.status === 'RUNNING') ? 5000 : false`.
**Warning signs:** Network tab shows repeated `GET /check-runs` requests after all runs show COMPLETED.

---

## Code Examples

### ChatLog Repository — Filter Query

```java
// Source: follows Spring Data JPA @Query pattern used in AiParameterSetRepository
public interface ChatLogRepository extends JpaRepository<ChatLog, UUID> {

    @Query("""
        SELECT c FROM ChatLog c
        WHERE (:groundingStatus IS NULL OR c.groundingStatus = :groundingStatus)
          AND (:dateFrom IS NULL OR c.createdDate >= :dateFrom)
          AND (:dateTo IS NULL OR c.createdDate <= :dateTo)
          AND (:question IS NULL OR LOWER(c.question) LIKE LOWER(CONCAT('%', :question, '%')))
        ORDER BY c.createdDate DESC
        """)
    Page<ChatLog> findWithFilters(
        @Param("groundingStatus") GroundingStatus groundingStatus,
        @Param("dateFrom") OffsetDateTime dateFrom,
        @Param("dateTo") OffsetDateTime dateTo,
        @Param("question") String question,
        Pageable pageable
    );
}
```

### CheckDef Repository

```java
// Source: follows jmix CheckRunner query pattern
public interface CheckDefRepository extends JpaRepository<CheckDef, UUID> {
    List<CheckDef> findByActiveTrue();
}
```

### Frontend: query-keys extension

```typescript
// Source: verified against frontend/lib/query-keys.ts
export const queryKeys = {
  // ... existing keys ...
  chatLogs: (filters: ChatLogFilters) => ['admin', 'chat-logs', filters] as const,
  chatLog: (id: string) => ['admin', 'chat-logs', id] as const,
  checkDefs: ['admin', 'check-defs'] as const,
  checkDef: (id: string) => ['admin', 'check-defs', id] as const,
  checkRuns: ['admin', 'check-runs'] as const,
  checkRun: (id: string) => ['admin', 'check-runs', id] as const,
  checkRunResults: (id: string) => ['admin', 'check-runs', id, 'results'] as const,
  allowedModels: ['admin', 'allowed-models'] as const,
};
```

### Frontend: new types

```typescript
// Source: follows existing types/api.ts conventions
export type CheckRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface ChatLogResponse {
  id: string;
  conversationId: string | null;
  question: string;
  groundingStatus: GroundingStatus;
  promptTokens: number | null;
  completionTokens: number | null;
  responseTime: number | null;
  createdDate: string;
}

export interface ChatLogDetailResponse extends ChatLogResponse {
  answer: string;
  sources: string[];   // split from comma-separated server value
}

export interface CheckDefResponse {
  id: string;
  question: string;
  referenceAnswer: string;
  category: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CheckRunResponse {
  id: string;
  averageScore: number | null;
  parameterSetId: string | null;
  parameterSetName: string | null;
  status: CheckRunStatus;
  checkCount: number | null;
  createdDate: string;
}

export interface CheckResultResponse {
  id: string;
  question: string;
  referenceAnswer: string;
  actualAnswer: string;
  score: number | null;
}

export interface AllowedModelResponse {
  modelId: string;
  displayName: string;
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| jmix `Executors.newFixedThreadPool()` per run | Spring `@Async` + virtual thread executor | Phase 4.1 added virtual thread `SimpleAsyncTaskExecutor` | No new pool creation needed; reuse existing executor |
| jmix `UnconstrainedDataManager` (Jmix-specific) | Spring Data JPA repositories | Project baseline | Do not use Jmix APIs; use `JpaRepository` |
| jmix `@Lob String answer` in CheckDef | `TEXT column referenceAnswer` | Project adaptation | Clearer naming; avoids ambiguity with `actualAnswer` in CheckResult |
| jmix full YAML snapshot in `CheckRun.parameters` | FK + name snapshot only | Phase 5 design decision | Avoids large BLOB per run; still identifies parameter set for display |
| `chatClient.call().content()` | `chatClient.call().chatResponse()` | Phase 5 token capture | Required to access `Usage` metadata for token counts |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring AI ChatClient `call().chatResponse()` exposes `Usage` with `getPromptTokens()` / `getGenerationTokens()` | Pattern 2 (ChatService retrofit) | Token capture fails; log columns always null |
| A2 | Spring AI supports per-request model override via `ChatOptions` passed to `ChatClient.prompt().options(...)` | Pattern 5 (Evaluator) | Evaluator model selection requires a different implementation approach |
| A3 | The `AllowedModel` enum contains Claude models (claude-3-5-sonnet, claude-3-5-haiku, claude-3-opus) | Pattern 6 (AllowedModel) | Model IDs must match actual Spring AI provider config in application.yaml |
| A4 | Phase 3 (multi-turn) is NOT yet shipped in the deployed codebase at the time Phase 5 executes | Pattern 2 (conversationId) | If Phase 3 is shipped, threadId may already flow through — check `ChatService` signature before adding the overload |

---

## Open Questions

1. **Spring AI `Usage` API shape**
   - What we know: `ChatResponse` has `getMetadata()` which likely has `getUsage()`
   - What's unclear: Exact method names (`getPromptTokens()` vs `getInputTokens()`, etc.) depend on Spring AI version in pom.xml
   - Recommendation: Verify with Context7 `mcp__context7__query-docs` for Spring AI `ChatResponse` usage before writing token capture code

2. **Per-request model override in Spring AI ChatClient**
   - What we know: Spring AI `ChatOptions` can specify a model; jmix passes it via constructor
   - What's unclear: Whether `ChatClient.prompt().options(ChatOptions)` supports model override per-call without a new `ChatClient` instance
   - Recommendation: If per-call model override is not supported cleanly, create a secondary `ChatClient` bean for the evaluator model via `ChatClient.Builder` with different options

3. **Phase 3 shipping status**
   - What we know: Phase 3 plans exist (03-01, 03-02, 03-03-PLAN.md) but STATE.md shows Phase 3 as "Pending"
   - What's unclear: Whether Phase 3 has been partially or fully implemented before Phase 5 planning begins
   - Recommendation: Before retrofitting `ChatService.answer()`, check the current method signature — if a `threadId` parameter already exists, use it; if not, add `String conversationId` parameter and update the controller

---

## Environment Availability

Phase 5 is entirely code changes — no new external services, databases, or CLI tools required. All infrastructure is already in place.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | chat_log, check_def, check_run, check_result tables | Yes | Existing DB | — |
| Spring AI (LLM provider) | Semantic evaluator | Yes | Existing config | — |
| virtual thread executor | Async check runner | Yes | AsyncConfig.java | — |
| TanStack Query, shadcn/ui | Admin UI screens | Yes | Existing frontend | — |

---

## Validation Architecture

Nyquist validation is enabled (`workflow.nyquist_validation: true`).

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito (backend) / Jest + React Testing Library (frontend) |
| Config file | `src/test/` directory (backend); `frontend/__tests__/` if exists (frontend) |
| Quick run command | `./mvnw test -pl . -Dtest=ChatLog*,CheckDef*,CheckRun*,SemanticEval* -q` |
| Full suite command | `./mvnw test -q` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ADMIN-04 | ChatLog entity persists after `answer()` call | Unit | `./mvnw test -Dtest=ChatServiceTest -q` | Partial — ChatServiceTest exists, needs retrofit coverage |
| ADMIN-04 | ChatLog REST list returns paginated results with filters | Integration | `./mvnw test -Dtest=ChatLogControllerTest -q` | No — Wave 0 gap |
| ADMIN-04 | ChatLog save failure does not break chat response | Unit | `./mvnw test -Dtest=ChatServiceTest#answer_*log_failure* -q` | No — Wave 0 gap |
| ADMIN-05 | CheckDef CRUD operations (create, update, delete, findActive) | Unit | `./mvnw test -Dtest=CheckDefServiceTest -q` | No — Wave 0 gap |
| ADMIN-05 | CheckRunner runs all active defs and saves results | Unit | `./mvnw test -Dtest=CheckRunnerTest -q` | No — Wave 0 gap |
| ADMIN-05 | SemanticEvaluator parses score from valid JSON response | Unit | `./mvnw test -Dtest=LlmSemanticEvaluatorTest -q` | No — Wave 0 gap |
| ADMIN-05 | SemanticEvaluator returns 0.0 on LLM failure (no throw) | Unit | `./mvnw test -Dtest=LlmSemanticEvaluatorTest -q` | No — Wave 0 gap |
| ADMIN-05 | CheckRun trigger returns 202 with run ID | Integration | `./mvnw test -Dtest=CheckRunControllerTest -q` | No — Wave 0 gap |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=ChatLogRepositoryTest,CheckDefServiceTest -q`
- **Per wave merge:** `./mvnw test -q` (full backend suite)
- **Phase gate:** Full backend suite green + frontend build passes (`cd frontend && npm run build`) before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/.../chatlog/ChatLogServiceTest.java` — covers ChatService retrofit (log persisted, log failure swallowed)
- [ ] `src/test/java/.../chatlog/ChatLogControllerTest.java` — covers REST list with filter params, REST detail
- [ ] `src/test/java/.../checks/CheckDefServiceTest.java` — covers CRUD operations
- [ ] `src/test/java/.../checks/CheckRunnerTest.java` — covers async run, result aggregation, COMPLETED status set
- [ ] `src/test/java/.../checks/LlmSemanticEvaluatorTest.java` — covers score parsing, language mismatch penalty, failure returns 0.0
- [ ] `src/test/java/.../checks/CheckRunControllerTest.java` — covers 202 trigger response, results endpoint

---

## Security Domain

Phase 5 adds admin-only endpoints and LLM evaluator calls. No user-facing authentication changes.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---|---|---|
| V2 Authentication | No — admin-only, no new auth added | — |
| V3 Session Management | No | — |
| V4 Access Control | Yes — all Phase 5 endpoints are under `/api/v1/admin/` | Existing security config must include new paths; verify `SecurityConfig` or equivalent covers `/api/v1/admin/**` |
| V5 Input Validation | Yes — CheckDef create/update accepts user text | `@Valid` + `@NotBlank` on request DTOs; zod validation on frontend |
| V6 Cryptography | No | — |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---|---|---|
| CheckDef question injection into LLM evaluator prompt | Tampering | Treat CheckDef fields as data, not as prompt instructions — wrap in delimiters in the evaluator prompt: `"Câu hỏi:\n{question}\n"` not interpolated directly |
| Chat log question contains PII | Information Disclosure | D-04 (append-only) means no deletion. Document this limitation. No encryption at rest in v1. |
| Evaluator LLM API key exposure in logs | Information Disclosure | Use existing Spring AI API key config (`spring.ai.anthropic.api-key`) — do not log API keys in CheckRunner or evaluator |

---

## Sources

### Primary (HIGH confidence — verified by codebase file read)

- `ChatService.java` — current `answer()` method signature, imports, token capture gap identified
- `AsyncConfig.java` — `SimpleAsyncTaskExecutor` with virtual threads, `ingestionExecutor` bean name
- `AiParameterSet.java` / `AiParameterSetService.java` — current entity shape, service patterns
- `ApiPaths.java` — existing path constants, numbering convention
- `ErrorCode.java` — existing error codes, pattern for new ones
- `GlobalExceptionHandler.java` — existing handler shape (ProblemDetail)
- `005-ai-parameter-set-schema.xml` — Liquibase changeSet 005 format; 008 must follow same format
- `007-source-trust-policy-schema.xml` — most recent changeSet pattern to replicate
- `frontend/components/admin/data-table.tsx` — DataTable component API (columns, data, isLoading props)
- `frontend/app/(admin)/parameters/page.tsx` — exact master-detail form pattern to replicate
- `frontend/hooks/use-parameters.ts` — hook pattern for new hooks
- `frontend/lib/query-keys.ts` — query key structure to extend
- `frontend/types/api.ts` — existing DTO types to extend

### Primary (HIGH confidence — verified by jmix reference implementation read)

- `jmix ChatLog.java` — entity shape baseline (fields, types, indexes)
- `jmix ChatLogManager.java` — save pattern (create → populate → save)
- `jmix CheckDef.java` — entity shape (`question`, `answer`, `category`, `active`)
- `jmix CheckRun.java` — entity shape (`score`, `parameters`, `createdDate`)
- `jmix Check.java` — result entity shape, FK columns, reserved-word table name issue identified
- `jmix CheckRunner.java` — parallel execution pattern, error handling per check
- `jmix ExternalEvaluator.java` — evaluator interface contract
- `jmix ExternalEvaluatorImpl.java` — score parsing logic, language mismatch penalty, JSON extraction pattern

### Secondary (MEDIUM confidence — derived from existing patterns)

- `05-CONTEXT.md` — all locked decisions D-01 through D-15
- `05-UI-SPEC.md` — 6 screen specifications, component inventory, interaction patterns
- `04-CONTEXT.md` — DataTable, TanStack Query, shadcn pattern decisions
- `04.1-CONTEXT.md` — async executor decisions (virtual threads confirmed)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified in codebase; no new dependencies
- Architecture: HIGH — entity shapes verified against jmix source + existing backend patterns
- Pitfalls: HIGH — most identified from direct code inspection (reserved keywords, token metadata API, async entity detachment)
- Evaluator model API: MEDIUM — Spring AI `ChatOptions` per-request model override flagged as assumed (A2); verify before implementing

**Research date:** 2026-04-12
**Valid until:** 2026-05-12 (stable stack, no fast-moving libraries)
