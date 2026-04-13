---
phase: 6
slug: audit-real-data-validation-and-stabilization
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-13
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Maven (Spring Boot test suite) + manual UI verification |
| **Config file** | `pom.xml` |
| **Quick run command** | `./mvnw test -q` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -q`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | DB cleanup | — | Non-seed data deleted | manual SQL | `psql -c "SELECT COUNT(*) FROM kb_source"` | ✅ | ⬜ pending |
| 06-01-02 | 01 | 1 | Param set rebuild | — | DefaultParameterSetSeeder creates new shell | manual | App startup log check | ✅ | ⬜ pending |
| 06-02-01 | 02 | 1 | Ingestion | — | Sources approved+trusted+active | manual | `psql -c "SELECT COUNT(*) FROM kb_vector_store"` | ✅ | ⬜ pending |
| 06-02-02 | 02 | 2 | 3-level validation | — | Chunk count + spot-check + retrieval pass | manual | API calls + DB queries | ✅ | ⬜ pending |
| 06-03-01 | 03 | 1 | Check run | — | avg_score ≥ 0.75, no result < 0.6 | manual | GET /api/v1/admin/check-runs/{id}/results | ✅ | ⬜ pending |
| 06-03-02 | 03 | 2 | Investigation | — | Root cause identified and fixed | manual | AUDIT.md complete | ✅ | ⬜ pending |
| 06-04-01 | 04 | 1 | Chat scenarios | — | 30+ scenarios tested | manual | Scenario checklist complete | ✅ | ⬜ pending |
| 06-05-01 | 05 | 1 | Feature audit | — | All P1-P5 features pass/fail/skipped | manual | AUDIT.md go/no-go table | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Phase 06 is an execution-and-validation phase, not a code-writing phase.
- No new test stubs are required — validation is done via API calls, DB queries, and manual checklist.
- Existing Spring Boot test suite must remain green throughout.

*If none: "Existing infrastructure covers all phase requirements — validation is manual (real data, live API)."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Vietnamese PDF ingestion | D-02 | Requires human to download PDFs and call upload API | Upload via POST /api/v1/admin/sources/upload, verify chunk count |
| Spot-check chunk content | D-03 | Requires human to compare DB text vs source PDF | SELECT content FROM kb_vector_store LIMIT 5 per source, verify text accuracy |
| Chat scenario testing | D-13/D-14 | Requires human judgment on legal correctness | 30+ scenarios via chat UI, classify each finding |
| Release go/no-go decision | D-17 | Milestone close requires human sign-off | Complete AUDIT.md go/no-go table |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
