# Phase 06: Audit, Real-Data Validation, and Stabilization - Context

**Gathered:** 2026-04-13
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase is the hard release gate before milestone completion. It verifies that everything delivered across Phases 1–5 is actually production-ready using real data and real behavior validation — not assumptions or synthetic tests. The milestone cannot close until this phase passes.

Deliverables:
1. Real Vietnamese legal sources ingested, approved, and validated end-to-end
2. 20+ check definitions authored and a check run executed against real data
3. Low-scoring results investigated to root cause and fixed (config, prompt, or code)
4. Database wiped of all dev/test/stale data and rebuilt with intentional production state
5. 30+ chat scenarios tested with a structured checklist and severity-classified results
6. Full feature go/no-go table across all phases — blocking findings resolved before milestone close

</domain>

<decisions>
## Implementation Decisions

### Real Data Ingestion

- **D-01: Source selection** — Ingest all three core legal pillars plus supporting circulars: Nghị định 168/2024/NĐ-CP (current penalty decree), Luật Giao thông đường bộ 2008, Nghị định 100/2019/NĐ-CP (prior penalty decree, still relevant for comparison queries), plus circular documents from Bộ Công an / Bộ GTVT. This is the broadest coverage set to match real user expectations.

- **D-02: Source format** — Mix strategy: PDFs for the key decrees (highest fidelity, preserves article/clause formatting), URLs from thuvienphapluat.vn for supplementary content (batch-importable, auto-classified as PRIMARY by trust policy). Both parsers must be validated during the ingestion audit.

- **D-03: Ingestion validation depth** — Three-level validation per source after ingestion: (1) eligible chunk count check (approved + trusted + active), (2) spot-check content of sample chunks by querying the DB and manually verifying text against the source document, (3) targeted retrieval query per source to confirm that chunks from that source actually surface in vector search results. A source is not considered ingested until it passes all three levels.

### Check Run Design

- **D-04: Check definition count** — Author 20+ check definitions. This is the comprehensive baseline target, not a minimum. It creates a real regression baseline for future phases.

- **D-05: Check categories** — Full coverage across all implemented response modes:
  - Fine amounts and penalty tiers (core grounded Q&A)
  - Required documents and procedures
  - Refusal boundaries (out-of-scope or zero-retrieval questions)
  - Multi-turn scenario (CLARIFICATION_NEEDED → FINAL_ANALYSIS flow)
  - LIMITED_GROUNDING edge cases (weak retrieval, partial information)
  Each category must have at least 3 check definitions.

- **D-06: Score threshold** — Average ≥ 0.75 with no individual result below 0.6. This is a moderate gate appropriate for a legal-context release. Any run that fails either condition triggers mandatory investigation.

### Low Score Investigation

- **D-07: Investigation trigger** — Investigate all results below the 0.6 individual floor, all results that contribute to a failed average, AND any result that seems semantically wrong regardless of score. Quantitative floor + qualitative judgment.

- **D-08: Investigation depth** — Full remediation: identify the root cause layer (ingestion → normalization → indexing → retrieval → prompt → context assembly → model → evaluator criteria), then fix it. Fix config issues via AiParameterSet updates, fix CheckDef reference answers directly, fix prompt issues in ChatPromptFactory or system prompt, fix code where necessary. Nothing is deferred just because it is hard — if it can be fixed in this phase, it must be.

- **D-09: Audit artifact** — Single `AUDIT.md` in the phase directory covering all investigation findings. Required structure: one row per investigated result with columns for question, score, root cause layer, what was changed, and status (fixed / deferred-with-rationale). Deferred items require explicit rationale. The milestone cannot close without a complete AUDIT.md.

### Database Cleanup

- **D-10: Cleanup scope** — Full wipe of all non-seed data. This is not a light cleanup pass. Everything created during development is deleted and rebuilt from scratch. The database after cleanup should contain only intentionally authored production-state data.

- **D-11: Cleanup process** — Delete without extensive pre-review process (user confirmed "delete anyway"). Execute DELETE SQL with documented criteria. Record what was deleted in AUDIT.md with table names and row counts before/after. No backup required — the user accepted the risk.

- **D-12: What survives cleanup** — Custom policy (user-specified):
  - **Keep:** Real source data and ingestion metadata created during this phase, audit artifacts
  - **Rebuild from scratch:** AI parameter set (rewrite content and system prompt entirely), check definitions (delete all, rebuild with real production-grade questions), trust policy (delete and rewrite from scratch)
  - **Delete entirely:** All dev/test sources and their vector chunks, all test chat logs, all test/placeholder check defs and their runs/results, all dev chat threads and messages and thread facts
  - Note: The default parameter set seeded by `DefaultParameterSetSeeder` will be deleted and the seeder will recreate it on next startup — then the content should be replaced with the new production system prompt.

### Chat Behavior Testing

- **D-13: Scenario volume** — 30+ manual test scenarios. This is the exhaustive tier: full scenario matrix including recovery flows, tone consistency checks, and hallucination probes.

- **D-14: Scenario categories** — Full coverage required:
  - Happy path: grounded single-turn answer, multi-turn FINAL_ANALYSIS completion
  - Refusal: out-of-scope question, zero-retrieval result (REFUSED grounding)
  - Clarification loop: missing vehicleType, missing violationType, correction mid-thread
  - Negation correction: "không phải xe máy, tôi đi ô tô" — verify ACTIVE fact wins
  - Off-topic rejection: non-traffic questions that should not receive legal guidance
  - Ambiguous vehicle type: multiple valid matches, system must clarify
  - Multi-violation: two or more violations in one scenario
  - Tone and disclaimer consistency: disclaimer present in all grounded answers, correct tone across scenarios
  - Hallucination probes: questions where the correct answer is "I don't have enough information"

- **D-15: Pass/fail definition** — Structured checklist per scenario with severity classification. Checklist criteria per response: (1) disclaimer present when expected, (2) citation/source reference present for grounded answers, (3) no fabricated article numbers or invented legal content, (4) grounding_status correct (GROUNDED / LIMITED_GROUNDING / REFUSED as expected for the query), (5) response tone is appropriate (not overconfident, not evasive). Each failure is classified: **blocking** (wrong law cited, missing disclaimer, hallucinated content), **major** (wrong grounding state, citation format broken, clarification loop stuck), **minor** (phrasing awkward, response verbose). Blocking and major findings gate milestone completion. Minor findings are documented and deferred.

### Release Gate and Milestone Sign-Off

- **D-16: Audit scope** — All phases — full feature inventory audit. Every shipped feature from every phase gets an explicit pass/fail judgment:
  - P1: Ingestion pipeline, source registry, vector store, retrieval gate
  - P2: Single-turn grounded Q&A, AnswerCompositionPolicy, citation formatting
  - P3: Multi-turn threads, FactMemoryService, ClarificationPolicy, ScenarioAnswerComposer
  - P4: Next.js chat UI, admin UI, source management, parameter sets screens
  - P4.1: AppProperties, LoggingAspect, trust policy, GlobalExceptionHandler hardening
  - P5: ChatLog persistence + admin UI, CheckDef/Run system + admin UI

- **D-17: Blocking criteria** — Full go/no-go table: one row per feature area, columns for status (pass / fail / skipped) and blocking (yes / no). The milestone closes only when all blocking rows show "pass". A "skipped" with documented rationale is acceptable for non-blocking features.

- **D-18: Open issues at milestone close** — Severity-based policy: blocking findings must be fixed before the milestone closes. Major and minor findings may be deferred to v2 but must be documented in AUDIT.md with explicit rationale. No finding is silently dropped — every open item has a documented resolution or deferral.

</decisions>

<specifics>
## Specific Ideas

- **Database rebuild is intentional, not reluctant.** The user explicitly chose the "nuclear option" for DB cleanup and confirmed "delete anyway" for the process. This means no hesitation — DELETE first, document what was removed, then rebuild.

- **Parameter set replacement is required.** The default seeded parameter set content (system prompt, temperature, retrieval config) must be rewritten as part of this phase — not just preserved. The seeder creates the shell; this phase fills it with production-quality content.

- **Check definitions must be production-grade.** The 20+ check defs authored here are not placeholders or test questions — they are the real evaluation suite that will be reused in future phases. Question wording and reference answers must be carefully written against the actual legal documents that were ingested.

- **Investigation means fix, not document.** The user chose full remediation (Q-08: option c). When a root cause is found, it gets fixed. The AUDIT.md records what was found AND what was changed.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase goals and requirements
- `.planning/ROADMAP.md` §Phase 6 — Phase goal and success criteria
- `.planning/REQUIREMENTS.md` — Active requirements to verify coverage
- `.planning/PROJECT.md` — Core value proposition and constraints

### Prior phase summaries (audit targets)
- `.planning/phases/01-backend-foundation-and-knowledge-base/` — P1 SUMMARY files
- `.planning/phases/02-grounded-legal-q-a-core/02-01-SUMMARY.md` — Answer contract and AnswerCompositionPolicy
- `.planning/phases/02-grounded-legal-q-a-core/02-02-SUMMARY.md` — CitationMapper and ChatService grounding logic
- `.planning/phases/03-multi-turn-case-analysis/03-01-SUMMARY.md` — Thread schema, repositories, endpoints
- `.planning/phases/03-multi-turn-case-analysis/03-02-SUMMARY.md` — FactMemoryService and ClarificationPolicy
- `.planning/phases/03-multi-turn-case-analysis/03-03-SUMMARY.md` — ScenarioAnswerComposer and UAT bug fixes
- `.planning/phases/05-quality-operations-evaluation/` — P5 SUMMARY files (ChatLog, CheckDef/Run system)

### Key source code contracts
- `src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java` — Composition logic to audit
- `src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java` — Prompt construction to audit
- `src/main/java/com/vn/traffic/chatbot/check/` — CheckDef/CheckRun/CheckResult domain to exercise
- `src/main/resources/default-parameter-set.yml` — Current default parameter set content (to be rewritten)
- `src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java` — Negation guard behavior to test
- `src/main/java/com/vn/traffic/chatbot/chat/service/ScenarioAnswerComposer.java` — Final analysis composition

### Database schema
- `src/main/resources/db/changelog/` — All 13 Liquibase migrations defining current schema
- Migrations 001–003: sources, ingestion jobs, vector store
- Migration 008: chat_log (pipeline trace columns in 012, 013)
- Migrations 009–010: check_def, check_run, check_result

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets for This Phase
- `mcp__postgres__execute_sql` — Direct DB access for cleanup queries and chunk inspection
- `POST /api/v1/admin/ingestion/batch` — Batch URL import for supplementary content
- `POST /api/v1/admin/sources/upload` — PDF file upload for key decree documents
- `POST /api/v1/admin/sources/{sourceId}/approve` + `activate` — Required approval workflow steps
- `POST /api/v1/admin/check-runs/trigger` — Trigger evaluation runs; returns 202 Accepted (async)
- `GET /api/v1/admin/check-runs/{runId}/results` — Retrieve per-result scores for investigation

### Key Constraints
- Vector store retrieval gate requires: `approvalState == 'APPROVED' && trusted == 'true' && active == 'true'` — all three flags must be set after ingestion for chunks to appear in retrieval
- `GroundingStatus` thresholds: GROUNDED ≥ 3 chunks, LIMITED_GROUNDING 1–2, REFUSED 0 — retrieval count directly determines answer quality path
- `FactMemoryService` requires vehicleType and violationType as material facts — multi-turn tests must verify these are collected before FINAL_ANALYSIS
- `DefaultParameterSetSeeder` runs as ApplicationRunner on startup — deleting the parameter set row is safe; seeder recreates the shell on next boot
- `source_trust_policy` table drives trust-tier classification by domain pattern — rewriting it from scratch means re-defining all domain → trust tier mappings

### Integration Points
- Check run evaluation reads the active AiParameterSet at trigger time — the rebuilt parameter set must be active before running checks
- ChatLog pipeline trace (`retrieved_chunks`, `prompt_text`, `raw_model_response`, `pipeline_log`) is the primary instrument for low-score root cause investigation
- `kb_vector_store` metadata is JSONB — chunk cleanup requires JSONB-aware WHERE clauses on the metadata field

</code_context>

<deferred>
## Deferred Ideas

- Automated ingestion correctness tests (unit/integration) — this phase uses manual validation; automating it is a v2 quality improvement
- Scheduled check runs or regression monitoring — out of scope for v1
- Retention policy for chat logs — acknowledged as needed but deferred beyond this milestone
- Parameter set version history — deferred to v2 if the system evolves to need audit trails on parameter changes

</deferred>

---

*Phase: 06-audit-real-data-validation-and-stabilization*
*Context gathered: 2026-04-13*
