<!-- generated-by: gsd-doc-writer -->
# Testing

## Test Framework And Setup

This project uses Gradle's `test` task with `useJUnitPlatform()` enabled in `build.gradle`, so the suite runs on the JUnit Platform. The resolved test stack comes primarily from `org.springframework.boot:spring-boot-starter-test:4.0.5`, which brings in `org.junit.jupiter:junit-jupiter:6.0.3`, `org.mockito:mockito-core:5.20.0`, `org.mockito:mockito-junit-jupiter:5.20.0`, `org.assertj:assertj-core:3.27.7`, and `org.springframework:spring-test:7.0.6`.

The repository currently keeps all tests in a single source set under `src/test/java`; there is no separate `integrationTest` task or source directory. A verified `./gradlew cleanTest test` run executed 30 test classes and 130 tests successfully.

Before running the full suite:

- Use the Gradle wrapper from the repository root: `./gradlew` on macOS/Linux or `gradlew.bat` on Windows.
- Have `Java 25` available, matching the Gradle toolchain configured in `build.gradle`.
- Make sure the datasource settings resolved from `src/main/resources/application.yaml` and the optional repo-root `.env` point to a reachable PostgreSQL instance. The current application-context tests (`TrafficLawChatbotApplicationTests` and `SpringBootSmokeTest`) boot the Spring application, run Liquibase, and open a real JDBC connection.
- No checked-in `src/test/resources` directory exists. Parser fixtures and YAML examples are created inline inside the test classes instead of being loaded from external test resources.

## Running Tests

Use the wrapper commands below from the repository root. Replace `./gradlew` with `gradlew.bat` on Windows.

Run the full suite:

```bash
./gradlew cleanTest test
```

Run the default verification task chain:

```bash
./gradlew check
```

Run a small subset of classes:

```bash
./gradlew test \
  --tests com.vn.traffic.chatbot.chat.service.ChatServiceTest \
  --tests com.vn.traffic.chatbot.chat.service.AnswerComposerTest
```

Run a single test class:

```bash
./gradlew test --tests com.vn.traffic.chatbot.chat.api.ChatControllerTest
```

Run a single test method:

```bash
./gradlew test --tests com.vn.traffic.chatbot.chat.api.ChatControllerTest.postChatRejectsBlankQuestionWithProblemDetailErrors
```

No dedicated watch-mode task is configured in the build.

## Writing New Tests

Place new tests in the package that matches the production code under `src/test/java/com/vn/traffic/chatbot/...`.

- Use `*Test.java` for unit, controller, serialization, parser, and repository-adjacent tests.
- Use `*IntegrationTest.java` for higher-level flow coverage that wires several collaborators together while still running inside the same Gradle `test` task.
- Follow the existing slice patterns:
  - Service and policy tests use `@ExtendWith(MockitoExtension.class)`, `@Mock`, and AssertJ assertions.
  - Controller tests build `MockMvc` manually with `MockMvcBuilders.standaloneSetup(...)`, `GlobalExceptionHandler`, Jackson converters, and `LocalValidatorFactoryBean`.
  - Higher-level chat flow tests assemble a small Spring context with `AnnotationConfigApplicationContext` and mocked `ChatModel` or `VectorStore` collaborators.
  - Application smoke tests use `@SpringBootTest` and currently exercise the real datasource and Liquibase path.
- Prefer local helper methods and inline fixtures over shared base classes. Existing examples include in-class response builders in the chat flow tests, inline YAML in `ActiveParameterSetProviderTest`, and in-memory DOCX generation in `TikaDocumentParserDocxTest`.

## Coverage Requirements

No coverage threshold is configured in the repository. The Gradle build does not apply a `jacoco` plugin or define coverage gates in `build.gradle`.

| Type | Threshold |
| --- | --- |
| Lines | No threshold configured |
| Branches | No threshold configured |
| Functions | No threshold configured |
| Statements | No threshold configured |

## CI Integration

No in-repo CI test workflow was detected. The repository does not currently contain `.github/workflows/`, `.gitlab-ci.yml`, `.circleci/`, `azure-pipelines.yml`, `buildkite` pipeline files, or a `Jenkinsfile`, so there is no checked-in pipeline definition that runs the Gradle test suite automatically.
