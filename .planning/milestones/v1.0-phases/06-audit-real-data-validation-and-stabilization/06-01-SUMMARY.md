---
phase: 06-audit-real-data-validation-and-stabilization
plan: 01
subsystem: database
tags: [cleanup, trust-policy, parameter-set, production-config]
dependency_graph:
  requires: []
  provides: [clean-database, production-trust-policy, production-parameter-set]
  affects: [06-02, 06-03, 06-04, 06-05, 06-06]
tech_stack:
  added: []
  patterns: [direct-sql-seed, FK-safe-delete-order]
key_files:
  created:
    - .planning/phases/06-audit-real-data-validation-and-stabilization/db_pre_cleanup_counts.txt
    - .planning/phases/06-audit-real-data-validation-and-stabilization/db_post_cleanup_counts.txt
  modified:
    - src/main/resources/default-parameter-set.yml
decisions:
  - "Seeded AiParameterSet directly via SQL because Spring Boot was not running; seeder behavior replicated exactly (count()==0 -> INSERT active=true with full YAML content)"
  - "NULLed kb_source.active_version_id before deleting kb_source_version to break circular FK without disabling constraints"
  - "Task 2 and 3 committed together since Task 2 produced no file artifacts (DB-only) and Task 3's YAML change was the only file modification"
metrics:
  duration_minutes: 8
  completed_date: "2026-04-13"
  tasks_completed: 3
  files_modified: 3
---

# Phase 06 Plan 01: DB Nuclear Cleanup + Production Config Baseline Summary

**One-liner:** Full database wipe (320 rows deleted across 15 tables) followed by production trust policy (7 domains, 3 tiers) and production Vietnamese legal assistant system prompt seeded into AiParameterSet.

## Task Results

### Task 1: Full Database Wipe

**Pre-cleanup row counts (2026-04-13T03:43:32Z):**

| Table                      | Before | After |
|----------------------------|--------|-------|
| check_result               | 17     | 0     |
| check_run                  | 7      | 0     |
| check_def                  | 6      | 0     |
| chat_log                   | 27     | 0     |
| thread_fact                | 9      | 0     |
| chat_message               | 10     | 0     |
| chat_thread                | 5      | 0     |
| source_trust_policy        | 0      | 0     |
| ai_parameter_set           | 5      | 0     |
| kb_vector_store            | 174    | 0     |
| kb_source_approval_event   | 20     | 0     |
| kb_source_fetch_snapshot   | 10     | 0     |
| kb_ingestion_job           | 10     | 0     |
| kb_source_version          | 10     | 0     |
| kb_source                  | 10     | 0     |
| **TOTAL**                  | **320** | **0** |

**Delete execution notes:**
- Wave 1 (leaf tables: check_result through chat_thread): all deleted without FK errors
- Wave 2 (standalone domain: source_trust_policy already empty, ai_parameter_set 5 rows): success
- Wave 3 (vector store: kb_vector_store 174 chunks): success
- Wave 4 (source lineage): NULLed `kb_source.active_version_id` first to break the self-referential FK before deleting kb_source_version (10 rows) and kb_source (10 rows)
- No FK constraint violations encountered

**Post-cleanup:** All 15 tables confirmed at 0 rows.

### Task 2: Rebuild Trust Policy

7 production rows inserted into `source_trust_policy`:

| Domain Pattern     | Trust Tier    | Description                                              |
|--------------------|---------------|----------------------------------------------------------|
| thuvienphapluat.vn | PRIMARY       | Cơ sở dữ liệu pháp luật quốc gia — nguồn tin cậy cấp 1 |
| chinhphu.vn        | PRIMARY       | Cổng thông tin điện tử Chính phủ Việt Nam                |
| moj.gov.vn         | PRIMARY       | Bộ Tư pháp Việt Nam — cơ quan ban hành và quản lý pháp luật |
| bocongan.gov.vn    | SECONDARY     | Bộ Công an — ban hành thông tư hướng dẫn giao thông     |
| mt.gov.vn          | SECONDARY     | Bộ Giao thông Vận tải — văn bản giao thông đường bộ     |
| vnexpress.net      | MANUAL_REVIEW | Báo điện tử — cần xem xét thủ công                      |
| tuoitre.vn         | MANUAL_REVIEW | Báo điện tử — cần xem xét thủ công                      |

Verification: `COUNT(*) = 7`, `PRIMARY count = 3`, `thuvienphapluat.vn count = 1` — all pass.

### Task 3: Rebuild AiParameterSet

**default-parameter-set.yml updated:** Replaced the 3-line placeholder system prompt with a full production-quality Vietnamese legal assistant prompt covering:
- Scope: NĐ 168/2024/NĐ-CP, NĐ 100/2019/NĐ-CP, Luật GTĐB 2008
- Limitations: no personal legal advice, no speculation without sources
- Style: formal Vietnamese, always cite legal documents, ask clarifying questions on fine queries

**Database state:** 1 active AiParameterSet seeded with production content (full YAML including updated systemPrompt). Content verified to contain:
- "Phạm vi hỗ trợ" — PRESENT
- "NĐ 168/2024/NĐ-CP" — PRESENT
- "Không hỗ trợ tư vấn pháp lý" — PRESENT

**Test suite:** `./gradlew test` — BUILD SUCCESSFUL, no regressions from YAML change.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | 9fb58fe | chore(06-01): full DB wipe — before/after row counts |
| Task 2+3 | fcfe1dc | feat(06-01): rebuild trust policy (7 rows) and production AiParameterSet |

## Deviations from Plan

### Auto-adaptation (not a bug fix)

**1. [Rule 1 - Adaptation] Backend not running — direct SQL seed used for AiParameterSet**
- **Found during:** Task 3
- **Issue:** Spring Boot backend was not running; plan assumed restart would trigger seeder
- **Fix:** Directly replicated the seeder behavior via SQL INSERT (same logic: `count()==0 -> INSERT name='Bộ tham số mặc định', active=true, content=<full YAML>`). The YAML file was already updated, so the content inserted matches what a real seeder run would produce.
- **Files modified:** none additional
- **Commit:** fcfe1dc

**2. [Rule 1 - Adaptation] kb_source.active_version_id NULLed before version delete**
- **Found during:** Task 1, Wave 4
- **Issue:** kb_source has a self-referential FK via active_version_id pointing to kb_source_version; direct delete of kb_source_version would fail without clearing this
- **Fix:** Executed `UPDATE kb_source SET active_version_id = NULL` before Wave 4 deletes
- **Commit:** 9fb58fe

**3. [Schema mismatch] Plan references `system_prompt` column; actual schema uses `content`**
- **Found during:** Task 3 acceptance criteria review
- **Issue:** Plan's acceptance criteria says `SELECT system_prompt FROM ai_parameter_set WHERE active = true` but the actual DB column is `content` (stores full YAML). The check was adapted to verify the `content` column contains the required phrases.
- **Impact:** None — the production content was seeded correctly; the column name in the plan was a documentation error.

## Known Stubs

None — all three tasks produced complete, production-quality artifacts.

## Threat Flags

None — no new network endpoints or auth paths introduced. DELETE statements were static SQL with no user input. DB access was via direct psycopg2 connection (trusted admin channel).

## Self-Check: PASSED

- db_pre_cleanup_counts.txt: EXISTS at .planning/phases/06-audit-real-data-validation-and-stabilization/db_pre_cleanup_counts.txt
- db_post_cleanup_counts.txt: EXISTS at .planning/phases/06-audit-real-data-validation-and-stabilization/db_post_cleanup_counts.txt
- default-parameter-set.yml: MODIFIED — contains "Phạm vi hỗ trợ"
- commit 9fb58fe: EXISTS
- commit fcfe1dc: EXISTS
- All 15 DB tables at 0: CONFIRMED
- source_trust_policy: 7 rows (3 PRIMARY, 2 SECONDARY, 2 MANUAL_REVIEW): CONFIRMED
- ai_parameter_set: 1 active row with production content: CONFIRMED
- Test suite: BUILD SUCCESSFUL
