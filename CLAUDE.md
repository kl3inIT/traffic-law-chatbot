# Vietnam Traffic Law Chatbot

This project uses the GSD workflow artifacts in `.planning/`.

## Current Project Context

- Product: Vietnam traffic-law AI chatbot
- Primary focus: source-grounded traffic laws and regulations in Vietnamese
- Backend: Java 25, Spring REST architecture
- Frontend: Next.js sidebar-style app for chat + admin
- Reference alignment:
  - Preserve core concepts from `jmix-ai-backend`
  - Follow controller/service/API patterns similar to the migrated shoes backend

## Working Rules

- Read `.planning/PROJECT.md`, `.planning/milestones/v1.0-REQUIREMENTS.md`, and `.planning/ROADMAP.md` before major implementation work.
- Prefer REST-first backend changes.
- Keep traffic-condition integrations out of v1 unless requirements change.
- Preserve trusted-source ingestion, vector-store management, parameters, chat logs, and answer checks as first-class capabilities.
- Treat legal source provenance and answer grounding as critical, not optional.

## Current Roadmap

1. Backend Foundation & Knowledge Base
2. Grounded Legal Q&A Core
3. Multi-turn Case Analysis
4. Next.js Chat & Admin App
5. Quality Operations & Evaluation

## Next Step

Use `/gsd-discuss-phase 1` to begin Phase 1.
