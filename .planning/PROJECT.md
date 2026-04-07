# Vietnam Traffic Law Chatbot

## What This Is

A REST-first AI chatbot system for answering questions about traffic in Vietnam, focused primarily on traffic laws and regulations in v1. It helps users understand rules, fines, penalties, procedures, required documents, and legal guidance, including analysis of real-life scenarios, while also providing an admin interface for managing the knowledge base and AI behavior.

The backend should preserve the core RAG, ingestion, chat, parameter management, chat logging, and answer-check capabilities of the existing Jmix AI backend, but re-implemented in a Spring REST architecture aligned with the migrated shoes shopping online system. The frontend should be built in Next.js with a practical existing AI UI library and a sidebar-style app that includes both chat and admin screens.

## Core Value

Users can describe a Vietnam traffic-law situation in natural language and receive grounded, source-backed guidance that explains the relevant rule, likely penalty, required documents, procedure, and recommended next steps.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Public users can ask Vietnam traffic-law questions in Vietnamese and receive source-backed answers.
- [ ] Public users can describe real-life traffic cases and receive structured legal guidance grounded in retrieved sources.
- [ ] Admins can ingest and manage trusted knowledge sources for the vector store, including PDF, Word, structured regulation documents, and website content.
- [ ] Admins can manage AI parameters, inspect chat logs, run answer checks, and monitor retrieval quality through an admin interface inspired by the Jmix AI backend.
- [ ] The system preserves the main RAG/admin architecture intent from jmix-ai-backend while exposing capabilities through REST APIs and a Next.js frontend.

### Out of Scope

- Live traffic-condition integrations — deferred for now because the current phase should focus on traffic laws and regulations.
- User-role separation and permission modeling — deferred because the current stage does not require distinct public/admin role separation.
- Manual curated Q&A fallback workflows — excluded from v1 because the admin scope is limited to ingestion, vector store management, parameter management, chat logs, and answer checks.

## Context

This project is intended to answer questions about traffic in Vietnam, with traffic laws and regulations as the primary focus in v1 and traffic-condition information kept out of current scope. The chatbot should support realistic user scenarios, not just simple FAQ-style retrieval.

The user wants the backend to take the core ideas from `jmix-ai-backend` and keep the architecture aligned with that system's RAG, chat, ingestion, parameter, logging, and answer-checking concepts, but replace the Jmix UI-driven approach with a REST-first Spring architecture. The user also wants the implementation to align with patterns already used in the migrated shoes shopping backend, especially its controller/service/API structure.

Reference systems reviewed during initialization:
- `D:/Study materials spring 2026/EXE101/ai/jmix-ai-backend` — source of the core RAG/admin concepts and feature set.
- `D:/Study materials spring 2026/SBA301/project/shoes-shopping-online-system-be` — source of the preferred REST/controller-service structure after migrating away from Jmix.

The frontend should be Vietnamese-first and use a practical, maintainable existing AI component approach, with shadcn-based chat UI or Vercel AI SDK-style UI being acceptable options. The admin experience should resemble a Jmix AI backend/admin panel and live in the same sidebar-style application as the chat UI for now.

Knowledge ingestion in v1 must support trusted sources across PDF/Word legal documents, structured policy/regulation documents, and website content, prioritizing official and trustworthy materials.

## Constraints

- **Architecture**: REST-first Spring backend — must keep the main ideas from `jmix-ai-backend` but expose them through REST APIs instead of Jmix UI-driven flows.
- **Alignment**: Follow migrated backend patterns — should stay structurally aligned with the shoes shopping backend's controller/service/API conventions where practical.
- **Frontend**: Next.js with existing AI UI library — should prefer stable, easy-to-implement, maintainable UI components rather than custom-heavy chat UI.
- **Language**: Vietnamese-first — prompts, UX, and answer quality should prioritize Vietnamese usage in v1.
- **Data sources**: Trusted legal content only — ingestion should prioritize official and trustworthy legal/regulatory materials.
- **Scope**: Traffic law first — traffic-condition features should not drive v1 architecture or delivery scope.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Focus v1 on traffic laws and regulations, not traffic conditions | The user explicitly wants current work centered on legal/regulatory assistance | — Pending |
| Support chat plus case analysis in v1 | The chatbot must handle real-life scenarios, not just direct Q&A | — Pending |
| Preserve Jmix AI backend capabilities but implement them as REST services | The user wants to keep the main idea of the existing AI backend while replacing Jmix-driven interaction patterns | — Pending |
| Use Next.js for frontend and reuse an existing AI UI approach | The user prefers a practical, maintainable UI stack instead of building chat UX from scratch | — Pending |
| Build admin scope around ingestion, vector store management, parameter management, chat logs, and answer checks | The user wants the admin area to match the core Jmix AI backend/admin feature set for v1 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-07 after initialization*
