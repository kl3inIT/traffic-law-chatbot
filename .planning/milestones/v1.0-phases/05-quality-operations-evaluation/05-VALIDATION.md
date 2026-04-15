---
phase: 5
slug: quality-operations-evaluation
status: verified
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-12
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (backend) / shadcn DataTable build check (frontend) |
| **Config file** | `src/test/` (backend) |
| **Quick run command** | `./mvnw test -Dtest=ChatLog*,CheckDef*,CheckRun*,LlmSemantic* -q` |
| **Full suite command** | `./mvnw test -q` |
| **Frontend gate** | `cd frontend && npm run build` |
| **Estimated runtime** | ~30 seconds (backend quick), ~90 seconds (full) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=ChatLog*,CheckDef*,CheckRun*,LlmSemantic* -q`
- **After every plan wave:** Run `./mvnw test -q` + `cd frontend && npm run build`
- **Before `/gsd-verify-work`:** Full suite must be green + frontend build passes
- **Max feedback latency:** 30 seconds (quick), 90 seconds (full)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | ADMIN-04 | — | ChatLog written after answer, never before | Unit | `./gradlew test --tests "*.ChatServiceTest"` | ✅ | ✅ green |
| 05-01-02 | 01 | 1 | ADMIN-04 | — | Log failure does not break chat response | Unit | `./gradlew test --tests "*.ChatLogServiceTest"` | ✅ | ✅ green |
| 05-01-03 | 01 | 1 | ADMIN-04 | — | ChatLog REST returns paginated + filtered list | Integration | `./gradlew test --tests "*.ChatLogControllerTest"` | ✅ | ✅ green |
| 05-02-01 | 02 | 2 | ADMIN-05 | — | CheckDef CRUD (create, update, delete, findActive) | Unit | `./gradlew test --tests "*.CheckDefServiceTest"` | ✅ | ✅ green |
| 05-02-02 | 02 | 2 | ADMIN-05 | — | CheckRunner runs all active defs, saves results | Unit | `./gradlew test --tests "*.CheckRunnerTest"` | ✅ | ✅ green |
| 05-02-03 | 02 | 2 | ADMIN-05 | — | SemanticEvaluator parses score from valid LLM response | Unit | `./gradlew test --tests "*.LlmSemanticEvaluatorTest"` | ✅ | ✅ green |
| 05-02-04 | 02 | 2 | ADMIN-05 | — | SemanticEvaluator returns 0.0 on LLM failure (no throw) | Unit | `./gradlew test --tests "*.LlmSemanticEvaluatorTest"` | ✅ | ✅ green |
| 05-02-05 | 02 | 2 | ADMIN-05 | — | CheckRun trigger returns 202 with run ID | Integration | `./gradlew test --tests "*.CheckRunControllerTest"` | ✅ | ✅ green |
| 05-03-01 | 03 | 3 | ADMIN-04 | — | Frontend build passes with chat-logs and checks pages | Build | `cd frontend && npm run build` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogServiceTest.java` — ChatService retrofit: log persisted after answer, log failure swallowed
- [x] `src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogControllerTest.java` — REST list with filter params (groundingStatus, dateRange), REST detail
- [x] `src/test/java/com/vn/traffic/chatbot/checks/CheckDefServiceTest.java` — CRUD operations (create, update, delete, findActive)
- [x] `src/test/java/com/vn/traffic/chatbot/checks/CheckRunnerTest.java` — async run, result aggregation, COMPLETED status set on CheckRun
- [x] `src/test/java/com/vn/traffic/chatbot/checks/LlmSemanticEvaluatorTest.java` — score parsing from JSON, failure returns 0.0 (no exception propagation)
- [x] `src/test/java/com/vn/traffic/chatbot/checks/CheckRunControllerTest.java` — 202 trigger response, GET results endpoint

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Chat log detail view shows correct citations panel | ADMIN-04 | UI state dependent on backend data | Log a chat, open log detail in admin, verify citations, grounding badge, token counts display correctly |
| Check run detail shows per-check scores | ADMIN-05 | Requires real LLM evaluator call | Create check defs, run checks, verify each check row shows question/reference/actual/score |
| AiParameterSet model dropdowns work | ADMIN-05 | Requires AllowedModel enum rendered in dropdown | Open parameters admin screen, verify chatModel and evaluatorModel selects appear and save correctly |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s
- [x] `nyquist_compliant: true` set in frontmatter

## Validation Audit 2026-04-13

| Metric | Count |
|--------|-------|
| Gaps found | 6 (Wave 0 unwritten + 1 broken AiParameterSet test) |
| Resolved | 7 |
| Escalated | 0 |

**Approval:** verified 2026-04-13
