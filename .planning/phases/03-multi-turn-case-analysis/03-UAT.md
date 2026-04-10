---
status: complete
phase: 03-multi-turn-case-analysis
source: [03-01-PLAN.md, 03-02-PLAN.md, 03-03-PLAN.md, 03-VALIDATION.md]
started: 2026-04-10T15:35:00Z
updated: 2026-04-10T15:55:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Thread Creation
expected: POST /api/v1/chat/threads with a Vietnamese traffic question returns HTTP 200, a UUID in `threadId`, `responseMode` set, `disclaimer` present, and Phase 2 fields (`groundingStatus`, `citations`, `sources`) intact.
result: pass
notes: Returned FINAL_ANALYSIS with 4 citations and 4 sources. All Phase 2 fields preserved.

### 2. Thread Continuity (multi-turn)
expected: Sending a follow-up to POST /api/v1/chat/threads/{threadId}/messages returns HTTP 200 with the same `threadId` in the response and a contextually aware reply.
result: pass
notes: Same threadId echoed back. Follow-up processed within thread context.

### 3. Unknown Thread Returns 404-style Error
expected: Posting to a non-existent threadId returns a 4xx ProblemDetail, NOT a 500.
result: pass
notes: Returns HTTP 404 with detail "Chat thread not found: 00000000-0000-0000-0000-000000000000". Clean domain error.

### 4. Clarification-Needed Response
expected: An incomplete scenario (missing vehicleType and violationType) returns `responseMode=CLARIFICATION_NEEDED`, non-empty `pendingFacts`, null `scenarioAnalysis`, and disclaimer present.
result: pass
notes: pendingFacts=['violationType','vehicleType']. scenarioAnalysis=null. Disclaimer present. Behaves exactly as specified.

### 5. Fact Correction — Latest Wins
expected: In a multi-turn thread where Turn 1 states xe máy, Turn 2 corrects to ô tô, Turn 3 should show ô tô as the active vehicleType.
result: fixed
reported: "When the correction message says 'Thực ra tôi đi ô tô, không phải xe máy', the active vehicleType stays as xe máy instead of switching to ô tô. Clean correction phrasing ('Nhầm rồi, tôi đi ô tô') works correctly."
fix: "Added negation guard in FactMemoryService.addMatch() — matches preceded by 'không phải'/'không phải là'/'chứ không phải' within 30 chars are skipped, so only the affirmed value wins."
severity: major

### 6. Clarification Resolves to Final Analysis
expected: After receiving CLARIFICATION_NEEDED, providing explicit vehicleType + violationType in the same thread transitions to a non-clarification responseMode with citations.
result: pass
notes: Turn 1 → CLARIFICATION_NEEDED. Turn 2 "Tôi điều khiển xe máy và bị phát hiện vượt đèn đỏ" → FINAL_ANALYSIS with 4 citations. Correct transition.

### 7. Final Scenario Structure (Facts → Rule → Outcome → Actions → Sources)
expected: A FINAL_ANALYSIS response contains `scenarioAnalysis` with facts, rule, outcome, and actions fields, plus citations and sources.
result: fixed
reported: "scenarioAnalysis structure is present with all required keys (facts, rule, outcome, actions, sources) and citations/sources are non-empty (4 each). However, rule and outcome fields contain fallback placeholder text ('Đối chiếu các nguồn trích dẫn bên dưới...' / 'Chưa thể tổng hợp đầy đủ...') instead of actual grounded legal analysis. The AI model response is not being parsed as the expected JSON LegalAnswerDraft format, triggering the fallback path."
fix: "Added extractJson() in ChatService.parseDraft() — strips markdown code block wrappers (```json...```) and extracts the JSON object by {/} boundaries before parsing, so model responses wrapped in code fences no longer trigger the fallback path."
severity: major

### 8. Phase 2 Single-Turn Regression (/api/v1/chat)
expected: POST /api/v1/chat accepts a standalone question and returns a grounded response with disclaimer, citations, and sources — no threadId required.
result: pass
notes: Returns HTTP 200, groundingStatus=GROUNDED, responseMode=STANDARD, threadId=null, disclaimer present, 4 citations.

## Summary

total: 8
passed: 6
fixed: 2
issues: 0
pending: 0
skipped: 0

## Gaps

- truth: "When a user corrects a fact using a sentence that contains both the old and new vehicle type (e.g. 'không phải xe máy, tôi đi ô tô'), the corrected fact (ô tô) must become active and the old fact (xe máy) must be superseded."
  status: failed
  reason: "User reported: FactMemoryService VEHICLE_PATTERN uses while(matcher.find()) and last-match-wins into the facts map. In the phrase 'không phải xe máy' the regex matches 'xe máy' AFTER matching 'ô tô', so xe máy overwrites ô tô as the stored fact. The negation 'không phải' is not stripped before matching."
  severity: major
  test: 5
  artifacts:
    - src/main/java/com/vn/traffic/chatbot/chat/service/FactMemoryService.java:85-88
  missing:
    - Negation guard in extractExplicitFacts() — strip or skip matches preceded by "không phải" / "không phải là" before storing the value

- truth: "A FINAL_ANALYSIS response must have rule and outcome fields containing actual grounded legal analysis derived from retrieved documents, not fallback placeholder text."
  status: failed
  reason: "User reported: ScenarioAnswerComposer falls back to fallbackDraft() because the AI model returns unstructured plain text rather than the expected JSON matching LegalAnswerDraft. The scenario structure (keys) is present but content is the fallback message. This may indicate the prompt in ChatPromptFactory does not reliably produce structured JSON output."
  severity: major
  test: 7
  artifacts:
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java:57
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/LegalAnswerDraft.java
  missing:
    - Prompt instruction to produce JSON matching LegalAnswerDraft schema
    - Or response format enforcement (e.g. Spring AI structured output / JSON mode)
