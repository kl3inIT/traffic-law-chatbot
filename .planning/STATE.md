---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: — Chat Performance & Spring AI Modular RAG
current_phase: 08
status: Phase 08 goal achieved (20/20 code-level must-haves + live run 5/7 pass)
last_updated: "2026-04-18T12:55:25.651Z"
last_activity: 2026-04-18 -- Phase 08 verification passed; 2 regression tests deferred to Phase 9 (NoOpRetrievalAdvisor → 100% refusal is scope-correct)
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 8
  completed_plans: 8
  percent: 100
---

# State: Vietnam Traffic Law Chatbot

**Initialized:** 2026-04-07
**Current phase:** 08
**Project status:** v1.1 roadmap approved (v1.0 MVP shipped 2026-04-15)

## Current Position

Phase: 08 COMPLETE → next: Phase 09 (Modular RAG + Prompt Caching)
Plan: 4 of 4 plans verified
Status: Phase 08 goal achieved (20/20 code-level must-haves + live run 5/7 pass)
Last activity: 2026-04-18 -- Phase 08 verification passed; 2 regression tests deferred to Phase 9 (NoOpRetrievalAdvisor → 100% refusal is scope-correct)

## Phase 8 Live Run Result (2026-04-18)

- ✓ StructuredOutputMatrixIT (all 8 models, no schema 400s)
- ✓ IntentClassifierIT (LEGAL/CHITCHAT/OFF_TOPIC live)
- ✓ twoTurnConversationMemoryWorks
- Deferred → Phase 9: twentyQueryRegressionSuiteAtLeast95Percent (0/20 — NoOpRetrievalAdvisor, no real retrieval yet)
- Deferred → Phase 9: refusalRateWithinTenPercentOfPhase7Baseline (100% refusal; same root cause; re-run with RAG + Phase7Baseline backfill)

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-17)

**Core value:** Users can describe a Vietnam traffic-law situation in natural language and receive grounded, source-backed guidance that explains the relevant rule, likely penalty, required documents, procedure, and recommended next steps.
**Current focus:** Phase 09 — Modular RAG + Prompt Caching

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
| 9 | Modular RAG + Prompt Caching | Not started |
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
*Last updated: 2026-04-18 — Phase 08 complete*
