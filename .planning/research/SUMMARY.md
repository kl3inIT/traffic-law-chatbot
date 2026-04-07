# Research Summary

**Project:** Vietnam Traffic Law Chatbot  
**Date:** 2026-04-07

## Key Findings

**Stack:** Use a Spring Boot REST modular monolith with Spring AI, PostgreSQL + pgvector, Liquibase, and a Next.js 16 App Router frontend using shadcn/ui plus AI SDK chat patterns.

**Table Stakes:** Vietnamese-first source-backed legal Q&A, structured case analysis, visible citations, clarifying questions for ambiguous scenarios, and admin operations for ingestion, vector-store inspection, parameters, chat logs, and answer checks.

**Watch Out For:** Outdated legal sources, weak source authority handling, overconfident answers when facts are missing, prompt injection via website ingestion, and delaying quality controls until after the UI is built.

## Synthesis

This product should be treated as a **source-grounded traffic-law case assistant**, not a generic chatbot. The core architecture should preserve the operational AI capabilities from `jmix-ai-backend` but express them in a REST-first backend and a Next.js sidebar application. The shoes-shopping backend provides the right structural template for controllers, services, DTOs, persistence, and operational APIs.

The highest-leverage v1 is:
- trusted source ingestion,
- source-grounded Vietnamese legal Q&A,
- structured case analysis for real-life traffic-law scenarios,
- and admin controls to keep answer quality observable and correctable.

## Recommended Initial Scope

### Must-have in v1
- Public Vietnamese-first chat for traffic-law questions
- Structured case analysis for real-life scenarios
- Citations / legal basis in answers
- Guidance on penalty, documents, procedure, and next steps
- Admin ingestion for PDF/Word/structured documents/websites
- Vector store management / source inspection
- AI parameter management
- Chat logs
- Answer checks

### Explicitly defer
- Live traffic-condition integrations
- Broad non-traffic legal coverage
- Advanced document drafting / appeal generation
- Rich user-role separation beyond current simple admin usage

## Roadmap Implications

1. Start with backend/data foundations and source governance.
2. Build trusted retrieval and cited legal Q&A before richer scenario handling.
3. Add structured case analysis and clarifying-question flow next.
4. Build the combined Next.js chat/admin shell around those backend capabilities.
5. Harden with answer checks, retrieval inspection, and source freshness controls.

## Files

- `.planning/research/STACK.md`
- `.planning/research/FEATURES.md`
- `.planning/research/ARCHITECTURE.md`
- `.planning/research/PITFALLS.md`
