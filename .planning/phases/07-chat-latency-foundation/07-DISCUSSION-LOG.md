# Phase 7: Chat Latency Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-17
**Phase:** 07-chat-latency-foundation
**Areas discussed:** Slim JSON schema, Baseline snapshot, Async chat-log executor (Feature-flag skipped by user)

---

## Gray Area Selection

| Area | Selected |
|------|----------|
| Feature-flag infrastructure | ✓ (later skipped by user — "không cần cái này đâu bỏ đi và next") |
| Slim JSON schema scope | ✓ |
| Baseline snapshot mechanism | ✓ |
| Async chat-log executor + refusal path | ✓ |

---

## Feature-flag Infrastructure

User requested to skip this area and continue. No questions answered.

**Resolution:** Defaulted to `@ConfigurationProperties` + Actuator `/refresh` + granular per-feature flag naming (`app.chat.v11.<feature>`). Captured as D-01/D-02/D-03 without deeper discussion.

---

## Slim JSON Schema

### Q1: LegalAnswerDraft hiện có 12 field. Bỏ field nào để giảm completion tokens?

| Option | Description | Selected |
|--------|-------------|----------|
| Bỏ 4 scenario fields | Drop scenarioFacts/Rule/Outcome/Actions; AnswerComposer fill null/[] để API contract ổn định; ~40% output token saving cho common Q&A | ✓ |
| Bỏ scenario + rule explanation | Aggressive, có thể mất chi tiết practical legal | |
| Giữ nguyên 12 field | Chờ P9 prompt caching | |

**User's choice:** Bỏ 4 scenario fields.

### Q2: API contract stability: frontend có thấy thay đổi không?

| Option | Description | Selected |
|--------|-------------|----------|
| Backend-only slim, API contract đứng yên | AnswerComposer fill null/[]; zero FE change | |
| Contract-first: update FE tolerant render trước | Safer, but 2 coordinated deploys | |
| Thật sự xoá field khỏi DTO (với FE update đồng thời) | Both sides change in lockstep | ✓ (via "Other") |

**User's choice (free-text):** "update đôngf thời và không quan tâm deploy hay không tôi chưa quan trọng ship code lên produciton"
**Notes:** User explicitly deferred production-deploy tolerance concerns for v1.1 iteration speed. Backend and frontend update in the same PR sequence; no null-fill backward-compat shim.

---

## Baseline Snapshot

### Q1: Cơ chế chụp baseline p95 latency + refusal-rate cho top-20 canonical legal queries?

| Option | Description | Selected |
|--------|-------------|----------|
| Spring integration test + JSON report | ChatLatencyBaselineIT via MockMvc/RestAssured, exports BASELINE.json + .md | ✓ |
| k6 / JMeter script ngoài | External load tool, more network-realistic | |
| Manual curl loop + bash script | Simple, not reproducible | |
| Admin endpoint tự report | Overkill cho baseline 1-lần | |

**User's choice:** Spring integration test + JSON report.

### Q2: Top-20 queries lấy từ đâu?

| Option | Description | Selected |
|--------|-------------|----------|
| Fixture file commit trong repo | `src/test/resources/benchmark/top20-queries.json` deterministic | ✓ |
| Query top-20 từ chat_log DB runtime | Representative but non-deterministic | |
| Tôi sẽ cung cấp list 20 câu | User provides the 20 queries | |

**User's choice:** Fixture file committed in repo.
**Notes:** Claude proposes 20 Vietnamese canonical queries drawn from traffic-law patterns (red-light, helmet, license, speeding, alcohol, documents, procedures) during planning; user reviews before lock.

---

## Async chat-log Executor

### Q1: Executor pool config cho chat-log async save?

| Option | Description | Selected |
|--------|-------------|----------|
| core=2, max=8, queue=1000, CallerRunsPolicy | Pitfall 7 reference; bounded backpressure | ✓ |
| core=4, max=16, queue=2000, CallerRunsPolicy | Larger, unnecessary for single-node v1.1 | |
| Unbounded queue default | OOM risk when DB slow | |

**User's choice:** core=2, max=8, queue=1000, CallerRunsPolicy.

### Q2: Refusal-path có async log giống happy-path không?

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ sync | Refusal rare; sync cost ~0; async loss hides grounding regressions (Pitfall 2/3) | ✓ |
| Cũng async | Nhất quán, rủi ro mất refusal evidence | |

**User's choice:** Giữ sync.

### Q3: Transaction propagation cho async save?

| Option | Description | Selected |
|--------|-------------|----------|
| @Transactional(REQUIRES_NEW) | Async thread inherits no tx; REQUIRES_NEW tạo tx riêng | ✓ |
| No transaction (auto-commit) | Risky nếu multi-table save | |

**User's choice:** @Transactional(REQUIRES_NEW).

---

## Claude's Discretion

- Feature-flag class layout, exact nested grouping of `ChatV11Properties`.
- JPA/Hibernate details of async `ChatLogService.save(...)`.
- Caffeine version + BOM alignment.
- `BASELINE.md` human-readable report formatting.
- Embedding cache key normalization (lowercase + NFC + trim, no accent-strip), maxSize=10_000, TTL=30min, dimension-mismatch guard, `EmbeddingModelChangedEvent` listener.
- Prompt trim scope in P7 = only scenario-field instruction block; no system/user split until P9.

## Deferred Ideas

- DB-backed feature-flag table + admin UI.
- Profile-based v11 master switch.
- External k6/JMeter harness.
- Runtime chat_log-based query sampling.
- Prompt split into cacheable + dynamic halves (P9).
- Deletion of `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` (P9 cleanup).
- Deletion of `containsAnyLegalCitation` keyword list (P8).
- Accent-stripping in embedding cache key.
</content>
</invoke>