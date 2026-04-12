# Quick Task 260412-wwk: Fix YAML Form UX, Run Check Error, Pipeline Log Coverage

**Date:** 2026-04-12
**Status:** Planning

## Root Cause Analysis

### Issue 1: YAML UX
The parameters page has a raw YAML textarea as primary input. The `YamlPreview` panel is a render of the textarea content — exactly the UX the user doesn't want. Need to invert: structured form inputs generate YAML behind the scenes.

### Issue 2: Run Check error — `CheckRun not found: <uuid>`
**Root cause:** Classic Spring `@Async` + `@Transactional` race condition.
- `CheckRunService.trigger()` is `@Transactional`
- It saves a `CheckRun` (not yet committed to DB)
- Then calls `checkRunner.runAll(run.getId())` which is `@Async` — spawns a new thread immediately
- The new thread starts a new transaction and looks up the CheckRun by ID
- But the outer transaction has NOT committed yet → row doesn't exist in DB → `orElseThrow` fires

**Fix:** Use `TransactionSynchronizationManager.registerSynchronization()` with `afterCommit()` so the async call fires only after the transaction commits.

### Issue 3: Pipeline log coverage
`ChatService.answer()` only logs 5 steps. Many steps are silent: citations mapping, legal citation check, prompt building, LLM call initiation, draft parsing, answer composition, chat log persistence. Need `logger.accept()` at each step.

## Changes

### Task 1: Install yaml package + rewrite parameters form
**File:** `frontend/package.json` + pnpm install
**File:** `frontend/app/(admin)/parameters/page.tsx`

Replace raw YAML textarea with structured form:
- Model section: name, temperature, maxTokens
- Retrieval section: topK, similarityThreshold, groundingLimitedThreshold  
- System Prompt: labeled textarea
- Messages section: all message keys as labeled inputs
- Case Analysis: maxClarifications input (requiredFacts preserved from existing YAML)
- Remove raw textarea; keep "View YAML" read-only collapsed section

Parse existing YAML on load → populate form fields
Serialize form fields → YAML content on submit

### Task 2: Fix CheckRunner race condition
**File:** `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunService.java`

In `trigger()`, replace direct `checkRunner.runAll(run.getId())` call with a `TransactionSynchronizationManager.registerSynchronization()` that calls it in `afterCommit()`.

### Task 3: Expand pipeline log coverage  
**File:** `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`

Add `logger.accept()` calls for:
- Citations mapped (count)
- Legal citation check result
- Refusal path entered
- Prompt built (char length)
- LLM call started
- Sources mapped (count)
- Draft parsed (success/fallback)
- Answer composed
- Chat log saved
