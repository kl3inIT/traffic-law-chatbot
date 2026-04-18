---
phase: 09-modular-rag-prompt-caching
fixed_at: 2026-04-19T00:00:00Z
review_path: .planning/phases/09-modular-rag-prompt-caching/09-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 9: Code Review Fix Report

**Fixed at:** 2026-04-19
**Source review:** .planning/phases/09-modular-rag-prompt-caching/09-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (Critical: 0, Warning: 3)
- Fixed: 3
- Skipped: 0
- Info findings (6) deferred per `fix_scope=critical_warning`

## Fixed Issues

### WR-01: `CitationMapper.integerValue` can throw `NumberFormatException` that escapes the citation pipeline

**Files modified:** `src/main/java/com/vn/traffic/chatbot/chat/citation/CitationMapper.java`
**Commit:** b401c62
**Applied fix:** Wrapped `Integer.valueOf(text.trim())` in a try/catch that returns `null` on `NumberFormatException`, matching the tolerant handling already present in `LegalQueryAugmenter.LegalCitationBlockFormatter.toInt`. Non-numeric `pageNumber` metadata values (e.g. `"N/A"`, `"trang 5"`) no longer abort the advisor chain via `CitationStashAdvisor.adviseCall`.

### WR-02: `ChatClientConfig.intentChatClientMap` double-builds `OpenAiChatModel` instances per model

**Files modified:** `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java`
**Commit:** c1a6426
**Applied fix:** Extracted a new `openAiChatModelMap` `@Bean` that builds each `OpenAiChatModel` exactly once keyed by model id. Both `chatClientMap` and `intentChatClientMap` now consume that registry instead of calling `buildChatModel` twice. For N models the startup cost drops from 2N to N HTTP clients / observation-registered chat models, and the javadoc ("built from the same `OpenAiChatModel` instances") is now accurate.

### WR-03: `ChatService.resolveEntry` silent fallback to `models().get(0)` can desync from `resolveClient`

**Files modified:** `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`
**Commit:** 5390e84
**Applied fix:** Rewrote `resolveEntry` so its fallback precedence mirrors `resolveClient` exactly — request id (if present in `chatClientMap`), then `aiModelProperties.chatModel()` (only if present in `chatClientMap`), then first entry of `chatClientMap.keySet()`. The method now resolves the `ModelEntry` by the actually-resolved id and throws `IllegalStateException` on config drift rather than silently picking `models().get(0)`. This keeps `supportsStructuredOutput()` in sync with the `ChatClient` actually dispatched, preventing the `ENABLE_NATIVE_STRUCTURED_OUTPUT` branch at line 93 from going the wrong way. Flagged as requiring human verification because it changes a silent-fallback branch to a throw — review `aiModelProperties.chatModel()` config to confirm it is always present in `models()`.

## Skipped Issues

None.

---

_Fixed: 2026-04-19_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
