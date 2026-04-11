<!-- generated-by: gsd-doc-writer -->
# Getting Started

This guide gets the Spring Boot backend running locally with its PostgreSQL-backed vector store and chat API.

## Prerequisites

- `Java 25` (`build.gradle` pins the toolchain to `JavaLanguageVersion.of(25)`).
- `Git` for cloning the repository.
- A reachable `PostgreSQL` database for the app datasource.
- Database support for the `vector`, `hstore`, and `uuid-ossp` extensions used by the Liquibase changelogs.
- `OPENAI_API_KEY` if you want `/api/v1/chat` to produce model-backed answers.

## Installation Steps

1. Clone the repository.

   ```bash
   git clone https://github.com/kl3inIT/traffic-law-chatbot.git
   cd traffic-law-chatbot
   ```

2. Configure the runtime variables. The app imports a repo-root `.env` file through `spring.config.import`, and there is no checked-in `.env.example`.

   ```bash
   DB_URL=jdbc:postgresql://localhost:5432/traffic_law
   DB_USERNAME=traffic_user
   DB_PASSWORD=traffic_pass
   OPENAI_API_KEY=your_openai_api_key
   ```

3. Resolve dependencies and build the project with the Gradle wrapper.

   ```bash
   ./gradlew build
   ```

   On Windows, use `gradlew.bat build`.

## First Run

1. Make sure your PostgreSQL instance is running and the `.env` values point to it.
2. Start the application:

   ```bash
   ./gradlew bootRun
   ```

   On Windows, use `gradlew.bat bootRun`.

3. Verify that the service is up on port `8088`:

   ```bash
   curl http://localhost:8088/actuator/health
   ```

   A healthy instance returns a JSON payload whose `status` is `UP`.

4. Before expecting grounded legal answers from `POST /api/v1/chat`, ingest at least one source through the admin API and then approve and activate it.

## Common Setup Issues

- `bootRun` fails during datasource or Liquibase startup:
  Check `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, and make sure the target PostgreSQL database can use the `vector`, `hstore`, and `uuid-ossp` extensions referenced by the Liquibase changelogs.
- The app starts, but chat answers are missing or refuse to answer:
  `OPENAI_API_KEY` is required for OpenAI-backed completions, and retrieval only returns chunks whose metadata is `APPROVED`, `trusted == true`, and `active == true`.
- You expected Docker Compose to start PostgreSQL automatically:
  `spring.docker.compose.enabled` is set to `false` in `src/main/resources/application.yaml`, and the checked-in `compose.yaml` is only commented example content, so `bootRun` does not provision the database for you.
- Gradle commands fail on an older JDK:
  The project is configured for `Java 25`; use a matching local JDK so the Gradle toolchain can compile and run the app cleanly.

## Next Steps

- See [README.md](../README.md) for API usage examples, including one-shot chat, threaded chat, and source ingestion flows.
- See [CONFIGURATION.md](./CONFIGURATION.md) for the full environment-variable and runtime-settings reference.
- See [ARCHITECTURE.md](./ARCHITECTURE.md) for the ingestion, retrieval, and chat-service data flow.
- See [DEVELOPMENT.md](./DEVELOPMENT.md) for local setup, build commands, and PR workflow guidance, and [TESTING.md](./TESTING.md) for test commands, coverage notes, and CI execution details.
