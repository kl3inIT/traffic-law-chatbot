---
phase: 02-grounded-legal-q-a-core
verified: 2026-04-08T13:43:55Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 2: Grounded Legal Q&A Core Verification Report

**Phase Goal:** Deliver Vietnamese-first source-backed legal Q&A with visible citations and practical legal guidance.
**Verified:** 2026-04-08T13:43:55Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
| --- | --- | --- | --- |
| 1 | A user can ask a Vietnam traffic-law question in Vietnamese and receive a relevant answer grounded in retrieved content. | VERIFIED | `PublicChatController` exposes `POST /api/v1/chat`; `ChatQuestionRequest` accepts Vietnamese question text with validation; `ChatService.answer()` always routes through `RetrievalPolicy.buildRequest(question, retrievalTopK)` then `vectorStore.similaritySearch(request)` before model generation; `ChatFlowIntegrationTest` covers grounded and refused outcomes. |
| 2 | Answers display source references clearly enough for user and admin verification. | VERIFIED | `ChatAnswerResponse` exposes both `citations` and `sources`; `CitationMapper` maps `sourceId`, `sourceVersionId`, `sourceTitle`, `origin`, `pageNumber`, `sectionRef`, and excerpt from retrieved metadata; `ChatContractSerializationTest`, `CitationMapperTest`, `ChatControllerTest`, and `ChatFlowIntegrationTest` all verify those fields are present and populated. |
| 3 | Answers include the relevant legal basis when the source supports it. | VERIFIED | `LegalAnswerDraft` and `ChatAnswerResponse` both include `legalBasis`; `AnswerComposer` renders `Căn cứ pháp lý` only when supported; `AnswerComposerTest` and grounded-flow tests assert legal-basis content. |
| 4 | Answers include likely penalty/consequence and required documents/procedure when relevant. | VERIFIED | `ChatAnswerResponse` includes `penalties`, `requiredDocuments`, and `procedureSteps`; `AnswerComposer` conditionally renders `Mức phạt hoặc hậu quả` and `Giấy tờ hoặc thủ tục`; `AnswerComposerTest` verifies presence when supported and omission when unsupported. |
| 5 | Answers include a clear informational-guidance disclaimer and recommended next steps. | VERIFIED | `AnswerCompositionPolicy.DEFAULT_DISCLAIMER` is the locked Vietnamese disclaimer; `AnswerComposer` appends disclaimer and `nextSteps`; `AnswerComposerTest`, `ChatControllerTest`, and `ChatFlowIntegrationTest` assert disclaimer presence, including refusal path. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| --- | --- | --- | --- |
| `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java` | Stable public chat route constant | VERIFIED | Defines `CHAT = "/api/v1/chat"`; used by `PublicChatController`. |
| `src/main/java/com/vn/traffic/chatbot/chat/api/PublicChatController.java` | Public REST entry point | VERIFIED | Exists, substantive, wired to `ChatService.answer(request.question())`, validated by `ChatControllerTest` and integration flow. |
| `src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java` | Stable Phase 2 response envelope | VERIFIED | Includes grounding, disclaimer, legal guidance sections, citations, and sources; serialization contract pinned by `ChatContractSerializationTest`. |
| `src/main/java/com/vn/traffic/chatbot/chat/citation/CitationMapper.java` | Metadata-to-citation/source mapping | VERIFIED | Extracts provenance from retrieved documents and deduplicates source list while preserving inline citations. |
| `src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java` | Deterministic Vietnamese-first answer composition | VERIFIED | Builds conclusion-first answer, suppresses unsupported sections, applies refusal/limited-grounding policy. |
| `src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java` | Retrieval-backed grounded Q&A orchestration | VERIFIED | Uses `RetrievalPolicy`, `VectorStore`, `ChatPromptFactory`, `ChatClient`, `CitationMapper`, and `AnswerComposer`; refusal bypasses model call. |
| `src/main/java/com/vn/traffic/chatbot/chat/config/ChatClientConfig.java` | Runtime `ChatClient` bean wiring | VERIFIED | `ChatClient.builder(chatModel).build()` present and exercised in `ChatFlowIntegrationTest`. |
| `src/main/resources/application.yaml` | Runtime Phase 2 chat config | VERIFIED | Contains `app.chat.retrieval.top-k: 5` and `app.chat.grounding.limited-threshold: 2`. |
| `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java` | End-to-end API proof | VERIFIED | Exercises real controller/service/client wiring with mocked retrieval/model for grounded and refused responses. |
| `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java` | Request validation and contract coverage | VERIFIED | Verifies 200 response shape and ProblemDetail validation behavior. |
| `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java` | Grounded/limited/refused service behavior | VERIFIED | Verifies retrieval policy usage, grounding branches, refusal bypass, and composition handoff. |
| `src/test/java/com/vn/traffic/chatbot/chat/citation/CitationMapperTest.java` | Citation provenance coverage | VERIFIED | Verifies mapping, null page handling, and source deduplication. |
| `src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerTest.java` | Composition/disclaimer behavior | VERIFIED | Verifies conclusion-first ordering, omission of unsupported sections, limited notice, refusal, and exact disclaimer string. |
| `src/test/java/com/vn/traffic/chatbot/chat/api/ChatContractSerializationTest.java` | Locked API field names | VERIFIED | Verifies JSON field presence for response, citation, and source reference DTOs. |

### Key Link Verification

| From | To | Via | Status | Details |
| --- | --- | --- | --- | --- |
| `PublicChatController` | `ChatService.answer` | `POST /api/v1/chat` | WIRED | Controller method delegates directly to `chatService.answer(request.question())`. |
| `ChatClientConfig` | `ChatClient` | `builder(chatModel).build()` | WIRED | Bean factory present; integration test retrieves non-null `ChatClient` from Spring context. |
| `ChatService` | `RetrievalPolicy.buildRequest` | `VectorStore similarity search` | WIRED | `ChatService.answer()` builds bounded request before retrieval; `ChatServiceTest` verifies `buildRequest(question, 5)`. |
| `ChatService` | `CitationMapper` | `toCitations` and `toSources` | WIRED | Retrieved documents become response citations and source references before composition. |
| `ChatService` | `AnswerComposer` | `compose(...)` | WIRED | Final API DTO is always produced through deterministic composition policy. |
| `ChatService` | `ChatPromptFactory` and `ChatClient` | prompt generation and model call | WIRED | Non-refused paths build prompt and parse model JSON into `LegalAnswerDraft`. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| --- | --- | --- | --- | --- |
| `ChatService` | `documents` | `vectorStore.similaritySearch(retrievalPolicy.buildRequest(...))` | Yes — retrieval is driven by real vector-store request path with trusted/approved/active filter in `RetrievalPolicy`. | FLOWING |
| `CitationMapper` | `citations` / `sources` | Retrieved `Document` metadata (`sourceId`, `sourceVersionId`, `origin`, `pageNumber`, `sectionRef`) | Yes — mapper builds user-visible provenance directly from retrieved metadata, not hardcoded values. | FLOWING |
| `AnswerComposer` | `answer`, `legalBasis`, `penalties`, `requiredDocuments`, `procedureSteps`, `nextSteps` | Parsed `LegalAnswerDraft` from model payload plus mapped citations/sources | Yes — grounded and limited paths pass parsed draft into composer; refusal path intentionally emits empty legal sections and fixed refusal text. | FLOWING |
| `PublicChatController` | HTTP response body | `ChatService.answer()` return value | Yes — response serialization is contract-tested and integration-tested. | FLOWING |

### Behavioral Spot-Checks

Using existing session evidence supplied by the caller.

| Behavior | Command | Result | Status |
| --- | --- | --- | --- |
| Controller returns structured chat contract | `./gradlew test --tests "com.vn.traffic.chatbot.chat.api.ChatControllerTest"` | Passed in this session | PASS |
| End-to-end grounded and refused API flow works | `./gradlew test --tests "com.vn.traffic.chatbot.chat.ChatFlowIntegrationTest"` | Passed in this session | PASS |
| Service grounding logic and citation mapping behave as expected | `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.ChatServiceTest" --tests "com.vn.traffic.chatbot.chat.citation.CitationMapperTest"` | Passed in this session | PASS |
| Main sources compile with current wiring | `./gradlew compileJava` | Passed in this session | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| --- | --- | --- | --- | --- |
| `CHAT-01` | 02-02, 02-03 | User can ask a Vietnam traffic-law question in Vietnamese and receive a source-backed answer. | SATISFIED | Public `/api/v1/chat` endpoint, retrieval-backed `ChatService`, grounded/refused integration coverage, and Vietnamese prompt/composition rules. |
| `CHAT-03` | 02-01, 02-02, 02-03 | User can view cited source references used to support an answer. | SATISFIED | `ChatAnswerResponse.citations` and `.sources`; `CitationMapper` preserves provenance; controller/integration tests assert populated citation/source fields. |
| `CHAT-04` | 02-01, 02-03 | User receives a clear disclaimer when the system provides informational legal guidance rather than formal legal advice. | SATISFIED | Locked disclaimer constant and assertions in composer/controller/integration tests. |
| `LEGAL-01` | 02-01, 02-02 | User receives the relevant legal basis for an answer, tied to retrieved source content. | SATISFIED | `legalBasis` field and section rendering; inline citation labels tie legal basis back to mapped sources. |
| `LEGAL-02` | 02-01, 02-02 | User receives likely fine, penalty, or administrative consequence information when relevant to the question. | SATISFIED | `penalties` field and conditional rendering validated by `AnswerComposerTest` and grounded flow examples. |
| `LEGAL-03` | 02-01, 02-02 | User receives required documents, procedure, or compliance steps when relevant to the question. | SATISFIED | `requiredDocuments` and `procedureSteps` fields with conditional section rendering in `AnswerComposer`. |
| `LEGAL-04` | 02-01, 02-02 | User receives recommended next steps based on the described traffic-law situation. | SATISFIED | `nextSteps` field rendered in answer composition and included in test-covered grounded/limited examples. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| --- | --- | --- | --- | --- |
| None | — | No blocker anti-patterns detected in Phase 2 chat files. | — | Grep scan found no TODO/FIXME/placeholder markers in `src/main/java/com/vn/traffic/chatbot/chat/**`. |

### Human Verification Required

None. The phase goal is backend/API deliverable scope on the current branch state, and the available automated evidence is sufficient for the roadmap contract verified here.

### Gaps Summary

No blocking gaps found in the live current repository state. The Phase 2 implementation now delivers retrieval-backed Vietnamese legal Q&A through a public REST API, preserves provenance-backed citations in both inline/source-list forms, enforces disclaimer and refusal behavior, and covers legal basis, penalties, documents/procedure, and next steps with passing contract, unit, controller, integration, and compile evidence.

### Disconfirmation Pass Notes

- Partial-requirement check: no partial roadmap truth remained after tracing controller -> retrieval -> citation -> composition -> response.
- Misleading-test check: `ChatServiceTest` is mock-heavy and does not itself prove full Spring wiring, but that gap is closed by `ChatFlowIntegrationTest`, which exercises real controller/service/client wiring with mocked external dependencies only.
- Uncovered-error-path check: malformed model JSON parsing failure (`IllegalStateException("Failed to parse legal answer draft")`) is not directly test-covered. This is a non-blocking warning for future hardening, not a Phase 2 goal failure.

---

_Verified: 2026-04-08T13:43:55Z_
_Verifier: Claude (gsd-verifier)_
