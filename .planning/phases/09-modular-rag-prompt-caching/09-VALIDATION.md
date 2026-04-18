---
phase: 9
slug: modular-rag-prompt-caching
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-18
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Spring AI Test (`spring-ai-test`) |
| **Config file** | `build.gradle` (springAiVersion=2.0.0-M4) |
| **Quick run command** | `./gradlew test --tests "*LegalQueryAugmenterTest*"` |
| **Full suite command** | `./gradlew test` |
| **Live suite (gated)** | `./gradlew test -PincludeTags=live` (requires `OPENROUTER_API_KEY`) |
| **Estimated runtime** | ~60s unit / ~5min live |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*<TouchedClass>*"`
- **After every plan wave:** Run `./gradlew test` (non-live)
- **Before `/gsd-verify-work`:** Full suite (unit + live regression) green
- **Max feedback latency:** 60s for unit tier; 5min for live tier

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 9-01-01 | 01 | 1 | ARCH-01 | — | `LegalQueryAugmenter` emits `[Nguồn n]` byte-for-byte vs `ChatPromptFactory` golden | unit | `./gradlew test --tests "*LegalQueryAugmenterTest*"` | ❌ W0 | ⬜ pending |
| 9-01-02 | 01 | 1 | ARCH-01 | — | `CitationPostProcessor` assigns sequential `labelNumber` metadata | unit | `./gradlew test --tests "*CitationPostProcessorTest*"` | ❌ W0 | ⬜ pending |
| 9-01-03 | 01 | 2 | ARCH-01 | — | `RetrievalAugmentationAdvisor` wired with `VectorStoreDocumentRetriever` + `FILTER_EXPRESSION` | integration | `./gradlew test --tests "*ChatClientConfigTest*"` | ❌ W0 | ⬜ pending |
| 9-01-04 | 01 | 2 | ARCH-01 | — | `allowEmptyContext(true)` — empty retrieval still routes to refusal via `GroundingGuardOutputAdvisor` | integration | `./gradlew test --tests "*EmptyContextRefusalIT*"` | ❌ W0 | ⬜ pending |
| 9-01-05 | 01 | 3 | ARCH-05 | — | `ChatAnswerResponse` JSON byte-for-byte parity vs v1.0 fixture | regression | `./gradlew test --tests "*CitationFormatRegressionIT*"` | ❌ W0 | ⬜ pending |
| 9-01-06 | 01 | 3 | ARCH-01, ARCH-05 | — | 20-query Vietnamese regression ≥95% pass (closes P8 deferred) | live | `./gradlew test --tests "*VietnameseRegressionIT*" -PincludeTags=live` | ✅ (extend) | ⬜ pending |
| 9-01-07 | 01 | 3 | ARCH-01 | — | Refusal rate within 10% of P7 baseline (closes P8 deferred) | live | `./gradlew test --tests "*RefusalParityIT*" -PincludeTags=live` | ❌ W0 | ⬜ pending |
| 9-01-08 | 01 | 3 | ARCH-01 | — | `ChatService.doAnswer` LOC shrink ≥60% vs Phase 8 baseline | static | `./gradlew verifyDoAnswerLocShrink` (or manual `wc -l`) | manual | ⬜ pending |
| 9-02-01 | 02 | 4 | ARCH-01 | — | `StructuredOutputValidationAdvisor(maxRepeatAttempts=1)` retries once on parse failure | integration | `./gradlew test --tests "*StructuredOutputValidationAdvisorIT*"` | ❌ W0 | ⬜ pending |
| 9-02-02 | 02 | 4 | ARCH-01 | — | `NoOpValidationAdvisor` removed; build + tests green | smoke | `./gradlew test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/.../chat/advisor/LegalQueryAugmenterTest.java` — golden fixture for `[Nguồn n]` parity vs `ChatPromptFactory.formatCitation`
- [ ] `src/test/java/.../chat/advisor/CitationPostProcessorTest.java` — `labelNumber` metadata assignment
- [ ] `src/test/java/.../chat/config/ChatClientConfigTest.java` — advisor chain wiring assertion
- [ ] `src/test/java/.../chat/regression/EmptyContextRefusalIT.java` — empty-context → refusal path (D-08)
- [ ] `src/test/java/.../chat/regression/CitationFormatRegressionIT.java` — v1.0 JSON fixture replay
- [ ] `src/test/java/.../chat/regression/RefusalParityIT.java` — refusal-rate ±10% of P7 baseline (live)
- [ ] `src/test/java/.../chat/advisor/StructuredOutputValidationAdvisorIT.java` — `maxRepeatAttempts=1` behavior
- [ ] `VietnameseRegressionIT` — extend existing P8 fixture with real retrieval enabled

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 50 post-deploy chat logs show zero citations to untrusted sources | ROADMAP SC 3 | D-03: solo-dev deferral of CheckDef/harness | `SELECT * FROM chat_log cl JOIN citations c ON … WHERE c.trusted = false LIMIT 50;` — eyeball review |
| `doAnswer` LOC shrink ≥60% | D-06 / Domain #5 | No static-analysis tooling in repo | `wc -l` on Phase-8 vs Phase-9 `ChatService.doAnswer` body; record in VERIFICATION.md |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s (unit) / 5min (live)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
