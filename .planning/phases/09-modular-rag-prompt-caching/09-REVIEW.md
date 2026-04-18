---
phase: 09-modular-rag-prompt-caching
reviewed: 2026-04-19T00:00:00Z
depth: standard
files_reviewed: 25
files_reviewed_list:
  - frontend/hooks/use-index.ts
  - frontend/lib/query-keys.ts
  - lombok.config
  - src/main/java/com/vn/traffic/chatbot/chat/advisor/CitationPostProcessor.java
  - src/main/java/com/vn/traffic/chatbot/chat/advisor/CitationStashAdvisor.java
  - src/main/java/com/vn/traffic/chatbot/chat/advisor/LegalQueryAugmenter.java
  - src/main/java/com/vn/traffic/chatbot/chat/advisor/PolicyAwareDocumentRetriever.java
  - src/main/java/com/vn/traffic/chatbot/chat/advisor/context/ChatAdvisorContextKeys.java
  - src/main/java/com/vn/traffic/chatbot/chat/citation/CitationMapper.java
  - src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java
  - src/main/java/com/vn/traffic/chatbot/chat/intent/IntentClassifier.java
  - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
  - src/test/java/com/vn/traffic/chatbot/chat/advisor/CitationPostProcessorTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/advisor/GroundingGuardAdvisorTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/advisor/LegalQueryAugmenterTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/advisor/StructuredOutputValidationAdvisorIT.java
  - src/test/java/com/vn/traffic/chatbot/chat/archunit/ChatServiceShrinkArchTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/config/ChatClientConfigTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/intent/IntentClassifierWiringTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/regression/CitationFormatRegressionIT.java
  - src/test/java/com/vn/traffic/chatbot/chat/regression/EmptyContextRefusalIT.java
  - src/test/java/com/vn/traffic/chatbot/chat/regression/Phase7Baseline.java
  - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceChitchatTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceEphemeralConversationIdTest.java
  - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java
findings:
  critical: 0
  warning: 3
  info: 6
  total: 9
status: issues_found
---

# Phase 9: Code Review Report

**Reviewed:** 2026-04-19
**Depth:** standard
**Files Reviewed:** 25
**Status:** issues_found

## Summary

Phase 9 delivers the modular RAG rewrite on Spring AI 2.0.0-M4: `RetrievalAugmentationAdvisor` composed from `PolicyAwareDocumentRetriever` + `CitationPostProcessor` + `LegalQueryAugmenter`, a `CitationStashAdvisor` that publishes citations/sources into `ChatClientResponse.context()`, a real `StructuredOutputValidationAdvisor` replacing the `NoOpValidationAdvisor` placeholder, a sibling `intentChatClientMap` for `IntentClassifier` to dodge the `LegalAnswerDraft`-bound validator, and the 78% `ChatService.doAnswer` shrink. The G4 fix (bare 36-char UUID for memory conversationId) is clean and well-tested.

Overall the wiring is careful and defensive — ordering slots are asserted by dedicated tests, context keys are centralized, the `LegalQueryAugmenter` rebuild-on-change cache is thread-safe, and the refusal path has multiple layers (grounding guard + ChatService hit-count check + BeanOutputConverter fallback). No critical bugs or security issues were found.

Three Warning findings concern (a) an unchecked `Integer.valueOf` inside `CitationMapper.integerValue` that can throw `NumberFormatException` and propagate out of the citation-response build, (b) a subtle duplication-of-model-builder cost in `intentChatClientMap` (each model instantiated twice at startup with independent HTTP clients), and (c) `resolveEntry` silently falling back to `models().get(0)` instead of the configured default when `aiModelProperties.chatModel()` isn't in the list, which can drift `supportsStructuredOutput` checks. Info items cover style/duplication.

## Warnings

### WR-01: `CitationMapper.integerValue` can throw `NumberFormatException` that escapes the citation pipeline

**File:** `src/main/java/com/vn/traffic/chatbot/chat/citation/CitationMapper.java:88-90`
**Issue:** `integerValue` calls `Integer.valueOf(text.trim())` without a try/catch. If vector-store metadata contains a non-numeric `pageNumber` (e.g., `"N/A"`, `"trang 5"`, whitespace-containing strings that trim cleanly but aren't numeric), the exception propagates out of `CitationStashAdvisor.adviseCall` via `toCitations` -> `toCitation` -> `integerValue`, aborting the advisor chain. The sibling `LegalQueryAugmenter.LegalCitationBlockFormatter.toInt` at line 143-154 handles this correctly with a try/catch returning `null`. Inconsistent handling across two code paths for the same metadata key is a latent bug.
**Fix:**
```java
private Integer integerValue(Object value) {
    if (value == null) return null;
    if (value instanceof Number number) return number.intValue();
    if (value instanceof String text && !text.isBlank()) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    return null;
}
```

### WR-02: `ChatClientConfig.intentChatClientMap` double-builds `OpenAiChatModel` instances per model

**File:** `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java:166-173`
**Issue:** `intentChatClientMap` loops `modelProperties.models()` and calls `buildChatModel(entry, modelProperties)` for each — the same call path used by `chatClientMap`. Each `buildChatModel` invocation creates a new `SimpleClientHttpRequestFactory`, a new `RestClient.Builder`, a new `OpenAiApi`, and a new `OpenAiChatModel`. So for N models, the app now instantiates 2N HTTP clients + 2N chat-model beans at startup, each with an independent 10-minute read timeout connection pool. Not a correctness bug, but wasteful and makes metric/observation cardinality confusing (two observation-registered models per id). The plan-comment says "built from the same `OpenAiChatModel` instances" which is not what the code does.
**Fix:** Either (a) share the `OpenAiChatModel` instances between the two maps (build once, reuse), or (b) update the javadoc to match reality. Option (a):
```java
@Bean
public Map<String, ChatClient> chatClientMap(... /* as today */) {
    Map<String, ChatClient> map = new LinkedHashMap<>();
    for (AiModelProperties.ModelEntry entry : modelProperties.models()) {
        OpenAiChatModel chatModel = getOrBuildChatModel(entry, modelProperties); // cache by id
        ...
    }
    ...
}

@Bean("intentChatClientMap")
public Map<String, ChatClient> intentChatClientMap(AiModelProperties modelProperties) {
    Map<String, ChatClient> map = new LinkedHashMap<>();
    for (AiModelProperties.ModelEntry entry : modelProperties.models()) {
        OpenAiChatModel chatModel = getOrBuildChatModel(entry, modelProperties);
        map.put(entry.id(), ChatClient.builder(chatModel).build());
    }
    return Collections.unmodifiableMap(map);
}
```

### WR-03: `ChatService.resolveEntry` silent fallback to `models().get(0)` can desync from `resolveClient`

**File:** `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java:147-161`
**Issue:** When the requested `modelId` is unknown, `resolveClient` (line 128-145) falls back to `chatClientMap.get(aiModelProperties.chatModel())`, then to `chatClientMap.values().iterator().next()`. But `resolveEntry` (line 147-161) resolves by filtering `aiModelProperties.models()` for `id().equals(resolved)` where `resolved` uses `aiModelProperties.chatModel()` as the fallback. If the configured default `chatModel()` is NOT present in `models()`, `resolveEntry` silently returns `models().get(0)` — which may have a different `supportsStructuredOutput()` value than the ChatClient actually dispatched. That mismatch drives the wrong branch at line 93 (`ENABLE_NATIVE_STRUCTURED_OUTPUT`) and could either skip native structured output on a model that supports it, or enable it on a model that doesn't. Not exercised on happy-path config, but a config drift bug waiting to surface.
**Fix:** Make `resolveEntry` mirror `resolveClient`'s fallback exactly — filter by the actually-resolved model's key, not `aiModelProperties.chatModel()`:
```java
private AiModelProperties.ModelEntry resolveEntry(String requestedModelId) {
    // Resolve the actual model id using the same precedence as resolveClient.
    String resolved = (requestedModelId != null && chatClientMap.containsKey(requestedModelId))
            ? requestedModelId
            : (chatClientMap.containsKey(aiModelProperties.chatModel())
                    ? aiModelProperties.chatModel()
                    : chatClientMap.keySet().stream().findFirst().orElseThrow(
                            () -> new IllegalStateException("No ChatClient available")));
    return aiModelProperties.models().stream()
            .filter(m -> m.id().equals(resolved))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "ModelEntry for resolved id '" + resolved + "' missing — config drift"));
}
```

## Info

### IN-01: `ChatService.doAnswer` multi-statement lines reduce readability despite the shrink goal

**File:** `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java:86-94, 96, 99`
**Issue:** Lines like `case CHITCHAT -> { return persisted(...); }` and `if (entry.supportsStructuredOutput()) spec = spec.advisors(...);` pack control flow and assignment onto single lines. The shrink goal is fewer LOC, but `checkstyle`-style single-statement-per-line improves `git blame` and stack-trace precision. Low priority.
**Fix:** Break multi-statement lines and inline braces to one statement per line, or accept as-is if repo convention favors terse style.

### IN-02: `resolveClient` is duplicated between `ChatService` and `IntentClassifier`

**File:** `src/main/java/com/vn/traffic/chatbot/chat/intent/IntentClassifier.java:69-88` and `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java:128-145`
**Issue:** Near-identical `resolveClient` logic is copy-pasted. The Javadoc even acknowledges the copy ("Copied from ChatService.java:232-251 per PATTERNS.md"). If the fallback semantics evolve (e.g., WR-03 fix), both must change in lockstep or they drift.
**Fix:** Extract to a small package-private helper (e.g., `ChatClientMapResolver#resolve(Map<String,ChatClient>, String requestedId, String defaultId)`) and call it from both places.

### IN-03: `ChatAdvisorContextKeys` constants could be namespace-prefixed for advisor-context hygiene

**File:** `src/main/java/com/vn/traffic/chatbot/chat/advisor/context/ChatAdvisorContextKeys.java:10-12`
**Issue:** `CITATIONS_KEY = "chat.rag.citations"` and `SOURCES_KEY = "chat.rag.sources"` are prefixed, but `LABEL_NUMBER_METADATA = "labelNumber"` is a bare key used against `Document.metadata()` — it could collide with upstream Spring AI or reader-provided metadata using the same key. Consider `"chat.rag.labelNumber"` for consistency.
**Fix:** Rename to `"chat.rag.labelNumber"` (breaking change across `CitationPostProcessor` + `LegalQueryAugmenter.LegalCitationBlockFormatter.formatDoc` + tests) or document that `labelNumber` is an intentional public metadata contract.

### IN-04: `Phase7Baseline.REFUSAL_RATE_PERCENT = Double.NaN` is correctly documented but still a live landmine

**File:** `src/test/java/com/vn/traffic/chatbot/chat/regression/Phase7Baseline.java:36`
**Issue:** The class is well-documented and deliberately uses `NaN` so comparisons fail-loud, which is the right call. But anyone running `VietnameseRegressionIT#refusalRateWithinTenPercentOfPhase7Baseline` without reading the javadoc will see a confusing failure. The comment references Plan 08-04 Task 1 which remains open. Consider adding `@Disabled("Phase-7 baseline not recovered — see Phase7Baseline.java")` on the specific parity test as a belt-and-suspenders guard.
**Fix:** Optional — add `@Disabled` annotation on the parity test pointing to the baseline file.

### IN-05: Frontend `queryKeys.chunks` and `queryKeys.chunk` share the `['admin','index','chunks']` prefix — invalidation ambiguity

**File:** `frontend/lib/query-keys.ts:9-11`
**Issue:** `queryKeys.chunks(sourceId, page, size)` returns `['admin','index','chunks', sourceId, page, size]` and `queryKeys.chunk(chunkId)` returns `['admin','index','chunks', chunkId]`. A React Query `invalidateQueries(['admin','index','chunks'])` invalidates both list and single-chunk caches, which may or may not be intentional. Typical convention separates list vs. detail with distinct segments (e.g., `['admin','index','chunks','list', ...]` and `['admin','index','chunks','detail', chunkId]`).
**Fix:** Consider:
```ts
chunks: (sourceId?: string, page = 0, size = 20) =>
  ['admin', 'index', 'chunks', 'list', sourceId ?? '', page, size] as const,
chunk: (chunkId: string) =>
  ['admin', 'index', 'chunks', 'detail', chunkId] as const,
```
Low priority if callers never invalidate the common prefix.

### IN-06: `CitationPostProcessor` builds a fresh `Document` per input; consider preserving `media` if present

**File:** `src/main/java/com/vn/traffic/chatbot/chat/advisor/CitationPostProcessor.java:36-41`
**Issue:** The `Document.builder()` call copies `id`, `text`, `metadata`, `score` but does not forward `media`. Spring AI `Document` supports a `media` list; if a future embedding/reader pipeline attaches media, it will be silently dropped here. Not a bug today (no reader currently attaches media), but a minor forward-compatibility hazard.
**Fix:** Either assert documents have no media at ingestion time, or copy `media`:
```java
Document.Builder b = Document.builder()
        .id(d.getId())
        .text(d.getText())
        .metadata(md)
        .score(d.getScore());
if (d.getMedia() != null && !d.getMedia().isEmpty()) b.media(d.getMedia());
labeled.add(b.build());
```

---

_Reviewed: 2026-04-19_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
