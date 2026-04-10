---
phase: 4
slug: next-js-chat-admin-app
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-10
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | jest 29.x / React Testing Library / Playwright (E2E) |
| **Config file** | `jest.config.ts` / `playwright.config.ts` — Wave 0 installs |
| **Quick run command** | `npm run test` |
| **Full suite command** | `npm run test:ci && npm run e2e` |
| **Estimated runtime** | ~60 seconds (unit) / ~180 seconds (E2E) |

---

## Sampling Rate

- **After every task commit:** Run `npm run test`
- **After every plan wave:** Run `npm run test:ci`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds (unit), 180 seconds (E2E full)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 4-01-01 | 01 | 0 | PLAT-02 | — | N/A | scaffold | `npx create-next-app --version` | ❌ W0 | ⬜ pending |
| 4-01-02 | 01 | 0 | PLAT-02 | — | N/A | unit | `npm run lint` | ❌ W0 | ⬜ pending |
| 4-02-01 | 02 | 1 | PLAT-02 | T-4-01 | API requests use env-based base URL, not hardcoded | unit | `npm test -- --testPathPattern=api-client` | ❌ W0 | ⬜ pending |
| 4-03-01 | 03 | 1 | ADMIN-01 | T-4-02 | Source list only loads on authenticated session | unit | `npm test -- --testPathPattern=sources` | ❌ W0 | ⬜ pending |
| 4-04-01 | 04 | 2 | ADMIN-02 | T-4-03 | Chat input sanitized before dispatch | unit | `npm test -- --testPathPattern=chat` | ❌ W0 | ⬜ pending |
| 4-05-01 | 05 | 2 | ADMIN-03 | — | N/A | unit | `npm test -- --testPathPattern=param` | ❌ W0 | ⬜ pending |
| 4-06-01 | 06 | 3 | ADMIN-06 | — | N/A | unit | `npm test -- --testPathPattern=vector` | ❌ W0 | ⬜ pending |
| 4-E2E-01 | all | 4 | all | — | End-to-end chat workflow completes | e2e | `npm run e2e` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontend/jest.config.ts` — Jest + React Testing Library config
- [ ] `frontend/playwright.config.ts` — Playwright E2E config
- [ ] `frontend/__tests__/` — stub test directory with at minimum one passing test
- [ ] `frontend/package.json` — `"test"`, `"test:ci"`, `"e2e"` script entries

*Wave 0 installs the test framework so all subsequent tasks have automated verification available.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Sidebar collapses correctly at mobile breakpoint | PLAT-02 | Visual/responsive check | Open app at 375px width, verify sidebar collapses |
| Admin screens restricted to admin role users | ADMIN-01 | Auth state requires real session | Log in as non-admin user, verify /admin redirects or 403 |
| Chat streaming renders tokens incrementally | ADMIN-02 | SSE streaming is visual | Send a question, observe response stream word by word |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
