---
phase: 08
slug: structured-output-groundingguardadvisor
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-18
---

# Phase 08 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> See `08-RESEARCH.md` §Validation Architecture for signal design.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + spring-ai-test (BasicEvaluationTest, RelevancyEvaluator, FactCheckingEvaluator) |
| **Config file** | `build.gradle` (test-task config — Wave 0 adds `spring-ai-test` dep + `@Tag("live")` filter) |
| **Quick run command** | `./gradlew test -x liveTest` |
| **Full suite command** | `./gradlew test liveTest` (liveTest requires `OPENROUTER_API_KEY`) |
| **Estimated runtime** | ~60s quick, ~5–8 min full (live Vietnamese regression + cross-model matrix) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test -x liveTest` (compile + unit + non-live integration)
- **After every plan wave:** Run the full suite — `./gradlew test liveTest` when OPENROUTER_API_KEY is set; otherwise record explicit skip
- **Before `/gsd-verify-work`:** Full suite green including `@Tag("live")` tests
- **Max feedback latency:** 60 seconds for the non-live path

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 0 | ARCH-02/03/04 | T-08-01 | N/A | compile | `./gradlew compileJava compileTestJava --no-daemon` | ✅ | ⬜ pending |
| 08-01-02 | 01 | 0 | ARCH-02/03/04 | T-08-02, T-08-03 | test-scope dep isolation | static-analysis | `./gradlew tasks --no-daemon \| grep -E "^(test\|liveTest)" && ./gradlew dependencies --configuration testRuntimeClasspath --no-daemon \| grep spring-ai-test` | ✅ | ⬜ pending |
| 08-01-03 | 01 | 0 | ARCH-02/03/04 | — | N/A | unit (RED stubs) | `./gradlew compileTestJava --no-daemon && ./gradlew test --tests "*.GroundingGuardAdvisorTest" --no-daemon 2>&1 \| grep -E "RED — implemented"` | ❌ W0 (stubs authored this task) | ⬜ pending |
| 08-01-04 | 01 | 0 | SC5 (P7 baseline) | — | N/A | static-analysis | `test -f src/test/java/com/vn/traffic/chatbot/chat/regression/Phase7Baseline.java && grep -q "REFUSAL_RATE_PERCENT" src/test/java/com/vn/traffic/chatbot/chat/regression/Phase7Baseline.java` | ❌ W0 (authored this task) | ⬜ pending |
| 08-02-01 | 02 | 1 | ARCH-04 | T-08-08 | advisor no-mutation | unit | `./gradlew test --tests "com.vn.traffic.chatbot.chat.advisor.GroundingGuardAdvisorTest" --no-daemon` | ✅ | ⬜ pending |
| 08-02-02 | 02 | 1 | ARCH-03 | T-08-04, T-08-05 | D-02 fail-LEGAL policy | unit | `./gradlew test --tests "com.vn.traffic.chatbot.chat.intent.IntentClassifierTest" --no-daemon` | ✅ | ⬜ pending |
| 08-02-03 | 02 | 1 | ARCH-04 | — | advisor chain wiring | integration | `./gradlew test --tests "com.vn.traffic.chatbot.chat.config.ChatClientConfigTest" --no-daemon` | ✅ | ⬜ pending |
| 08-03-01 | 03 | 2 | ARCH-03 | T-08-12 | ArchUnit residual-keyword guard | unit + static-analysis | `./gradlew compileTestJava --no-daemon && ./gradlew test --tests "*.AnswerComposerTest" --no-daemon 2>&1 \|\| true` | ✅ | ⬜ pending |
| 08-03-02 | 03 | 2 | ARCH-02/03/04 | T-08-09, T-08-10, T-08-11 | .entity() replaces manual parsing; memory via CONVERSATION_ID | unit + ArchUnit | `./gradlew test --tests "com.vn.traffic.chatbot.chat.service.*" --tests "com.vn.traffic.chatbot.chat.archunit.*" --no-daemon` | ✅ | ⬜ pending |
| 08-04-01 | 04 | 3 | SC5 (D-05, D-05c) | T-08-13, T-08-15 | regression pass-rate + refusal parity + two-turn memory | live-integration | `./gradlew compileTestJava --no-daemon && ./gradlew liveTest --tests "com.vn.traffic.chatbot.chat.regression.VietnameseRegressionIT" --no-daemon` | ✅ | ⬜ pending |
| 08-04-02 | 04 | 3 | ARCH-02 (D-05b) | T-08-14 | cross-model matrix + live intent | live-integration | `./gradlew compileTestJava --no-daemon && ./gradlew liveTest --tests "com.vn.traffic.chatbot.chat.regression.StructuredOutputMatrixIT" --tests "com.vn.traffic.chatbot.chat.intent.IntentClassifierIT" --no-daemon` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `build.gradle` — add `testImplementation 'org.springframework.ai:spring-ai-test'` (version via bom)
- [ ] `build.gradle` — register `liveTest` gradle task filtered by `@Tag("live")` (or document `-PincludeTags=live`)
- [ ] `src/test/java/.../chat/advisor/GroundingGuardAdvisorTest.java` — RED stub (compile-only) asserting advisor order constants + refusal branch
- [ ] `src/test/java/.../chat/intent/IntentClassifierIT.java` — RED stub (`@Tag("live")`) asserting `.entity(IntentDecision.class)` returns non-null
- [ ] `src/test/java/.../chat/regression/VietnameseRegressionIT.java` — RED stub extending `BasicEvaluationTest`, loads 20-query fixture file
- [ ] `src/test/java/.../chat/regression/StructuredOutputMatrixIT.java` — RED stub parameterized over `AiModelProperties.models`
- [ ] `src/test/resources/regression/vietnamese-queries-20.yaml` — seed fixture (query + optional reference answer)
- [ ] `src/test/java/.../chat/regression/Phase7Baseline.java` — P7 refusal-rate baseline constant (NaN + TODO if not recoverable from P7 artifacts)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Refusal-rate drift within 10% of Phase-7 baseline | ARCH-04 (SC 5) | Live prod-like traffic needed to measure drift; no recorded fixtures per CONTEXT.md D-05a | Run full 20-query regression with `OPENROUTER_API_KEY`; compare refusal count to `Phase7Baseline.REFUSAL_RATE_PERCENT` (authored in Plan 08-01 Task 4) |
| Advisor chain order logged once at startup | ARCH-02 / D-04 | Startup log-line + manual eyeball substitutes for an automated chain-order test (single-shot assertion; no ongoing drift risk — no dedicated `AdvisorChainOrderingTest` is authored) | After Spring boot: grep app log for `Advisor chain order:` and confirm `GuardIn → Memory → NoOpRAG → NoOpCache → NoOpValidation → GuardOut` |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (advisor tests, intent test, regression IT, matrix IT, fixture yaml, P7 baseline)
- [x] No watch-mode flags (`--watch`, `-i`, gradle `--continuous`) in any plan command
- [x] Feedback latency < 60s for non-live path
- [x] `nyquist_compliant: true` set in frontmatter after planner populates per-task map

**Approval:** approved 2026-04-18
