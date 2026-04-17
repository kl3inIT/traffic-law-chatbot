# Phase 07 — Deferred Items

## Pre-existing test compile errors (blocks plan 07-01 runtime verification)

**Discovered during:** 07-01 Task 2 verify step
**Status:** Pre-existing on base branch (confirmed via `git stash` — errors persist without Wave-0 changes)
**Cause:** Commit `b1f55ab feat(chat): multi-model routing via 9router with timeout and cleanup` changed `AiModelProperties.ModelEntry` record from 2 to 4 constructor args; multiple test files were not updated to match.

**Files with unresolved constructor-arity mismatch (`new ModelEntry(String, String)` → needs `(String, String, String, String)`):**

- `src/test/java/com/vn/traffic/chatbot/ai/config/AiModelPropertiesTest.java` (lines 22-24)
- `src/test/java/com/vn/traffic/chatbot/chat/api/AllowedModelsControllerTest.java` (lines 37-39)
- `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java` (lines 78-79)
- `src/test/java/com/vn/traffic/chatbot/chat/config/ChatClientConfigTest.java` (lines 27-29)
- `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java` (lines 71-72)
- `src/test/java/com/vn/traffic/chatbot/checks/LlmSemanticEvaluatorTest.java` (lines 43-44)

Additional cascading failures (symbols not found because the above don't compile): `ChatControllerTest`, `ChatThreadControllerTest`, `ChatLogControllerTest`, `CheckRunControllerTest`, `AiParameterSetControllerTest`.

**Impact on 07-01:**
- All four new Wave-0 test files (`CacheKeyNormalizerTest`, `CachingEmbeddingModelTest`, `ChatLogServiceAsyncTest`, `AnswerComposerSlimSchemaTest`) and the two new production stubs compile green in isolation — verified by targeted review; the compile errors are in OTHER files.
- `./gradlew compileTestJava` fails due to these pre-existing errors, which prevents `./gradlew test --tests ...` from running any test (Gradle requires whole source-set compile).
- Runtime RED/GREEN assertions in `<verify>` cannot be executed until the pre-existing errors are fixed. They must be re-run the first time a Wave-1 plan lands, after those test files are brought back in line with the new `ModelEntry` shape.

**Decision:** Out of 07-01 scope per execute-plan.md scope-boundary rule ("pre-existing failures in unrelated files are out of scope"). Recommend a small follow-up fix in phase 07-02 (or a standalone chore commit) to update the 6 test files' `ModelEntry` instantiations to the 4-arg form.

**Follow-up action:** Before Plan 07-02 Task 1 RED→GREEN run, add a one-line chore commit updating each `new ModelEntry(id, name)` call-site to `new ModelEntry(id, name, baseUrl, apiKey)` matching the production record signature.
