---
phase: 2
slug: grounded-legal-q-a-core
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-08
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + AssertJ + Mockito |
| **Config file** | `build.gradle` |
| **Quick run command** | `./gradlew test --tests "*Chat*Test" --tests "*Retrieval*Test" --tests "*Citation*Test"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*Chat*Test" --tests "*Retrieval*Test" --tests "*Citation*Test"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 2-01-01 | 01 | 0 | CHAT-01, CHAT-04 | T-2-01 / T-2-03 | Chat flow returns grounded answer or refusal with disclaimer path | unit | `./gradlew test --tests "*ChatServiceTest" --tests "*ChatControllerTest"` | ❌ W0 | ⬜ pending |
| 2-01-02 | 01 | 0 | CHAT-03 | T-2-02 | Citations come from retrieved metadata, not model prose | unit | `./gradlew test --tests "*CitationMapperTest"` | ❌ W0 | ⬜ pending |
| 2-01-03 | 01 | 0 | LEGAL-01, LEGAL-02, LEGAL-03, LEGAL-04 | T-2-03 | Legal sections appear only when grounded by retrieved evidence | unit | `./gradlew test --tests "*AnswerComposerTest"` | ❌ W0 | ⬜ pending |
| 2-02-01 | 02 | 1 | CHAT-01, CHAT-03, CHAT-04 | T-2-01 / T-2-02 / T-2-03 | Public chat endpoint validates requests and returns grounded response envelope | controller slice | `./gradlew test --tests "*ChatControllerTest"` | ❌ W0 | ⬜ pending |
| 2-02-02 | 02 | 1 | CHAT-01 | T-2-01 | Retrieval path preserves approved/trusted/active source gating | unit | `./gradlew test --tests "*Retrieval*Test" --tests "*ChatServiceTest"` | ❌ W0 | ⬜ pending |
| 2-03-01 | 03 | 1 | CHAT-01, CHAT-03, LEGAL-01, LEGAL-02, LEGAL-03, LEGAL-04 | T-2-01 / T-2-02 / T-2-03 | End-to-end chat orchestration returns structured grounded answer with citations | integration | `./gradlew test --tests "*ChatFlowIntegrationTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java` — grounded answer, refusal behavior, disclaimer inclusion, and section suppression for unsupported claims
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java` — request validation, response contract shape, and ProblemDetail behavior for bad requests
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/citation/CitationMapperTest.java` — mapping from retrieved metadata to citation DTOs
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerTest.java` — legal-basis/penalty/procedure/next-step field population rules
- [ ] `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java` — end-to-end chat wiring with mocked model/retrieval boundary or Spring test configuration

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Vietnamese legal wording feels plain + formal while staying non-authoritative | CHAT-04, LEGAL-04 | Tone quality is partly subjective | Submit 3 representative Vietnamese questions and verify answers stay respectful, easy to understand, and include informational-guidance disclaimer |
| Citation labels are understandable to end users and useful for admin verification | CHAT-03 | Readability of citation rendering needs human judgment | Ask a grounded question, inspect inline citations and source list, and confirm source references include enough provenance/location detail to trace back to retrieved content |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
