<!-- generated-by: gsd-doc-writer -->
# Development

This project is developed as a Spring Boot 4 application with the Gradle wrapper. Run commands from the repository root; on Windows, replace `./gradlew` with `gradlew.bat`.

## Local Setup

1. Clone the repository and enter the project directory.

   ```bash
   git clone https://github.com/kl3inIT/traffic-law-chatbot.git
   cd traffic-law-chatbot
   ```

2. Install `Java 25`. The build uses a Gradle toolchain pinned in `build.gradle`, and the wrapper downloads Gradle `9.4.1` automatically on first use.

3. Provision PostgreSQL with the `vector`, `hstore`, and `uuid-ossp` extensions available. Local development assumes an existing database:
   - `src/main/resources/application.yaml` points to PostgreSQL by default.
   - `spring.docker.compose.enabled` is set to `false`.
   - The checked-in `compose.yaml` is only a commented scaffold, not a ready-to-run local stack.

4. Configure runtime variables in a repo-root `.env` file or export them directly in your shell. Spring imports `.env` through `spring.config.import`.

   ```bash
   DB_URL=jdbc:postgresql://localhost:5432/traffic_law
   DB_USERNAME=traffic_user
   DB_PASSWORD=traffic_pass
   OPENAI_API_KEY=your_openai_api_key
   ```

   See [CONFIGURATION.md](CONFIGURATION.md) for defaults and override behavior. A valid `OPENAI_API_KEY` is required for real model-backed chat responses, even though the application can still bind configuration with an empty value.

5. Start the application.

   ```bash
   ./gradlew bootRun
   ```

   The API listens on `http://localhost:8088`.

6. Verify the service is running.

   ```bash
   curl http://localhost:8088/actuator/health
   ```

   A healthy instance returns a JSON payload whose `status` is `UP`.

## Build Commands

| Command | Description |
| --- | --- |
| `./gradlew bootRun` | Run the application with the main runtime classpath. |
| `./gradlew bootTestRun` | Run the application with the test runtime classpath. |
| `./gradlew build` | Compile the code, run the test task, and assemble the project outputs. |
| `./gradlew test` | Run the JUnit Platform test suite. |
| `./gradlew check` | Run Gradle verification tasks; in the current build this is the main verification entry point alongside `test`. |
| `./gradlew assemble` | Build jars and other outputs without the full verification lifecycle. |
| `./gradlew clean` | Remove the `build/` directory. |
| `./gradlew bootJar` | Create the executable Spring Boot jar. |
| `./gradlew jar` | Create the plain jar for the `main` feature. |
| `./gradlew bootBuildImage` | Build an OCI image from the application using the Spring Boot plugin. |
| `./gradlew javadoc` | Generate Javadoc for the main source set. |
| `./gradlew dependencies` | Print the resolved dependency graph. |
| `./gradlew dependencyInsight --dependency <group-or-module>` | Inspect why a dependency is present and which version wins. |
| `./gradlew javaToolchains` | Show the Java toolchains Gradle can detect. |
| `./gradlew tasks --all` | List the full task surface exposed by the current build. |

## Code Style

No dedicated formatting or lint configuration is checked into this repository. There is no `.editorconfig`, Checkstyle, Spotless, PMD, or formatter-specific Gradle task in the current tree.

- Follow the domain-oriented package layout under `com.vn.traffic.chatbot`, such as `chat`, `ingestion`, `source`, `chunk`, `parameter`, and `common`.
- Prefer constructor injection via Lombok `@RequiredArgsConstructor`; controllers and services consistently use that pattern.
- Use Java `record` types for request and response DTOs and other small value carriers.
- Keep transaction boundaries in the service layer with `@Transactional`; background ingestion work is dispatched with `@Async("ingestionExecutor")`.
- Run the Gradle wrapper commands before opening a review, since build and test execution are the only checked-in automated quality gates.

## Branch Conventions

- The default integration branch is `main` (`origin/HEAD` points to `origin/main`).
- No contributor branch naming convention is documented in repository files.
- No checked-in branch protection, release-branch, or hotfix-branch workflow is present in the repository contents.

## PR Process

The repository does not include a checked-in contributor guide or pull-request template, so there is no formal pull-request checklist checked into source control. The baseline process below is inferred from the current build, schema, and documentation layout.

- Open pull requests against `main`.
- Run `./gradlew build` or, at minimum, the focused Gradle task that exercises your change before requesting review.
- Keep schema changes and their Liquibase changelog entries together in the same PR under `src/main/resources/db/changelog/`.
- If you change runtime settings or new required variables, update `src/main/resources/application.yaml` and [CONFIGURATION.md](CONFIGURATION.md) in the same change.
- If you change public REST contracts or developer-facing workflows, update the relevant docs such as `README.md`, `docs/ARCHITECTURE.md`, or this file alongside the code.
