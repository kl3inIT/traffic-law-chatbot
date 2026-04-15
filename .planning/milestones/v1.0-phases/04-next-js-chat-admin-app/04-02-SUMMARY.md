---
phase: 04-next-js-chat-admin-app
plan: 02
subsystem: backend-parameter-integration
tags: [parameters, cors, refactoring, yaml, spring]
dependency_graph:
  requires: []
  provides: [ActiveParameterSetProvider, dynamic-ai-settings, cors-config]
  affects: [AnswerCompositionPolicy, ClarificationPolicy, ChatPromptFactory, RetrievalPolicy, ChatService, AnswerComposer]
tech_stack:
  added: [SnakeYAML (org.yaml.snakeyaml, already transitive), CorsConfig]
  patterns: [dot-path YAML navigation, fallback-first parameter reads, @Component injection chain]
key_files:
  created:
    - src/main/java/com/vn/traffic/chatbot/parameter/domain/AiParameterSet.java
    - src/main/java/com/vn/traffic/chatbot/parameter/repo/AiParameterSetRepository.java
    - src/main/java/com/vn/traffic/chatbot/parameter/service/ActiveParameterSetProvider.java
    - src/main/java/com/vn/traffic/chatbot/common/config/CorsConfig.java
    - src/test/java/com/vn/traffic/chatbot/parameter/service/ActiveParameterSetProviderTest.java
  modified:
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerCompositionPolicy.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/AnswerComposer.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicy.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatPromptFactory.java
    - src/main/java/com/vn/traffic/chatbot/chat/service/ChatService.java
    - src/main/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicy.java
    - src/main/resources/application.properties
    - src/test/java/com/vn/traffic/chatbot/chat/service/AnswerComposerTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicyTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/ChatScenarioAnalysisIntegrationTest.java
    - src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java
    - src/test/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicyTest.java
decisions:
  - "Use SnakeYAML (already on Spring Boot classpath) instead of jackson-dataformat-yaml to avoid adding a new dependency"
  - "Keep static constants in AnswerCompositionPolicy with original names for backward compatibility with tests and external comparisons"
  - "Use lenient() Mockito stubbing in integration test setUp methods to prevent UnnecessaryStubbingException when some test methods do not trigger the YAML read path"
  - "Safety-critical settings (RETRIEVAL_FILTER, citation JSON schema, grounding-status branching) remain hardcoded per D-13"
  - "ClarificationPolicy falls back to built-in keyword rules when no requiredFacts list is in the active parameter set"
metrics:
  duration_minutes: 35
  completed_date: "2026-04-11"
  tasks_completed: 2
  tasks_total: 2
  files_created: 5
  files_modified: 14
---

# Phase 04 Plan 02: Parameter-Driven AI Settings & CORS Summary

**One-liner:** Refactored 4 core AI services to read configurable settings from the active AiParameterSet YAML via a centralized `ActiveParameterSetProvider`, with SnakeYAML dot-path navigation, typed fallbacks, and CORS enabled for localhost:3000.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | ActiveParameterSetProvider with TDD | `632ac3f` | ActiveParameterSetProvider.java, AiParameterSet.java, AiParameterSetRepository.java, ActiveParameterSetProviderTest.java |
| 2 | Service refactoring + CORS config | `3f7068c` | AnswerCompositionPolicy.java, ClarificationPolicy.java, ChatPromptFactory.java, RetrievalPolicy.java, AnswerComposer.java, ChatService.java, CorsConfig.java |

## What Was Built

### Task 1: ActiveParameterSetProvider

A `@Component` that reads and parses the active `AiParameterSet` YAML content using `org.yaml.snakeyaml.Yaml` (already on the Spring Boot classpath — no new dependency needed).

Provides typed dot-path accessors:
- `getString("messages.disclaimer", fallback)` 
- `getInt("caseAnalysis.maxClarifications", fallback)`
- `getDouble("retrieval.similarityThreshold", fallback)`
- `getList("caseAnalysis.requiredFacts")`
- `getActiveParams()` — returns the full parsed map, empty if no active set

YAML content is parsed as `Map<String, Object>` data only — never executed as code (T-04-05 mitigation).

Also created minimal `AiParameterSet` JPA entity and `AiParameterSetRepository` (full CRUD implementation in Plan 04-01; these stubs compile and allow 04-02 to proceed in parallel).

8 unit tests cover: nested path navigation, fallback on missing keys, int/double/string/list type coercion, and the empty-set edge case.

### Task 2: Service Refactoring

**AnswerCompositionPolicy:** Converted from `final` utility class to `@Component`. Static constants kept with original names for test compatibility and as fallback values. New instance methods `getDisclaimer()`, `getRefusalMessage()`, `getLimitedNotice()`, `getRefusalNextSteps()` delegate to `paramProvider.getString(...)`.

**AnswerComposer:** Injected `AnswerCompositionPolicy`; replaced all static constant references with instance method calls.

**ClarificationPolicy:** Injected `ActiveParameterSetProvider`. `maxClarifications` now read from `paramProvider.getInt("caseAnalysis.maxClarifications", 2)`. `requiredFacts` rules are dynamic from `paramProvider.getList("caseAnalysis.requiredFacts")` with built-in keyword fallback rules when the list is absent.

**ChatPromptFactory:** Injected `ActiveParameterSetProvider`. Opening system context line reads from `paramProvider.getString("systemPrompt", SYSTEM_CONTEXT_FALLBACK)`. Citation formatting, JSON schema instructions, and grounding-status branching remain hardcoded (D-13 safety-critical).

**RetrievalPolicy:** Injected `ActiveParameterSetProvider`. `topK` reads from `paramProvider.getInt("retrieval.topK", 5)`. `similarityThreshold` reads from `paramProvider.getDouble("retrieval.similarityThreshold", 0.7)`. `RETRIEVAL_FILTER` remains `public static final` per D-13.

**ChatService:** Added `AnswerCompositionPolicy` dependency for `fallbackDraft()` refusal next steps.

**CorsConfig:** New `@Configuration` class implementing `WebMvcConfigurer.addCorsMappings()` for `/api/**` with `allowedOrigins` from `app.cors.allowed-origins` property (default `http://localhost:3000`). `allowCredentials=false` (T-04-06 mitigated).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing infrastructure] Created AiParameterSet entity and AiParameterSetRepository stubs**
- **Found during:** Task 1
- **Issue:** Plan 04-01 creates these files but runs in parallel; 04-02 needs them to compile
- **Fix:** Created minimal stub implementations compatible with both 04-01 and 04-02 design
- **Files modified:** AiParameterSet.java, AiParameterSetRepository.java
- **Commit:** 632ac3f

**2. [Rule 3 - Blocking issue] Used SnakeYAML instead of jackson-dataformat-yaml**
- **Found during:** Task 1 implementation
- **Issue:** Plan specified `jackson-dataformat-yaml` but only `tools.jackson.dataformat:jackson-dataformat-yaml:3.1.0` (Jackson 3.x) is on the transitive classpath; existing code uses `com.fasterxml.jackson` (2.x); mixing would be fragile
- **Fix:** Used `org.yaml.snakeyaml.Yaml` which is already on the Spring Boot classpath with no version mismatch concerns
- **Files modified:** ActiveParameterSetProvider.java, build.gradle (no change needed)
- **Commit:** 632ac3f

**3. [Rule 1 - Bug] Fixed UnnecessaryStubbingException in integration tests**
- **Found during:** Task 2 verification
- **Issue:** Added `lenient().when(paramRepo.findByActiveTrue())...` stubs in `@BeforeEach` setUp methods; some test methods (`unknownThreadRaisesDomainSpecificNotFoundError`) never call `ClarificationPolicy.evaluate()` so the stub was never consumed
- **Fix:** Changed `when(...)` to `lenient().when(...)` in both `ChatThreadFlowIntegrationTest` and `ChatScenarioAnalysisIntegrationTest` setUp methods
- **Files modified:** ChatThreadFlowIntegrationTest.java, ChatScenarioAnalysisIntegrationTest.java
- **Commit:** 3f7068c

## Known Stubs

None. The `AiParameterSet` entity and `AiParameterSetRepository` are minimal stubs intended to be replaced/extended by Plan 04-01's full implementation. The `ActiveParameterSetProvider` works correctly with them as-is; it reads from whatever `findByActiveTrue()` returns.

## Test Results

| Suite | Result |
|-------|--------|
| ActiveParameterSetProviderTest (8 tests) | PASS |
| AnswerComposerTest | PASS |
| ClarificationPolicyTest | PASS |
| RetrievalPolicyTest | PASS |
| ChatServiceTest | PASS |
| ChatFlowIntegrationTest | PASS |
| ChatScenarioAnalysisIntegrationTest | PASS |
| ChatThreadFlowIntegrationTest | PASS |
| SpringBootSmokeTest | FAIL (pre-existing — PostgreSQL not running in CI) |

Pre-existing `SpringBootSmokeTest` failures (`java.net.ConnectException` to PostgreSQL) exist before and after this plan's changes. All 113 unit and mock-based tests pass.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: config-injection | CorsConfig.java | New CORS config reads `allowedOrigins` from property — production deployments must override `app.cors.allowed-origins` with explicit non-localhost origins |

## Self-Check: PASSED
