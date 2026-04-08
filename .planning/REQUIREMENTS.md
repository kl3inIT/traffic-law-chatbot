# Requirements: Vietnam Traffic Law Chatbot

**Defined:** 2026-04-07
**Core Value:** Users can describe a Vietnam traffic-law situation in natural language and receive grounded, source-backed guidance that explains the relevant rule, likely penalty, required documents, procedure, and recommended next steps.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Chat Foundation

- [ ] **CHAT-01**: User can ask a Vietnam traffic-law question in Vietnamese and receive a source-backed answer.
- [ ] **CHAT-02**: User can continue a conversation across multiple turns within the same thread.
- [ ] **CHAT-03**: User can view cited source references used to support an answer.
- [ ] **CHAT-04**: User receives a clear disclaimer when the system provides informational legal guidance rather than formal legal advice.

### Legal Guidance

- [ ] **LEGAL-01**: User receives the relevant legal basis for an answer, tied to retrieved source content.
- [ ] **LEGAL-02**: User receives likely fine, penalty, or administrative consequence information when relevant to the question.
- [ ] **LEGAL-03**: User receives required documents, procedure, or compliance steps when relevant to the question.
- [ ] **LEGAL-04**: User receives recommended next steps based on the described traffic-law situation.

### Case Analysis

- [ ] **CASE-01**: User can describe a real-life traffic scenario in natural language and receive structured analysis grounded in retrieved sources.
- [ ] **CASE-02**: System can identify missing facts that materially affect the legal outcome.
- [ ] **CASE-03**: System can ask clarifying follow-up questions before finalizing a scenario-based answer.
- [ ] **CASE-04**: Scenario-based answers follow a consistent structure covering facts, rule, consequence, action, and sources.

### Knowledge Sources

- [ ] **KNOW-01**: Admin can ingest PDF legal documents into the knowledge base.
- [ ] **KNOW-02**: Admin can ingest Word legal documents into the knowledge base.
- [ ] **KNOW-03**: Admin can ingest structured policy or regulation documents into the knowledge base.
- [x] **KNOW-04**: Admin can ingest website content from trusted sources into the knowledge base.
- [x] **KNOW-05**: Ingested knowledge items retain provenance metadata including source type and origin.
- [x] **KNOW-06**: Only active and trusted knowledge sources are eligible for retrieval in production answers.

### Admin Operations

- [ ] **ADMIN-01**: Admin can view and manage ingested sources through an admin interface.
- [ ] **ADMIN-02**: Admin can inspect vector-store content or indexed documents relevant to the knowledge base.
- [ ] **ADMIN-03**: Admin can create, edit, copy, and activate AI parameter sets.
- [ ] **ADMIN-04**: Admin can review chat logs for past conversations.
- [ ] **ADMIN-05**: Admin can define and run answer checks to evaluate chatbot quality.
- [ ] **ADMIN-06**: Admin can use a sidebar-style interface that combines chat and admin screens in one application.

### Platform

- [x] **PLAT-01**: Backend exposes the core chatbot and admin capabilities through REST APIs.
- [ ] **PLAT-02**: Frontend is implemented in Next.js and supports the v1 chat and admin workflows.
- [ ] **PLAT-03**: System stores operational data, source metadata, and vector embeddings in a persistent backend data layer.
- [ ] **PLAT-04**: Backend targets Java 25.

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Traffic Conditions

- **TRAF-01**: User can receive traffic-condition information from live or regularly refreshed traffic data sources.

### Legal Depth

- **LDPT-01**: System can distinguish regulation versions and effective dates in end-user answers.
- **LDPT-02**: User can upload traffic tickets, notices, or related documents for document-assisted analysis.
- **LDPT-03**: User can compare alternate scenario outcomes based on changed facts.

### Product Expansion

- **PROD-01**: System supports legal domains beyond Vietnam traffic law.
- **PROD-02**: System supports richer user-role separation and permission controls.

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Live traffic-condition integrations | User explicitly deferred traffic-condition work from the current phase |
| Broad non-traffic legal assistant coverage | v1 must stay focused on Vietnam traffic-law trust and accuracy |
| Manual curated Q&A fallback workflows | User limited admin scope to ingestion, vector store management, parameters, chat logs, and answer checks |
| Advanced legal drafting or appeal generation | Too complex and outside the current core value |
| Custom-built chat UI from scratch | Existing AI UI patterns are preferred for maintainability |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CHAT-01 | Phase 2 | Pending |
| CHAT-02 | Phase 3 | Pending |
| CHAT-03 | Phase 2 | Pending |
| CHAT-04 | Phase 2 | Pending |
| LEGAL-01 | Phase 2 | Pending |
| LEGAL-02 | Phase 2 | Pending |
| LEGAL-03 | Phase 2 | Pending |
| LEGAL-04 | Phase 2 | Pending |
| CASE-01 | Phase 3 | Pending |
| CASE-02 | Phase 3 | Pending |
| CASE-03 | Phase 3 | Pending |
| CASE-04 | Phase 3 | Pending |
| KNOW-01 | Phase 1 | Complete |
| KNOW-02 | Phase 1 | Complete |
| KNOW-03 | Phase 1 | Complete |
| KNOW-04 | Phase 1 | Complete |
| KNOW-05 | Phase 1 | Complete |
| KNOW-06 | Phase 1 | Complete |
| ADMIN-01 | Phase 4 | Pending |
| ADMIN-02 | Phase 4 | Pending |
| ADMIN-03 | Phase 4 | Pending |
| ADMIN-04 | Phase 5 | Pending |
| ADMIN-05 | Phase 5 | Pending |
| ADMIN-06 | Phase 4 | Pending |
| PLAT-01 | Phase 1 | Complete |
| PLAT-02 | Phase 4 | Pending |
| PLAT-03 | Phase 1 | Complete |
| PLAT-04 | Phase 1 | Complete |

**Coverage:**
- v1 requirements: 28 total
- Mapped to phases: 28
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-07*
*Last updated: 2026-04-07 after initial definition*
