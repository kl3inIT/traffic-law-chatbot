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
