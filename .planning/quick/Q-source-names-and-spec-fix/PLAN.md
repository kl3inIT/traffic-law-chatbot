---
phase: quick
plan: Q-source-names-and-spec-fix
type: execute
wave: 1
depends_on: []
files_modified:
  - frontend/e2e/chat.spec.ts
autonomous: true
requirements: []

must_haves:
  truths:
    - "NGUỒN THAM KHẢO section in chat responses shows accented Vietnamese source names"
    - "T-UI-04 passes with a specific enough question to get a grounded response"
    - "T-UI-08 passes without false-positive hallucination detection"
  artifacts:
    - path: "frontend/e2e/chat.spec.ts"
      provides: "Fixed T-UI-04 and T-UI-08 assertions"
  key_links:
    - from: "kb_vector_store.metadata->>'sourceTitle'"
      to: "CitationMapper.resolveSourceTitle()"
      via: "JSONB field read at citation build time"
      pattern: "metadata.get(\"sourceTitle\")"
---

<objective>
Fix two issues found during Phase 06 Playwright testing: (1) vector store chunk metadata
contains unaccented Vietnamese source titles that appear in citations, and (2) two Playwright
spec assertions that no longer match current system behaviour.

Purpose: Unblock the Phase 06 Playwright test suite so all 10 tests pass against the live stack.
Output: Updated vector store metadata (via SQL) and corrected e2e spec file.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md

<!-- Key fact: CitationMapper reads metadata.get("sourceTitle") to build the citation label.
     The vector store table is kb_vector_store with a JSONB metadata column.
     Updating metadata->>'sourceTitle' fixes what appears in NGUỒN THAM KHẢO. -->
</context>

<tasks>

<task type="auto">
  <name>Task 1: Patch sourceTitle in kb_vector_store JSONB metadata</name>
  <files>src/main/resources/db/changelog/014-fix-source-titles.xml</files>
  <action>
Create a new Liquibase changeset file at
`src/main/resources/db/changelog/014-fix-source-titles.xml` that runs three UPDATE
statements to patch the `sourceTitle` field inside the JSONB `metadata` column of
`kb_vector_store`. Use the `jsonb_set` function. One UPDATE per source — match rows by
`metadata->>'sourceId'` (the UUID stored in that JSONB field).

The three corrections:
  - sourceId = '05828361-4b54-40bb-9530-e2aaf42e6add'  → sourceTitle = 'Nghị định 168/2024/NĐ-CP'
  - sourceId = '98727f7a-c539-45d1-9042-236e21be3ca8'  → sourceTitle = 'Luật Giao thông đường bộ 2008'
  - sourceId = 'f0633a28-d8e1-46a6-b74d-681b6e75f248'  → sourceTitle = 'Nghị định 100/2019/NĐ-CP'

SQL pattern for each row (repeat three times with the correct values):
  UPDATE kb_vector_store
  SET metadata = jsonb_set(metadata, '{sourceTitle}', '"Nghị định 168/2024/NĐ-CP"', false)
  WHERE metadata->>'sourceId' = '05828361-4b54-40bb-9530-e2aaf42e6add';

After creating the XML file, register it in
`src/main/resources/db/changelog/db.changelog-master.xml` by adding:
  &lt;include file="db/changelog/014-fix-source-titles.xml"/&gt;

Then start the Spring Boot application (or run `mvn liquibase:update`) so the changeset
is applied. Verify with:
  SELECT metadata->>'sourceId', metadata->>'sourceTitle'
  FROM kb_vector_store
  WHERE metadata->>'sourceId' IN (
    '05828361-4b54-40bb-9530-e2aaf42e6add',
    '98727f7a-c539-45d1-9042-236e21be3ca8',
    'f0633a28-d8e1-46a6-b74d-681b6e75f248'
  )
  GROUP BY 1, 2;

Expected: three rows with the accented Vietnamese titles above.
  </action>
  <verify>
    <automated>
      psql $DATABASE_URL -c "SELECT metadata->>'sourceTitle' FROM kb_vector_store WHERE metadata->>'sourceId' = '05828361-4b54-40bb-9530-e2aaf42e6add' LIMIT 1;"
      # Expected output contains: Nghị định 168/2024/NĐ-CP
    </automated>
  </verify>
  <done>All three source rows in kb_vector_store have accented Vietnamese titles in metadata->>'sourceTitle'.</done>
</task>

<task type="auto">
  <name>Task 2: Fix T-UI-04 and T-UI-08 in chat.spec.ts</name>
  <files>frontend/e2e/chat.spec.ts</files>
  <action>
Make two targeted edits to `frontend/e2e/chat.spec.ts`:

**Edit 1 — T-UI-04 (line 46):**
Change question from:
  'Cần mang theo giấy tờ gì khi tham gia giao thông?'
to:
  'Xe máy cần mang theo giấy tờ gì khi tham gia giao thông?'

This makes the question vehicle-specific so the LLM returns a grounded response with
`Căn cứ pháp lý` and `tham khảo` instead of triggering CLARIFICATION_NEEDED. Do not
change the assertions on lines 48–50.

**Edit 2 — T-UI-08 (line 81):**
Change the assertion from:
  await expect(bubble).not.toContainText('Điều 99b');
to:
  await expect(bubble).toContainText('không thể xác nhận', { timeout: 15000 });

Rationale: the system correctly echoes the article name in its refusal while refusing to
fabricate content; `not.toContainText('Điều 99b')` is therefore a false positive.
Using `toContainText('không thể xác nhận')` tests the actual safety property — that
the system says it cannot verify the article — without being brittle to the echo.

If after review the actual refusal phrase differs (e.g. 'không có trong tài liệu'),
adjust to match the real response text returned by the backend.
  </action>
  <verify>
    <automated>cd /d/ai/traffic-law-chatbot/frontend && npx playwright test e2e/chat.spec.ts --grep "T-UI-04|T-UI-08" --reporter=line</automated>
  </verify>
  <done>T-UI-04 and T-UI-08 both pass in the Playwright run against the live stack.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries
| Boundary | Description |
|----------|-------------|
| SQL UPDATE → kb_vector_store | Direct metadata patch; no user input involved |

## STRIDE Threat Register
| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-Q-01 | Tampering | Liquibase changeset patching JSONB metadata | accept | Changeset is idempotent by sourceId; only touches three known rows; no external input |
</threat_model>

<verification>
1. Run `SELECT metadata->>'sourceTitle' FROM kb_vector_store WHERE metadata->>'sourceId' IN (...)` — confirm three accented titles.
2. Send a chat question about "Nghị định 168" via the UI — confirm NGUỒN THAM KHẢO shows "Nghị định 168/2024/NĐ-CP" (accented).
3. Run `npx playwright test e2e/chat.spec.ts` — all 10 tests pass.
</verification>

<success_criteria>
- kb_vector_store metadata has correct accented Vietnamese sourceTitle for all three sources.
- chat.spec.ts T-UI-04 and T-UI-08 pass without modifying any other test.
- Full Playwright suite (T-UI-01 through T-UI-10) stays green.
</success_criteria>

<output>
After completion, create `.planning/quick/Q-source-names-and-spec-fix/SUMMARY.md` with:
- What was changed and why
- SQL UPDATE row counts (how many chunks were patched per source)
- Playwright test results (pass/fail per test ID)
</output>
