# Security Audit — Phase 05: Quality Operations & Evaluation

**Audit date:** 2026-04-13
**ASVS Level:** 1
**Plans audited:** 05-01, 05-02, 05-03
**Auditor:** gsd-security-auditor (claude-sonnet-4-6)
**Verdict:** SECURED — all threats closed

---

## Threat Verification

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-05-01 | Information Disclosure | accept | CLOSED | No auth in v1; admin-only network boundary — accepted per threat register |
| T-05-02 | Tampering | mitigate | CLOSED | `ChatService.java` lines 95-103 (refusal path) and lines 136-144 (normal path): two independent try/catch blocks wrap `chatLogService.save()`; `log.warn()` on failure; chat response returned regardless |
| T-05-03 | Denial of Service | mitigate | CLOSED | `CheckRunner.java` line 34: `@Async("ingestionExecutor")` annotation present on `runAll()`; unbounded list execution is offloaded to virtual-thread executor |
| T-05-04 | Information Disclosure | accept | CLOSED | Admin context appropriate — accepted per threat register |
| T-05-05 | Elevation of Privilege | accept | CLOSED | Same unauthenticated surface as Phase 4 — accepted per threat register |
| T-05-06 | Denial of Service | mitigate | CLOSED | `CheckRunAdminController.java`: trigger is a single POST endpoint with no `@Scheduled`, `@EnableScheduling`, cron, `fixedDelay`, or `fixedRate` annotations anywhere in the checks package; on-demand only |
| T-05-07 | Tampering | mitigate | CLOSED | `CheckRunner.java` line 103: `chatService.answer(def.getQuestion())` — CheckDef question follows the identical plain-text path as end-user input; no new injection vector introduced |
| T-05-08 | Information Disclosure | accept | CLOSED | Admin review context appropriate — accepted per threat register |
| T-05-09 | Denial of Service | mitigate | CLOSED | `CheckRunner.java` lines 102-113 (`runSingle()`): per-definition try/catch; on exception `score=0.0`, `log="error:..."`, result preserved; outer `runAll()` continues with remaining defs |
| T-05-10 | Repudiation | accept | CLOSED | No auth in v1; deferred to v2 — accepted per threat register |
| T-05-11 | Information Disclosure | accept | CLOSED | Admin-only UI; no auth in v1 — accepted per threat register |
| T-05-12 | Tampering | accept | CLOSED | Same surface as end-user chat — accepted per threat register |
| T-05-13 | Denial of Service | mitigate | CLOSED | `frontend/app/(admin)/checks/page.tsx` line 315: `disabled={!hasActiveDefs \|\| triggerMutation.isPending}`; button label also changes to `'Đang chạy...'` while pending (line 319) |
| T-05-14 | Spoofing | accept | CLOSED | No auth; no session cookies — accepted per threat register |
| T-05-15 | Information Disclosure | accept | CLOSED | Admin-only screen; admin-authored content — accepted per threat register |

---

## Accepted Risks Log

The following threats were accepted by design for v1. All are deferred to v2 when authentication is introduced.

| Threat ID | Category | Rationale |
|-----------|----------|-----------|
| T-05-01 | Information Disclosure | ChatLogAdminController unauthenticated in v1; admin-only network boundary assumed |
| T-05-04 | Information Disclosure | Full answer in ChatLogDetailResponse appropriate for admin context |
| T-05-05 | Elevation of Privilege | /api/v1/admin/ endpoints unauthenticated; same surface established in Phase 4 |
| T-05-08 | Information Disclosure | CheckResult.actualAnswer stored without expiry; admin review context appropriate |
| T-05-10 | Repudiation | CheckRun has no audit of who triggered it; no auth in v1; deferred to v2 |
| T-05-11 | Information Disclosure | /chat-logs admin screen unauthenticated; admin-only UI |
| T-05-12 | Tampering | CheckDef form question field; same surface as end-user chat |
| T-05-14 | Spoofing | CSRF on POST /check-runs/trigger; no auth, no session cookies |
| T-05-15 | Information Disclosure | Check run detail exposes reference answers; admin-only screen, admin-authored content |

---

## Unregistered Threat Flags

| Plan | Flag | File | Notes |
|------|------|------|-------|
| 05-01 | threat_flag: information_disclosure | `ChatLogAdminController.java` | Maps to T-05-01 (registered, accepted) — informational only |
| 05-02 | (none) | — | No unregistered flags |
| 05-03 | (none) | — | No unregistered flags; T-05-13 mitigation confirmed implemented |

---

## Mitigate Controls — Evidence Detail

### T-05-02: ChatService log persistence try/catch

File: `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java`

Two independent guard blocks present:
- Refusal path (lines 95-103): wraps `chatLogService.save(...)` with catch on `Exception`; emits `log.warn()`; returns `refused` regardless.
- Normal answer path (lines 136-144): wraps `chatLogService.save(...)` with catch on `Exception`; emits `log.warn()`; returns `response` regardless.

### T-05-03: CheckRunner async executor

File: `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java`

`@Async("ingestionExecutor")` at line 34 on `runAll(UUID checkRunId)`. Execution is offloaded to the named executor (virtual-thread pool defined in infrastructure config). The HTTP trigger thread returns immediately.

### T-05-06: No scheduler on trigger endpoint

File: `src/main/java/com/vn/traffic/chatbot/checks/api/CheckRunAdminController.java`

Trigger is a plain `@PostMapping` with no auto-invocation mechanism. Grep across the entire `checks` package for `@Scheduled`, `@EnableScheduling`, `cron`, `fixedDelay`, `fixedRate` returns zero matches. On-demand only.

### T-05-07: CheckDef question — same plain-text path as end-user input

File: `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java`

`runSingle()` (line 103) calls `chatService.answer(def.getQuestion())` — the same `ChatService.answer()` entry point used by end-user requests. No additional processing or privilege escalation. Injection risk is identical to the end-user surface, which is in-scope for v2 hardening.

### T-05-09: Per-definition try/catch in CheckRunner.runSingle()

File: `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java`

`runSingle()` lines 102-113: try/catch on `Exception` for each definition. On failure: `score=0.0`, `actualAnswer=null`, `log="error: " + ex.getMessage()`. The outer `runAll()` loop continues with the remaining definitions. An outer try/catch (lines 56-62) guards the entire run and marks the `CheckRun` as FAILED only on an unexpected top-level exception.

### T-05-13: Trigger button disabled while pending

File: `frontend/app/(admin)/checks/page.tsx`

Line 315: `disabled={!hasActiveDefs || triggerMutation.isPending}` — button is non-interactive while the mutation is in-flight. Line 319: label changes to `'Đang chạy...'` as visual feedback. Both conditions must be false for the button to become interactive again.
