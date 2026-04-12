---
phase: 5
slug: quality-operations-evaluation
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| 05-01-01 | 01 | 1 | ADMIN-04 | — | ChatLog written after answer, never before | Unit | `./mvnw test -Dtest=ChatServiceTest -q` | Partial (needs retrofit coverage) | ⬜ pending |
| 05-01-02 | 01 | 1 | ADMIN-04 | — | Log failure does not break chat response | Unit | `./mvnw test -Dtest=ChatLogServiceTest#*log_failure* -q` | ❌ W0 | ⬜ pending |
| 05-01-03 | 01 | 1 | ADMIN-04 | — | ChatLog REST returns paginated + filtered list | Integration | `./mvnw test -Dtest=ChatLogControllerTest -q` | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 2 | ADMIN-05 | — | CheckDef CRUD (create, update, delete, findActive) | Unit | `./mvnw test -Dtest=CheckDefServiceTest -q` | ❌ W0 | ⬜ pending |
| 05-02-02 | 02 | 2 | ADMIN-05 | — | CheckRunner runs all active defs, saves results | Unit | `./mvnw test -Dtest=CheckRunnerTest -q` | ❌ W0 | ⬜ pending |
| 05-02-03 | 02 | 2 | ADMIN-05 | — | SemanticEvaluator parses score from valid LLM response | Unit | `./mvnw test -Dtest=LlmSemanticEvaluatorTest -q` | ❌ W0 | ⬜ pending |
| 05-02-04 | 02 | 2 | ADMIN-05 | — | SemanticEvaluator returns 0.0 on LLM failure (no throw) | Unit | `./mvnw test -Dtest=LlmSemanticEvaluatorTest -q` | ❌ W0 | ⬜ pending |
| 05-02-05 | 02 | 2 | ADMIN-05 | — | CheckRun trigger returns 202 with run ID | Integration | `./mvnw test -Dtest=CheckRunControllerTest -q` | ❌ W0 | ⬜ pending |
| 05-03-01 | 03 | 3 | ADMIN-04 | — | Frontend build passes with chat-logs and checks pages | Build | `cd frontend && npm run build` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogServiceTest.java` — ChatService retrofit: log persisted after answer, log failure swallowed
- [ ] `src/test/java/com/vn/traffic/chatbot/chatlog/ChatLogControllerTest.java` — REST list with filter params (groundingStatus, dateRange), REST detail
- [ ] `src/test/java/com/vn/traffic/chatbot/checks/CheckDefServiceTest.java` — CRUD operations (create, update, delete, findActive)
- [ ] `src/test/java/com/vn/traffic/chatbot/checks/CheckRunnerTest.java` — async run, result aggregation, COMPLETED status set on CheckRun
- [ ] `src/test/java/com/vn/traffic/chatbot/checks/LlmSemanticEvaluatorTest.java` — score parsing from JSON, failure returns 0.0 (no exception propagation)
- [ ] `src/test/java/com/vn/traffic/chatbot/checks/CheckRunControllerTest.java` — 202 trigger response, GET results endpoint

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Chat log detail view shows correct citations panel | ADMIN-04 | UI state dependent on backend data | Log a chat, open log detail in admin, verify citations, grounding badge, token counts display correctly |
| Check run detail shows per-check scores | ADMIN-05 | Requires real LLM evaluator call | Create check defs, run checks, verify each check row shows question/reference/actual/score |
| AiParameterSet model dropdowns work | ADMIN-05 | Requires AllowedModel enum rendered in dropdown | Open parameters admin screen, verify chatModel and evaluatorModel selects appear and save correctly |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
