---
phase: quick
plan: 260412-olx
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/resources/db/changelog/012-chat-log-pipeline-columns.xml
  - src/main/resources/db/changelog/013-chat-log-pipeline-log-column.xml
  - src/main/java/com/vn/traffic/chatbot/chatlog/domain/ChatLog.java
  - src/main/java/com/vn/traffic/chatbot/chatlog/service/ChatLogService.java
  - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
  - src/main/java/com/vn/traffic/chatbot/chatlog/api/dto/ChatLogDetailResponse.java
  - frontend/app/(admin)/parameters/page.tsx
autonomous: true
requirements: []

must_haves:
  truths:
    - "chat_log table has a single pipeline_log TEXT column instead of 3 separate columns"
    - "ChatService collects structured log messages via Consumer<String> during each pipeline step and stores them joined by newline"
    - "ChatLogDetailResponse exposes pipelineLog field instead of retrievedChunks/promptText/rawModelResponse"
    - "Parameters page shows a live preview panel parsing YAML into labeled sections when editor is open"
    - "Preview updates on 300ms debounce and shows 'YAML không hợp lệ' badge on parse error"
  artifacts:
    - path: "src/main/resources/db/changelog/013-chat-log-pipeline-log-column.xml"
      provides: "Liquibase migration: drop 3 columns, add pipeline_log column"
    - path: "src/main/java/com/vn/traffic/chatbot/chatlog/domain/ChatLog.java"
      provides: "ChatLog entity with pipelineLog field"
    - path: "src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java"
      provides: "Consumer<String> logger collecting jmix-pattern log messages"
    - path: "frontend/app/(admin)/parameters/page.tsx"
      provides: "Parameters page with 2-column editor + live YAML preview panel"
  key_links:
    - from: "ChatService.answer()"
      to: "ChatLogService.save()"
      via: "pipelineLog string (String.join newline from logMessages list)"
    - from: "parameters/page.tsx content watch"
      to: "YamlPreview component"
      via: "300ms debounced useEffect on form.watch('content')"
---

<objective>
Two focused improvements: (A) rework backend pipeline logging from 3 separate DB columns to a single jmix-pattern pipeline_log column with structured Consumer<String> collection in ChatService, and (B) add a live YAML preview panel to the parameters editor so operators can see parsed key sections while typing.

Purpose: Align pipeline logging with the proven jmix-ai-backend pattern for unified observability. Give parameter editors immediate visual feedback without leaving the YAML textarea.
Output: New Liquibase migration 013, updated ChatLog entity/service/DTO, updated ChatService with Consumer<String> logger, updated parameters page with debounced YAML preview.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md

<!-- Key interfaces the executor needs — no codebase exploration required -->
<interfaces>
<!-- Current ChatLog entity (3 fields to replace) -->
From src/main/java/com/vn/traffic/chatbot/chatlog/domain/ChatLog.java:
```java
// REMOVE these 3 fields:
@Column(name = "retrieved_chunks", columnDefinition = "TEXT")
private String retrievedChunks;

@Column(name = "prompt_text", columnDefinition = "TEXT")
private String promptText;

@Column(name = "raw_model_response", columnDefinition = "TEXT")
private String rawModelResponse;

// REPLACE with:
@Column(name = "pipeline_log", columnDefinition = "TEXT")
private String pipelineLog;
```

<!-- Current ChatLogService.save() signature (7 params after the 3-column addition) -->
From src/main/java/com/vn/traffic/chatbot/chatlog/service/ChatLogService.java:
```java
public void save(String question, ChatAnswerResponse response, GroundingStatus groundingStatus,
                 String conversationId, int promptTokens, int completionTokens, int responseTime,
                 String retrievedChunks, String promptText, String rawModelResponse)
```

<!-- ChatService call sites (2 calls to save()) -->
From src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java:
```java
// Refusal path:
chatLogService.save(question, refused, GroundingStatus.REFUSED, null, 0, 0, 0,
        chunksJson, null, null);

// Normal path:
chatLogService.save(question, response, groundingStatus, null,
        promptTokens, completionTokens, responseTime,
        chunksJson, prompt, modelPayload);
```

<!-- Document score access in Spring AI -->
// doc.getScore() returns Double (nullable). Format as "(0.821)" in log.
// doc.getId() returns String. doc.getText() for preview.

<!-- ChatLogDetailResponse (record, fromEntity factory) -->
From src/main/java/com/vn/traffic/chatbot/chatlog/api/dto/ChatLogDetailResponse.java:
```java
// Current fields to replace: retrievedChunks, promptText, rawModelResponse
// Replace with single: String pipelineLog
// Update fromEntity(): log.getPipelineLog()
```

<!-- Parameters page: no js-yaml in package.json -->
// Use simple line-by-line YAML parsing (no external library needed).
// form.watch('content') returns the current textarea value.
// useEffect with 300ms setTimeout for debounce.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Backend — jmix-pattern pipeline_log column and Consumer logger</name>
  <files>
    src/main/resources/db/changelog/013-chat-log-pipeline-log-column.xml,
    src/main/resources/db/changelog/db.changelog-master.xml,
    src/main/java/com/vn/traffic/chatbot/chatlog/domain/ChatLog.java,
    src/main/java/com/vn/traffic/chatbot/chatlog/service/ChatLogService.java,
    src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java,
    src/main/java/com/vn/traffic/chatbot/chatlog/api/dto/ChatLogDetailResponse.java
  </files>
  <action>
**Step 1 — Create Liquibase migration 013.**

Create `src/main/resources/db/changelog/013-chat-log-pipeline-log-column.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog ...>
    <changeSet id="013-chat-log-pipeline-log-column" author="kl3inIT">
        <comment>Replace 3 separate pipeline trace columns with a single pipeline_log TEXT column (jmix pattern)</comment>
        <dropColumn tableName="chat_log" columnName="retrieved_chunks"/>
        <dropColumn tableName="chat_log" columnName="prompt_text"/>
        <dropColumn tableName="chat_log" columnName="raw_model_response"/>
        <addColumn tableName="chat_log">
            <column name="pipeline_log" type="TEXT">
                <constraints nullable="true"/>
            </column>
        </addColumn>
    </changeSet>
</databaseChangeLog>
```

Register in `db.changelog-master.xml` by adding `<include file="classpath:db/changelog/013-chat-log-pipeline-log-column.xml"/>` after the 012 include line.

**Step 2 — Update ChatLog entity.**

In `ChatLog.java`, remove the 3 fields (`retrievedChunks`, `promptText`, `rawModelResponse`) and their `@Column` annotations. Add:

```java
@Column(name = "pipeline_log", columnDefinition = "TEXT")
private String pipelineLog;
```

The Lombok `@Data` / `@Builder` / `@AllArgsConstructor` / `@NoArgsConstructor` will auto-generate the getter/setter/builder for the new field.

**Step 3 — Update ChatLogService.save().**

Change the method signature from 10 params to 8 params, replacing the last 3 with `String pipelineLog`:

```java
public void save(String question, ChatAnswerResponse response, GroundingStatus groundingStatus,
                 String conversationId, int promptTokens, int completionTokens, int responseTime,
                 String pipelineLog)
```

In the builder call, remove `.retrievedChunks(...)`, `.promptText(...)`, `.rawModelResponse(...)` and add `.pipelineLog(pipelineLog)`.

**Step 4 — Update ChatService to use Consumer<String> jmix-pattern logger.**

Replace the `serializeChunks()` helper and the two `chatLogService.save()` call sites with a unified Consumer-based logging approach in `ChatService.answer()`:

```java
public ChatAnswerResponse answer(String question) {
    List<String> logMessages = new ArrayList<>();
    Consumer<String> logger = msg -> {
        log.info(msg);
        logMessages.add(msg);
    };

    // Step: user prompt
    logger.accept("User prompt: " + question);

    // Step: retrieval
    SearchRequest request = retrievalPolicy.buildRequest(question, retrievalTopK);
    double threshold = request.getSimilarityThreshold();
    logger.accept(String.format(">>> Using vector_store [topK=%d, threshold=%.2f]: %s",
            retrievalTopK, threshold, question));

    List<Document> documents = safeDocuments(vectorStore.similaritySearch(request));

    // Step: found documents summary
    String docSummary = documents.stream()
            .map(doc -> {
                Double score = doc.getScore();
                String scoreStr = score != null ? String.format("(%.3f)", score) : "(?.???)";
                return scoreStr + " " + doc.getId();
            })
            .collect(Collectors.joining(", "));
    logger.accept(String.format("Found %d documents: [%s]", documents.size(), docSummary));

    List<CitationResponse> citations = safeCitations(citationMapper.toCitations(documents));
    List<SourceReferenceResponse> sources = citationMapper.toSources(citations);
    GroundingStatus groundingStatus = determineGroundingStatus(documents.size());

    // Step: grounding status
    logger.accept(String.format("Grounding: %s (%d docs)", groundingStatus.name(), documents.size()));

    if (groundingStatus == GroundingStatus.REFUSED || !containsAnyLegalCitation(citations)) {
        chunkInspectionService.getRetrievalReadinessCounts();
        ChatAnswerResponse refused = refusalResponse();
        try {
            String pipelineLog = String.join("\n", logMessages);
            chatLogService.save(question, refused, GroundingStatus.REFUSED, null, 0, 0, 0, pipelineLog);
        } catch (Exception ex) {
            log.warn("Failed to persist chat log entry for refusal: {}", ex.getMessage());
        }
        return refused;
    }

    long startTime = System.currentTimeMillis();
    String prompt = chatPromptFactory.buildPrompt(question, groundingStatus, citations);
    ChatResponse chatResponse = chatClient.prompt()
            .user(prompt)
            .call()
            .chatResponse();

    String modelPayload = chatResponse.getResult().getOutput().getText();
    var usage = chatResponse.getMetadata().getUsage();
    int promptTokens = usage != null ? (int) usage.getPromptTokens() : 0;
    int completionTokens = usage != null ? (int) usage.getCompletionTokens() : 0;
    int responseTime = (int) (System.currentTimeMillis() - startTime);

    // Step: response summary (preview first 120 chars of answer)
    String answerPreview = modelPayload != null && modelPayload.length() > 120
            ? modelPayload.substring(0, 120) + "…"
            : modelPayload;
    logger.accept(String.format("Response in %dms [prompt=%d, completion=%d]: %s",
            responseTime, promptTokens, completionTokens, answerPreview));

    LegalAnswerDraft draft = parseDraft(modelPayload, groundingStatus, citations, sources);
    ChatAnswerResponse response = answerComposer.compose(groundingStatus, draft, citations, sources);

    try {
        String pipelineLog = String.join("\n", logMessages);
        chatLogService.save(question, response, groundingStatus, null,
                promptTokens, completionTokens, responseTime, pipelineLog);
    } catch (Exception ex) {
        log.warn("Failed to persist chat log entry: {}", ex.getMessage());
    }

    return response;
}
```

Remove the now-unused `serializeChunks()` method and its `ObjectMapper` usage for chunk serialization (the `objectMapper` field is still needed for `parseDraft()`).

Add `java.util.ArrayList` and `java.util.function.Consumer` to imports. Remove `java.util.Map` if no longer used.

**Step 5 — Update ChatLogDetailResponse.**

Replace the 3 fields (`retrievedChunks`, `promptText`, `rawModelResponse`) with `String pipelineLog` in the record declaration. Update `fromEntity()` to use `log.getPipelineLog()`.
  </action>
  <verify>
    <automated>cd D:/ai/traffic-law-chatbot && ./mvnw compile -q 2>&amp;1 | tail -20</automated>
  </verify>
  <done>
    - `./mvnw compile` succeeds with no errors
    - `013-chat-log-pipeline-log-column.xml` exists and is referenced in master changelog
    - `ChatLog.java` has only `pipelineLog` field (no `retrievedChunks`, `promptText`, `rawModelResponse`)
    - `ChatService.answer()` uses `Consumer&lt;String&gt; logger` collecting 5 structured log lines and passes `String.join("\n", logMessages)` to `chatLogService.save()`
    - `ChatLogDetailResponse` record has `pipelineLog` field with `fromEntity()` mapping
  </done>
</task>

<task type="auto">
  <name>Task 2: Frontend — Parameters page live YAML preview panel</name>
  <files>
    frontend/app/(admin)/parameters/page.tsx
  </files>
  <action>
Add a live YAML preview panel to the right of the YAML textarea within the editor area. No external library — use simple line-by-line parsing.

**Layout change:** The editor's YAML content area (currently a single `flex flex-col flex-1 min-h-0 space-y-1` div with the Textarea) becomes a 2-column grid:
- Left column (60% width): Label + Textarea (existing)
- Right column (40% width): Live YAML preview panel

**Add a `YamlPreview` component** within the same file (above `ParametersPage`):

```tsx
interface ParsedYaml {
  model?: { name?: string; temperature?: string; maxTokens?: string };
  retrieval?: { topK?: string; similarityThreshold?: string; groundingLimitedThreshold?: string };
  systemPrompt?: string;
}

function parseSimpleYaml(yaml: string): ParsedYaml | null {
  try {
    const lines = yaml.split('\n');
    const result: ParsedYaml = {};
    let currentSection: string | null = null;
    let systemPromptLines: string[] = [];
    let inSystemPrompt = false;

    for (const rawLine of lines) {
      const line = rawLine;
      const trimmed = line.trimStart();

      if (inSystemPrompt) {
        // systemPrompt block ends when a non-indented key appears
        if (/^\w/.test(line) && line.includes(':') && !line.startsWith(' ')) {
          inSystemPrompt = false;
          result.systemPrompt = systemPromptLines.join('\n').trim();
        } else {
          systemPromptLines.push(trimmed);
          continue;
        }
      }

      if (/^model:/.test(trimmed)) { currentSection = 'model'; result.model = {}; continue; }
      if (/^retrieval:/.test(trimmed)) { currentSection = 'retrieval'; result.retrieval = {}; continue; }
      if (/^systemPrompt:\s*\|/.test(trimmed)) { currentSection = 'systemPrompt'; inSystemPrompt = true; systemPromptLines = []; continue; }
      if (/^systemPrompt:\s*(.+)/.test(trimmed)) {
        result.systemPrompt = trimmed.replace(/^systemPrompt:\s*/, '');
        currentSection = null; continue;
      }

      // key: value under current section
      const kvMatch = trimmed.match(/^(\w+):\s*(.+)/);
      if (kvMatch && currentSection === 'model' && result.model) {
        result.model[kvMatch[1] as keyof typeof result.model] = kvMatch[2];
      }
      if (kvMatch && currentSection === 'retrieval' && result.retrieval) {
        result.retrieval[kvMatch[1] as keyof typeof result.retrieval] = kvMatch[2];
      }
    }

    if (inSystemPrompt) result.systemPrompt = systemPromptLines.join('\n').trim();
    return result;
  } catch {
    return null;
  }
}

function YamlPreview({ yaml }: { yaml: string }) {
  const parsed = parseSimpleYaml(yaml);

  if (!yaml.trim()) {
    return (
      <div className="h-full flex items-center justify-center text-muted-foreground text-xs">
        Nhập YAML để xem trước
      </div>
    );
  }

  if (!parsed) {
    return (
      <div className="p-3">
        <Badge variant="destructive" className="text-xs">YAML không hợp lệ</Badge>
      </div>
    );
  }

  return (
    <div className="p-3 space-y-3 overflow-y-auto h-full text-xs">
      {parsed.model && (
        <div>
          <p className="font-semibold text-muted-foreground uppercase tracking-wide mb-1">Model</p>
          {parsed.model.name && <p><span className="text-muted-foreground">name:</span> <span className="font-mono">{parsed.model.name}</span></p>}
          {parsed.model.temperature && <p><span className="text-muted-foreground">temperature:</span> <span className="font-mono">{parsed.model.temperature}</span></p>}
          {parsed.model.maxTokens && <p><span className="text-muted-foreground">maxTokens:</span> <span className="font-mono">{parsed.model.maxTokens}</span></p>}
        </div>
      )}
      {parsed.retrieval && (
        <div>
          <p className="font-semibold text-muted-foreground uppercase tracking-wide mb-1">Retrieval</p>
          {parsed.retrieval.topK && <p><span className="text-muted-foreground">topK:</span> <span className="font-mono">{parsed.retrieval.topK}</span></p>}
          {parsed.retrieval.similarityThreshold && <p><span className="text-muted-foreground">similarityThreshold:</span> <span className="font-mono">{parsed.retrieval.similarityThreshold}</span></p>}
          {parsed.retrieval.groundingLimitedThreshold && <p><span className="text-muted-foreground">groundingLimitedThreshold:</span> <span className="font-mono">{parsed.retrieval.groundingLimitedThreshold}</span></p>}
        </div>
      )}
      {parsed.systemPrompt && (
        <div>
          <p className="font-semibold text-muted-foreground uppercase tracking-wide mb-1">System Prompt</p>
          <p className="text-muted-foreground line-clamp-6 whitespace-pre-wrap">{parsed.systemPrompt}</p>
        </div>
      )}
      {!parsed.model && !parsed.retrieval && !parsed.systemPrompt && (
        <p className="text-muted-foreground">Không nhận ra cấu trúc YAML chuẩn</p>
      )}
    </div>
  );
}
```

**Debounced preview state in `ParametersPage`:**

Add state and effect to the component body (after existing state declarations):

```tsx
const [previewYaml, setPreviewYaml] = useState('');
const watchedContent = form.watch('content');

useEffect(() => {
  const timer = setTimeout(() => {
    setPreviewYaml(watchedContent ?? '');
  }, 300);
  return () => clearTimeout(timer);
}, [watchedContent]);
```

**Replace the YAML content section** (the `div` with `flex flex-col flex-1 min-h-0 space-y-1` containing Label + Textarea) with a 2-column grid:

```tsx
<div className="flex flex-1 min-h-0 gap-3">
  {/* Left: YAML textarea */}
  <div className="flex flex-col flex-1 min-h-0 space-y-1">
    <Label htmlFor="content">Cấu hình YAML</Label>
    <Textarea
      id="content"
      {...form.register('content')}
      className="font-mono text-sm flex-1 resize-none"
      placeholder={"model:\n  name: claude-sonnet-4-6\n  temperature: 0.3\n  maxTokens: 2048\nretrieval:\n  topK: 5\n  similarityThreshold: 0.7\n  groundingLimitedThreshold: 0.5\nsystemPrompt: |\n  ..."}
    />
    {form.formState.errors.content && (
      <p className="text-xs text-destructive">{form.formState.errors.content.message}</p>
    )}
  </div>
  {/* Right: live preview */}
  <div className="w-56 flex-shrink-0 border rounded-md flex flex-col min-h-0">
    <p className="text-xs font-semibold text-muted-foreground px-3 pt-2 pb-1 border-b flex-shrink-0">Xem trước</p>
    <YamlPreview yaml={previewYaml} />
  </div>
</div>
```

Ensure `useState` is imported (already is). No new imports needed — `Badge` is already imported.
  </action>
  <verify>
    <automated>cd D:/ai/traffic-law-chatbot/frontend && pnpm build 2>&amp;1 | tail -30</automated>
  </verify>
  <done>
    - `pnpm build` succeeds with no TypeScript errors
    - Opening the parameters page with a parameter set selected shows 2-column layout: YAML textarea on left, preview panel on right
    - Typing in the textarea updates the preview panel after ~300ms
    - If the YAML cannot be parsed, the preview shows "YAML không hợp lệ" badge
    - Valid YAML with model/retrieval/systemPrompt keys shows labeled sections in the preview
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Admin UI → parameters API | YAML content entered by admin operator — authenticated admin only |
| ChatService → pipeline_log | Internal data, no external input in log messages (question is already validated upstream) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-olx-01 | Information Disclosure | pipeline_log in ChatLogDetailResponse | accept | Log is admin-only endpoint; pipeline log contains prompts which are already stored in `question` field; no new exposure surface |
| T-olx-02 | Tampering | YAML preview client-side parse | accept | Preview is read-only display; YAML is saved and validated server-side independently; client parse failure only affects display |
| T-olx-03 | Information Disclosure | question text in pipeline_log | accept | question is already stored verbatim in `question` column; log is same sensitivity level |
</threat_model>

<verification>
Backend compile check:
```bash
cd D:/ai/traffic-law-chatbot && ./mvnw compile -q
```

Frontend build check:
```bash
cd D:/ai/traffic-law-chatbot/frontend && pnpm build
```

Smoke test after app start — verify pipeline_log appears in chat log detail response:
```bash
curl -s http://localhost:8080/api/chat-logs?size=1 | jq '.[0].pipelineLog'
# Should contain newline-joined log lines starting with "User prompt: ..."
```
</verification>

<success_criteria>
- Backend compiles cleanly after schema and code changes
- `chat_log` table has `pipeline_log` column only (3 old columns removed via migration 013)
- A chat request produces a `pipeline_log` with 4-5 structured lines matching jmix pattern format
- Frontend builds with no TypeScript errors
- Parameters editor shows YAML preview panel alongside textarea with 300ms debounce
- YAML parse error shows "YAML không hợp lệ" badge in preview
</success_criteria>

<output>
After completion, create `.planning/quick/260412-olx-pipeline-logging-jmix-pattern-v-paramete/260412-olx-SUMMARY.md`
</output>
