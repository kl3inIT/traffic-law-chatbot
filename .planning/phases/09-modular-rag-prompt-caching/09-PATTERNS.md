# Phase 9: Modular RAG + Prompt Caching - Pattern Map

**Mapped:** 2026-04-18
**Files analyzed:** 10 (2 new main + 2 new tests + 6 modified/deleted)
**Analogs found:** 10 / 10 (all files have strong in-repo analogs from P8)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `chat/advisor/LegalQueryAugmenter.java` (NEW) | advisor component (QueryAugmenter) | request-response / transform | `chat/service/ChatPromptFactory.java` (port of `buildPrompt`/`formatCitation`) + `chat/advisor/GroundingGuardInputAdvisor.java` (advisor-component shape) | exact (logic port + shape) |
| `chat/advisor/CitationPostProcessor.java` (NEW) | advisor component (DocumentPostProcessor) | transform + side-effect stash | `chat/citation/CitationMapper.java` (calls from inside) + `chat/advisor/GroundingGuardOutputAdvisor.java` (advisor-component shape with PostConstruct logging) | exact |
| `chat/advisor/context/ChatAdvisorContextKeys.java` (NEW, optional) | constants/config | n/a | `GroundingGuardInputAdvisor.FORCE_REFUSAL` constant pattern | role-match |
| `chat/config/ChatClientConfig.java` (MOD) | config | wiring | self (current P8 `defaultAdvisors(...)`) | in-place edit |
| `chat/service/ChatService.java` (MOD, shrink ≥60%) | service | request-response orchestration | self (P8 `doAnswer` current shape — delete retrieval+prompt+citation lines 114–154) | in-place edit |
| `chat/advisor/placeholder/NoOpRetrievalAdvisor.java` (DELETE) | placeholder | pass-through | — | n/a |
| `chat/advisor/placeholder/NoOpValidationAdvisor.java` (DELETE) | placeholder | pass-through | — | n/a |
| `chat/service/ChatPromptFactory.java` (ABSORB/DELETE) | prompt builder | transform | logic migrates into `LegalQueryAugmenter` | n/a |
| `chat/citation/CitationMapper.java` (UNCHANGED, new call site) | mapper | transform | self | unchanged |
| `test/.../LegalQueryAugmenterTest.java` (NEW) | unit test | assertion | `chat/advisor/GroundingGuardAdvisorTest.java` + `chat/citation/CitationMapperTest.java` | exact |
| `test/.../CitationFormatRegressionIT.java` (NEW) | live IT | request-response | `chat/regression/StructuredOutputMatrixIT.java` + `VietnameseRegressionIT.java` | exact |
| `test/.../StructuredOutputValidationAdvisorIT.java` (NEW, PR2) | unit/IT | mock-model retry | `GroundingGuardAdvisorTest.java` (mock chain) | role-match |
| `test/.../ChatServiceShrinkArchTest.java` (NEW) | ArchUnit | file-parse assertion | `chat/archunit/ChatServiceDeletionArchTest.java` | exact |
| `test/.../ChatClientConfigTest.java` (EXTEND) | unit | wiring assertion | self | in-place edit |

## Pattern Assignments

### `chat/advisor/LegalQueryAugmenter.java` (NEW — advisor component, transform)

**Analogs:** `chat/service/ChatPromptFactory.java` (logic port) + `chat/advisor/GroundingGuardOutputAdvisor.java` (component shape)

**Component shape pattern** (from `GroundingGuardOutputAdvisor.java` lines 1-60):
- `package com.vn.traffic.chatbot.chat.advisor;`
- `@Slf4j @Component public final class ...`
- `@PostConstruct void logOrder()` — emits `log.info("Advisor registered: {} order={}", getName(), ORDER);`
- Expose constants as `public static final String` (e.g. `REFUSAL_TEMPLATE`) — use same style for any prompt-template string that tests need to assert on.

**Vietnamese prompt text to PORT verbatim** (from `ChatPromptFactory.java`):
- Line 56 — `[Nguồn n]` rule (MUST survive byte-for-byte):
  ```
  "Mọi nhận định có căn cứ phải gắn nhãn trích dẫn nội dòng đúng định dạng [Nguồn n]; tuyệt đối không tự tạo nhãn ngoài danh sách được cung cấp."
  ```
- Lines 53-63 — full instruction block (conclusion-first, section order, JSON-only, required keys) must port into the augmenter template.
- Lines 64-69 — `Lịch sử hội thoại`, `Câu hỏi người dùng`, `Danh sách trích dẫn được phép dùng` section headers.
- Line 87 (`formatCitation`) — **byte-for-byte format** the augmenter must emit per doc:
  ```java
  "- [" + citation.inlineLabel() + "] "
      + nullSafe(citation.sourceTitle())
      + " | origin=" + nullSafe(citation.origin())
      + " | page=" + (citation.pageNumber() == null ? "null" : citation.pageNumber())
      + " | section=" + nullSafe(citation.sectionRef())
      + " | excerpt=" + nullSafe(citation.excerpt());
  ```
- Line 44 — `systemPrompt = paramProvider.getString("systemPrompt", SYSTEM_CONTEXT_FALLBACK)` — the augmenter must preserve this runtime-reloadable system prompt hook (inject `ActiveParameterSetProvider` the same way).

**Label format constant** (from `CitationMapper.java` line 61): `"Nguồn " + labelNumber`. Extract to a shared constant (e.g. `CitationMapper.INLINE_LABEL_PREFIX = "Nguồn "`) consumed by both `CitationMapper.toCitation` and `LegalQueryAugmenter` — Pitfall 4 byte-drift defense.

---

### `chat/advisor/CitationPostProcessor.java` (NEW — DocumentPostProcessor, transform+stash)

**Analog:** `chat/citation/CitationMapper.java` (unchanged callee) + `chat/advisor/GroundingGuardInputAdvisor.java` (advisor-component shape with public context-key constants).

**Component shape** (from `GroundingGuardInputAdvisor.java` lines 28-59):
```java
@Slf4j
@Component
public final class GroundingGuardInputAdvisor implements CallAdvisor {
    public static final String FORCE_REFUSAL = "chat.guard.forceRefusal";
    // ... @PostConstruct logOrder ...
}
```
Mirror this: `public static final String CITATIONS_KEY = "chat.rag.citations";` and `SOURCES_KEY = "chat.rag.sources";` directly on `CitationPostProcessor` (or extract to `ChatAdvisorContextKeys.java` if multiple advisors read them).

**Mapper call pattern** (reuse `CitationMapper` verbatim, `chat/citation/CitationMapper.java` line 18-26):
```java
return java.util.stream.IntStream.range(0, documents.size())
        .mapToObj(index -> toCitation(documents.get(index), index + 1))
        .toList();
```
The `index + 1` labelNumber assignment is the SAME number to stash into `Document.metadata["labelNumber"]` in the post-processor — identical retrieval-order numbering guarantees `[Nguồn n]` matches `CitationResponse.inlineLabel()`.

**Constructor injection** (from `CitationMapper.java` + `RetrievalPolicy.java` line 8-19):
```java
@Component
@RequiredArgsConstructor
public class CitationPostProcessor implements DocumentPostProcessor {
    private final CitationMapper citationMapper;
    // ...
}
```

---

### `chat/config/ChatClientConfig.java` (MODIFIED — swap wiring)

**Analog:** self. Current shape in `ChatClientConfig.java` lines 51-121 is the exact edit target.

**Current P8 wiring** (lines 98-107):
```java
ChatClient client = ChatClient.builder(chatModel)
        .defaultAdvisors(
                guardIn,                                              // HIGHEST_PRECEDENCE + 100
                MessageChatMemoryAdvisor.builder(chatMemory).build(), // HIGHEST_PRECEDENCE + 200
                noOpRag,                                              // HIGHEST_PRECEDENCE + 300
                noOpCache,                                            // HIGHEST_PRECEDENCE + 500
                noOpValidation,                                       // HIGHEST_PRECEDENCE + 1000
                guardOut                                              // LOWEST_PRECEDENCE  - 100
        )
        .build();
```

**P9 swap** — PR1 replaces `noOpRag` with a bean named e.g. `Advisor ragAdvisor` built via `RetrievalAugmentationAdvisor.builder()` (see RESEARCH Pattern 1). PR2 replaces `noOpValidation` with `StructuredOutputValidationAdvisor.builder().outputType(LegalAnswerDraft.class).maxRepeatAttempts(1).advisorOrder(HIGHEST_PRECEDENCE + 1000).build()`. Bean-parameter names on `chatClientMap(...)` lines 52-59 change from `NoOpRetrievalAdvisor`/`NoOpValidationAdvisor` to `Advisor ragAdvisor`/`Advisor validationAdvisor`. Keep `guardIn`, `MessageChatMemoryAdvisor`, `noOpCache`, `guardOut` and their orders unchanged (D-13).

**New `@Bean` methods added to this file** — `Advisor retrievalAdvisor(VectorStore, RetrievalPolicy, CitationPostProcessor, LegalQueryAugmenter)` and `Advisor validationAdvisor()` — see RESEARCH §3.1 and §3.4 for builder call shapes.

---

### `chat/service/ChatService.java` (MODIFIED — shrink ≥60%)

**Analog:** self. Target lines to DELETE (`ChatService.java`):
- Field injections no longer needed: `VectorStore vectorStore` (line 48), `ChatPromptFactory chatPromptFactory` (line 52), `CitationMapper citationMapper` (line 50) if fully absorbed.
- Lines 114-138 — manual retrieval + citation mapping (the `SearchRequest request = retrievalPolicy.buildRequest(...)`, `vectorStore.similaritySearch(request)`, `citationMapper.toCitations(documents)`, `citationMapper.toSources(citations)` block).
- Lines 154 — `chatPromptFactory.buildPrompt(...)` call; replace with passing the bare `question` to `.user(question)` (advisor chain handles augmentation).
- Lines 141-149 — `GroundingStatus.REFUSED` early-return based on document count; refusal ownership moves to `GroundingGuardOutputAdvisor` (D-08).

**What stays** (lines 79-112, 158-211):
- Intent dispatch switch (lines 94-112) — CHITCHAT / OFF_TOPIC fast paths unchanged.
- Ephemeral `memConvId` + `a.param(ChatMemory.CONVERSATION_ID, memConvId)` pattern (lines 158-171) — Pitfall 2 defense.
- `.entity(LegalAnswerDraft.class)` + try/catch (lines 176-191) — retry is now advisor-driven (PR2) so catch-all may simplify.
- `answerComposer.compose(groundingStatus, draft, citations, sources)` (line 206) — now sources/citations read from advisor context. Example read pattern (from RESEARCH §Code Examples):
  ```java
  var chatResponse = spec.call().chatResponse();
  @SuppressWarnings("unchecked")
  List<CitationResponse> citations = (List<CitationResponse>)
      chatResponse.getMetadata().get(CitationPostProcessor.CITATIONS_KEY);
  ```
  (Exact context hook TBD during planning per Open Question Q-01; fallback = thin `CitationStashAdvisor` wrapping `RetrievalAugmentationAdvisor`.)

---

### `test/.../LegalQueryAugmenterTest.java` (NEW — unit, golden fixture)

**Analog:** `chat/advisor/GroundingGuardAdvisorTest.java` (test shape) + `chat/citation/CitationMapperTest.java` (fixture construction).

**Test shape** (from `GroundingGuardAdvisorTest.java` lines 20-33, plain JUnit + AssertJ + Mockito, no Spring context):
```java
class GroundingGuardAdvisorTest {
    @Test
    void inputAdvisorOrderIsHighestPrecedencePlus100() {
        GroundingGuardInputAdvisor advisor = new GroundingGuardInputAdvisor();
        assertThat(advisor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 100);
        assertThat(advisor.getName()).isEqualTo("GroundingGuardInputAdvisor");
    }
}
```

**Fixture pattern** (from `CitationMapperTest.java` lines 17-42) — build `Document` with `Map.of(...)` metadata (sourceId, sourceVersionId, origin, pageNumber, sectionRef). Re-use for the 3-doc golden fixture.

**Golden comparison** — instantiate both `ChatPromptFactory` (with a stub `ActiveParameterSetProvider`) and `LegalQueryAugmenter`, run both against the same 3-doc `List<CitationResponse>`, assert the `[Nguồn 1/2/3]` citation block substring is byte-for-byte identical (`.contains(...)` or full `.isEqualTo(...)` on the extracted block).

---

### `test/.../CitationFormatRegressionIT.java` (NEW — live IT)

**Analog:** `chat/regression/StructuredOutputMatrixIT.java` (compact live-IT shape, 70 lines) + `VietnameseRegressionIT.java` (fixture iteration).

**Live-IT shape** (from `StructuredOutputMatrixIT.java` lines 34-69):
```java
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
class StructuredOutputMatrixIT {
    @Autowired ChatService chatService;
    @Autowired AiModelProperties properties;

    @Test
    void allEightCatalogedModelsReturnLegalAnswerDraft() {
        for (AiModelProperties.ModelEntry entry : properties.models()) {
            assertThatCode(() -> {
                ChatAnswerResponse r = chatService.answer("Vượt đèn đỏ phạt bao nhiêu với xe máy?", entry.id(), null);
                assertThat(r).isNotNull();
                assertThat(r.answer()).isNotBlank();
            }).doesNotThrowAnyException();
        }
    }
}
```
Copy the annotations + autowiring block verbatim. Swap assertion to: for each v1.0-fixture query, compare `response.citations()` list and `[Nguồn n]` labels embedded in `response.answer()` against stored JSON snapshot byte-for-byte.

---

### `test/.../StructuredOutputValidationAdvisorIT.java` (NEW — PR2)

**Analog:** `chat/advisor/GroundingGuardAdvisorTest.java` (Mockito `CallAdvisorChain.nextCall(...)` stub pattern).

**Mock chain pattern** (from `GroundingGuardAdvisorTest.java` lines 73-84):
```java
ChatClientRequest req = mock(ChatClientRequest.class);
ChatClientResponse resp = mock(ChatClientResponse.class);
CallAdvisorChain chain = mock(CallAdvisorChain.class);
when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(resp);
ChatClientResponse actual = fn.apply(req, chain);
assertThat(actual).isSameAs(resp);
verify(chain, times(1)).nextCall(req);
```
For the retry test: `when(chain.nextCall(...)).thenReturn(badJsonResp).thenReturn(goodJsonResp);` — assert `verify(chain, times(2)).nextCall(...)` confirms `maxRepeatAttempts=1` = one additional call.

---

### `test/.../ChatServiceShrinkArchTest.java` (NEW — ArchUnit)

**Analog:** `chat/archunit/ChatServiceDeletionArchTest.java` (exact shape).

**File-parse pattern** (from `ChatServiceDeletionArchTest.java` lines 19-43):
```java
class ChatServiceDeletionArchTest {
    private static final List<String> DELETED_IDENTIFIERS = List.of(
            "parseDraft", "extractJson", "fallbackDraft", /* ... */);

    @Test
    void chatServiceNoLongerContainsDeletedIdentifiers() throws Exception {
        String chatService = Files.readString(
                Paths.get("src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java"));
        for (String id : DELETED_IDENTIFIERS) {
            assertThat(chatService)
                    .as("ChatService.java must not contain deleted identifier '%s'", id)
                    .doesNotContain(id);
        }
    }
}
```
Copy verbatim, replace identifier list with P9 deletions: `"vectorStore"`, `"similaritySearch"`, `"citationMapper.toCitations"`, `"chatPromptFactory"`, `"buildPrompt"` (absence in `ChatService.java` — they may still exist elsewhere in repo).

## Shared Patterns

### Advisor component skeleton
**Source:** `chat/advisor/GroundingGuardInputAdvisor.java` + `GroundingGuardOutputAdvisor.java`
**Apply to:** `LegalQueryAugmenter.java`, `CitationPostProcessor.java`
```java
package com.vn.traffic.chatbot.chat.advisor;
// ... imports ...
@Slf4j
@Component
public final class XxxAdvisor implements Yyy {
    public static final String SOME_KEY = "chat.xxx.key";
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + N;
    @Override public String getName() { return "XxxAdvisor"; }
    @Override public int getOrder() { return ORDER; }
    @PostConstruct void logOrder() {
        log.info("Advisor registered: {} order={}", getName(), ORDER);
    }
}
```

### Constructor injection via `@RequiredArgsConstructor`
**Source:** `chat/citation/CitationMapper.java`-style (no-arg) and `retrieval/RetrievalPolicy.java` lines 8-19 (`@RequiredArgsConstructor` + `private final` fields)
**Apply to:** `CitationPostProcessor` (inject `CitationMapper`), `LegalQueryAugmenter` (inject `ActiveParameterSetProvider` to preserve the `systemPrompt` runtime-reload hook).
```java
@Component
@RequiredArgsConstructor
public class RetrievalPolicy {
    private final ActiveParameterSetProvider paramProvider;
    // ...
}
```

### `Supplier<>` for per-request runtime reload
**Source:** `retrieval/RetrievalPolicy.java` lines 21-27 — `getTopK()` and `getSimilarityThreshold()` both read from `paramProvider` on every call.
**Apply to:** `ChatClientConfig.retrievalAdvisor(...)` — must use method-reference form `retrievalPolicy::getTopK` / `retrievalPolicy::getSimilarityThreshold` on `VectorStoreDocumentRetriever.builder()` (Pitfall 5). Filter literal constant: `RetrievalPolicy.RETRIEVAL_FILTER` (line 14).

### Live-IT gating
**Source:** `chat/regression/StructuredOutputMatrixIT.java` lines 34-43, `VietnameseRegressionIT.java` lines 62-65
**Apply to:** all new live ITs (`CitationFormatRegressionIT`, extended `VietnameseRegressionIT`).
```java
@SpringBootTest
@Tag("live")
@DisabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = "^$")
```

### ArchUnit file-parse guard
**Source:** `chat/archunit/ChatServiceDeletionArchTest.java`
**Apply to:** `ChatServiceShrinkArchTest.java` — enforces the ≥60% LOC shrink by asserting deleted identifiers absent, not by line counting.

### Public context-key constants
**Source:** `GroundingGuardInputAdvisor.FORCE_REFUSAL` (line 36)
**Apply to:** `CitationPostProcessor.CITATIONS_KEY` / `SOURCES_KEY` (or a dedicated `ChatAdvisorContextKeys.java` if multiple readers).

### Label-format single source of truth (Pitfall 4)
**Source:** `CitationMapper.java` line 61 — `"Nguồn " + labelNumber`.
**Apply to:** `LegalQueryAugmenter` must read from the SAME constant (extract to `CitationMapper.INLINE_LABEL_PREFIX`). Both call sites feed the byte-for-byte `[Nguồn n]` regression fixture.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| (none) | | | Every P9 target has a close P7/P8 in-repo analog. `RetrievalAugmentationAdvisor` builder wiring itself is sourced from Spring AI context7 docs (RESEARCH §3.1) rather than a repo analog — but that is a library call inside `ChatClientConfig.@Bean`, not a new file. |

## Metadata

**Analog search scope:** `src/main/java/com/vn/traffic/chatbot/chat/{advisor,service,citation,config}/**`, `src/main/java/com/vn/traffic/chatbot/retrieval/**`, `src/test/java/com/vn/traffic/chatbot/chat/{advisor,citation,config,regression,archunit}/**`
**Files scanned:** 14 read (5 main advisor + config, 2 service, 1 citation, 1 retrieval, 5 test)
**Pattern extraction date:** 2026-04-18
