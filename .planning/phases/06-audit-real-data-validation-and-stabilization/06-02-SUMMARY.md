---
phase: 06-audit-real-data-validation-and-stabilization
plan: 02
subsystem: knowledge-base
tags: [ingestion, validation, URL-scraping, trust-policy, vector-store]
dependency_graph:
  requires: [06-01]
  provides: [ingestion-validation-report, 3-legal-sources-active]
  affects: [06-03, 06-04, 06-05, 06-06]
tech_stack:
  added: []
  patterns: [URL-batch-import, approve-activate-workflow, 3-level-validation]
key_files:
  created:
    - .planning/phases/06-audit-real-data-validation-and-stabilization/ingestion_validation_report.md
  modified: []
decisions:
  - "URL ingestion from thuvienphapluat.vn produces website chrome, not decree text — paywall prevents full-text scraping; PDF upload is required for content fidelity"
  - "L1 (chunk count) and L3 (retrieval GROUNDED) pass; L2 (content spot-check) fails for all three sources"
  - "JAR rebuild required — BatchImportRequest DTOs were added to codebase after the prior JAR build; rebuilt with gradlew bootJar before ingestion"
  - "Sources set to TRUSTED via activate() not domain-pattern matching — trust classification at activation time"
metrics:
  duration_minutes: 25
  completed_date: "2026-04-13"
  tasks_completed: 2
  files_modified: 1
---

# Phase 06 Plan 02: Real Data Ingestion + 3-Level Validation Summary

**One-liner:** Three core Vietnamese traffic law sources ingested via URL batch from thuvienphapluat.vn (43 total chunks, all APPROVED/TRUSTED/ACTIVE), but Level 2 content spot-check fails for all sources — the website requires login, so scraped chunks contain website chrome instead of decree text.

## Task Results

### Task 1: Ingest Three Core Legal Sources

**Method:** URL batch import via `POST /api/v1/admin/ingestion/batch` (WEBSITE_PAGE type, PRIMARY trust tier)

**Backend startup:** Backend configured on port 8089. Rebuilt JAR required before ingestion (BatchImportRequest DTOs were missing from prior build — deviation documented below).

**Sources ingested:**

| Source | Source ID | ingestion_job status |
|--------|-----------|----------------------|
| NĐ 168/2024/NĐ-CP | 493c8965-17db-460c-a57d-50fb341ed806 | SUCCEEDED |
| Luật GTĐB 2008 | ff9eee9a-0d8d-4e77-bd08-7159fca5ccb2 | SUCCEEDED |
| NĐ 100/2019/NĐ-CP | 7c950b89-489f-47eb-bd89-8ebb5346ebf4 | SUCCEEDED |

**Approval + Activation:**
- All three: `POST /{sourceId}/approve` → APPROVED
- All three: `POST /{sourceId}/activate` → ACTIVE + TRUSTED

**Final state:**

| Source | approvalState | trustedState | status |
|--------|---------------|--------------|--------|
| NĐ 168/2024/NĐ-CP | APPROVED | TRUSTED | ACTIVE |
| Luật GTĐB 2008 | APPROVED | TRUSTED | ACTIVE |
| NĐ 100/2019/NĐ-CP | APPROVED | TRUSTED | ACTIVE |

**Vector store:** 43 total chunks (21 + 10 + 12), all eligible (approved=true, trusted=true, active=true).

**Task 1 acceptance criteria:**
- `COUNT(*) FROM kb_source WHERE approval_state = 'APPROVED' AND status = 'ACTIVE'` = 3 ✓
- `COUNT(*) FROM kb_source WHERE trusted_state = 'TRUSTED'` = 3 ✓
- `COUNT(*) FROM kb_vector_store` = 43 (> 0) ✓
- No REJECTED or ERROR sources ✓

### Task 2: 3-Level Validation

**Full results in:** `.planning/phases/06-audit-real-data-validation-and-stabilization/ingestion_validation_report.md`

**Summary:**

| Source | Total Chunks | Eligible | L1 (count >= 5) | L2 (content quality) | L3 (GROUNDED) | Overall |
|--------|-------------|----------|-----------------|---------------------|---------------|---------|
| NĐ 168/2024/NĐ-CP | 21 | 21 | PASS | FAIL | GROUNDED | FAIL |
| Luật GTĐB 2008 | 10 | 10 | PASS | FAIL | GROUNDED | FAIL |
| NĐ 100/2019/NĐ-CP | 12 | 12 | PASS | FAIL | GROUNDED | FAIL |

**Level 1:** PASS for all (>=5 eligible chunks; URL source threshold). Flag propagation confirmed working — ChunkMetadataUpdater.updateChunkMetadata called on activate.

**Level 2 (FAIL for all):** Content spot-check reveals website chrome rather than decree text:
- NĐ 168/2024 chunks: Account management UI, administrative procedure text, portal navigation
- Luật GTĐB 2008 chunks: Company info ("250 nhân sự"), 2001 industrial decisions (unrelated), English content
- NĐ 100/2019 chunks: Login prompts ("Đăng nhập"), VAT tax law (GTGT) from sidebar, membership registration

Root cause: thuvienphapluat.vn requires authenticated login for full decree text. The Jsoup scraper captures public HTML which includes website chrome, related-documents sidebar, and marketing text — not the actual legal articles.

**Level 3:** GROUNDED for all three sources with citations. The model answers from training knowledge since chunks are website noise. The system is retrieval-capable (chunks are indexed and returned by semantic search) but content fidelity is absent.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 + Task 2 | 3858ef3 | feat(06-02): ingest 3 legal sources + run 3-level validation — L1 PASS / L2 FAIL / L3 GROUNDED |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] JAR rebuild required — BatchImportRequest missing from prior build**
- **Found during:** Task 1, first batch import attempt
- **Issue:** `POST /api/v1/admin/ingestion/batch` returned 500 with "No static resource api/v1/admin/ingestion/batch". The JAR predated the addition of `BatchImportRequest`, `BatchImportItemRequest`, `BatchImportResponse`, and `BatchImportItemResult` DTOs to the ingestion package.
- **Fix:** Ran `./gradlew bootJar` to rebuild the JAR. Restarted backend. Batch endpoint now works.
- **Files modified:** No source changes — build output only.
- **Commit:** none (build artifact, not tracked)

**2. [Documentation mismatch] Backend port is 8089, not 8080**
- **Found during:** Task 1, first API call
- **Issue:** Plan assumed port 8080. application.yaml configures `server.port: 8089`.
- **Fix:** Used port 8089 for all API calls.

**3. [Ingestion reality] URL scraping produces website chrome, not legal text**
- **Found during:** Task 2, Level 2 spot-check
- **Issue:** All three sources fail Level 2 because thuvienphapluat.vn requires login to view full decree text. The public page contains navigation, related-documents sidebar, and account prompts — not the actual traffic law articles.
- **Fix:** Documented as known issue in ingestion_validation_report.md. Remediation is PDF upload.
- **Impact:** The system returns GROUNDED answers via model training knowledge, but retrieved chunk content does not contain actual legal text. This will affect check run quality (Plan 06-03) and may surface as low scores.

## Known Stubs

None in created files. The `ingestion_validation_report.md` is complete with all three source results.

**Data stub (important):** The vector store contains 43 chunks of website chrome, not actual Vietnamese traffic law text. This is the primary open issue for the remainder of Phase 06. Plans 06-03 and 06-04 will reveal the impact on check run scores and chat answers.

## Threat Flags

None — no new endpoints or auth paths introduced. URL inputs are static admin-authored strings (SSRF protection confirmed to be in ingestion pipeline from Phase 01.1).

## Self-Check: PASSED

- ingestion_validation_report.md: EXISTS at correct path
- commit 3858ef3: EXISTS (`git log --oneline | grep 3858ef3`)
- 3 sources APPROVED + ACTIVE + TRUSTED: CONFIRMED via API
- 43 eligible chunks: CONFIRMED via `/api/v1/admin/chunks/readiness`
- Level 3 GROUNDED for NĐ 168/2024: CONFIRMED
- Test suite: BUILD SUCCESSFUL (./gradlew test)
