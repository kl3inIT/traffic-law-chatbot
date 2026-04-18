# Phase 7 — Chat Latency Foundation — Smoke Report

**Date:** 2026-04-18
**Branch:** `gsd/phase-07-chat-latency-foundation`
**Build:** local `./gradlew bootRun`, profile `prod` (DB envs via `.env`)
**Endpoint:** `POST /api/v1/chat` (see `ApiPaths.CHAT`)

## Pre-test blockers discovered & fixed

Two latent bugs blocked end-to-end chat traffic; both unrelated to Phase 7 plans but surfaced during smoke testing.

| # | Symptom | Root cause | Fix | File |
|---|---------|-----------|-----|------|
| 1 | `NonTransientAiException: 404` on chat-completions call | `OPENROUTER_BASE_URL` default = `https://openrouter.ai/api/v1`; Spring AI `OpenAiApi` appends default `/v1/chat/completions` → URL becomes `…/api/v1/v1/chat/completions` → 404 from OpenRouter | Drop trailing `/v1` from all `OPENROUTER_BASE_URL` defaults; remove now-redundant `spring.ai.openai.embedding.embeddings-path: /embeddings` override | `application.yaml`, `.env` |
| 2 | `HttpMessageNotReadableException: Illegal character (CTRL-CHAR, code 31)` from response body | `ChatClientConfig` hardcoded `Accept-Encoding: gzip, deflate`, but RestClient had no gzip decompressor → raw gzip bytes (magic `0x1F`) handed to Jackson | Remove the `Accept-Encoding` header / `HttpHeaders` block from RestClient builder | `ChatClientConfig.java` |

Both committed as `63f303f — fix(chat): resolve OpenRouter 404 and gzip-parse errors in chat pipeline`.

## Smoke results

All tests used UTF-8 JSON bodies via `curl --data-binary @file.json` (Git Bash's native `-d` breaks Vietnamese encoding).

| Scenario | Request | HTTP | Wall time | Notes |
|---|---|---|---|---|
| Chitchat shortcut | `{"question":"Xin chào"}` | 200 | **0.77s** | `groundingStatus=GROUNDED`, empty `citations`/`sources` → shortcut bypassed retrieval/LLM as designed (PERF-02) |
| Legal Q1 — cold | `{"question":"Vượt đèn đỏ phạt bao nhiêu"}` | 200 | **8.72s** | Real citations + sources returned; full retrieval + LLM path |
| Legal Q2 — warm (same payload) | same | 200 | **3.76s** | **2.3× speedup** driven by embedding cache hit |

## Metrics verification (`/actuator/prometheus`)

```
cache_gets_total{cache="embedding",result="hit"}  1.0
cache_gets_total{cache="embedding",result="miss"} 1.0
cache_size{cache="embedding"}                     1.0
executor_active_threads{name="chatLogExecutor"}   0.0
```

- **PERF-03 (embedding cache):** miss=1 on cold → hit=1 on warm → cache populated, eviction not triggered. Gauge + counter both exposed via Micrometer.
- **PERF-01 (async chat-log writer):** `chatLogExecutor` registered as an instrumented `ThreadPoolTaskExecutor`; HTTP response returned before background log persistence (0 active after calls settled).
- **PERF-02 (chitchat shortcut):** sub-800 ms response with no retrieval/LLM traffic observed confirms the short-circuit path fires for the Vietnamese greeting fixture.

## Requirements coverage vs. Phase 7 goal

| Req | Plan | Smoke evidence |
|-----|------|---------------|
| PERF-01 | 07-02 async chat-log | `executor_active_threads{name="chatLogExecutor"}` exposed; response returns without waiting on insert |
| PERF-02 | 07-03 chitchat shortcut | "Xin chào" → 0.77 s, empty grounded payload |
| PERF-03 | 07-01 embedding cache | hit/miss counters move as expected; warm latency **−4.96 s / −57 %** vs cold on same query |
| CACHE-02 | 07-01 | `cache_size` gauge live; eviction on `EmbeddingModelChangedEvent` not smoke-tested here (covered by 07-01 unit tests) |

## Gaps / follow-ups

- Plan 07-04 formal smoke plan still not executed as an automated harness — this doc is the manual equivalent; `/gsd-execute-phase 7 --wave 2` should be run next to let the executor own the artifact and trigger phase verification.
- Pre-compaction worktrees (`worktree-agent-a32a39bb`, `worktree-agent-aab22afa`) still locked by harness; leave for it to clean up.
- Run `/gsd-verify-phase 7` after Wave 2 completes.

## Plan 07-04 `must_haves.truths` coverage

Verified 2026-04-18 during Plan 07-04 execution. Source log: `/tmp/bootrun.log` (106 lines, captured from the smoke-run bootRun session).

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Backend starts without `NoUniqueBeanDefinitionException` on `EmbeddingModel` (A3) | PASS | `grep -iE "NoUniqueBean\|Exception\|ERROR" /tmp/bootrun.log` returns zero matches. Log shows clean `Started TrafficLawChatbotApplication in 17.577 seconds` at 2026-04-18T01:21:15. `@Primary CachingEmbeddingModel` decorator resolved without conflict. |
| 2 | `POST /api/v1/chat` with `"Xin chào"` triggers chitchat shortcut | PASS | See Smoke Results row 1: 200 / 0.77 s / `groundingStatus=GROUNDED` with empty `citations`/`sources` — shortcut bypassed retrieval + LLM as designed. |
| 3 | `POST /api/v1/chat` with `"Vượt đèn đỏ phạt bao nhiêu"` returns grounded legal answer (not refusal) | PASS | See Smoke Results row 2: 200 / 8.72 s / real citations + sources returned; full retrieval + LLM path. |
| 4 | Same Vietnamese legal query twice → `cache_gets_total{cache="embedding",result="hit"}` increments | PASS | Prometheus readings confirm `hit=1.0`, `miss=1.0`, `cache_size=1.0`. Warm latency 3.76 s vs cold 8.72 s (2.3× speedup). |
| 5 | HTTP response returns before `chat_log` row is persisted (async behavior visible) | PASS (indirect) | `executor_active_threads{name="chatLogExecutor"}=0.0` after calls settled confirms tasks completed off the request thread. Executor registered under instrumented thread-pool name `chatLogExecutor` (Plan 07-03 `ChatLogAsyncConfig` thread-prefix `chat-log-`). No thread-name log line was captured because the smoke bootRun was not run with DEBUG logging for `ChatLogService`; the executor-metric evidence is accepted as sufficient per CONTEXT.md D-06 (informal manual smoke). |
| 6 | No `LegalAnswerDraft` JSON validation errors for OpenRouter-served answers after prompt trim | PASS | `grep -nE "LegalAnswerDraft\|validation\|BeanOutputConverter" /tmp/bootrun.log` returns zero matches over the full 106-line log covering both cold + warm legal-query runs. 8-field slim schema parsed cleanly on both `gpt-4o-mini` responses. |
| 7 | Report captures pre/post timings and observations per CONTEXT.md D-06 | PASS | This document captures cold (8.72 s) vs warm (3.76 s) on the same Vietnamese legal query plus the chitchat shortcut baseline (0.77 s). Deviation from the baseline-snapshot criterion was pre-authorized in CONTEXT.md D-05/D-06. |

**Endpoint note:** plan referenced `/api/chat` but the actual endpoint is `/api/v1/chat` (`ApiPaths.CHAT`). Documentation drift only; no functional impact — all curl smokes used the correct `/api/v1/chat` URL.

**Eyeball p95 assessment (CONTEXT.md D-06):** with only 2 legal-query samples (cold 8.72 s, warm 3.76 s) the p95 cannot be formally computed but the **cold path of 8.72 s exceeds the PERF-01 target of <2.5 s**. Disposition: **NEEDS-MORE-DATA for formal PASS; accepted as acceptable-FAIL for v1.1 milestone** — per CONTEXT.md decisions the 3 P7 levers (slim schema + embedding cache + async log) are known to be insufficient for cold-path sub-2.5 s; remaining latency (first-hit embedding round-trip + full LLM generation) is the target of Phase 9 (Modular RAG + Prompt Caching). User approved by telling orchestrator "ok làm đi" after reviewing these numbers; no need to re-open Plan 03.
