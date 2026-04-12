---
phase: quick
plan: 260412-olx
subsystem: backend-chatlog, frontend-parameters
tags: [pipeline-logging, jmix-pattern, liquibase, consumer-logger, yaml-preview, react]
dependency_graph:
  requires: []
  provides:
    - pipeline_log column in chat_log table (migration 013)
    - Consumer<String> logger in ChatService producing structured pipeline trace
    - ChatLogDetailResponse.pipelineLog field
    - Parameters page live YAML preview panel
  affects:
    - ChatService.answer()
    - ChatLogService.save()
    - ChatLog entity
    - ChatLogDetailResponse
    - parameters/page.tsx
tech_stack:
  added: []
  patterns:
    - Consumer<String> logger collecting structured log lines joined by newline (jmix pattern)
    - 300ms debounced useEffect on form.watch for live preview
    - Simple line-by-line YAML parser in TSX (no external library)
key_files:
  created:
    - src/main/resources/db/changelog/013-chat-log-pipeline-log-column.xml
  modified:
    - src/main/resources/db/changelog/db.changelog-master.xml
    - src/main/java/com/vn/traffic/chatbot/chatlog/domain/ChatLog.java
    - src/main/java/com/vn/traffic/chatbot/chatlog/service/ChatLogService.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    - src/main/java/com/vn/traffic/chatbot/chatlog/api/dto/ChatLogDetailResponse.java
    - frontend/app/(admin)/parameters/page.tsx
decisions:
  - Use Consumer<String> logger pattern (jmix) instead of serializing chunks to JSON — produces human-readable structured trace lines
  - Use retrievalPolicy.getSimilarityThreshold() instead of SearchRequest.getSimilarityThreshold() (method does not exist on SearchRequest)
  - Simple line-by-line YAML parser over js-yaml library — avoids adding a dependency for a read-only display feature
  - useEffect + setTimeout debounce over useWatch subscription — simpler, no additional import needed
metrics:
  duration: ~25 minutes
  completed_date: "2026-04-12"
  tasks_completed: 2
  files_changed: 7
---

# Quick Plan 260412-olx: Pipeline Logging jmix-Pattern + Parameters YAML Preview — Summary

**One-liner:** Replace 3 separate pipeline-trace DB columns with a single `pipeline_log TEXT` column using a `Consumer<String>` jmix-pattern logger in ChatService, and add a 300ms-debounced live YAML preview panel to the parameters editor.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Backend — jmix-pattern pipeline_log column and Consumer logger | `bf4a959` | 013 migration, ChatLog, ChatLogService, ChatService, ChatLogDetailResponse |
| 2 | Frontend — Parameters page live YAML preview panel | `3f146de` | parameters/page.tsx |

## What Was Built

### Task 1: Backend Pipeline Logging

**Migration 013** drops `retrieved_chunks`, `prompt_text`, and `raw_model_response` columns from `chat_log` and adds a single `pipeline_log TEXT` column. Registered in `db.changelog-master.xml` after 012.

**ChatLog entity** now has a single `pipelineLog` field replacing the three separate fields. Lombok `@Builder`/`@Data` auto-generates the accessor.

**ChatLogService.save()** reduced from 10 to 8 parameters — the three separate string params collapsed into a single `String pipelineLog`.

**ChatService.answer()** now uses a `Consumer<String> logger` that simultaneously calls `log.info()` and appends to a `List<String> logMessages`. Five structured log lines are collected:
1. `User prompt: {question}`
2. `>>> Using vector_store [topK=N, threshold=0.70]: {question}`
3. `Found N documents: [(score) id, ...]`
4. `Grounding: STATUS (N docs)`
5. `Response in Nms [prompt=N, completion=N]: {first 120 chars}...`

These are joined with `\n` and passed to `chatLogService.save()` as `pipelineLog`.

The `serializeChunks()` helper and its `Map`/`LinkedHashMap` usage were removed entirely.

**ChatLogDetailResponse** record updated: three fields replaced with `String pipelineLog`, `fromEntity()` updated accordingly.

### Task 2: Frontend YAML Preview

**`YamlPreview` component** added (inline, no new file) with a `parseSimpleYaml()` function that parses `model`, `retrieval`, and `systemPrompt` sections line-by-line without any external library.

**Preview states:** empty input shows placeholder text, parse failure shows "YAML không hợp lệ" badge, valid YAML shows labeled sections for model/retrieval/systemPrompt.

**300ms debounce** via `useEffect` + `setTimeout` on `form.watch('content')`.

**Layout change:** YAML content area is now a 2-column flex row — textarea on left (flex-1), preview panel fixed at `w-56` on right.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Used retrievalPolicy.getSimilarityThreshold() instead of request.getSimilarityThreshold()**
- **Found during:** Task 1, Step 4
- **Issue:** Plan suggested `request.getSimilarityThreshold()` but `SearchRequest` has no such method in this Spring AI version
- **Fix:** Used `retrievalPolicy.getSimilarityThreshold()` which already exists and returns the same value
- **Files modified:** `ChatService.java`
- **Commit:** `bf4a959`

**2. [Rule 1 - Bug] Fixed unescaped `"` entities in JSX text**
- **Found during:** Task 2 commit (ESLint pre-commit hook)
- **Issue:** Two JSX text nodes contained literal `"` characters (`"+ Tạo mới"` and `"{deleteTarget?.name}"`) violating `react/no-unescaped-entities`
- **Fix:** Replaced with `&quot;` HTML entities
- **Files modified:** `parameters/page.tsx`
- **Commit:** `3f146de`

## Known Stubs

None — all data flows are wired end-to-end.

## Threat Flags

No new security surface introduced beyond what was assessed in the plan threat model. `pipeline_log` is admin-only and contains data already present in other columns.

## Self-Check

- `src/main/resources/db/changelog/013-chat-log-pipeline-log-column.xml` — created
- `bf4a959` — exists in git log
- `3f146de` — exists in git log
- Backend compiled: `BUILD SUCCESSFUL` (Gradle compileJava --rerun-tasks)
- Frontend built: `Compiled successfully` + TypeScript passed

## Self-Check: PASSED
