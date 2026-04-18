---
phase: 08-structured-output-groundingguardadvisor
verified: 2026-04-18T00:00:00Z
status: human_needed
score: 20/20 must-haves programmatically verified; 4 live-gated truths pending human execution
overrides_applied: 0
human_verification:
  - test: "Run live regression suite: VietnameseRegressionIT (20-query ≥95% via RelevancyEvaluator + FactCheckingEvaluator)"
    expected: "20-query regression pass rate ≥ 95%"
    why_human: "@Tag(\"live\") test is @DisabledIfEnvironmentVariable when OPENROUTER_API_KEY is unset. Live execution requires user-exported API key."
    command: "export OPENROUTER_API_KEY=sk-or-...; ./gradlew liveTest --tests \"com.vn.traffic.chatbot.chat.regression.VietnameseRegressionIT.twentyQueryRegressionSuiteAtLeast95Percent\""
  - test: "Run live refusal-rate parity check (±10% of Phase 7 baseline)"
    expected: "|observed_refusal_rate − Phase7Baseline.REFUSAL_RATE_PERCENT| ≤ 10. NOTE: baseline currently Double.NaN (Plan 08-01 Case B fail-loud). Remediation is to backfill a real baseline by running 20-query suite against pre-P8 main, NOT to relax the assertion."
    why_human: "Live-gated + requires Phase7Baseline backfill decision by developer."
    command: "./gradlew liveTest --tests \"com.vn.traffic.chatbot.chat.regression.VietnameseRegressionIT.refusalRateWithinTenPercentOfPhase7Baseline\""
  - test: "Run live two-turn conversation memory integration test"
    expected: "Turn-2 answer is non-blank and references turn-1 topic (e.g., contains 'đèn đỏ', 'vượt', or 'xe máy') — proves MessageChatMemoryAdvisor under defaultAdvisors carries context."
    why_human: "Live-gated."
    command: "./gradlew liveTest --tests \"com.vn.traffic.chatbot.chat.regression.VietnameseRegressionIT.twoTurnConversationMemoryWorks\""
  - test: "Run cross-model StructuredOutputMatrixIT + live IntentClassifierIT (LEGAL/CHITCHAT/OFF_TOPIC)"
    expected: "All 8 ModelEntry rows return non-blank ChatAnswerResponse via .entity(LegalAnswerDraft.class); classifier returns LEGAL for a legal question, CHITCHAT for 'Xin chào!', OFF_TOPIC for 'Giá Bitcoin hôm nay là bao nhiêu?'. Any 400 on response_format from a YAML-true model → flip that row to false and re-run."
    why_human: "Live-gated; all 4 tests sit behind OPENROUTER_API_KEY + @DisabledIfEnvironmentVariable."
    command: "./gradlew liveTest --tests \"com.vn.traffic.chatbot.chat.regression.StructuredOutputMatrixIT\" --tests \"com.vn.traffic.chatbot.chat.intent.IntentClassifierIT\""
---

# Phase 8: Structured Output + GroundingGuardAdvisor — Verification Report

**Phase Goal:** Chat responses use native structured output and refusal/chitchat policy is encapsulated in an advisor pair; no hardcoded Vietnamese keyword heuristic drives grounding decisions.
**Verified:** 2026-04-18
**Status:** human_needed (all code-level must-haves VERIFIED; live-execution truths pending human-action gate per 08-04-SUMMARY.md)
**Re-verification:** No — initial verification.

## Goal Achievement

### Observable Truths — Roadmap Success Criteria

| # | Roadmap Success Criterion | Status | Evidence |
|---|---------------------------|--------|----------|
| SC1 | ChatService produces LegalAnswerDraft via .entity(LegalAnswerDraft.class); legacy parseDraft/extractJson/fallbackDraft/markdown-fence deleted | ✓ VERIFIED | `ChatService.java:178` contains `draft = spec.call().entity(LegalAnswerDraft.class)`; grep of 9 legacy identifiers (parseDraft\|extractJson\|fallbackDraft\|CHITCHAT_PATTERN\|isGreetingOrChitchat\|containsAnyLegalCitation\|looksLikeLegalCitation\|containsLegalSignal\|hasLegalCitation) in ChatService.java returns 0 matches. ChatServiceDeletionArchTest guards this at CI. |
| SC2 | Per-model supportsStructuredOutput flag governs native vs prompt-instruction mode; cross-model matrix test exists for 8 models | ✓ VERIFIED | AiModelProperties.ModelEntry has 5th arg `boolean supportsStructuredOutput` (line 35). application.yaml has 8 `supports-structured-output:` entries (7 true, 1 false for deepseek/deepseek-v3.2). ChatService conditionally attaches `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` based on this flag (line 164). StructuredOutputMatrixIT iterates `properties.models()` (line 51) — all 8 rows. |
| SC3 | GroundingGuardInputAdvisor + GroundingGuardOutputAdvisor pair owns refusal decision; MessageChatMemoryAdvisor attached via defaultAdvisors(...) not per-call | ✓ VERIFIED | Both advisor classes exist under `chat/advisor/` with correct orders (HIGHEST_PRECEDENCE+100, LOWEST_PRECEDENCE-100). `ChatClientConfig.java:99-101` wires `MessageChatMemoryAdvisor.builder(chatMemory).build()` inside `.defaultAdvisors(...)`. ChatService uses only `a.param(ChatMemory.CONVERSATION_ID, …)` — no per-call `.advisors(memoryAdvisor)` attach. |
| SC4 | LLM intent classifier (.entity(IntentDecision.class)) short-circuits small talk; zero keyword-list references in production grounding paths | ✓ VERIFIED | IntentClassifier.java:46 calls `.entity(IntentDecision.class)`; IntentDecision nests `enum Intent { CHITCHAT, LEGAL, OFF_TOPIC }`. ChatService.doAnswer line 90 dispatches via `intentClassifier.classify(...)` BEFORE retrieval. NoKeywordGateArchTest guards 15 Vietnamese legal-keyword literals outside chat/intent/. |
| SC5 | 20-query Vietnamese regression suite + refusal-rate check ±10% of Phase 7 baseline + two-turn conversation memory test | ⚠ CODE VERIFIED / LIVE EXECUTION PENDING | All three test methods exist in `VietnameseRegressionIT` (`twentyQueryRegressionSuiteAtLeast95Percent` line 82, `refusalRateWithinTenPercentOfPhase7Baseline` line 121, `twoTurnConversationMemoryWorks` line 142); fixture has 20 `question:` entries; RelevancyEvaluator + FactCheckingEvaluator wired via isolated evaluator ChatClient.Builder. Live execution deferred to human-action gate per 08-04-SUMMARY; baseline `Double.NaN` is INTENTIONAL (fail-loud per Plan 08-01 Case B). |

**Score (code-level):** 5/5 roadmap SCs programmatically verifiable; SC5 live-execution deferred to human.

### Must-Haves from PLAN Frontmatter

| Source | Must-Have | Status | Evidence |
|--------|-----------|--------|----------|
| 08-01 | 5-arg ModelEntry with boolean supportsStructuredOutput | ✓ | AiModelProperties.java:35 |
| 08-01 | @Tag("live") excluded from default `./gradlew test` | ✓ | build.gradle:79 `excludeTags 'live'` + 88 `includeTags 'live'` under `liveTest` |
| 08-01 | spring-ai-test dep via BOM (no hardcoded version) | ✓ | build.gradle:49 `testImplementation 'org.springframework.ai:spring-ai-test'` |
| 08-01 | 20-query fixture loadable from classpath | ✓ | vietnamese-queries-20.yaml has 20 `question:` entries |
| 08-01 | Phase7Baseline.REFUSAL_RATE_PERCENT exists | ✓ | Phase7Baseline.java:29 — `Double.NaN` (Case B, intentional per contract) |
| 08-02 | GroundingGuardInputAdvisor + GroundingGuardOutputAdvisor Spring beans impl CallAdvisor | ✓ | Files exist under chat/advisor/, orders HIGHEST+100 / LOWEST-100 |
| 08-02 | 3 NoOp placeholders pass through via chain.nextCall(req) | ✓ | 3 files under chat/advisor/placeholder/; each carries 1 `return chain.nextCall(` |
| 08-02 | IntentClassifier returns IntentDecision(LEGAL, 0.0) on exception | ✓ | IntentClassifier.java has 2 `new IntentDecision(IntentDecision.Intent.LEGAL, 0.0)` — null-guard + catch branches |
| 08-02 | ChatClientConfig attaches 6 advisors via defaultAdvisors(...) in order | ✓ | ChatClientConfig.java:99-101 defaultAdvisors(guardIn, MessageChatMemoryAdvisor, noOpRag, noOpCache, noOpValidation, guardOut) |
| 08-02 | Advisor orders unique: +100, +200, +300, +500, +1000, LP-100 | ✓ | Grep of HIGHEST_PRECEDENCE/LOWEST_PRECEDENCE in chat/advisor/ confirms 5 distinct values + Memory at +200 via builder |
| 08-02 | LegalAnswerDraft @JsonClassDescription + @JsonPropertyDescription (8 fields) | ✓ | Plan 08-02 SUMMARY verified 8 annotations; test suite green |
| 08-03 | ChatService.doAnswer calls IntentClassifier.classify BEFORE advisor chain | ✓ | ChatService.java:90 |
| 08-03 | ChatService.doAnswer obtains LegalAnswerDraft via .entity(...) — no manual JSON parse | ✓ | ChatService.java:178 + 0 matches for ObjectMapper/parseDraft/extractJson |
| 08-03 | All 9 deleted identifiers absent from ChatService.java | ✓ | grep returns 0; ChatServiceDeletionArchTest enforces |
| 08-03 | AnswerComposer.composeOffTopicRefusal() exists | ✓ | AnswerComposer.java:64 + OFF_TOPIC_TEMPLATE constant line 25 |
| 08-03 | Zero Vietnamese legal-keyword literals outside intent/ | ✓ | NoKeywordGateArchTest enforces (in non-live test suite — green) |
| 08-03 | Conditional AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT | ✓ | ChatService.java:164 guarded by `entry.supportsStructuredOutput()` |
| 08-03 | Per-call memory via a.param(ChatMemory.CONVERSATION_ID, …) — NOT .advisors(memoryAdvisor) | ✓ | ChatService.java:171; MessageChatMemoryAdvisor only imported/used in ChatClientConfig |
| 08-04 | VietnameseRegressionIT 3 live tests (20-query, refusal, two-turn) exist & compile | ✓ | All 3 methods present; `./gradlew compileTestJava` passes |
| 08-04 | StructuredOutputMatrixIT iterates all 8 ModelEntry rows | ✓ | StructuredOutputMatrixIT.java:51 `for (AiModelProperties.ModelEntry entry : properties.models())` |

### Required Artifacts

| Artifact | Expected | Status |
|----------|----------|--------|
| src/main/java/.../AiModelProperties.java | 5-arg ModelEntry | ✓ VERIFIED (exists, substantive, wired) |
| src/main/java/.../advisor/GroundingGuardInputAdvisor.java | CallAdvisor @ HIGHEST+100 | ✓ VERIFIED |
| src/main/java/.../advisor/GroundingGuardOutputAdvisor.java | CallAdvisor @ LOWEST-100 | ✓ VERIFIED |
| src/main/java/.../advisor/placeholder/NoOp{Retrieval,PromptCache,Validation}Advisor.java | 3 pass-through advisors | ✓ VERIFIED (all 3 contain `return chain.nextCall(`) |
| src/main/java/.../intent/IntentClassifier.java | @Service w/ .entity(IntentDecision.class) + D-02 fail-LEGAL | ✓ VERIFIED |
| src/main/java/.../intent/IntentDecision.java | record w/ Intent enum | ✓ VERIFIED |
| src/main/java/.../service/LegalAnswerDraft.java | 8 Jackson field annotations, no required=true | ✓ VERIFIED (08-02 SUMMARY grep evidence) |
| src/main/java/.../config/ChatClientConfig.java | defaultAdvisors(...) 6-advisor wiring | ✓ VERIFIED |
| src/main/java/.../service/ChatService.java | Rewritten, .entity(LegalAnswerDraft.class), intent dispatch | ✓ VERIFIED (302 LOC down from 389) |
| src/main/java/.../service/AnswerComposer.java | composeOffTopicRefusal() | ✓ VERIFIED |
| src/test/java/.../archunit/NoKeywordGateArchTest.java | Grep-based guard | ✓ VERIFIED (non-live test suite green) |
| src/test/java/.../archunit/ChatServiceDeletionArchTest.java | Grep-based guard | ✓ VERIFIED |
| src/test/java/.../regression/VietnameseRegressionIT.java | 3 live tests | ✓ VERIFIED (code); ⚠ live execution pending |
| src/test/java/.../regression/StructuredOutputMatrixIT.java | 8-model iteration | ✓ VERIFIED (code); ⚠ live execution pending |
| src/test/java/.../regression/Phase7Baseline.java | REFUSAL_RATE_PERCENT constant | ✓ VERIFIED (Double.NaN — Case B by design) |
| src/test/resources/regression/vietnamese-queries-20.yaml | 20 queries | ✓ VERIFIED (grep -c "question:" → 20) |
| src/test/java/.../intent/IntentClassifierIT.java | 3 live classifier tests | ✓ VERIFIED (code); ⚠ live execution pending |
| src/test/java/.../intent/IntentClassifierTest.java | D-02 unit coverage | ✓ VERIFIED (green in ./gradlew test) |

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| ChatClientConfig.chatClientMap | 6-advisor chain | `.defaultAdvisors(guardIn, MessageChatMemoryAdvisor…, noOpRag, noOpCache, noOpValidation, guardOut)` | ✓ WIRED (line 99-101) |
| ChatService.doAnswer | IntentClassifier.classify | Injected @Service dependency (constructor), invoked line 90 | ✓ WIRED |
| ChatService.doAnswer | LegalAnswerDraft via .entity(...) | `spec.call().entity(LegalAnswerDraft.class)` line 178 | ✓ WIRED |
| ChatService OFF_TOPIC branch | AnswerComposer.composeOffTopicRefusal | direct invocation line 103 | ✓ WIRED |
| IntentClassifier.classify | IntentDecision record | `.entity(IntentDecision.class)` line 46 | ✓ WIRED |
| catch (Exception e) | IntentDecision(LEGAL, 0.0) | D-02 failure policy | ✓ WIRED (2 occurrences: null-guard + catch) |
| VietnameseRegressionIT | RelevancyEvaluator + FactCheckingEvaluator | Built via evaluatorChatClientBuilder nested @TestConfiguration | ✓ WIRED |
| StructuredOutputMatrixIT | all 8 ModelEntry rows | `for (ModelEntry e : properties.models())` | ✓ WIRED |
| two-turn memory test | MessageChatMemoryAdvisor via defaultAdvisors | same conversationId on two successive `chatService.answer(...)` calls | ✓ WIRED (code); live validation pending |

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| ARCH-02 | 08-01, 08-03, 08-04 | `.entity(LegalAnswerDraft.class)` via BeanOutputConverter replaces parseDraft/extractJson/fallbackDraft; cross-model matrix passes | ✓ SATISFIED (code); live matrix pending | ChatService.java:178 + ChatServiceDeletionArchTest + StructuredOutputMatrixIT |
| ARCH-03 | 08-01, 08-02, 08-03, 08-04 | LLM intent classifier + score-threshold grounding replace keyword list; containsAnyLegalCitation + keyword list deleted | ✓ SATISFIED | IntentClassifier.java + NoKeywordGateArchTest + ChatServiceDeletionArchTest (all green) |
| ARCH-04 | 08-01, 08-02, 08-03, 08-04 | GroundingGuardInput/OutputAdvisor pair owns refusal policy; togglable via config without editing ChatService | ✓ SATISFIED | Both advisor beans exist under chat/advisor/; ChatClientConfig.defaultAdvisors(...) wires them; AnswerComposer.composeOffTopicRefusal/composeRefusal encapsulate wording; ChatService.doAnswer emits no literal refusal strings directly |

No orphaned requirements: all 3 phase requirement IDs (ARCH-02, ARCH-03, ARCH-04) are covered by ≥1 plan.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| Phase7Baseline.java | 29 | `public static final double REFUSAL_RATE_PERCENT = Double.NaN;` | ℹ Info (INTENTIONAL) | Fail-loud contract per Plan 08-01 Case B — live parity test fails until backfilled. Documented extensively. NOT a defect. |
| ChatService.java | 179-186 | try/catch around .entity() routing to refusal | ℹ Info | Rule-2 auto-fix documented in 08-03 SUMMARY — routes parse failures to grounding refusal (D-06). |

No blockers. No hidden stubs. No disconnected props. No hardcoded empty fixtures.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Non-live test suite compiles & passes | `./gradlew test --no-daemon` | BUILD SUCCESSFUL (5 tasks UP-TO-DATE) | ✓ PASS |
| ArchUnit deletion guard runs in default suite | part of `./gradlew test` | Green (confirmed via BUILD SUCCESSFUL) | ✓ PASS |
| Fixture has 20 entries | `grep -c "question:" src/test/resources/regression/vietnamese-queries-20.yaml` | 20 | ✓ PASS |
| Matrix IT iterates `properties.models()` | grep | Match at StructuredOutputMatrixIT.java:51 | ✓ PASS |
| Live tests correctly skip without API key | `./gradlew test` green despite @Tag("live") classes present | Yes (build succeeded) | ✓ PASS |

### Human Verification Required

See YAML frontmatter `human_verification:` section. Four live-gated tests require `OPENROUTER_API_KEY` and are intentionally skipped by default:

1. **VietnameseRegressionIT.twentyQueryRegressionSuiteAtLeast95Percent** — 20-query pass rate via RelevancyEvaluator + FactCheckingEvaluator.
2. **VietnameseRegressionIT.refusalRateWithinTenPercentOfPhase7Baseline** — parity ±10%. NOTE: baseline is `Double.NaN` by design (Plan 08-01 Case B); this test is expected to fail loud until Phase7Baseline is backfilled with a real measurement. Backfill, don't relax.
3. **VietnameseRegressionIT.twoTurnConversationMemoryWorks** — two-turn memory via defaultAdvisors MessageChatMemoryAdvisor.
4. **StructuredOutputMatrixIT.allEightCatalogedModelsReturnLegalAnswerDraft** + **IntentClassifierIT (3 tests)** — cross-model + live classifier.

### Gaps Summary

No code-level gaps. All roadmap SCs, plan must-haves, artifacts, key links, and requirement IDs are satisfied at the code level. Remaining work is a deliberate human-action gate for live-tagged integration tests — documented explicitly in 08-04-SUMMARY.md. The Phase7Baseline `Double.NaN` is an intentional fail-loud contract, not a defect.

---

_Verified: 2026-04-18_
_Verifier: Claude (gsd-verifier)_
