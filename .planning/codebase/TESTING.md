# Testing Patterns

**Analysis Date:** 2026-04-11

## Test Framework

**Runner:**
- Backend tests run through Gradle's default `test` task in `build.gradle`, which enables JUnit Platform with `useJUnitPlatform()`.
- Frontend unit and component tests run through Vitest in `frontend/vitest.config.ts` with `jsdom`, `globals: true`, `css: true`, and `frontend/vitest.setup.ts`.
- Frontend browser smoke tests run through Playwright in `frontend/playwright.config.ts`, targeting `frontend/e2e`.

**Assertion Library:**
- Backend tests use AssertJ, Mockito verification, and Spring `MockMvc` matchers throughout `src/test/java/com/vn/traffic/chatbot/**`.
- Frontend tests use Vitest `expect`, Testing Library assertions from `@testing-library/jest-dom/vitest`, and Playwright `expect`.

**Run Commands:**
```bash
.\gradlew.bat test                 # Backend suite via Gradle/JUnit Platform
cd frontend && pnpm test           # Vitest once
cd frontend && pnpm test:watch     # Vitest watch mode
cd frontend && pnpm test:ci        # Vitest with V8 coverage
cd frontend && pnpm e2e            # Playwright smoke tests
cd frontend && pnpm e2e -- --list  # List Playwright tests
```

## Test File Organization

**Location:**
- Backend tests mirror production package structure under `src/test/java/com/vn/traffic/chatbot/...`, for example `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java`, `src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java`, and `src/test/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParserDocxTest.java`.
- Frontend unit and component tests live in `frontend/__tests__`.
- Frontend browser tests live in `frontend/e2e`.
- No shared fixture tree is checked in under `src/test/resources` or `frontend/__tests__/fixtures`; test data is built inline.

**Naming:**
- Backend uses `*Test.java` for unit, controller, contract, parser, and service coverage, and `*IntegrationTest.java` for higher-level flow composition such as `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java`, `src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java`, and `src/test/java/com/vn/traffic/chatbot/chat/ChatScenarioAnalysisIntegrationTest.java`.
- Frontend unit tests use `*.test.tsx` such as `frontend/__tests__/app-sidebar.test.tsx` and `frontend/__tests__/message-bubble.test.tsx`.
- Frontend browser tests use `*.spec.ts` such as `frontend/e2e/smoke.spec.ts`.

**Structure:**
```text
src/test/java/com/vn/traffic/chatbot/<feature>/**/*.java
frontend/__tests__/**/*.test.tsx
frontend/e2e/**/*.spec.ts
```

## Test Inventory

**Current checked-in files:**
- Backend: 30 Java test files under `src/test/java`.
- Frontend Vitest: 7 `*.test.tsx` files under `frontend/__tests__`.
- Frontend Playwright: 1 `*.spec.ts` file under `frontend/e2e` containing 3 discovered Chromium smoke tests.

**Notable split:**
- Four frontend files under `frontend/__tests__/stubs/*.test.tsx` are placeholder-only and contain `it.todo(...)` assertions rather than executable coverage.
- The meaningful frontend assertions currently live in `frontend/__tests__/app-sidebar.test.tsx`, `frontend/__tests__/message-bubble.test.tsx`, and `frontend/__tests__/smoke.test.tsx`.

## Test Structure

**Suite Organization:**
```java
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new PublicChatController(chatService, null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }
}
```

**Patterns:**
- Backend unit and service tests use `@ExtendWith(MockitoExtension.class)` with `@Mock`, `@InjectMocks`, and direct `when(...)` / `verify(...)` interactions, for example `src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java` and `src/test/java/com/vn/traffic/chatbot/chunk/service/ChunkMetadataUpdaterTest.java`.
- Backend controller tests prefer `MockMvcBuilders.standaloneSetup(...)` plus real `GlobalExceptionHandler` and `LocalValidatorFactoryBean` instead of `@WebMvcTest`, as shown by `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java`, `src/test/java/com/vn/traffic/chatbot/chat/api/ChatThreadControllerTest.java`, `src/test/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetControllerTest.java`, and `src/test/java/com/vn/traffic/chatbot/chunk/api/ChunkAdminControllerTest.java`.
- Backend higher-level flow tests wire a focused Spring container or real service graph with mocked external collaborators instead of booting the full app. See `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java` and `src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java`.
- Full-context smoke coverage is minimal and uses `@SpringBootTest` in `src/test/java/com/vn/traffic/chatbot/TrafficLawChatbotApplicationTests.java` and `src/test/java/com/vn/traffic/chatbot/common/SpringBootSmokeTest.java`.
- Frontend Vitest suites use `describe(...)`, `it(...)`, Testing Library `render(...)`, and `screen.getByText(...)`, as shown in `frontend/__tests__/app-sidebar.test.tsx` and `frontend/__tests__/message-bubble.test.tsx`.

## Mocking

**Framework:** Mockito on the backend and `vi.mock(...)` in frontend Vitest suites.

**Patterns:**
```java
@Mock
private KbSourceRepository sourceRepo;

@InjectMocks
private SourceService sourceService;

when(sourceRepo.findById(sourceId)).thenReturn(Optional.of(source));
verify(chunkMetadataUpdater).updateApprovalState(sourceId.toString(), ApprovalState.APPROVED.name());
```

```tsx
vi.mock('next/navigation', () => ({
  usePathname: () => '/',
  useRouter: () => ({ push: vi.fn() }),
  useParams: () => ({}),
}));
```

**What to Mock:**
- Backend unit tests mock repositories, JDBC templates, vector stores, chat models, and service collaborators in files such as `src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java`, `src/test/java/com/vn/traffic/chatbot/ingestion/orchestrator/IngestionOrchestratorUrlHtmlPipelineTest.java`, and `src/test/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicyTest.java`.
- Controller tests mock the service layer but leave validation and exception handling real.
- Frontend component tests mock Next navigation and complex UI wrappers when rendering full providers would be noisy, as in `frontend/__tests__/app-sidebar.test.tsx`.

**What NOT to Mock:**
- Do not mock `GlobalExceptionHandler` or the validator in controller tests; they are part of the HTTP contract being verified in `src/test/java/com/vn/traffic/chatbot/chat/api/*.java` and `src/test/java/com/vn/traffic/chatbot/parameter/api/*.java`.
- Do not mock `frontend/types/api.ts`; import and use the real TypeScript contract types.
- Playwright smoke tests in `frontend/e2e/smoke.spec.ts` are written as app-level navigation checks without mocks.

## Fixtures and Factories

**Test Data:**
```java
private KbSource buildPendingSource(UUID id) {
    return KbSource.builder()
            .id(id)
            .sourceType(SourceType.PDF)
            .title("Test Source")
            .originKind(OriginKind.FILE_UPLOAD)
            .status(SourceStatus.DRAFT)
            .approvalState(ApprovalState.PENDING)
            .trustedState(TrustedState.UNTRUSTED)
            .build();
}
```

**Location:**
- Backend tests build domain objects inline with builders, helper methods, literal UUIDs, and `OffsetDateTime.now()`, for example `src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java` and `src/test/java/com/vn/traffic/chatbot/chat/ChatThreadFlowIntegrationTest.java`.
- Parser tests synthesize binary fixtures in memory rather than reading checked-in files. `src/test/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParserDocxTest.java` builds a minimal DOCX archive with `ZipOutputStream`.
- Frontend tests build local base objects inside the suite, such as the `baseResponse` object in `frontend/__tests__/message-bubble.test.tsx`.
- Placeholder tests live in `frontend/__tests__/stubs/chat.test.tsx`, `frontend/__tests__/stubs/sources.test.tsx`, `frontend/__tests__/stubs/param.test.tsx`, and `frontend/__tests__/stubs/vector.test.tsx`.

## Coverage

**Requirements:** None enforced.

**Signals:**
- Backend does not apply JaCoCo or any coverage gate in `build.gradle`.
- Frontend exposes `cd frontend && pnpm test:ci`, which runs `vitest run --coverage` from `frontend/package.json`, but `frontend/vitest.config.ts` does not define coverage thresholds.
- Repository and database integration are not directly covered with `@DataJpaTest`, Testcontainers, or a dedicated integration source set. There are no `@DataJpaTest` annotations anywhere under `src/test/java`.
- Browser coverage is deliberately thin: `frontend/e2e/smoke.spec.ts` only checks three route-level page loads.

**View Coverage:**
```bash
cd frontend && pnpm test:ci
```

## Test Types

**Unit Tests:**
- Service and policy units: `src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java`, `src/test/java/com/vn/traffic/chatbot/chat/service/ChatServiceTest.java`, `src/test/java/com/vn/traffic/chatbot/retrieval/RetrievalPolicyTest.java`, `src/test/java/com/vn/traffic/chatbot/chat/service/ClarificationPolicyTest.java`.
- Parser and fetch units: `src/test/java/com/vn/traffic/chatbot/ingestion/parser/TikaDocumentParserDocxTest.java`, `src/test/java/com/vn/traffic/chatbot/ingestion/parser/springai/HtmlDocumentParserTest.java`, `src/test/java/com/vn/traffic/chatbot/ingestion/fetch/SafeUrlFetcherTest.java`.
- Contract serialization units: `src/test/java/com/vn/traffic/chatbot/chat/api/ChatContractSerializationTest.java`.
- Frontend unit/component checks: `frontend/__tests__/smoke.test.tsx`, `frontend/__tests__/app-sidebar.test.tsx`, `frontend/__tests__/message-bubble.test.tsx`.

**Integration Tests:**
- Chat composition and flow tests under `src/test/java/com/vn/traffic/chatbot/chat/*IntegrationTest.java` assemble more realistic service graphs with mocked AI and vector collaborators.
- Ingestion orchestrator flow tests under `src/test/java/com/vn/traffic/chatbot/ingestion/orchestrator/*.java` verify multi-step ordering and provenance behavior.
- Backend integration still stops short of real database and migration verification under test-specific infrastructure.

**E2E Tests:**
- Playwright is configured in `frontend/playwright.config.ts` to run against `http://localhost:3000` and start the app with `npm run dev`.
- The only checked-in browser suite is `frontend/e2e/smoke.spec.ts`, which covers `/`, `/sources`, and `/parameters`.

## Common Patterns

**Async Testing:**
```ts
test('homepage loads with sidebar', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('Tro chuyen')).toBeVisible();
});
```
- Frontend browser tests use async Playwright expectations in `frontend/e2e/smoke.spec.ts`.
- Backend tests are mostly synchronous because collaborators are mocked directly, even when production code uses async executors.

**Error Testing:**
```java
assertThatThrownBy(() -> sourceService.activate(sourceId, "admin"))
        .isInstanceOf(AppException.class)
        .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR));
```
- Service tests assert typed `AppException` failures with specific `ErrorCode` values, for example in `src/test/java/com/vn/traffic/chatbot/source/service/SourceServiceTest.java` and `src/test/java/com/vn/traffic/chatbot/ingestion/service/IngestionServiceTest.java`.
- Controller tests assert `ProblemDetail` payloads for validation and not-found cases in `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java`, `src/test/java/com/vn/traffic/chatbot/chat/api/ChatThreadControllerTest.java`, and `src/test/java/com/vn/traffic/chatbot/parameter/api/AiParameterSetControllerTest.java`.

## CI Hooks

**Checked-in automation:**
- There is no checked-in CI workflow under `.github/workflows/`, `.gitlab-ci.yml`, `Jenkinsfile`, or similar pipeline files in the repository root.
- The only active contributor hook in source control is `.husky/pre-commit`, which runs `cd frontend && pnpm lint-staged`.
- `frontend/package.json` wires Husky from the repository root through the `prepare` script (`cd .. && husky || true`).
- `frontend/.husky/pre-commit` exists and contains `npm test`, but there is no checked-in install path that targets `frontend/.husky`, so do not assume it executes automatically.

**Implication:**
- Frontend staged files get formatting and ESLint fixes before commit, but backend tests, frontend Vitest, and Playwright are not enforced by a checked-in pipeline.

## Important Gaps

**Live suite status:**
- `.\gradlew.bat test` currently fails during `compileTestJava` because `src/test/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionServiceTest.java` still constructs `ChunkSummaryResponse` and `ChunkDetailResponse` with outdated record signatures.
- The same backend compilation run emits deprecation warnings for `MappingJackson2HttpMessageConverter` in `src/test/java/com/vn/traffic/chatbot/chat/api/ChatControllerTest.java`, `src/test/java/com/vn/traffic/chatbot/chat/api/ChatThreadControllerTest.java`, and `src/test/java/com/vn/traffic/chatbot/chat/ChatFlowIntegrationTest.java`.
- `cd frontend && pnpm test` currently reports 3 failing assertions, 8 passing assertions, and 9 `todo` items. The failing tests in `frontend/__tests__/app-sidebar.test.tsx` and `frontend/__tests__/message-bubble.test.tsx` assert ASCII labels while the components now render accented Vietnamese copy.
- `frontend/__tests__/app-sidebar.test.tsx` also emits React `act(...)` warnings from `ScrollAreaRoot`, which indicates the current render helper does not fully settle component updates before assertions.

**Coverage gaps:**
- `frontend/__tests__/stubs/*.test.tsx` is placeholder coverage only for chat, sources, parameters, and vector/index UI. Those files do not currently protect the real admin workflows.
- There is no dedicated repository, Liquibase migration, or database contract suite under `src/test/java`. Persistence behavior is mostly mocked or covered indirectly.
- The Playwright suite only verifies route boot and sidebar visibility. It does not exercise chat submission, source upload, parameter editing, or admin table actions.
- No coverage threshold or CI gate prevents broken suites from merging.

---

*Testing analysis: 2026-04-11*
