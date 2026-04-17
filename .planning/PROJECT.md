# Vietnam Traffic Law Chatbot

## What This Is

A REST-first AI chatbot system for answering questions about Vietnam traffic laws and regulations. It helps users understand rules, fines, penalties, procedures, required documents, and legal guidance — including multi-turn analysis of real-life scenarios — while providing an admin interface for managing the knowledge base, AI parameters, chat logs, and answer quality checks.

The backend is a Java 25 Spring REST application with PostgreSQL + pgvector persistence, Spring AI ETL ingestion, and multi-provider LLM routing via 9router. The frontend is a Next.js sidebar-style app combining chat and admin screens.

## Core Value

Users can describe a Vietnam traffic-law situation in natural language and receive grounded, source-backed guidance that explains the relevant rule, likely penalty, required documents, procedure, and recommended next steps.

## Requirements

### Validated

- ✓ CHAT-01: Source-backed Vietnamese traffic-law Q&A — v1.0
- ✓ CHAT-02: Multi-turn conversation threads — v1.0
- ✓ CHAT-03: Cited source references in answers — v1.0
- ✓ CHAT-04: Informational guidance disclaimer — v1.0
- ✓ LEGAL-01: Legal basis tied to retrieved sources — v1.0
- ✓ LEGAL-02: Fine/penalty/consequence information — v1.0
- ✓ LEGAL-03: Required documents and procedures — v1.0
- ✓ LEGAL-04: Recommended next steps — v1.0
- ✓ CASE-01: Real-life scenario analysis grounded in sources — v1.0
- ✓ CASE-02: Missing fact detection — v1.0
- ✓ CASE-03: Clarifying follow-up questions — v1.0
- ✓ CASE-04: Consistent scenario response structure — v1.0
- ✓ KNOW-01: PDF legal document ingestion — v1.0
- ✓ KNOW-02: Word legal document ingestion — v1.0
- ✓ KNOW-03: Structured regulation document ingestion — v1.0
- ✓ KNOW-04: Website content ingestion from trusted sources — v1.0
- ✓ KNOW-05: Provenance metadata retention — v1.0
- ✓ KNOW-06: Active/trusted-only retrieval gating — v1.0
- ✓ ADMIN-01: Source management admin interface — v1.0
- ✓ ADMIN-02: Vector-store/knowledge inspection — v1.0
- ✓ ADMIN-03: AI parameter set CRUD — v1.0
- ✓ ADMIN-04: Chat log review — v1.0
- ✓ ADMIN-05: Answer check definitions and runs — v1.0
- ✓ ADMIN-06: Sidebar-style combined chat+admin app — v1.0
- ✓ PLAT-01: REST API backend — v1.0
- ✓ PLAT-02: Next.js frontend — v1.0
- ✓ PLAT-03: Persistent data layer with vector embeddings — v1.0
- ✓ PLAT-04: Java 25 backend — v1.0

### Active

**v1.1 — Chat Performance & Spring AI Modular RAG**

- [ ] PERF-01: Chat p95 latency under 2.5s for common lookups
- [ ] PERF-02: No false refusal on greetings or non-legal small talk
- [ ] ARCH-01: Chat pipeline uses Spring AI idiomatic advisors (modular RAG)
- [ ] ARCH-02: Structured output via BeanOutputConverter (drop manual JSON parsing)
- [ ] ARCH-03: Minimize hardcoded keyword matching — prefer score thresholds, policy config, or LLM classification
- [ ] CACHE-01: Prompt caching for static system block via OpenRouter cache_control
- [ ] CACHE-02: Embedding cache for repeat queries (Caffeine)
- [ ] ADMIN-07: User-managed API key admin (per-provider, encrypted at rest, masked display, audit logged)

### Out of Scope

- Live traffic-condition integrations — deferred; v1 focused on laws and regulations
- User-role separation and permission modeling — deferred; current stage does not require distinct public/admin role separation
- Manual curated Q&A fallback workflows — excluded from v1; admin scope limited to ingestion, vector store, parameters, chat logs, and answer checks
- Regulation version/effective date distinction — v2 scope
- Document upload for document-assisted analysis — v2 scope
- Alternate scenario comparison — v2 scope
- Non-traffic legal domains — v2 scope

## Current Milestone: v1.1 Chat Performance & Spring AI Modular RAG

**Goal:** Cut chat latency 3-5× and migrate the manual RAG pipeline to Spring AI idiomatic (modular RAG + structured output + custom advisors). Eliminate hardcoded keyword matching. Add user-managed API key administration.

**Target features:**
- Chat latency quick wins (async log, slim JSON schema, prompt trim, loosen grounding gate)
- Extract GroundingGuardAdvisor (refusal policy as CallAroundAdvisor, chitchat mode)
- Migrate to RetrievalAugmentationAdvisor with custom CitationPostProcessor
- Structured output via BeanOutputConverter
- Prompt caching (OpenRouter cache_control) + embedding cache (Caffeine)
- User-managed API key admin with encryption at rest

## Context

Shipped v1.0 MVP on 2026-04-15. 291 commits over 9 days. Post-v1.0 migration on 2026-04-17 replaced 9router + beeknoee with OpenRouter as sole AI gateway for both chat and embedding.

**Tech stack:** Java 25 Spring Boot, PostgreSQL + pgvector, Spring AI ETL, OpenRouter (unified OpenAI-compatible API for chat + embedding), Next.js 16, shadcn/ui, React Query, base-ui.

**Codebase:** 12,837 LOC Java (backend) + 11,455 LOC TypeScript (frontend). 15 database tables via Liquibase migrations. 185+ automated tests.

**Knowledge base:** 3 core Vietnamese legal decrees ingested and validated (ND 168/2024, Luat GTDB 2008, ND 100/2019). Trust policy enforcement with PRIMARY/SECONDARY/MANUAL_REVIEW tiers.

**AI routing:** OpenRouter unified gateway (`https://openrouter.ai/api/v1`) with 8-model catalog: Anthropic (Sonnet/Opus/Haiku 4.6), OpenAI (GPT-5.1, 4o-mini), Google (Gemini 3.1 Pro), DeepSeek V3.2, Claude Sonnet 4.5 1M-context. Default chat model: `openai/gpt-4o-mini` (speed-optimized); user picks heavier models via UI dropdown.

## Constraints

- **Architecture**: REST-first Spring backend
- **Runtime**: Java 25
- **Frontend**: Next.js with shadcn/ui
- **Language**: Vietnamese-first prompts and UX
- **Data sources**: Trusted legal content only with provenance tracking
- **Ingestion**: Spring AI ETL/readers as primary pipeline with SSRF-safe fetch
- **Scope**: Traffic law first

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Focus v1 on traffic laws, not conditions | User explicitly scoped current work to legal/regulatory | ✓ Good — clean scope |
| Chat plus case analysis in v1 | Real-life scenarios are core to the product promise | ✓ Good — delivered multi-turn |
| REST services instead of Jmix UI | Preserves backend concepts while enabling modern frontend | ✓ Good — clean separation |
| Next.js + shadcn/ui for frontend | Practical, maintainable UI stack | ✓ Good — shipped quickly |
| Spring AI ETL as primary ingestion | Leverage framework readers while layering trust/provenance | ✓ Good — reduced custom code |
| Inline clarification via system prompt | Simpler than explicit clarification gate; removed in quick task 260414-kfe | ✓ Good — reduced complexity |
| 9router as sole AI gateway | Single OpenAI-compatible endpoint for all providers | ✓ Superseded by OpenRouter migration (v1.1) |
| YAML-driven model catalog | Replace hardcoded AllowedModel enum with config-driven catalog | ✓ Good — extensible without code changes |
| OpenRouter replaces 9router + beeknoee | One unified provider for chat + embedding, single credential, broader catalog | 2026-04-17 — removes local tunnel dependency |
| Default chat model → openai/gpt-4o-mini | 3-5× faster p95 for common lookups; heavier models opt-in via dropdown | 2026-04-17 — speed optimization |
| Minimize hardcoded keyword matching (v1.1) | Previous `containsAnyLegalCitation` gate caused false refusals; prefer policy config / LLM classification | Cross-cutting principle for v1.1 |

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
*Last updated: 2026-04-17 — v1.1 milestone started*
