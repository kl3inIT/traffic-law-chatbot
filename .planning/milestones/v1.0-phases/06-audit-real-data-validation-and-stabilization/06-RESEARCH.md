# Phase 06: Audit, Real-Data Validation, and Stabilization - Research

**Researched:** 2026-04-13
**Domain:** Release gate — ingestion validation, check run execution, DB cleanup, chat scenario testing, feature audit
**Confidence:** HIGH (all findings verified from codebase inspection; external source URLs are ASSUMED)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Real Data Ingestion**
- D-01: Ingest NĐ 168/2024/NĐ-CP, Luật GTĐB 2008, NĐ 100/2019/NĐ-CP, plus Bộ Công an/GTVT circulars
- D-02: Mix format — PDFs for key decrees, URLs from thuvienphapluat.vn for supplementary content
- D-03: Three-level validation per source: (1) eligible chunk count, (2) spot-check DB content, (3) targeted retrieval query

**Check Run Design**
- D-04: 20+ check definitions (comprehensive baseline, not minimum)
- D-05: Full coverage — fine amounts, documents/procedures, refusal, multi-turn, LIMITED_GROUNDING (≥3 per category)
- D-06: Score threshold — average ≥ 0.75, no individual result < 0.6
- D-07: Investigate all below 0.6 + failed average + semantic surprises
- D-08: Full remediation — fix config, prompt, AND code. Nothing deferred if fixable.
- D-09: Single AUDIT.md with one row per investigated result

**Database Cleanup**
- D-10: Full wipe of all non-seed data (nuclear option confirmed)
- D-11: Delete without pre-review ("delete anyway")
- D-12: Keep real source data + ingestion metadata; rebuild parameter set (rewrite system prompt), check defs (delete all, rebuild), trust policy (delete and rewrite from scratch). Delete: all dev/test sources+chunks, all test chat logs, all test/placeholder check defs+runs+results, all dev chat threads+messages+facts

**Chat Behavior Testing**
- D-13: 30+ manual test scenarios
- D-14: Full coverage — happy path, refusal, clarification loop, negation correction, off-topic rejection, ambiguous vehicle, multi-violation, tone/disclaimer consistency, hallucination probes
- D-15: Blocking / major / minor severity per scenario. Blocking + major gate milestone.

**Release Gate**
- D-16: Full P1–P5 feature audit — one explicit pass/fail per feature area
- D-17: Full go/no-go table, one row per feature area, blocking column
- D-18: Blocking = must fix; major/minor = documented deferral acceptable

### Claude's Discretion

None specified. Phase is fully locked by D-01 through D-18.

### Deferred Ideas (OUT OF SCOPE)

- Automated ingestion correctness tests (unit/integration)
- Scheduled check runs or regression monitoring
- Retention policy for chat logs
- Parameter set version history
</user_constraints>

---

## Summary

Phase 06 is a pure execution and validation phase, not a code-building phase. The primary deliverables are: real legal data ingested and validated, 20+ production check definitions executed against a clean DB, 30+ chat scenarios manually tested, and a complete feature audit table covering P1–P5. No new backend code is expected unless investigation reveals bugs requiring remediation (D-08).

The phase has six logical workstreams that feed into each other sequentially: DB cleanup must precede ingestion (so there is no stale data contaminating validation); ingestion must precede the check run and chat testing (real data must be retrievable); check run and chat testing run in parallel after ingestion; the feature audit and AUDIT.md run throughout and finalize last.

**Primary recommendation:** Split into five atomic plan files: (1) DB cleanup + rebuild, (2) real data ingestion + 3-level validation, (3) check definitions + check run + investigation, (4) chat scenario testing, (5) feature audit + milestone sign-off. Each plan has a clear done-condition before the next begins.

---

## Project Constraints (from CLAUDE.md)

- Read `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md` before major work
- Prefer REST-first backend changes
- Treat legal source provenance and answer grounding as critical, not optional
- Backend: Java 25, Spring REST; Frontend: Next.js sidebar-style app

---

## Standard Stack

No new libraries are introduced in this phase. All tools already exist in the codebase.

### Existing Tools Available for This Phase

| Tool | Purpose | How Used in Phase 06 |
|------|---------|----------------------|
| `mcp__postgres__execute_sql` | Direct DB access | All DELETE queries, chunk count checks, spot-check queries |
| `POST /api/v1/admin/sources/upload` | PDF file ingestion | Upload NĐ 168/2024, NĐ 100/2019, GTĐB 2008 PDFs |
| `POST /api/v1/admin/ingestion/batch` | URL batch import | Import thuvienphapluat.vn supplementary URLs |
| `POST /api/v1/admin/sources/{id}/approve` | Approval workflow | Approve each ingested source |
| `POST /api/v1/admin/sources/{id}/activate` | Activation workflow | Activate each approved source |
| `POST /api/v1/admin/check-runs/trigger` | Trigger evaluation | Returns 202 Accepted, runs async |
| `GET /api/v1/admin/check-runs/{runId}/results` | Retrieve scores | Per-result score + actualAnswer for investigation |
| `PUT /api/v1/admin/check-defs/{id}` | Update check defs | Fix reference answers after investigation |
| `PUT /api/v1/admin/parameter-sets/{id}` | Update param set | Deploy production system prompt |
| `DELETE /api/v1/admin/check-defs/{id}` | Delete stale defs | Nuclear cleanup of all dev/test check defs |

[VERIFIED: codebase — IngestionAdminController.java, SourceAdminController.java, CheckRunAdminController.java, CheckDefAdminController.java]

---

## Architecture Patterns

### How the Check Engine Works (Critical for Planning)

`CheckRunner.runAll(UUID checkRunId)` is `@Async("ingestionExecutor")`. It:
1. Fetches all active `CheckDef` entities via `checkDefRepository.findByActiveTrue()`
2. For each def: calls `chatService.answer(def.getQuestion())` — this goes through the full pipeline (retrieval → prompt → LLM → composition)
3. Passes `referenceAnswer` + `actualAnswer` to `LlmSemanticEvaluator.evaluate()`
4. On any per-def exception: score=0.0, log="error:..." — does NOT abort the run
5. Saves all `CheckResult` entities + sets `CheckRun.status = COMPLETED` with `averageScore`

**Critical implication:** The rebuilt production parameter set must be ACTIVE before triggering a check run, because `CheckRunner` calls `chatService.answer()` which reads the active parameter set at query time. [VERIFIED: CheckRunner.java, CheckRunService.java]

### How ChatLog Pipeline Trace Helps Root Cause Investigation

`ChatLog` stores a `pipelineLog` TEXT column (migration 013). For low-scoring check results:
- The check runner calls `chatService.answer()` — this also writes a `ChatLog` row (the try/catch log hook added in P5-01)
- Lookup the `ChatLog` for the same question, same timestamp as the check result
- `pipelineLog` shows retrieved chunk count, chunk sources, prompt construction steps
- `grounding_status` field shows GROUNDED/LIMITED_GROUNDING/REFUSED — this is the first diagnostic
- `sources` TEXT field (JSON-serialized) shows which source documents contributed

**Root cause ladder:**
1. `grounding_status = REFUSED` → zero chunks retrieved → investigate retrieval gate flags (approved/trusted/active on source) or similarity threshold too high
2. `grounding_status = LIMITED_GROUNDING` → 1–2 chunks → ingestion gap (too few chunks for this topic) or chunking produced low-signal chunks
3. `grounding_status = GROUNDED` but score < 0.6 → prompt construction issue, reference answer too strict, or LLM evaluator scoring anomaly
4. Score = 0.0 exactly → likely evaluator exception or language mismatch cap applied (cap=0.2 but could round to 0.0 in edge cases)

[VERIFIED: ChatLog.java, LlmSemanticEvaluator.java, CheckRunner.java]

### Retrieval Gate — What Must Be True for Chunks to Surface

The retrieval filter is hardcoded (safety-critical, not configurable per D-13 in Phase 2):

```java
// Source: RetrievalPolicy.java
public static final String RETRIEVAL_FILTER =
    "approvalState == 'APPROVED' && trusted == 'true' && active == 'true'";
```

For a chunk to appear in retrieval:
- Its parent `KbSource.approvalState = 'APPROVED'` (set via `POST /approve`)
- Its parent `KbSource.trustedState = 'TRUSTED'` (set automatically by trust policy domain-pattern match OR manually)
- Its parent `KbSource.status = 'ACTIVE'` (set via `POST /activate`)

The JSONB metadata on each `kb_vector_store` row carries these flags. A source that is approved but not activated will produce zero retrieval results even if chunks exist in the vector store.

[VERIFIED: RetrievalPolicy.java, migration 003-vector-store-schema.xml]

### GroundingStatus Thresholds

```java
// Source: ChatService.java (inferred from GroundingStatus enum usage)
GROUNDED     → retrieved chunks >= 3
LIMITED_GROUNDING → retrieved chunks 1–2
REFUSED      → retrieved chunks == 0
```

[VERIFIED: 06-CONTEXT.md code_context section, GroundingStatus.java enum]

### FactMemoryService — What It Detects (Critical for Multi-Turn Test Scenarios)

`FactMemoryService` extracts facts from user messages using regex patterns. Recognized fact keys:
- `vehicleType` — xe máy điện, xe đạp điện, xe gắn máy, xe máy, mô tô, ô tô, xe hơi, xe tải, xe buýt, xe khách, xe container, xe đạp
- `violationType` — ~30 violation patterns (vượt đèn đỏ, không mang đăng ký xe, không đội mũ bảo hiểm, chạy quá tốc độ, sử dụng điện thoại, etc.)
- `alcoholStatus` — nồng độ cồn/cồn/rượu bia with qualifier (không/có/dương tính/âm tính/vượt mức)
- `licenseStatus` — không có bằng lái / có bằng lái
- `injuryStatus` — không ai bị thương / có người bị thương / gây tai nạn
- `documentStatus` — không mang đăng ký xe / có đăng ký xe

**Negation guard:** "không phải X" / "không phải là X" / "chứ không phải X" in the 30 chars before a match suppresses the match. This is the behavior tested in D-14 negation correction scenarios.

**Correction (SUPERSEDED) behavior:** When a new fact value differs from an existing ACTIVE fact for the same key, the old fact gets `ThreadFactStatus.SUPERSEDED` and the new fact becomes ACTIVE. This is what "không phải xe máy, tôi đi ô tô" should trigger.

[VERIFIED: FactMemoryService.java]

### DefaultParameterSetSeeder — Cleanup + Rebuild Sequence

```java
// Source: DefaultParameterSetSeeder.java
if (repository.count() == 0) {
    // Seeds from classpath:default-parameter-set.yml
    repository.save(defaultSet);
}
```

The seeder only runs when `count() == 0`. After DELETE FROM ai_parameter_set, the seeder recreates the shell on next Spring Boot startup. The content comes from `default-parameter-set.yml`. The new production system prompt should then be applied via the admin API (`PUT /api/v1/admin/parameter-sets/{id}`) to replace the default content with production-quality content.

[VERIFIED: DefaultParameterSetSeeder.java, default-parameter-set.yml]

---

## DB Cleanup — Exact SQL Statements

### Table Inventory (from all 13 migrations)

| Table | Created In | Contains |
|-------|-----------|---------|
| `kb_source` | 001 | Source registry |
| `kb_source_version` | 001 | Versioned snapshots |
| `kb_ingestion_job` | 001 | Async job records |
| `kb_source_approval_event` | 001 | Approval audit trail |
| `kb_source_fetch_snapshot` | 001 | HTTP fetch provenance |
| `kb_vector_store` | 003 | Chunks + embeddings (JSONB metadata) |
| `chat_thread` | 004 | Conversation threads |
| `chat_message` | 004 | Messages within threads |
| `thread_fact` | 004 | Facts extracted per thread |
| `ai_parameter_set` | 005 | Parameter sets |
| `source_trust_policy` | 007 | Domain → trust tier mappings |
| `chat_log` | 008 | Pipeline trace per question |
| `check_def` | 009 | Check definitions |
| `check_run` | 010 | Check run records |
| `check_result` | 010 | Per-def scores per run |

[VERIFIED: migrations 001–013]

### Cleanup SQL — Sequenced by FK Constraints

**Phase 1: Delete everything in correct FK order (preserves no dev data)**

```sql
-- Step 1: Check results + runs (no FK dependents)
DELETE FROM check_result;
DELETE FROM check_run;

-- Step 2: Check definitions
DELETE FROM check_def;

-- Step 3: Chat log (no FK to threads)
DELETE FROM chat_log;

-- Step 4: Thread-level data (facts before messages, messages before threads)
DELETE FROM thread_fact;
DELETE FROM chat_message;
DELETE FROM chat_thread;

-- Step 5: Trust policy (standalone, no FK dependents)
DELETE FROM source_trust_policy;

-- Step 6: Parameter sets (ai_parameter_set)
-- Note: After delete, Spring Boot restart recreates via DefaultParameterSetSeeder
DELETE FROM ai_parameter_set;

-- Step 7: Vector store chunks
DELETE FROM kb_vector_store;

-- Step 8: Source ingestion data (FK order: approval_events, fetch_snapshots, jobs, versions, sources)
DELETE FROM kb_source_approval_event;
DELETE FROM kb_source_fetch_snapshot;
DELETE FROM kb_ingestion_job;
DELETE FROM kb_source_version;
DELETE FROM kb_source;
```

**Important:** `kb_vector_store.metadata` is JSONB. The vector store does NOT have a direct FK to `kb_source` — it only carries source metadata in the JSONB `metadata` column. Deleting all sources does not cascade-delete chunks. Both must be explicitly deleted.

[VERIFIED: migration 001 (FK structure), migration 003 (no FK on kb_vector_store)]

### Row Count Verification Query (run BEFORE each DELETE)

```sql
SELECT
  (SELECT COUNT(*) FROM check_result)               AS check_results,
  (SELECT COUNT(*) FROM check_run)                  AS check_runs,
  (SELECT COUNT(*) FROM check_def)                  AS check_defs,
  (SELECT COUNT(*) FROM chat_log)                   AS chat_logs,
  (SELECT COUNT(*) FROM thread_fact)                AS thread_facts,
  (SELECT COUNT(*) FROM chat_message)               AS chat_messages,
  (SELECT COUNT(*) FROM chat_thread)                AS chat_threads,
  (SELECT COUNT(*) FROM source_trust_policy)        AS trust_policies,
  (SELECT COUNT(*) FROM ai_parameter_set)           AS parameter_sets,
  (SELECT COUNT(*) FROM kb_vector_store)            AS vector_chunks,
  (SELECT COUNT(*) FROM kb_source_approval_event)   AS approval_events,
  (SELECT COUNT(*) FROM kb_source_fetch_snapshot)   AS fetch_snapshots,
  (SELECT COUNT(*) FROM kb_ingestion_job)           AS ingestion_jobs,
  (SELECT COUNT(*) FROM kb_source_version)          AS source_versions,
  (SELECT COUNT(*) FROM kb_source)                  AS sources;
```

[VERIFIED: migration schema inspection]

---

## Ingestion Validation — 3-Level Protocol

### Level 1: Eligible Chunk Count Query

After ingest + approve + activate, verify chunks are eligible for retrieval:

```sql
-- Replace 'NĐ 168/2024' with the actual source title
SELECT
  s.title,
  s.approval_state,
  s.trusted_state,
  s.status,
  COUNT(v.id) AS total_chunks,
  SUM(CASE WHEN
    (v.metadata->>'approvalState') = 'APPROVED'
    AND (v.metadata->>'trusted') = 'true'
    AND (v.metadata->>'active') = 'true'
  THEN 1 ELSE 0 END) AS eligible_chunks
FROM kb_source s
LEFT JOIN kb_vector_store v ON (v.metadata->>'source_id') = s.id::text
GROUP BY s.id, s.title, s.approval_state, s.trusted_state, s.status
ORDER BY s.created_at DESC;
```

**Minimum acceptable:** A 50+ page PDF legal decree should produce 100+ chunks with token-based splitting. A URL page should produce 5–30 chunks. Zero eligible chunks with non-zero total means the approval/trust/active flags are not propagated to JSONB metadata.

[VERIFIED: migration 003 (JSONB metadata structure), RetrievalPolicy.java (filter expression)]

### Level 2: Spot-Check Content Query

```sql
-- Sample 5 chunks from a source and verify text quality
SELECT
  LEFT(content, 300) AS content_preview,
  metadata->>'source_title' AS source_title,
  metadata->>'chunk_index' AS chunk_index,
  metadata->>'page_number' AS page_number
FROM kb_vector_store
WHERE (metadata->>'source_title') ILIKE '%168/2024%'
ORDER BY (metadata->>'chunk_index')::int ASC
LIMIT 5;
```

Manual verification: open the actual PDF and compare the content preview against the corresponding article text. Token-split chunks should contain coherent legal text (full sentences, article references intact).

### Level 3: Targeted Retrieval Test

Via the chat API — send a question that specifically targets that source's content:

```
POST /api/v1/chat/answer
{ "question": "Mức phạt vi phạm nồng độ cồn theo Nghị định 168/2024 là bao nhiêu?" }
```

Check response: `grounding_status = GROUNDED`, citations reference NĐ 168/2024. If REFUSED or LIMITED_GROUNDING, the source is not retrievable despite ingestion.

[VERIFIED: ChatService.java ingestion pipeline, RetrievalPolicy.java]

---

## Production System Prompt Design

### Current Default (from default-parameter-set.yml)

The current system prompt is minimal: "Bạn là trợ lý hỏi đáp pháp luật giao thông Việt Nam." with basic tone instruction. This is intentionally a shell — Phase 06 must replace it with production content.

[VERIFIED: default-parameter-set.yml]

### What the Production System Prompt Must Include

The `systemPrompt` key in the parameter set YAML is the only configurable section of `ChatPromptFactory.buildPrompt()`. The rest (citation instructions, JSON schema, grounding status notice) is hardcoded for safety. The system prompt text is prepended first.

**Production system prompt requirements** (derived from legal grounding principles and the answer pipeline structure):

```yaml
systemPrompt: |
  Bạn là trợ lý hỏi đáp pháp luật giao thông Việt Nam, hoạt động trong phạm vi các văn bản pháp luật đã được phê duyệt và đáng tin cậy.

  Phạm vi hỗ trợ:
  - Xử phạt vi phạm hành chính trong lĩnh vực giao thông đường bộ (NĐ 168/2024/NĐ-CP, NĐ 100/2019/NĐ-CP)
  - Luật Giao thông đường bộ 2008 và các văn bản hướng dẫn thi hành
  - Giấy tờ, thủ tục xử lý vi phạm giao thông
  - Quy trình, bước thực hiện sau khi bị xử phạt

  Giới hạn hỗ trợ:
  - Không hỗ trợ tư vấn pháp lý cá nhân hoặc thay thế luật sư
  - Không trả lời các câu hỏi ngoài phạm vi giao thông đường bộ
  - Không suy đoán hoặc tự điền thông tin khi nguồn không hỗ trợ

  Phong cách trả lời:
  - Tiếng Việt, rõ ràng, trang trọng, không rườm rà
  - Luôn dẫn chiếu đến văn bản pháp luật cụ thể khi có căn cứ
  - Từ ngữ chuyên ngành giải thích rõ ràng khi cần
```

[ASSUMED — actual content requires legal review against real documents post-ingestion]

---

## Check Definitions — 20+ Production-Grade Definitions

### Category Matrix (D-05 requirements)

| Category | Minimum | Recommended Count | Description |
|----------|---------|-------------------|-------------|
| Fine amounts / penalty tiers | 3 | 6 | Specific fine amounts from NĐ 168/2024 and NĐ 100/2019 |
| Documents / procedures | 3 | 4 | Required docs for license, registration, court procedures |
| Refusal boundary | 3 | 4 | Out-of-scope questions, questions with no retrievable content |
| Multi-turn scenario | 3 | 4 | Clarification flow completion, FINAL_ANALYSIS structure |
| LIMITED_GROUNDING edge cases | 3 | 4 | Weak retrieval, partial information queries |
| **Total** | **15** | **22** | Must reach 20+ per D-04 |

### Sample Check Definition Questions (by category)

**Fine amounts / penalty tiers** [ASSUMED — exact amounts must be verified against ingested documents]:
1. "Mức phạt đối với người điều khiển xe máy vượt đèn đỏ theo quy định hiện hành là bao nhiêu?"
2. "Người lái ô tô có nồng độ cồn vượt mức cho phép bị phạt bao nhiêu tiền theo Nghị định 168/2024?"
3. "Phạt bao nhiêu nếu xe máy không có gương chiếu hậu?"
4. "Mức phạt đối với xe ô tô chạy quá tốc độ 20km/h trong khu dân cư là bao nhiêu?"
5. "Người điều khiển xe máy không đội mũ bảo hiểm bị xử phạt thế nào?"
6. "Mức phạt tịch thu giấy phép lái xe khi nồng độ cồn vượt mức tối đa theo NĐ 168?"

**Documents / procedures:**
7. "Cần mang theo giấy tờ gì khi bị cảnh sát giao thông dừng xe?"
8. "Thủ tục nộp phạt vi phạm giao thông như thế nào? Cần làm gì sau khi bị giữ xe?"
9. "Làm sao để lấy lại xe bị tạm giữ do vi phạm giao thông?"
10. "Giấy phép lái xe hết hạn bị xử phạt thế nào và gia hạn ở đâu?"

**Refusal boundary:**
11. "Tôi có thể kiện cảnh sát giao thông vì xử phạt sai không?" (legal advice, not factual lookup)
12. "Dự báo thời tiết Hà Nội ngày mai thế nào?" (completely out of domain)
13. "Quy định về tốc độ tối đa trên đường cao tốc loại A1 ở vùng núi phía Bắc là bao nhiêu?" (likely zero retrieval from current sources)
14. "Mức phạt vi phạm luật hàng hải Việt Nam là gì?" (wrong legal domain)

**Multi-turn scenario:**
15. "Tôi đang đi xe và bị CSGT dừng" → clarification → "tôi đi ô tô" → "tôi vượt đèn đỏ" → FINAL_ANALYSIS (question: "Sau khi cung cấp đủ thông tin xe ô tô vượt đèn đỏ, hệ thống có đưa ra phân tích hoàn chỉnh không?")
16. "Xe của tôi bị giữ, tôi phải làm gì?" (vehicleType missing — should trigger clarification)
17. "Tôi vi phạm, bị phạt tiền" (violationType missing AND vehicleType missing — double clarification)
18. "Không phải xe máy, tôi đi ô tô" (negation correction — vehicleType should SUPERSEDE from xe máy to ô tô)

**LIMITED_GROUNDING edge cases:**
19. "Nghị định 168/2024 có thay thế hoàn toàn Nghị định 100/2019 không?" (requires comparison across two docs — likely LIMITED_GROUNDING)
20. "Mức phạt đối với xe thô sơ không đèn chiếu sáng vào ban đêm?" (niche category, may have limited coverage)
21. "Thủ tục xử lý vi phạm giao thông của người nước ngoài tại Việt Nam?" (specialized, likely limited coverage)
22. "Quy định về đội mũ bảo hiểm trên đường trong khu vực tư nhân không phải đường công cộng?" (edge case)

[ASSUMED — exact reference answers must be written after real documents are ingested and verifiable]

### Reference Answer Writing Guidelines

Reference answers for check definitions must:
1. Cite the specific article and decree by number ("Theo Điều X, Nghị định 168/2024/NĐ-CP...")
2. Include the specific penalty amount (fine range, not just "bị phạt")
3. Include disclaimer when the answer involves legal guidance
4. Match the structure of what the chatbot produces (conclusion first, then legal basis, then penalty)
5. Be written AFTER documents are ingested so the answer matches retrievable content exactly

---

## Chat Scenario Testing — 30+ Scenario Matrix

### Category Breakdown (D-14 requirements)

| Category | Min Scenarios | Severity Focus |
|----------|--------------|----------------|
| Happy path — grounded single-turn | 4 | Disclaimer, citation, grounding_status |
| Happy path — multi-turn FINAL_ANALYSIS | 3 | Full pipeline completion |
| Refusal — out-of-scope | 3 | Correct REFUSED status, no invented content |
| Refusal — zero retrieval | 2 | REFUSED not 500, actionable next steps |
| Clarification loop — missing vehicleType | 3 | CLARIFICATION_NEEDED response mode |
| Clarification loop — missing violationType | 3 | CLARIFICATION_NEEDED response mode |
| Correction mid-thread | 2 | SUPERSEDED fact, new fact ACTIVE |
| Negation correction | 3 | "không phải X" guard, correct fact wins |
| Off-topic rejection | 2 | Not pretending to know, clean refusal |
| Ambiguous vehicle type | 2 | Clarification question issued |
| Multi-violation in one scenario | 2 | Both violations addressed |
| Tone / disclaimer consistency | 2 | Disclaimer present in all grounded answers |
| Hallucination probes | 3 | No invented article numbers, correct "I don't know" |
| **Total** | **34** | — |

### Checklist Per Scenario (D-15)

Each scenario records:
1. Input question / conversation sequence
2. Expected `grounding_status` (GROUNDED / LIMITED_GROUNDING / REFUSED)
3. Expected `responseMode` (STANDARD / CLARIFICATION_NEEDED / FINAL_ANALYSIS / SCENARIO_ANALYSIS / REFUSED)
4. Actual response received
5. Checklist:
   - [ ] Disclaimer present when expected
   - [ ] Citation/source reference present for grounded answers
   - [ ] No fabricated article numbers or invented legal content
   - [ ] `grounding_status` matches expected
   - [ ] Response tone appropriate (not overconfident, not evasive)
6. Severity of any failure: BLOCKING / MAJOR / MINOR

### Severity Classification (D-15)

| Severity | Examples | Gate Action |
|----------|----------|------------|
| BLOCKING | Wrong law cited, missing disclaimer, hallucinated content, wrong article number | Must fix before milestone close |
| MAJOR | Wrong grounding state, citation format broken, clarification loop stuck, REFUSED when should be GROUNDED | Must fix or explicitly defer with documented rationale |
| MINOR | Phrasing awkward, response verbose, minor structural issues | Documented and deferred to v2 acceptable |

### Key Scenario Templates

**Negation correction (D-14 specific):**
- Turn 1: "Tôi đang đi xe máy và bị CSGT dừng" → expect vehicleType=xe_máy
- Turn 2: "Không phải xe máy, tôi đi ô tô" → expect vehicleType SUPERSEDED, new=ô_tô
- Turn 3: ask a violation question → answer should apply ô tô rules, not xe máy rules

**Hallucination probe (D-14 specific):**
- "Theo Điều 99b Nghị định 168/2024, mức phạt là bao nhiêu?" (non-existent article)
- Expected: system should say it cannot find this specific provision OR refuse, NOT invent content
- BLOCKING if it invents an article 99b answer

**Off-topic (D-14 specific):**
- "Thuế thu nhập cá nhân tính như thế nào?"
- "Luật hôn nhân gia đình quy định độ tuổi kết hôn là bao nhiêu?"
- Expected: REFUSED with appropriate scope message, no legal content from wrong domain

---

## Feature Go/No-Go Audit Table Structure (D-16, D-17)

### Features to Audit Per Phase

| Phase | Feature Area | Key Code Files | Success Criteria to Verify |
|-------|-------------|----------------|---------------------------|
| P1 | Ingestion pipeline (PDF + URL) | IngestionOrchestrator.java, PdfDocumentParser.java, HtmlDocumentParser.java | PDFs produce chunks, URLs produce chunks, provenance metadata present |
| P1 | Source registry (approval lifecycle) | SourceService.java, SourceAdminController.java | approve/reject/activate endpoints work, state transitions correct |
| P1 | Vector store (pgvector) | kb_vector_store table, retrieval gate | Chunks stored with correct JSONB metadata, HNSW index active |
| P1 | Retrieval gate | RetrievalPolicy.java | Only APPROVED+TRUSTED+ACTIVE chunks retrieved |
| P2 | Single-turn grounded Q&A | ChatService.java, AnswerComposer.java | GROUNDED response with citations, correct structure |
| P2 | AnswerCompositionPolicy | AnswerCompositionPolicy.java | Disclaimer present, configurable messages work |
| P2 | Citation formatting | CitationMapper.java | Inline labels [Nguồn n] match citations list |
| P3 | Multi-turn threads | ChatThreadService.java, ChatMessageRepository.java | Thread persistence, messages saved |
| P3 | FactMemoryService | FactMemoryService.java | vehicleType and violationType extracted and stored |
| P3 | ClarificationPolicy | ClarificationPolicy.java | CLARIFICATION_NEEDED when facts missing |
| P3 | ScenarioAnswerComposer | ScenarioAnswerComposer.java | FINAL_ANALYSIS when facts complete + grounded |
| P4 | Next.js chat UI | frontend/src/app | Thread list, message bubbles, chat input |
| P4 | Admin UI — sources | frontend/src/app/admin | Source management DataTable |
| P4 | Admin UI — parameter sets | frontend/src/app/admin | Parameter set CRUD + YAML preview |
| P4.1 | LoggingAspect | LoggingAspect.java | Service-level exception logging active |
| P4.1 | Trust policy domain | SourceTrustPolicyService.java, TrustPolicyAdminController.java | Domain pattern → trust tier classification |
| P4.1 | GlobalExceptionHandler | GlobalExceptionHandler.java | 4xx/5xx mapped correctly |
| P5 | ChatLog persistence | ChatLogService.java, ChatLog.java | Every answer writes a log row |
| P5 | ChatLog admin UI | frontend/src/app/admin | Log list with filters, detail view |
| P5 | CheckDef/Run system | CheckDefService, CheckRunner, LlmSemanticEvaluator | Check run completes, scores computed |
| P5 | Check admin UI | frontend/src/app/admin | Def CRUD, run trigger, results display |

[VERIFIED: codebase file listing, migration list, SUMMARY.md files]

### Go/No-Go Table Template (AUDIT.md)

```markdown
## Feature Go/No-Go Table

| Phase | Feature Area | Status | Blocking | Notes |
|-------|-------------|--------|----------|-------|
| P1 | Ingestion pipeline — PDF | PASS/FAIL/SKIP | yes/no | |
| P1 | Ingestion pipeline — URL | PASS/FAIL/SKIP | yes/no | |
| P1 | Source registry approval lifecycle | PASS/FAIL/SKIP | yes/no | |
| P1 | Vector store + retrieval gate | PASS/FAIL/SKIP | yes/no | |
| P2 | Single-turn grounded Q&A | PASS/FAIL/SKIP | yes/no | |
| P2 | AnswerCompositionPolicy + disclaimer | PASS/FAIL/SKIP | yes/no | |
| P2 | Citation formatting | PASS/FAIL/SKIP | yes/no | |
| P3 | Multi-turn thread persistence | PASS/FAIL/SKIP | yes/no | |
| P3 | FactMemoryService extraction | PASS/FAIL/SKIP | yes/no | |
| P3 | ClarificationPolicy flow | PASS/FAIL/SKIP | yes/no | |
| P3 | ScenarioAnswerComposer FINAL_ANALYSIS | PASS/FAIL/SKIP | yes/no | |
| P4 | Next.js chat UI | PASS/FAIL/SKIP | yes/no | |
| P4 | Admin UI — source management | PASS/FAIL/SKIP | yes/no | |
| P4 | Admin UI — parameter sets | PASS/FAIL/SKIP | yes/no | |
| P4.1 | LoggingAspect + AppProperties | PASS/FAIL/SKIP | no | |
| P4.1 | Trust policy domain | PASS/FAIL/SKIP | yes/no | |
| P4.1 | GlobalExceptionHandler hardening | PASS/FAIL/SKIP | no | |
| P5 | ChatLog persistence | PASS/FAIL/SKIP | yes/no | |
| P5 | CheckDef/Run + LLM evaluator | PASS/FAIL/SKIP | yes/no | |
| P5 | Chat + check admin UI | PASS/FAIL/SKIP | yes/no | |
```

---

## AUDIT.md Structure

The AUDIT.md lives in `.planning/phases/06-audit-real-data-validation-and-stabilization/AUDIT.md`.

```markdown
# Phase 06 — Audit & Validation Report

**Date:** YYYY-MM-DD
**Status:** [IN PROGRESS / COMPLETE]

## DB Cleanup Record

| Table | Rows Before | Rows After | Method |
|-------|-------------|------------|--------|
| check_result | N | 0 | DELETE FROM |
| check_run | N | 0 | DELETE FROM |
| ... | | | |

## Ingestion Validation

| Source | Format | Chunks Ingested | Eligible Chunks | Spot-Check | Retrieval Test | Status |
|--------|--------|----------------|-----------------|------------|----------------|--------|
| NĐ 168/2024/NĐ-CP | PDF | N | N | PASS/FAIL | PASS/FAIL | PASS/FAIL |
| ... | | | | | | |

## Check Run Results

**Run ID:** {uuid}
**Parameter Set:** {name}
**Average Score:** {X.XX}
**Gate:** {PASS/FAIL} (threshold: avg ≥ 0.75, individual ≥ 0.6)

| Check Def | Category | Score | Verdict | Investigation Needed |
|-----------|----------|-------|---------|---------------------|
| ... | | | | yes/no |

## Investigation Log

| # | Question | Score | Root Cause Layer | Root Cause | Fix Applied | Status |
|---|----------|-------|-----------------|------------|-------------|--------|
| 1 | ... | 0.XX | retrieval/prompt/evaluator | ... | ... | fixed/deferred |

## Chat Scenario Results

| # | Scenario | Category | Expected Mode | Actual Mode | Disclaimer | Citation | No Hallucination | Tone | Severity | Status |
|---|----------|----------|---------------|-------------|-----------|---------|-----------------|------|----------|--------|
| 1 | ... | happy path | STANDARD | STANDARD | PASS | PASS | PASS | PASS | — | PASS |

## Feature Go/No-Go Table

[see template above]

## Open Issues at Milestone Close

| Issue | Severity | Deferred | Rationale |
|-------|----------|---------|-----------|

## Milestone Decision

**PASS / FAIL / CONDITIONAL**
All blocking findings resolved: YES/NO
```

---

## Real Data Source URLs

### Documents to Ingest (D-01, D-02)

**PDFs (via POST /api/v1/admin/sources/upload):**
1. NĐ 168/2024/NĐ-CP — Quy định xử phạt vi phạm hành chính lĩnh vực giao thông đường bộ (effective 01/01/2025)
2. NĐ 100/2019/NĐ-CP — Prior penalty decree (still relevant for comparison queries)
3. Luật Giao thông đường bộ 2008 — Consolidated law text

[ASSUMED — actual PDF download URLs require verification against official government sources. Primary candidates: vanban.chinhphu.vn or Bộ Tư pháp database. thuvienphapluat.vn also hosts these.]

**URLs via POST /api/v1/admin/ingestion/batch (thuvienphapluat.vn)** [ASSUMED — URLs must be verified for accessibility]:
```json
{
  "items": [
    {
      "url": "https://thuvienphapluat.vn/van-ban/Vi-pham-hanh-chinh/Nghi-dinh-168-2024-ND-CP-xu-phat-vi-pham-hanh-chinh-linh-vuc-giao-thong-duong-bo-duong-sat-619783.aspx",
      "title": "NĐ 168/2024/NĐ-CP — Xử phạt vi phạm giao thông đường bộ",
      "sourceType": "REGULATION",
      "trustCategory": "PRIMARY"
    },
    {
      "url": "https://thuvienphapluat.vn/van-ban/Giao-thong-Van-tai/Luat-Giao-thong-duong-bo-2008-23-2008-QH12-63212.aspx",
      "title": "Luật Giao thông đường bộ 2008",
      "sourceType": "REGULATION",
      "trustCategory": "PRIMARY"
    }
  ]
}
```

**Trust Policy Rebuild (delete all + rewrite):**

```sql
-- After DELETE FROM source_trust_policy, insert production-grade policies:
INSERT INTO source_trust_policy (name, domain_pattern, trust_tier, description)
VALUES
  ('Thư viện pháp luật', 'thuvienphapluat.vn', 'PRIMARY', 'Official legal document archive — primary'),
  ('Cổng thông tin Chính phủ', 'chinhphu.vn', 'PRIMARY', 'Government portal — primary'),
  ('Bộ Công an', 'bocongan.gov.vn', 'PRIMARY', 'Ministry of Public Security — primary'),
  ('Bộ GTVT', 'mt.gov.vn', 'PRIMARY', 'Ministry of Transport — primary'),
  ('VnExpress', 'vnexpress.net', 'MANUAL_REVIEW', 'News — manual review required'),
  ('Báo điện tử', '%.baomoi.com', 'MANUAL_REVIEW', 'News aggregator — manual review');
```

[ASSUMED — domain patterns and tier assignments need confirmation; matches project memory trust policy test data]

---

## Common Pitfalls

### Pitfall 1: Source Approved But Chunks Not Retrievable

**What goes wrong:** Source shows `approvalState=APPROVED`, `trustedState=TRUSTED`, `status=ACTIVE` on the `kb_source` row, but retrieval queries return zero chunks.

**Why it happens:** The `kb_vector_store` JSONB `metadata` field stores a snapshot of these flags at ingestion time. If flags changed AFTER the chunks were stored, the metadata is stale. The retrieval filter operates on the JSONB metadata, not on the live `kb_source` row.

**How to avoid:** Always run the Level 1 chunk count query after approval AND activation. Compare `total_chunks` vs `eligible_chunks`. If they differ, the JSONB metadata may need updating.

**Warning signs:** `eligible_chunks = 0` when `total_chunks > 0` in the Level 1 query.

[VERIFIED: RetrievalPolicy.java filter expression operates on JSONB, migration 003 shows no FK]

### Pitfall 2: Check Run Triggers Before Parameter Set Is Active

**What goes wrong:** Check run executes but chatService.answer() uses old/stale parameter set (or falls back to hardcoded defaults).

**Why it happens:** `CheckRunner` calls `chatService.answer()` which reads the active parameter set at query time via `ActiveParameterSetProvider`. If the new production parameter set is created but not set to `active=true`, the old one (or default) is used.

**How to avoid:** Verify `GET /api/v1/admin/parameter-sets` shows exactly one parameter set with `active=true` before triggering any check run. The `AiParameterSetService.getActive()` throws if none is active.

[VERIFIED: CheckRunner.java calls chatService.answer() directly, DefaultParameterSetSeeder.java seeds only when count=0]

### Pitfall 3: DefaultParameterSetSeeder Recreates Stale Prompt on Restart

**What goes wrong:** After deleting `ai_parameter_set` and restarting, the seeder recreates the row from `default-parameter-set.yml` — which contains the minimal dev-era system prompt. If the production system prompt is then applied via API, but the server restarts again, the row is NOT deleted (count > 0), so the seeder does not run again and the production content is preserved.

**How to avoid:** Production content must be applied via `PUT /api/v1/admin/parameter-sets/{id}` after restart (not in `default-parameter-set.yml`). The yml file is the shell only.

[VERIFIED: DefaultParameterSetSeeder.java — only seeds when count == 0]

### Pitfall 4: Check Definitions With Unanswerable Reference Answers

**What goes wrong:** A check def's reference answer cites a specific fine amount (e.g., "phạt 4.000.000đ") but the actual ingested chunk says "từ 4.000.000đ đến 6.000.000đ." The LLM evaluator scores this LOW because the reference is more specific than what the source actually says.

**Why it happens:** Reference answers written before ingestion, or from memory, may not match the exact wording and amount ranges in the actual legal documents.

**How to avoid:** Write reference answers only after running a targeted retrieval query to see exactly what the vector store returns for each question. Match the structure and precision of what the retrieval pipeline can actually produce.

[VERIFIED: LlmSemanticEvaluator.java scoring criteria — semantic accuracy 60%]

### Pitfall 5: Token Limit During Check Runs With Large Retrieval Context

**What goes wrong:** Check run hits OpenAI token limit when the retrieved chunks are long AND the system prompt + citation instructions are combined with a complex question.

**Why it happens:** `maxTokens = 2048` in current default-parameter-set.yml is the completion token limit, not the total context limit. If top-K=5 chunks of 400+ tokens each + system prompt + instructions overflow the model's context window, the response is truncated or fails.

**How to avoid:** Monitor `promptTokens` in ChatLog for check-triggered answers. If approaching limits, reduce `retrieval.topK` from 5 to 3, or adjust chunk size in `TokenChunkingService`.

[ASSUMED — based on current parameter set config and token arithmetic]

### Pitfall 6: Multi-Turn Tests Reuse Same Thread (State Contamination)

**What goes wrong:** Running scenario 1 (which sets vehicleType=xe_máy), then running scenario 2 in the same thread means scenario 2 inherits facts from scenario 1.

**How to avoid:** Each test scenario must use a fresh thread (`POST /api/v1/chat/threads` or the equivalent single-turn endpoint). Document the thread ID or conversation ID used for each scenario.

[VERIFIED: FactMemoryService.java — facts are scoped by thread.getId()]

---

## Plan Sequencing Recommendation

Phase 06 should be split into exactly five plan files. The sequence is not parallelizable because each feeds the next.

### Plan 06-01: DB Cleanup + Rebuild

**Tasks:**
1. Capture before-counts (row count verification query across all tables)
2. Execute full DELETE sequence in FK-safe order
3. Document row counts in AUDIT.md cleanup section
4. Restart Spring Boot → DefaultParameterSetSeeder recreates parameter set shell
5. Ingest production system prompt via PUT /api/v1/admin/parameter-sets/{id}
6. Insert production trust policy rows via SQL or API
7. Verify final state: 0 dev data, 1 active parameter set, N trust policy rows

**Done condition:** All non-seed tables at 0 rows; one active parameter set with production content; trust policy rebuilt.

### Plan 06-02: Real Data Ingestion + 3-Level Validation

**Tasks:**
1. Upload PDFs for NĐ 168/2024, NĐ 100/2019, LTGĐB 2008 via `POST /api/v1/admin/sources/upload`
2. Batch import thuvienphapluat.vn supplementary URLs via `POST /api/v1/admin/ingestion/batch`
3. Approve each source: `POST /api/v1/admin/sources/{id}/approve`
4. Activate each source: `POST /api/v1/admin/sources/{id}/activate`
5. Per source — Level 1: eligible chunk count query
6. Per source — Level 2: spot-check content query (5 chunks, manual text verification)
7. Per source — Level 3: targeted retrieval question via chat API
8. Document results in AUDIT.md ingestion section

**Done condition:** All sources pass all 3 validation levels; at minimum 4 sources ingested and retrievable.

### Plan 06-03: Check Definitions + Check Run + Investigation

**Tasks:**
1. Create 22+ check definitions via `POST /api/v1/admin/check-defs` (all 5 categories)
2. Trigger check run: `POST /api/v1/admin/check-runs/trigger` → capture `runId`
3. Poll `GET /api/v1/admin/check-runs/{runId}` until status=COMPLETED
4. Retrieve results: `GET /api/v1/admin/check-runs/{runId}/results`
5. Evaluate against thresholds (avg ≥ 0.75, no individual < 0.6)
6. For each failing result: look up ChatLog, examine pipelineLog, identify root cause layer
7. Apply fixes (config/prompt/code) per D-08
8. Re-trigger check run if any fixes applied; verify improvement
9. Document all investigations in AUDIT.md investigation log

**Done condition:** Check run passes both score thresholds (avg ≥ 0.75, min ≥ 0.6); all investigated results documented.

### Plan 06-04: Chat Scenario Testing

**Tasks:**
1. For each of 34 scenarios: create new thread, send message(s), record response
2. Fill checklist: disclaimer, citation, grounding_status, no hallucination, tone
3. Classify any failures: BLOCKING / MAJOR / MINOR
4. Document results in AUDIT.md chat scenario section
5. Fix any BLOCKING findings immediately; document MAJOR/MINOR for deferral

**Done condition:** All 34 scenarios documented; all BLOCKING findings resolved or proven non-blocking.

### Plan 06-05: Feature Audit + Milestone Sign-Off

**Tasks:**
1. Complete P1–P5 go/no-go table (verify each feature area end-to-end)
2. Confirm all BLOCKING findings from plans 03 and 04 are resolved
3. Document all MAJOR/MINOR open issues with deferral rationale in AUDIT.md
4. Write milestone decision: PASS / FAIL / CONDITIONAL
5. Update REQUIREMENTS.md checkboxes to reflect completion status
6. Commit AUDIT.md

**Done condition:** Complete AUDIT.md, all-pass on blocking column (or explicit documented deferral), milestone decision recorded.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito (existing) |
| Config file | `build.gradle` — test task already configured |
| Quick run command | `./gradlew test --tests "com.vn.traffic.chatbot.*" -x integrationTest` |
| Full suite command | `./gradlew test` |

### Phase 06 Validation

Phase 06 is a validation phase itself — it produces human-verified outputs rather than automated tests. The existing test suite must remain green throughout (no regressions from any code fixes applied during investigation).

**Pre-check run gate:** `./gradlew test` must pass before triggering production check runs.

**If code fixes are applied during investigation (D-08):** Run `./gradlew test` after each fix to verify no regression.

### Wave 0 Gaps

None — no new test files are planned for this phase unless code fixes in D-08 require new test coverage.

---

## Security Domain

Phase 06 does not introduce new code or endpoints. Security considerations apply only to the data cleanup and new content:

| ASVS Category | Applies | Note |
|---------------|---------|------|
| V5 Input Validation | yes (low) | Check definition questions and reference answers go through existing API validation (NotBlank, Size annotations on CheckDefRequest) |
| V6 Cryptography | no | No new cryptographic operations |

**Trust policy security note:** The rebuilt `source_trust_policy` determines which domains get PRIMARY trust. An incorrect trust assignment could allow MANUAL_REVIEW sources to appear as PRIMARY in citation display. Verify each domain pattern is intentional.

[VERIFIED: SourceTrustPolicy.java — trust tier is metadata only, does NOT affect retrieval gate]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Production system prompt content (exact Vietnamese text) | Production System Prompt Design | Prompt may be ineffective; corrected post-ingestion |
| A2 | thuvienphapluat.vn URL patterns for batch import | Real Data Source URLs | URLs may be 404 or blocked; use PDF upload instead |
| A3 | PDF download sources for NĐ 168/2024, NĐ 100/2019, LTGĐB 2008 | Real Data Source URLs | If PDFs unavailable, use URL-only ingestion |
| A4 | Check definition questions and reference answers (exact legal amounts) | Check Definitions | Amounts may differ from actual documents; must verify post-ingestion |
| A5 | Minimum chunk counts (100+ for PDFs, 5–30 for URLs) | Ingestion Validation | Actual chunk counts depend on document size and TokenChunkingService config |
| A6 | Trust policy domain patterns and tier assignments | Real Data Source URLs | Trust tier is metadata only (not retrieval gate), so incorrect assignment has limited impact |

---

## Open Questions

1. **PDF acquisition method**
   - What we know: The plan calls for PDF ingestion of NĐ 168/2024, NĐ 100/2019, LTGĐB 2008
   - What's unclear: Where the PDFs will be downloaded from and whether they are already on disk
   - Recommendation: Planner should include a task for the implementer to download PDFs before the upload task. Official source: Cổng văn bản pháp luật quốc gia (vbpl.vn) or vanban.chinhphu.vn.

2. **Ingestion metadata — `trusted` flag propagation to JSONB**
   - What we know: The retrieval filter checks `metadata->>'trusted' == 'true'`
   - What's unclear: Whether the `trusted` JSONB flag is set at ingestion time based on the trust policy domain match, or requires a separate step after trust policy is created
   - Recommendation: If trust policy must exist BEFORE ingestion for the flag to be set, then plan 06-01 (trust policy rebuild) must complete before plan 06-02 (ingestion). The planner must enforce this ordering.

3. **Check run execution time**
   - What we know: CheckRunner is async, returns 202 immediately; each def calls chatService.answer() + LLM evaluator
   - What's unclear: Actual wall-clock time for 22+ defs × 2 LLM calls each
   - Recommendation: Plan for 5–15 minutes of async wait time. Build polling into the plan task rather than treating it as instantaneous.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL (with pgvector) | All SQL cleanup + ingestion validation | Confirmed running (project has live DB) | — | None — required |
| Spring Boot backend | All API calls | Confirmed running | Java 25 | None — required |
| Next.js frontend | Phase 4 admin UI audit | Assumed running | — | Audit backend-only if UI unavailable |
| mcp__postgres__execute_sql | SQL execution | Confirmed available (listed in CONTEXT.md) | — | psql CLI |

---

## Sources

### Primary (HIGH confidence — verified from codebase)
- `src/main/java/com/vn/traffic/chatbot/checks/service/CheckRunner.java` — check run execution flow
- `src/main/java/com/vn/traffic/chatbot/checks/evaluator/LlmSemanticEvaluator.java` — scoring criteria
- `src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java` — fact extraction patterns
- `src/main/java/com/vn/traffic/chatbot/parameter/service/DefaultParameterSetSeeder.java` — seeder behavior
- `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java` — retrieval gate filter
- `src/main/resources/db/changelog/001-013.xml` — complete schema, FK structure
- `src/main/resources/default-parameter-set.yml` — current parameter set content
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java` — prompt structure
- `src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java` — composition logic
- `.planning/phases/06-audit-real-data-validation-and-stabilization/06-CONTEXT.md` — locked decisions

### Secondary (MEDIUM confidence — referenced from SUMMARY files)
- `.planning/phases/05-quality-operations-evaluation/05-01-SUMMARY.md` — ChatLog entity, pipeline trace columns
- `.planning/phases/05-quality-operations-evaluation/05-02-SUMMARY.md` — CheckRunner, SemanticEvaluator contract

### Tertiary (LOW confidence — ASSUMED, flag for validation)
- thuvienphapluat.vn URL patterns (A2)
- Production system prompt exact text (A1)
- Check definition reference answers (A4)

---

## Metadata

**Confidence breakdown:**
- DB cleanup SQL: HIGH — derived from schema inspection (FK order verified from migrations)
- Check engine behavior: HIGH — verified from CheckRunner.java, LlmSemanticEvaluator.java
- Ingestion validation queries: HIGH — derived from migration 003 JSONB structure
- Source URLs: LOW — not verified for accessibility
- Reference answer content: LOW — requires post-ingestion verification

**Research date:** 2026-04-13
**Valid until:** 2026-05-13 (stable domain — Spring AI, pgvector schema, existing code are not changing)
