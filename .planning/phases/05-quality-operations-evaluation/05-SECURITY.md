---
phase: 05
slug: quality-operations-evaluation
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-13
---

# Phase 05 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Admin client → ChatLog REST | Unauthenticated admin endpoints expose chat content (questions + full answers) | User message history, full LLM answers |
| ChatService → ChatLogService | Log persistence is a write path that must never block or fail the primary chat response | Chat text written to DB |
| Check runner → ChatService | The check runner calls ChatService.answer() repeatedly — malformed CheckDef could cause repeated LLM calls | CheckDef question text → LLM |
| Admin client → CheckDef REST | Admin creates CheckDef with arbitrary question/referenceAnswer text replayed against live ChatService | Admin-authored Q&A pairs |
| CheckRunner → external AI provider | LlmSemanticEvaluator makes separate LLM call per check item | LLM response text, reference answers |
| Browser → Next.js admin screens | Client renders sensitive chat content (user questions, full answers) — no auth gate in v1 | User questions, full LLM answers |
| Next.js client → Spring REST | Direct API calls to /api/v1/admin/* — CORS configured; no token auth in v1 | Admin API requests |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-05-01 | Information Disclosure | ChatLogAdminController — GET /chat-logs exposes user question history | accept | No auth in v1 scope; admin-only network boundary assumed per project constraints | closed |
| T-05-02 | Tampering | ChatService retrofit — log persistence throws could mask chat errors | mitigate | try/catch wraps chatLogService.save() in both chat paths; log.warn() on failure; response returned regardless — verified in ChatService.java lines 95-103 and 136-144 | closed |
| T-05-03 | Denial of Service | CheckRunner — unbounded CheckDef list executed async on each trigger | mitigate | @Async("ingestionExecutor") on runAll() offloads to virtual-thread executor — verified in CheckRunner.java line 34 | closed |
| T-05-04 | Information Disclosure | ChatLogDetailResponse — full answer text returned in API response | accept | Same information served to end users in chat; admin context is appropriate for review | closed |
| T-05-05 | Elevation of Privilege | ApiPaths — new admin endpoints under /api/v1/admin/ | accept | All admin/* endpoints share the same unauthenticated surface as Phase 4 admin endpoints; auth deferred to v2 per REQUIREMENTS.md | closed |
| T-05-06 | Denial of Service | CheckRunner — trigger endpoint can be called repeatedly | mitigate | On-demand POST only; no @Scheduled/@EnableScheduling/cron/fixedDelay/fixedRate in checks package — verified zero scheduler annotations | closed |
| T-05-07 | Tampering | CheckDef.question — malformed prompt injection into ChatService via check runner | mitigate | chatService.answer(def.getQuestion()) — identical plain-text path as end-user input; no new injection vector — verified CheckRunner.java line 103 | closed |
| T-05-08 | Information Disclosure | CheckResult.actualAnswer — full LLM responses stored in DB without expiry | accept | Same content served to users in chat; admin review context is appropriate | closed |
| T-05-09 | Denial of Service | LlmSemanticEvaluator — LLM failure causes 0.0 score but does not abort run | mitigate | Per-definition try/catch in runSingle(); score=0.0 on exception; run continues; outer runAll() catch marks FAILED only on unexpected top-level exception — verified CheckRunner.java lines 102-113 | closed |
| T-05-10 | Repudiation | CheckRun has no audit of who triggered it | accept | No auth in v1; trigger attribution deferred to v2 role separation | closed |
| T-05-11 | Information Disclosure | /chat-logs admin screen exposes all user questions and answers | accept | Admin-only UI; no auth in v1; same data available via REST; scoped to internal admin use | closed |
| T-05-12 | Tampering | CheckDef form — question field content sent as-is to ChatService.answer() | accept | Same input surface as end-user chat; existing input handling in ChatService applies | closed |
| T-05-13 | Denial of Service | "Chạy kiểm tra" button — rapid clicking could trigger multiple concurrent runs | mitigate | Button disabled while triggerMutation.isPending; label changes to "Đang chạy..." while pending — verified frontend/app/(admin)/checks/page.tsx line 315 | closed |
| T-05-14 | Spoofing | No CSRF protection on POST /check-runs/trigger | accept | No auth in v1; CSRF only meaningful with session cookies which this API does not use | closed |
| T-05-15 | Information Disclosure | Check run detail exposes full reference answers and LLM evaluator rationale | accept | Admin-only screen; reference answers authored by admin; appropriate access level | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-05-01 | T-05-01 | No auth in v1; admin-only network boundary; auth deferred to v2 per REQUIREMENTS.md | kl3inIT | 2026-04-13 |
| AR-05-04 | T-05-04 | Same content served to end users; admin context appropriate for full answer review | kl3inIT | 2026-04-13 |
| AR-05-05 | T-05-05 | Phase 4 admin endpoints already share same unauthenticated surface; v2 scoping per roadmap | kl3inIT | 2026-04-13 |
| AR-05-08 | T-05-08 | CheckResult.actualAnswer mirrors chat content; admin review context appropriate; no PII beyond what is already in chat logs | kl3inIT | 2026-04-13 |
| AR-05-10 | T-05-10 | No auth in v1; trigger attribution and audit trail deferred to v2 | kl3inIT | 2026-04-13 |
| AR-05-11 | T-05-11 | Admin-only UI scope; same data already exposed via REST API | kl3inIT | 2026-04-13 |
| AR-05-12 | T-05-12 | CheckDef question follows identical input path as end-user chat; no incremental injection surface | kl3inIT | 2026-04-13 |
| AR-05-14 | T-05-14 | CSRF only meaningful with session cookies; this API uses no session cookies | kl3inIT | 2026-04-13 |
| AR-05-15 | T-05-15 | Reference answers are admin-authored; rationale output is admin-facing; appropriate access level | kl3inIT | 2026-04-13 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-13 | 15 | 15 | 0 | gsd-security-auditor (claude-sonnet-4-6) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-13
