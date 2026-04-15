# Phase 2: Grounded Legal Q&A Core - Context

**Gathered:** 2026-04-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver Vietnamese-first source-backed legal Q&A on top of the Phase 1 knowledge base. This phase covers grounded single-turn answers with visible citations, legal-basis coverage, likely penalties/consequences, required documents or procedure when relevant, recommended next steps, and a clear informational-guidance disclaimer. Multi-turn thread memory, clarifying follow-up questioning, and the full Next.js chat/admin UI belong to later phases.

</domain>

<decisions>
## Implementation Decisions

### Answer structure
- **D-01:** Phase 2 answers should use a standard legal-guidance structure, but only include sections that are relevant to the user’s question.
- **D-02:** The practical answer flow should prioritize a clear conclusion first, then supporting sections such as legal basis, penalty/consequence, documents/procedure, next steps, and disclaimer as applicable.

### Citations and grounding
- **D-03:** Citations should appear both inline near the relevant claim and again in a dedicated source list at the end of the answer.
- **D-04:** Source references must stay visible enough for both end-user trust and later admin verification.

### Weak-retrieval behavior
- **D-05:** If retrieval is not strong enough to support a grounded legal answer, the system should refuse to provide substantive legal guidance.
- **D-06:** In weak-retrieval cases, the response should explain that the answer could not be grounded confidently enough in trusted sources and should encourage the user to narrow or restate the question.

### Vietnamese answer tone
- **D-07:** Vietnamese responses should use a plain but formal tone: professional, respectful, and easy for ordinary users to understand.
- **D-08:** The tone should remain cautious and informational rather than sounding like binding legal advice.

### Claude's Discretion
- Exact response DTO shape for answer sections, citation objects, and grounding metadata.
- Retrieval quality thresholds and any scoring heuristics used to trigger refusal behavior.
- Prompt composition details for producing Vietnamese legal answers from retrieved content.
- Exact inline citation formatting and whether section labels are fixed strings or prompt-driven.

</decisions>

<specifics>
## Specific Ideas

- Phase 2 should feel Vietnamese-first and trustworthy rather than generic chatbot-like.
- The answer should avoid forcing empty sections when a question does not involve penalties, documents, or procedure.
- Weakly grounded answers should fail safely instead of guessing.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase definition
- `.planning/ROADMAP.md` — Defines Phase 2 goal, requirements, and success criteria for grounded legal Q&A.
- `.planning/REQUIREMENTS.md` — Defines CHAT-01, CHAT-03, CHAT-04, LEGAL-01, LEGAL-02, LEGAL-03, and LEGAL-04.
- `.planning/PROJECT.md` — Defines Vietnamese-first guidance, trusted-source requirements, and REST-first backend alignment.
- `.planning/STATE.md` — Confirms Phase 2 is the current priority after Phase 1 shipping.
- `.planning/phases/01-backend-foundation-knowledge-base/01-CONTEXT.md` — Carries forward retrieval safety and trusted-source constraints already locked in Phase 1.

### Existing backend foundations
- `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java` — Defines current trusted/approved/active retrieval filter and base vector-search request pattern.
- `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java` — Defines the current pgvector-backed vector store wiring that Phase 2 chat retrieval will build on.
- `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java` — Shows chunk metadata fields and the source-grounding metadata available to downstream answer generation.
- `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java` — Defines current REST path conventions that should guide new Phase 2 chat endpoints.
- `src/main/resources/application.yaml` — Defines existing Spring AI/vector-store configuration baseline for backend chat integration.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java`: Already provides the trusted-source vector search filter that Phase 2 answers must preserve.
- `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java`: Already wires a `PgVectorStore` bean for retrieval-backed answer generation.
- `src/main/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestrator.java`: Already stores chunk metadata including source IDs, source version IDs, location hints, approval state, and processing version that can support citation rendering.
- `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java`: Provides the established REST path constant pattern for adding chat-facing endpoints.

### Established Patterns
- The current backend is REST-first and constant-driven for API path definitions.
- Source safety is already enforced at retrieval level through approved + trusted + active filtering and should remain non-negotiable.
- Indexed chunks already carry provenance/location metadata, enabling citation display without changing Phase 1 ingestion concepts.
- No chat controller, answer service, prompt assembly layer, or chat response DTOs exist yet, so planner must define these cleanly.

### Integration Points
- New Phase 2 chat APIs should connect under `src/main/java/com/vn/traffic/chatbot/` alongside the existing admin modules.
- Answer generation should build on the current `VectorStore` and retrieval policy rather than bypassing them.
- Citation rendering should map from stored chunk metadata to user-facing references.
- Disclaimer and structured-answer composition should live in the backend contract so later frontend work can consume them directly.

</code_context>

<deferred>
## Deferred Ideas

- Multi-turn conversation continuity and thread memory belong to Phase 3.
- Clarifying follow-up questions before finalizing ambiguous scenario guidance belong to Phase 3.
- Full Next.js chat UI presentation details belong to Phase 4.

</deferred>

---

*Phase: 02-grounded-legal-q-a-core*
*Context gathered: 2026-04-08*
