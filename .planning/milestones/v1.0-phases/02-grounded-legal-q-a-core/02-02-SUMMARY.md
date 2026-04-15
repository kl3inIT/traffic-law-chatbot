---
phase: 02-grounded-legal-q-a-core
plan: 02
subsystem: chat
status: complete
tags: [phase-2, chat, citation, retrieval, orchestration]
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/chat/citation/CitationMapper.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/PromptSectionRules.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    - src/test/java/com/vn/traffic/chatbot/chat/citation/CitationMapperTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java
decisions:
  - Citations are built exclusively from retrieved Document metadata — model prose is never used as a citation source per D-03/D-04.
  - Grounding thresholds: 0 docs → REFUSED, 1–2 docs → LIMITED_GROUNDING, ≥3 docs → GROUNDED.
  - All retrieval goes through RetrievalPolicy.buildRequest(question, 5) — no second filter string hardcoded.
  - Duplicate source chunks collapse to one SourceReferenceResponse entry in toSources() while keeping unique inline citations.
metrics:
  completed_date: 2026-04-08
  task_commits:
    - 99c4d17
    - 2b61046
---

# Phase 02 Plan 02: Retrieval-Backed Chat Orchestration Core Summary

The grounded chat orchestration core is implemented: citations are metadata-derived, retrieval is gated through the approved vector-store policy, and all three grounding branches (grounded, limited, refused) are wired and test-covered.

## Task Outcomes

### Task 1 — Complete: Map retrieved metadata into inline citations and dedicated source references
- Created `CitationMapper` under `com.vn.traffic.chatbot.chat.citation` with:
  - `List<CitationResponse> toCitations(List<Document> documents)` — sequential `Nguồn 1`, `Nguồn 2`, ... labels from metadata
  - `List<SourceReferenceResponse> toSources(List<CitationResponse> citations)` — de-duplicated source list
  - `excerpt` = first 280 chars of `Document.getText()` with normalized whitespace
  - `pageNumber` accepts both numeric and string metadata forms
- Created `CitationMapperTest` covering: metadata → CitationResponse mapping, SourceReferenceResponse mapping, null pageNumber handling, duplicate-source collapse
- Commit: `99c4d17` — `feat(02-02): add citation metadata mapper`

### Task 2 — Complete: Build retrieval-backed chat orchestration with grounded, limited, and refused paths
- Created `PromptSectionRules` with:
  - `SECTION_ORDER = List.of("Kết luận", "Căn cứ pháp lý", "Mức phạt hoặc hậu quả", "Giấy tờ hoặc thủ tục", "Các bước nên làm tiếp", "Lưu ý")`
  - `SUPPORTED_SECTION_NAMES` set of internal field names
- Created `ChatPromptFactory.buildPrompt(question, groundingStatus, citations)` with explicit D-01 through D-08 prompt instructions: Vietnamese formal tone, conclusion-first, conditional sections, inline `[Nguồn n]` citation labels, no unsupported legal claims
- Created `ChatService` with constructor-injected `ChatClient`, `VectorStore`, `ObjectMapper`, `RetrievalPolicy`, `CitationMapper`, `AnswerComposer`, `ChatPromptFactory`
- Implemented `ChatAnswerResponse answer(String question)` with exact three-branch grounding logic:
  1. Retrieve via `retrievalPolicy.buildRequest(question, 5)`
  2. REFUSED if 0 docs (bypasses model)
  3. LIMITED_GROUNDING if 1–2 docs; GROUNDED if ≥3 docs
  4. Parse model JSON response into `LegalAnswerDraft` via Jackson
  5. Compose final DTO via `AnswerComposer`
- Created `ChatServiceTest` with mocked dependencies proving all three branches and verifying `buildRequest(question, 5)` usage
- Commit: `2b61046` — `feat(02-02): add grounded chat orchestration core`

## Deviations from Plan

None recorded.

## Verification

Passed:
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.citation.CitationMapperTest"`
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.ChatServiceTest"`
- `./gradlew compileJava`
- `grep "buildRequest(question, 5)" ChatService.java` — confirmed
- `grep "Nguồn" CitationMapper.java` — confirmed

## Known Stubs

None — all plan must-haves delivered.

## Self-Check: PASSED
- `CitationMapper.toCitations` and `toSources` present with `Nguồn` labels
- `ChatService` contains all three grounding branches
- `ChatPromptFactory` contains Vietnamese-tone instructions
- `ChatServiceTest` verifies `buildRequest(question, 5)`
