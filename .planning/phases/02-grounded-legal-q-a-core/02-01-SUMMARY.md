---
phase: 02-grounded-legal-q-a-core
plan: 01
subsystem: chat
status: complete
tags: [phase-2, chat, dto, grounding, composition]
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatQuestionRequest.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/ChatAnswerResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/CitationResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/api/dto/SourceReferenceResponse.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/GroundingStatus.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/LegalAnswerDraft.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerCompositionPolicy.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/api/ChatContractSerializationTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java
    - src/main/java/com/vn/traffic/chatbot/common/error/ErrorCode.java
decisions:
  - Locked D-01 through D-08 into concrete DTO fields and enum values before any Spring AI wiring.
  - GroundingStatus enum uses three states — GROUNDED, LIMITED_GROUNDING, REFUSED — per research recommendation.
  - DEFAULT_DISCLAIMER and REFUSAL_MESSAGE pinned as exact Vietnamese string constants in AnswerCompositionPolicy so wording drift is caught by tests.
  - Section suppression is deterministic in AnswerComposer — no placeholders, no model-invented fallback text.
metrics:
  completed_date: 2026-04-08
  task_commits:
    - 093c123
    - 2f2395b
---

# Phase 02 Plan 01: Answer Contract & Composition Policy Summary

The Phase 2 public answer contract is frozen and the deterministic composition policy is implemented and test-backed, enabling later plans to wire Spring AI without inventing or changing response shape.

## Task Outcomes

### Task 1 — Complete: Freeze the public Phase 2 answer contract and grounding states
- Added `ApiPaths.CHAT = "/api/v1/chat"` to `src/main/java/com/vn/traffic/chatbot/common/api/ApiPaths.java`
- Added `CHAT_REQUEST_INVALID`, `CHAT_GROUNDING_INSUFFICIENT`, `CHAT_RESPONSE_INVALID` to `ErrorCode`
- Created `ChatQuestionRequest` with `@NotBlank` and `@Size(max = 4000)` on `question`
- Created `ChatAnswerResponse` with all 12 required fields including grounding status, answer, disclaimer, citations, sources, and all legal sections
- Created `CitationResponse` with `inlineLabel`, `sourceId`, `sourceVersionId`, `sourceTitle`, `origin`, `pageNumber`, `sectionRef`, `excerpt`
- Created `SourceReferenceResponse` for the dedicated end-of-answer source list (D-03, D-04)
- Created `GroundingStatus` enum: `GROUNDED`, `LIMITED_GROUNDING`, `REFUSED`
- Created `LegalAnswerDraft` record as the internal answer payload
- Created `ChatContractSerializationTest` locking field names via Jackson `ObjectMapper`
- Commit: `093c123` — `feat(02-01): freeze public grounded answer contract`

### Task 2 — Complete: Implement deterministic answer composition and section suppression policy
- Created `AnswerCompositionPolicy` with exact Vietnamese string constants:
  - `DEFAULT_DISCLAIMER = "Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức."`
  - `REFUSAL_MESSAGE` (full Vietnamese refusal text)
  - `LIMITED_NOTICE` (uncertainty notice for limited grounding)
- Created `AnswerComposer.compose(GroundingStatus, LegalAnswerDraft, citations, sources)` enforcing:
  - Conclusion-first ordering per D-02
  - Section suppression (null/empty lists omit the section, no placeholders) per D-01
  - REFUSED path returns refusal message with empty legal sections per D-05/D-06
  - Vietnamese section labels: `Kết luận`, `Căn cứ pháp lý`, `Mức phạt hoặc hậu quả`, etc. per D-07
- Created `AnswerComposerTest` with 5 behavior assertions covering GROUNDED, LIMITED_GROUNDING, REFUSED, empty-section suppression, and disclaimer wording
- Commit: `2f2395b` — `feat(02-01): add deterministic grounded answer composer`

## Deviations from Plan

None recorded.

## Verification

Passed:
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.api.ChatContractSerializationTest"`
- `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.AnswerComposerTest"`
- `./gradlew compileJava`

## Known Stubs

None — all plan must-haves delivered.

## Self-Check: PASSED
- `ApiPaths.CHAT = "/api/v1/chat"` present
- `GroundingStatus` contains GROUNDED, LIMITED_GROUNDING, REFUSED
- `AnswerCompositionPolicy` contains exact Vietnamese disclaimer string
- `AnswerComposer.compose` present with Vietnamese section labels
- Both serialization and composer tests pass
