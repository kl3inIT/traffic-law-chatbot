---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: — Chat Performance & Spring AI Modular RAG
current_phase: 9
status: code_complete_carryovers_open
last_updated: "2026-04-18T23:59:00.000Z"
last_activity: 2026-04-18 -- Phase 09 code-complete for original scope; G5/G7/CACHE-01 carry-overs
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 13
  completed_plans: 13
  percent: 95
---

# State: Vietnam Traffic Law Chatbot

**Initialized:** 2026-04-07
**Current phase:** 9
**Project status:** v1.1 roadmap approved (v1.0 MVP shipped 2026-04-15)

## Current Position

Phase: 9 (modular-rag-prompt-caching) — CODE-COMPLETE (original scope); carry-overs open
Plans executed: 09-01, 09-02, 09-03, 09-04 (G4 closed)
Plans pending: 09-05 (G5 — IntentClassifier dedicated ChatClient wiring), possible 09-06 (G7 — retrieval/grounding quality)
Status: Ready for next plan (09-05) OR roll carry-overs to v1.2
Last activity: 2026-04-18 -- Phase 09 live re-run; 3 GREEN, G7 identified, G5/G6/CACHE-01 deferred

## Phase 9 Live Run Result (2026-04-18)

- ✓ `twoTurnConversationMemoryWorks` — GREEN (G4 VARCHAR(36) fix validated)
- ✓ `CitationFormatRegressionIT` — GREEN (ARCH-05 byte-for-byte parity)
- ✓ `EmptyContextRefusalIT` — GREEN (T-9-02 / SC 3)
- ✗ `refusalRateWithinTenPercentOfPhase7Baseline` — RED-as-expected (G6, NaN baseline, NOT a blocker)
- ✗ `twentyQueryRegressionSuiteAtLeast95Percent` — 4/20 → NEW GAP G7 (retrieval/grounding quality, orthogonal to modular-RAG wiring)

## Phase 9 Carry-Overs

- **G5** — IntentClassifier misroute via shared advisor chain → Plan 09-05 created
- **G7** — 16/20 fact-check fails (retrieval quality / judge strictness / KB coverage) → needs dedicated investigation (tentative Plan 09-06)
- **CACHE-01** — prompt-caching requirement; no successor phase claims it (ROADMAP Phase 10 = ADMIN-* only); user decision needed (schedule late-v1.1 close-out OR roll to v1.2)

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-17)

**Core value:** Users can describe a Vietnam traffic-law situation in natural language and receive grounded, source-backed guidance that explains the relevant rule, likely penalty, required documents, procedure, and recommended next steps.
**Current focus:** Phase 9 — modular-rag-prompt-caching

## Roadmap Snapshot

v1.0 (Complete):

| Phase | Name | Status |
|------|------|--------|
| 1 | Backend Foundation & Knowledge Base | Complete |
| 01.1 | Spring AI-first Ingestion Alignment | Complete |
| 2 | Grounded Legal Q&A Core | Complete |
| 3 | Multi-turn Case Analysis | Complete |
| 4 | Next.js Chat & Admin App | Complete |
| 4.1 | Backend Hardening, ETL & Use-Case Architecture | Complete |
| 5 | Quality Operations & Evaluation | Complete |
| 6 | Audit, Real-Data Validation & Stabilization | Complete |
| 06.1 | Multi-Provider AI Model Selection | Complete |

v1.1 (Planned):

| Phase | Name | Status |
|------|------|--------|
| 7 | Chat Latency Foundation | Not started |
| 8 | Structured Output + GroundingGuardAdvisor | Complete |
| 9 | Modular RAG + Prompt Caching | Code-complete (original scope); G5/G7/CACHE-01 carry-overs |
| 10 | User-Managed API Key Admin | Not started (parallelizable with Phase 7) |

## Accumulated Context

- OpenRouter migration (2026-04-17): 9router + beeknoee replaced with unified OpenRouter gateway for chat + embedding
- Default chat model switched to `openai/gpt-4o-mini` for speed
- Chat pipeline currently uses manual RAG (ChatService.doAnswer ~250 lines): hardcoded keyword matching in `containsAnyLegalCitation` causes false refusals on greetings; 12-field JSON schema inflates completion tokens; sync chat log save blocks response; only `MessageChatMemoryAdvisor` from Spring AI is used
- Spring AI 2.0.0-M4 available; modular RAG building blocks (`RetrievalAugmentationAdvisor`, `ContextualQueryAugmenter`, `DocumentRetriever`, `DocumentPostProcessor`) are stable since M3
- ADR locked: single `OpenAiChatModel` via OpenRouter OpenAI-compat for all 8 models in v1.1 (no provider-specific starters)
- ADR locked: Caffeine in-JVM cache (not Redis/Hazelcast) for v1.1; Spring Cache abstraction preserves swap path
- ADR locked: hardcoded Vietnamese legal-keyword gate removed wholesale in v1.1 (ARCH-03)
- Roadmap v1.1 created 2026-04-17: 4 phases (7, 8, 9, 10), 16/16 requirement coverage

## Workflow Settings

- Mode: yolo
- Granularity: standard
- Parallelization: true
- Research: enabled
- Plan check: enabled
- Verifier: enabled
- Nyquist validation: enabled

---
*Last updated: 2026-04-18 — Phase 09 code-complete (carry-overs: G5, G7, CACHE-01)*
