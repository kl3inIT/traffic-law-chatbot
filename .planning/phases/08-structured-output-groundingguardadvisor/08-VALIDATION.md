---
phase: 08
slug: structured-output-groundingguardadvisor
status: draft
nyquist_compliant: false
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

*Populated by planner (step 8). Each task row maps to REQ-ID + test command + file artifact. Planner must ensure no three consecutive tasks lack an automated verify.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 0 | ARCH-02/03/04 | — | N/A | wave-0-stubs | `./gradlew compileTestJava` | ❌ W0 | ⬜ pending |

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

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Refusal-rate drift within 10% of Phase-7 baseline | ARCH-04 (SC 5) | Live prod-like traffic needed to measure drift; no recorded fixtures per CONTEXT.md D-05a | Run full 20-query regression with `OPENROUTER_API_KEY`; compare refusal count to recorded P7 baseline (informal smoke log) |
| Advisor chain order logged once at startup | ARCH-02 | Single-shot log line; easier to eyeball than assert | After Spring boot: grep app log for `Advisor chain order:` and confirm `GuardIn → Memory → NoOpRAG → NoOpCache → NoOpValidation → GuardOut` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (advisor tests, intent test, regression IT, matrix IT, fixture yaml)
- [ ] No watch-mode flags (`--watch`, `-i`, gradle `--continuous`) in any plan command
- [ ] Feedback latency < 60s for non-live path
- [ ] `nyquist_compliant: true` set in frontmatter after planner populates per-task map

**Approval:** pending
