---
phase: 07
slug: chat-latency-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-17
---

# Phase 07 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot Test) + Spring AI test patterns per Context7 `/spring-projects/spring-ai` |
| **Config file** | `src/test/resources/application-test.yaml` (existing) |
| **Quick run command** | `./mvnw test -Dtest='*Test'` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60s (unit), ~180s (verify incl. integration) |

Explicit non-tests (per CONTEXT.md D-05, D-06):
- NO `ChatLatencyBaselineIT`
- NO `src/test/resources/benchmark/top20-queries.json`
- NO custom perf / load harness (Micrometer + manual smoke is sufficient)

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest='*Test'` (unit quick-run)
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~60 seconds (unit quick-run)

---

## Per-Task Verification Map

> Populated by planner. Each task gets a row; automated command must be a
> concrete `./mvnw` invocation or manual-smoke entry, not TBD.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD     | TBD  | TBD  | PERF-01..03 / CACHE-02 | — | N/A (perf/infra) | unit / spring-ai-pattern / manual | `./mvnw test -Dtest=...` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/CachingEmbeddingModelTest.java` — decorator hit/miss/dimension-mismatch behavior, follows Spring AI `EmbeddingModel` test pattern from Context7 `/spring-projects/spring-ai`
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/CacheKeyNormalizerTest.java` (or equivalent helper class) — lowercase + NFC + trim, Vietnamese diacritics preserved (D-15)
- [ ] `src/test/java/com/vn/traffic/chatbot/chatlog/service/ChatLogServiceAsyncTest.java` — `@Async` proxy activated (cross-bean call) + `REQUIRES_NEW` semantics + `List.copyOf` snapshot immutability (Pitfall 7)
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/CitationMapperTest.java` — pure domain, only if touched by slim-schema change (D-08)
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerSlimSchemaTest.java` — composer still green after 4-field removal (D-03/D-04)

*Spring AI component tests (embedding decorator) MUST be written following the
official test pattern fetched from Context7 `/spring-projects/spring-ai` — no
invented harnesses (D-07).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Top-20 canonical Vietnamese lookups p95 < 2.5s end-to-end | PERF-01 / PERF-02 / PERF-03 | Explicit user decision (D-05/D-06): no custom perf harness. Micrometer + ad-hoc smoke only. | Run app locally; `curl` a handful of canonical Vietnamese queries pre/post P7 merge; eyeball p95 from `/actuator/prometheus` http_server_requests timer. |
| Embedding cache hit ratio visible in Prometheus | CACHE-02 | Infra-level observability, not a unit concern. | Hit `/actuator/prometheus` after warm queries; confirm `cache_gets_total{cache="embedding",result="hit"}` > 0 and monotonically increasing on repeated identical query. |
| Frontend renders slim-schema without dead branches | D-04 | UI-level, Next.js dev server. | Start FE + BE; submit a canonical legal question; confirm no console errors, no `undefined` rendering for the 4 dropped scenario fields. |
| `EmbeddingModelChangedEvent` invalidates cache | D-17 | Requires triggering `AiModelProperties` reload — integration concern. | Change embedding model ID via admin reload path; confirm next query miss then hit; `cache_evictions_total` increments. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s (unit quick-run)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
