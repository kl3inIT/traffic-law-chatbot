<!-- generated-by: gsd-doc-writer -->
# Getting Started

This guide gets the Spring Boot API running locally and, if you want the bundled UI, the Next.js frontend in `frontend/`.

## Prerequisites

- `Java 25` (`build.gradle` pins the toolchain to `JavaLanguageVersion.of(25)`).
- `Git` for cloning the repository.
- A reachable `PostgreSQL` database for the app datasource.
- Permission to use the `vector`, `hstore`, and `uuid-ossp` extensions created by the Liquibase changelogs.
- `OPENAI_API_KEY` if you want `/api/v1/chat` and source ingestion to perform model-backed chat and embedding calls.
- `OPENAI_BASE_URL` if your OpenAI-compatible chat router is not available at the default `http://localhost:20128`.
- `Node.js` and `pnpm 10.32.1` if you want to run the bundled Next.js UI in `frontend/`.

## Installation Steps

1. Clone the repository.

   ```bash
   git clone https://github.com/kl3inIT/traffic-law-chatbot.git
   cd traffic-law-chatbot
   ```

2. Create or update the backend runtime variables in a repo-root `.env`. Spring imports that file through `spring.config.import`, and no separate `.env.example` template is checked in.

   ```dotenv
   DB_URL=jdbc:postgresql://localhost:5432/traffic_law
   DB_USERNAME=traffic_user
   DB_PASSWORD=traffic_pass
   OPENAI_API_KEY=your_openai_api_key
   # Optional when your chat router is not at the default http://localhost:20128
   # OPENAI_BASE_URL=http://localhost:20128
   ```

3. Resolve dependencies and validate the backend toolchain with the Gradle wrapper. The wrapper downloads `Gradle 9.4.1` automatically on first use.

   ```bash
   ./gradlew build
   ```

   On Windows, use `gradlew.bat build`.

4. If you want the bundled web UI, install the frontend dependencies.

   ```bash
   cd frontend
   pnpm install
   cd ..
   ```

## First Run

1. Make sure your PostgreSQL instance is running and the `.env` values point to it.
2. Start the backend application:

   ```bash
   ./gradlew bootRun
   ```

   On Windows, use `gradlew.bat bootRun`.

3. Verify that the backend is up on port `8089`:

   ```bash
   curl http://localhost:8089/actuator/health
   ```

   A healthy instance returns a JSON payload whose `status` is `UP`.

4. If you want the bundled UI, start it in another terminal and point it at the backend's current port.

   ```bash
   cd frontend
   export NEXT_PUBLIC_API_BASE_URL=http://localhost:8089
   pnpm dev
   ```

   In PowerShell, use `$env:NEXT_PUBLIC_API_BASE_URL='http://localhost:8089'` before `pnpm dev`. For a repeatable local setup, put the same value in `frontend/.env.local`. This override is required because `frontend/lib/api/client.ts` otherwise falls back to `http://localhost:8088`.

5. Open `http://localhost:3000` for the chat and admin UI, or call the backend directly. Before expecting grounded answers from `POST /api/v1/chat`, ingest at least one source through the admin API and then approve and activate it.

## Common Setup Issues

- `bootRun` fails during datasource or Liquibase startup:
  Check `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, and make sure the target PostgreSQL database can use the `vector`, `hstore`, and `uuid-ossp` extensions referenced by the Liquibase changelogs.
- The frontend starts, but API calls fail or hit the wrong port:
  `frontend/lib/api/client.ts` falls back to `http://localhost:8088`, while the backend now defaults to `server.port: 8089`. Set `NEXT_PUBLIC_API_BASE_URL=http://localhost:8089` in your shell or `frontend/.env.local` before running `pnpm dev`.
- Chat requests fail with model authentication or router connection errors:
  Set `OPENAI_API_KEY`, and if your chat router is not running at `http://localhost:20128`, also set `OPENAI_BASE_URL` to the reachable base URL before starting the backend.
- The app starts, but grounded answers are still refused:
  Ingest at least one source through the admin API and then approve and activate it. `RetrievalPolicy` only returns chunks whose metadata is `APPROVED`, `trusted == true`, and `active == true`.
- You expected Docker Compose to start PostgreSQL automatically:
  `spring.docker.compose.enabled` is set to `false` in `src/main/resources/application.yaml`, and the checked-in `compose.yaml` is only commented example content, so `bootRun` does not provision the database for you.
- Gradle commands fail on an older JDK:
  The project is configured for `Java 25`; use a matching local JDK so the Gradle toolchain can compile and run the app cleanly.

## Next Steps

- See [README.md](../README.md) for API usage examples, including one-shot chat, threaded chat, and source ingestion flows.
- See [CONFIGURATION.md](./CONFIGURATION.md) for the full environment-variable and runtime-settings reference.
- See [ARCHITECTURE.md](./ARCHITECTURE.md) for the ingestion, retrieval, and chat-service data flow.
- See [DEVELOPMENT.md](./DEVELOPMENT.md) for local setup, build commands, and PR workflow guidance, and [TESTING.md](./TESTING.md) for test commands, coverage notes, and CI execution details.
