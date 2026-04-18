# Phase 8: Structured Output + GroundingGuardAdvisor - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-18
**Phase:** 08-structured-output-groundingguardadvisor
**Areas discussed:** Intent classifier model + failure policy, `supportsStructuredOutput` flag storage, Advisor chain wiring, 20-query regression suite shape, GroundingGuard configuration surface

---

## Area 1a: Intent classifier model

| Option | Description | Selected |
|--------|-------------|----------|
| A. Same chat model | 1 extra call per request; no new key/config; simpler single-catalog | ✓ |
| B. Dedicated cheap model | Haiku 4.5 / Gemini Flash; lower latency/cost but needs separate `ModelEntry` + key |  |
| C. Reuse primary with aggressive prompt cache | Same model but P9-territory caching (premature) |  |

**User's choice:** A — Same chat model.
**Notes:** Solo-dev iteration speed wins; revisit only if p95 latency regresses outside P7's 2.5s ceiling.

---

## Area 1b: Intent classifier failure policy

| Option | Description | Selected |
|--------|-------------|----------|
| A. Assume LEGAL | Falls through to grounding gate; safer during classifier outage | ✓ |
| B. Assume CHITCHAT | Faster refusal but could mask legal queries during outage |  |
| C. Hard refuse with generic error | Conservative; user sees error; good observability |  |

**User's choice:** A — Assume LEGAL on error/timeout/malformed response.
**Notes:** Grounding gate is the safety net; letting a flake drop to chitchat would hide legal queries from users.

---

## Area 2: `supportsStructuredOutput` flag storage

| Option | Description | Selected |
|--------|-------------|----------|
| A. 5th arg on `ModelEntry` record | Hardcoded in `application-*.yaml`, code-reviewed | ✓ |
| B. DB-backed / admin-editable | Couples with Phase 10 api-key admin; premature for P8 |  |
| C. Separate static `ModelCapabilities` map | Hardcoded but decoupled from `ModelEntry` |  |

**User's choice:** A — 5th arg on `ModelEntry`.
**Notes:** Consistent with P7 D-01 (solo dev, no feature-flag infra). Default = `false` for un-annotated entries.

---

## Area 3: Advisor chain wiring

| Option | Description | Selected |
|--------|-------------|----------|
| A. P8-minimal (`GuardIn → Memory → GuardOut`) | YAGNI; P9 inserts RAG/Cache/Validation later |  |
| B. Full P9 order with no-op placeholders | Wires all 6 slots now; P9 swaps implementations in place | ✓ |

**User's choice:** B — Full P9 target order with no-op placeholders for RAG/Cache/Validation.
**Notes:** Phase 9 swap will not require re-ordering or config churn.

---

## Area 4: 20-query regression suite shape

| Option | Description | Selected |
|--------|-------------|----------|
| A. Manual smoke only | Consistent with P7 D-06; curl loop + `08-SMOKE-REPORT.md` |  |
| B. Live JUnit with real OpenRouter | `@Tag("live")`, deterministic structure validation |  |
| C. Recorded fixture replay via WireMock | Brittle for evolving intent classifier |  |
| **Spring AI pattern (resolved via Context7)** | Extend `BasicEvaluationTest`, use `RelevancyEvaluator` + `FactCheckingEvaluator`, tag `@Tag("live")` + `@DisabledIfEnvironmentVariable` | ✓ |

**User's choice:** "follow spring ai docs" — resolved via Context7 `/spring-projects/spring-ai` §api/testing.
**Notes:** Spring AI's canonical evaluation pattern is `BasicEvaluationTest` + LLM-as-judge evaluators. No WireMock, no custom harness. CI skips without `OPENROUTER_API_KEY`; dev runs manually.

---

## Area 5: GroundingGuard configuration surface

Multi-select — which knobs expose via `@ConfigurationProperties`?

| Option | Description | Selected |
|--------|-------------|----------|
| a. Enable/disable toggle | `app.chat.guard.enabled` |  |
| b. Similarity threshold | `app.chat.guard.similarity-threshold` |  |
| c. Refusal template text | `app.chat.guard.refusal-template` |  |
| d. Intent classifier system prompt | `app.chat.guard.intent-prompt` |  |
| e. None — all hardcoded | Consistent with P7 D-01 philosophy | ✓ |

**User's choice:** e — None; all hardcoded.
**Notes:** Threshold stays in `RetrievalPolicy`, refusal template as constant in advisor, intent prompt as constant in `IntentClassifier`. Enable/disable via code change + `git revert`.

---

## Claude's Discretion

- Exact `BeanOutputConverter` wiring path (fluent vs. explicit builder).
- Internal advisor class hierarchy (`BaseAdvisor` vs. `CallAdvisor` vs. `StreamAdvisor`).
- JSON-schema instruction text for prompt-instruction mode.
- Advisor `order` numeric values (constrained only by P9 documented ordering).
- Intent classifier Vietnamese prompt wording — iterate until regression passes.
- `IntentDecision` as record vs. sealed interface (record preferred for `.entity()` ergonomics).

## Deferred Ideas

- RetrievalAugmentationAdvisor, CitationPostProcessor, FILTER_EXPRESSION trust-tier — Phase 9.
- PromptCachingAdvisor, StructuredOutputValidationAdvisor — Phase 9 (slots reserved in P8).
- Runtime-configurable guard knobs — rejected per D-06; revisit at production.
- Dedicated cheap intent-classifier model — rejected per D-01; revisit on p95 regression.
- DB-backed capability flags / admin UI — rejected per D-03.
- WireMock / recorded fixtures for tests — rejected per D-05a.
