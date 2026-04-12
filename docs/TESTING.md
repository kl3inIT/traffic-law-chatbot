<!-- generated-by: gsd-doc-writer -->
# Testing

## Test Framework And Setup

This repository has two separate test surfaces: a Gradle-managed Spring Boot backend in the project root and a pnpm-managed Next.js frontend in `frontend/`.

Backend tests run through Gradle's `test` task with `useJUnitPlatform()` enabled in `build.gradle`. The resolved backend stack includes JUnit Jupiter `6.0.3` on the JUnit Platform, Spring Boot test starters `4.0.5`, and Mockito JUnit Jupiter `5.20.0`. All backend tests live under `src/test/java`, and there is no separate `integrationTest` source set or Gradle task.

Frontend tests run from `frontend/package.json`, which declares `pnpm@10.32.1` as the package manager. Unit and component tests use Vitest `4.1.4` with `jsdom` `29.0.2`, `@testing-library/react` `16.3.2`, and `@testing-library/jest-dom` `6.9.1`. Browser smoke tests use Playwright `1.59.1`. `frontend/vitest.config.ts` loads `frontend/vitest.setup.ts`, and `frontend/playwright.config.ts` starts the app with `npm run dev`, targets `http://localhost:3000`, and writes an HTML report.

Before running tests:

- Use `gradlew.bat` on Windows or `./gradlew` on macOS/Linux for backend commands, with Java `25` matching the Gradle toolchain in `build.gradle`.
- Run frontend commands from `frontend/` after installing dependencies with pnpm.
- Install Playwright browsers with `pnpm exec playwright install` before the first `pnpm e2e` run. In this workspace, `pnpm e2e` currently stops before execution if the Chromium browser binary is missing.
- `SpringBootSmokeTest` excludes Google GenAI, Liquibase, datasource, and JPA auto-configuration, but `TrafficLawChatbotApplicationTests` uses a plain `@SpringBootTest`, so the default datasource and Liquibase settings from `src/main/resources/application.yaml` still apply when that class runs.
- The backend suite is not currently green: `./gradlew.bat test` fails during `compileTestJava` because `src/test/java/com/vn/traffic/chatbot/chunk/service/ChunkInspectionServiceTest.java` still constructs `ChunkSummaryResponse` and `ChunkDetailResponse` with outdated record signatures.

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

Backend caveat: filtered Gradle commands are still blocked by the same `compileTestJava` failure in `ChunkInspectionServiceTest`, because Gradle compiles the full `src/test/java` source set before applying `--tests`.

Use the frontend commands from `frontend/`.

Run the full Vitest suite:

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

Run the Playwright smoke suite:

```bash
pnpm e2e
```

List Playwright tests without running them:

```bash
pnpm e2e -- --list
```

Target the checked-in Playwright file directly:

```bash
pnpm e2e -- e2e/smoke.spec.ts
```

Current frontend status:

- `pnpm test` currently reports three failing assertions across `frontend/__tests__/app-sidebar.test.tsx` and `frontend/__tests__/message-bubble.test.tsx`.
- `pnpm test -- __tests__/smoke.test.tsx` passes in the current workspace.
- `pnpm e2e -- --list` discovers three Chromium smoke tests from `frontend/e2e/smoke.spec.ts`.
- `pnpm e2e` currently fails before running tests until Playwright browsers are installed with `pnpm exec playwright install`.

## Writing New Tests

Place backend tests in the package that matches the production code under `src/test/java/com/vn/traffic/chatbot/...`.

- Use `*Test.java` for unit, controller, parser, fetcher, service, and repository-adjacent coverage.
- Use `*IntegrationTest.java` for higher-level flows that still run inside Gradle's default `test` task, as shown by `ChatFlowIntegrationTest`, `ChatScenarioAnalysisIntegrationTest`, and `ChatThreadFlowIntegrationTest`.
- Service-heavy backend tests typically use `@ExtendWith(MockitoExtension.class)`, `@Mock`, and `@InjectMocks`.
- Controller tests build `MockMvc` manually with `MockMvcBuilders.standaloneSetup(...)`, `GlobalExceptionHandler`, Jackson message converters, and `LocalValidatorFactoryBean`.
- Higher-level backend flow tests assemble a small Spring container with `AnnotationConfigApplicationContext` instead of booting the full application.
- Full-context smoke coverage uses `@SpringBootTest`; `SpringBootSmokeTest` is the lightweight variant, while `TrafficLawChatbotApplicationTests` exercises the default application context.

Place frontend unit and component tests in `frontend/__tests__` using the existing `*.test.tsx` naming pattern.

- Use `frontend/e2e/*.spec.ts` for Playwright browser tests.
- When a component depends on React Query, follow the local helper pattern in `frontend/__tests__/app-sidebar.test.tsx` and render through a `QueryClientProvider`.
- Use `vi.mock(...)` for Next.js navigation hooks and heavyweight UI wrappers when needed; existing tests mock `next/navigation` and `@/components/ui/sidebar`.
- `frontend/__tests__/stubs/*.test.tsx` is currently used for placeholder coverage with `it.todo(...)`; keep those files for planned work, not finished assertions.
- Keep UI text assertions aligned with the actual rendered labels. The current failing frontend tests are a good reminder that the component copy now includes accented Vietnamese strings.

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

No checked-in CI workflow currently runs the test suite. The repository does not include `.github/workflows/`, `.gitlab-ci.yml`, `.circleci/`, `azure-pipelines.yml`, `buildkite` pipeline files, or a `Jenkinsfile`, so there is no versioned pipeline definition for backend Gradle tests, frontend Vitest, or Playwright.

The only checked-in automation related to contributor workflows is `.husky/pre-commit`, which changes into `frontend/` and runs `pnpm lint-staged`. That hook formats and lints staged frontend files, but it does not execute Gradle, Vitest, or Playwright.
