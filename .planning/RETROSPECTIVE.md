# Retrospective

## Milestone: v1.0 — MVP

**Shipped:** 2026-04-15
**Phases:** 9 | **Plans:** 38 | **Tasks:** 53
**Timeline:** 9 days (2026-04-07 to 2026-04-15)
**Commits:** 291

### What Was Built

- Java 25 Spring REST backend with PostgreSQL + pgvector, Liquibase schema, async ingestion pipeline
- Spring AI ETL-first ingestion with SSRF-safe fetch, provenance, token chunking, trust policy enforcement
- Source-grounded Vietnamese legal Q&A with citations, legal basis, penalties, procedures, disclaimers
- Multi-turn case analysis with thread memory and inline clarification via system prompt
- Next.js 16 sidebar app: chat UI, source management, knowledge inspection, AI parameters, chat logs, answer checks
- Quality operations: LLM semantic evaluator, check definitions, check runs, chat logging
- Full audit: 3 real legal decrees, 22+ checks, 34 scenarios, feature go/no-go table
- Multi-provider model routing via 9router with YAML catalog and dynamic selection UI

### What Worked

- **GSD workflow velocity:** 9 phases in 9 days with consistent discuss-plan-execute-verify cadence
- **Reference-driven development:** jmix-ai-backend provided a clear feature baseline; shoes-backend provided REST conventions
- **Spring AI ETL adoption:** Leveraging framework readers reduced custom parsing code significantly
- **Decimal phase insertion:** 01.1, 4.1, 06.1 handled urgent work without disrupting the main sequence
- **Quick tasks for small fixes:** 4 quick tasks handled UX polish and cleanup without full phase overhead
- **Yolo mode:** Minimal confirmation gates kept momentum high for a solo developer project

### What Was Inefficient

- **REQUIREMENTS.md checkbox lag:** Requirements traceability table was never updated after phases completed — 14/28 still showed "Pending" at milestone close despite all work being done
- **Summary one-liner extraction:** The `summary-extract` tool failed to produce useful accomplishment summaries, requiring manual curation
- **Phase 01.1 worktree restoration:** A blocking issue with missing fetch-layer files in the worktree caused rework during the ingestion refactor
- **Clarification gate complexity:** Built an explicit ClarificationPolicy/gate system in Phase 3, then removed it entirely in quick task 260414-kfe in favor of inline system-prompt clarification — wasted effort

### Patterns Established

- `Map<String,ChatClient>` factory pattern for multi-model routing
- Trust policy with PRIMARY/SECONDARY/MANUAL_REVIEW tiers for source credibility
- Active parameter set pattern: admin-controlled YAML config applied at runtime
- Retrieval gate: approved + trusted + active filter before any LLM call
- Structured REFUSED responses for zero-result retrieval instead of HTTP errors

### Key Lessons

- Keep REQUIREMENTS.md in sync with phase completions — stale traceability creates confusion at milestone close
- Prefer simple inline approaches (system prompt clarification) over explicit orchestration gates when the complexity isn't justified
- Vietnamese legal content works well with token-based chunking at ~800 tokens for retrieval quality
- 9router as a local gateway simplifies multi-provider routing to a single OpenAI-compatible endpoint

## Cross-Milestone Trends

| Metric | v1.0 |
|--------|------|
| Phases | 9 |
| Plans | 38 |
| Tasks | 53 |
| Days | 9 |
| Commits | 291 |
| Java LOC | 12,837 |
| TS LOC | 11,455 |
