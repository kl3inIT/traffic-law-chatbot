---
phase: 1
slug: backend-foundation-knowledge-base
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-08
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test |
| **Config file** | `pom.xml` or `build.gradle` |
| **Quick run command** | `./mvnw test -Dtest=*UnitTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=*UnitTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 1-01-01 | 01 | 1 | PLAT-01 | — | REST endpoints require valid auth headers | unit | `./mvnw test -Dtest=SpringBootApplicationTest` | ❌ W0 | ⬜ pending |
| 1-01-02 | 01 | 1 | PLAT-03 | — | PostgreSQL connection verified | integration | `./mvnw verify -Dtest=*IntegrationTest` | ❌ W0 | ⬜ pending |
| 1-02-01 | 02 | 1 | KNOW-01 | — | Source ingestion stores provenance metadata | unit | `./mvnw test -Dtest=*SourceServiceTest` | ❌ W0 | ⬜ pending |
| 1-02-02 | 02 | 2 | KNOW-02 | — | Trusted-source filter enforced on retrieval | unit | `./mvnw test -Dtest=*VectorStoreTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/.../SpringBootApplicationTest.java` — smoke test for PLAT-01
- [ ] `src/test/java/.../SourceServiceTest.java` — stubs for KNOW-01–KNOW-04
- [ ] `src/test/java/.../VectorStoreTest.java` — stubs for KNOW-05–KNOW-06
- [ ] `src/test/java/.../IntegrationTest.java` — PostgreSQL/pgvector connectivity

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| PDF/Word ingestion via admin API | KNOW-03 | Requires real documents | POST multipart to /api/sources/ingest with a sample PDF and verify stored metadata |
| Trusted-website content ingestion | KNOW-04 | Requires live HTTP crawl | POST target URL to /api/sources/ingest-url, verify provenance fields populated |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
