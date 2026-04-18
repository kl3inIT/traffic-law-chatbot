---
status: partial
phase: 08-structured-output-groundingguardadvisor
source: [08-VERIFICATION.md]
started: 2026-04-18T00:00:00Z
updated: 2026-04-18T00:00:00Z
---

## Current Test

[awaiting human testing — requires OPENROUTER_API_KEY export]

## Tests

### 1. Live 20-query Vietnamese regression (≥95%)
expected: 20-query regression pass rate ≥ 95% via RelevancyEvaluator + FactCheckingEvaluator
result: [pending]
command: `export OPENROUTER_API_KEY=sk-or-...; ./gradlew liveTest --tests "com.vn.traffic.chatbot.chat.regression.VietnameseRegressionIT.twentyQueryRegressionSuiteAtLeast95Percent"`

### 2. Live refusal-rate parity (±10% of Phase 7 baseline)
expected: |observed − Phase7Baseline.REFUSAL_RATE_PERCENT| ≤ 10. Phase7Baseline currently `Double.NaN` (Plan 08-01 Case B fail-loud). Remediation path = backfill real baseline by running 20-query suite against pre-P8 main, NOT relax the assertion.
result: [pending]
command: `./gradlew liveTest --tests "com.vn.traffic.chatbot.chat.regression.VietnameseRegressionIT.refusalRateWithinTenPercentOfPhase7Baseline"`

### 3. Live two-turn conversation memory
expected: Turn-2 answer is non-blank and references turn-1 topic (e.g. `đèn đỏ`, `vượt`, `xe máy`) — proves MessageChatMemoryAdvisor under defaultAdvisors carries context.
result: [pending]
command: `./gradlew liveTest --tests "com.vn.traffic.chatbot.chat.regression.VietnameseRegressionIT.twoTurnConversationMemoryWorks"`

### 4. Cross-model matrix + live IntentClassifierIT
expected: All 8 ModelEntry rows return non-blank ChatAnswerResponse via `.entity(LegalAnswerDraft.class)`; classifier returns LEGAL for a legal question, CHITCHAT for `Xin chào!`, OFF_TOPIC for `Giá Bitcoin hôm nay là bao nhiêu?`. Any 400 on response_format from a YAML-true model → flip that row to false and re-run.
result: [pending]
command: `./gradlew liveTest --tests "com.vn.traffic.chatbot.chat.regression.StructuredOutputMatrixIT" --tests "com.vn.traffic.chatbot.chat.intent.IntentClassifierIT"`

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
