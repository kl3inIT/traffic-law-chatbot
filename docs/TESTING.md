<!-- generated-by: gsd-doc-writer -->
# Testing

## Test Framework And Setup

This repository has two separate test surfaces: a Gradle-managed Spring Boot backend in the project root and a pnpm-managed Next.js frontend in `frontend/`.

Backend tests run through Gradle's `test` task with `useJUnitPlatform()` and `--enable-native-access=ALL-UNNAMED` enabled in `build.gradle`. The resolved backend stack includes Spring Boot test starters `4.0.5`, JUnit Jupiter and JUnit Platform `6.0.3`, and Mockito JUnit Jupiter `5.20.0`. All backend tests live under `src/test/java`, and there is no separate `integrationTest` source set or custom Gradle test task.

Frontend tests run from `frontend/package.json`, which declares `pnpm@10.32.1` as the package manager. Unit and component tests use Vitest `4.1.4` with `jsdom` `29.0.2`, `@testing-library/react` `16.3.2`, `@testing-library/jest-dom` `6.9.1`, and `@testing-library/user-event` `14.6.1`. Browser tests use Playwright `1.59.1`. `frontend/vitest.config.ts` loads `frontend/vitest.setup.ts`, and `frontend/playwright.config.ts` runs the app with `npm run dev`, targets `http://localhost:3000`, and writes an HTML report.

Before running tests:

- Use `gradlew.bat` on Windows or `./gradlew` on macOS/Linux for backend commands, with Java `25` matching the Gradle toolchain in `build.gradle`.
- The checked-in backend tests are not fully infrastructure-free. `TrafficLawChatbotApplicationTests` uses a plain `@SpringBootTest`, so the default datasource and Liquibase settings from `src/main/resources/application.yaml` still apply when that class runs.
- No checked-in test container or usable Docker Compose stack provisions PostgreSQL for the suite. `compose.yaml` is currently commented out.
- Run frontend commands from `frontend/` after installing dependencies with pnpm. No `.nvmrc`, `.node-version`, or `engines` field is checked in; the commands below were verified with `node v24.14.0`.
- Set `NEXT_PUBLIC_API_BASE_URL=http://localhost:8089` before browser tests that need the real backend. The frontend client otherwise falls back to `http://localhost:8088`, while the backend defaults to port `8089`.
- Playwright browsers are not vendored with the repo. On a fresh machine, install them with `pnpm exec playwright install`. In this workspace, Playwright's Chromium browser is already installed.

## Running Tests

Use the backend commands from the repository root.

Run the full backend suite:

```bash
./gradlew test
```

Run Gradle's default verification lifecycle:

```bash
./gradlew check
```

Target a single backend class:

```bash
./gradlew test --tests com.vn.traffic.chatbot.chat.api.ChatControllerTest
```

Target a single backend test method:

```bash
./gradlew test --tests com.vn.traffic.chatbot.chat.api.ChatControllerTest.postChatRejectsBlankQuestionWithProblemDetailErrors
```

Current backend status:

- `./gradlew.bat test` completes successfully in the current workspace.
- `./gradlew.bat test --tests com.vn.traffic.chatbot.chat.api.ChatControllerTest` also completes successfully, so the older `compileTestJava` blocker in `ChunkInspectionServiceTest` is no longer present.

Use the frontend commands from `frontend/`.

Run the default Vitest suite:

```bash
pnpm test
```

Run a single Vitest file:

```bash
pnpm test -- __tests__/smoke.test.tsx
```

Run Vitest in watch mode:

```bash
pnpm test:watch
```

Run Vitest with coverage enabled:

```bash
pnpm test:ci
```

Run the full Playwright suite:

```bash
pnpm e2e
```

List Playwright tests without running them:

```bash
pnpm e2e -- --list
```

Target the checked-in smoke file directly:

```bash
pnpm e2e -- e2e/smoke.spec.ts
```

Current frontend status:

- `pnpm test` currently fails before executing assertions in this workspace because Vitest fork workers time out on all seven discovered test files.
- `pnpm test -- __tests__/smoke.test.tsx` currently hits the same worker-start timeout as the full `pnpm test` script.
- `pnpm test:ci` does run the suite and currently reports `4 failed | 6 passed | 9 todo` across `7` files. The failing assertions are in `frontend/__tests__/app-sidebar.test.tsx` and `frontend/__tests__/message-bubble.test.tsx`.
- The current frontend assertion failures are stale-content failures, not missing-file failures. `app-sidebar.test.tsx` still expects three admin nav items and ASCII labels such as `Quan ly nguon` and `Tro chuyen`, while `frontend/components/layout/app-sidebar.tsx` now renders seven admin links with accented Vietnamese labels such as `Quản lý nguồn` and `Trò chuyện`.
- `message-bubble.test.tsx` similarly still asserts ASCII strings such as `Su kien duoc xac dinh`, while `frontend/components/chat/message-bubble.tsx` renders accented section labels through `ScenarioAccordion`.
- `pnpm e2e -- --list` currently discovers `16` Chromium tests across `frontend/e2e/admin.spec.ts`, `frontend/e2e/chat.spec.ts`, and `frontend/e2e/smoke.spec.ts`.
- `pnpm e2e -- --grep "homepage loads with sidebar"` currently fails in `frontend/e2e/smoke.spec.ts` because the spec still looks for ASCII sidebar labels rather than the accented labels the app now renders.
- The broader `frontend/e2e/chat.spec.ts` and `frontend/e2e/admin.spec.ts` flows are not self-contained smoke checks. From the checked-in specs, they assume a running backend, reachable API base URL, ingested legal sources, an active parameter set, and existing chat or check data.

## Writing New Tests

Place backend tests in the package that matches the production code under `src/test/java/com/vn/traffic/chatbot/...`.

- Use `*Test.java` for unit, controller, parser, fetcher, service, and repository-adjacent coverage.
- Use `*IntegrationTest.java` for higher-level flows that still run inside Gradle's default `test` task, as shown by `ChatFlowIntegrationTest`, `ChatScenarioAnalysisIntegrationTest`, and `ChatThreadFlowIntegrationTest`.
- Service-heavy backend tests typically use `@ExtendWith(MockitoExtension.class)`, `@Mock`, and `@InjectMocks`.
- Controller tests build `MockMvc` manually with `MockMvcBuilders.standaloneSetup(...)`, `GlobalExceptionHandler`, Jackson message converters, and `LocalValidatorFactoryBean`.
- Higher-level API flow tests such as `ChatFlowIntegrationTest` still construct a small object graph around mocked collaborators and exercise `PublicChatController` through standalone `MockMvc` rather than booting the full application.
- Configuration smoke tests such as `SpringBootSmokeTest` and `AppPropertiesTest` use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)` plus `@TestPropertySource` to exclude Google GenAI, Liquibase, datasource, and JPA auto-configuration when a full database-backed context is unnecessary.
- Full-context smoke coverage still uses `@SpringBootTest`; `TrafficLawChatbotApplicationTests` is the checked-in example.

Place frontend unit and component tests in `frontend/__tests__` using the existing `*.test.tsx` naming pattern.

- Use `frontend/e2e/*.spec.ts` for Playwright browser tests.
- When a component depends on React Query, follow the local helper pattern in `frontend/__tests__/app-sidebar.test.tsx` and render through a `QueryClientProvider`.
- Use `vi.mock(...)` for Next.js navigation hooks and heavyweight UI wrappers when needed; existing tests mock `next/navigation` and `@/components/ui/sidebar`.
- `frontend/__tests__/stubs/*.test.tsx` is currently used for placeholder coverage with `it.todo(...)`; those files are skipped until real assertions replace the placeholders.
- Keep UI text assertions aligned with the actual rendered Vietnamese labels. The current Vitest and Playwright failures are caused by stale ASCII expectations, not by missing components.
- Keep browser-test assumptions explicit. `smoke.spec.ts` checks shell navigation only, while `chat.spec.ts` and `admin.spec.ts` depend on live backend data and should only assert against fixtures or seeded state that the test setup guarantees.

## Coverage Requirements

No coverage threshold is configured for either backend or frontend.

The backend build does not apply the `jacoco` plugin or define Gradle coverage gates in `build.gradle`. The frontend exposes `pnpm test:ci`, which runs `vitest run --coverage`, but `frontend/vitest.config.ts` does not define `coverageThreshold` values.

| Type | Threshold |
| --- | --- |
| Lines | No threshold configured |
| Branches | No threshold configured |
| Functions | No threshold configured |
| Statements | No threshold configured |

## CI Integration

No checked-in CI workflow currently runs the test suite. The repository does not include `.github/workflows/`, GitLab CI configuration, `.circleci/`, Azure Pipelines configuration, `buildkite` pipeline files, or a `Jenkinsfile`, so there is no versioned pipeline definition for backend Gradle tests, frontend Vitest, or Playwright.

The only checked-in automation related to contributor workflows is `.husky/pre-commit`, which changes into `frontend/` and runs `pnpm lint-staged`. That hook formats and lints staged frontend files, but it does not execute Gradle, Vitest, or Playwright.
