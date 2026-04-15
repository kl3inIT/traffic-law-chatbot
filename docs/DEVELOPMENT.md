<!-- generated-by: gsd-doc-writer -->
# Development

Development is split between a Spring Boot 4 backend in `src/` and a Next.js 16 frontend in `frontend/`. Run Gradle commands from the repository root and `pnpm` commands from `frontend/`; on Windows, replace `./gradlew` with `gradlew.bat`.

## Local Setup

1. Clone the repository and enter the project directory.

   ```bash
   git clone https://github.com/kl3inIT/traffic-law-chatbot.git
   cd traffic-law-chatbot
   ```

2. Install the local toolchain:
   - `Java 25`. The backend toolchain is pinned in `build.gradle`.
   - `Node.js` and `pnpm`. The frontend package declares `pnpm@10.32.1` in `frontend/package.json`.
   - `PostgreSQL` with the `vector`, `hstore`, and `uuid-ossp` extensions available.
   - The Gradle wrapper downloads `Gradle 9.4.1` automatically on first use.

3. Install frontend dependencies.

   ```bash
   cd frontend
   pnpm install
   cd ..
   ```

   The backend does not have a separate package-install step; the Gradle wrapper resolves dependencies the first time you run a Gradle task. The frontend `prepare` lifecycle script runs `husky` from the repository root so the checked-in pre-commit hook is available locally.

4. Configure local runtime variables. Spring imports a repo-root `.env` through `spring.config.import`, and the frontend can read `frontend/.env.local` or a shell export for browser-safe overrides.

   ```bash
   DB_URL=jdbc:postgresql://localhost:5432/traffic_law
   DB_USERNAME=traffic_user
   DB_PASSWORD=traffic_pass
   OPENAI_API_KEY=your_openai_api_key
   ```

   ```bash
   NEXT_PUBLIC_API_BASE_URL=http://localhost:8089
   ```

   Local development assumes an existing PostgreSQL instance. `src/main/resources/application.yaml` points to PostgreSQL by default, `spring.docker.compose.enabled` is `false`, and the checked-in `compose.yaml` is only a commented scaffold. See [CONFIGURATION.md](CONFIGURATION.md) for defaults and override behavior. A valid `OPENAI_API_KEY` is still required for real model-backed chat responses even though Spring resolves the property to the fallback value `none` when it is unset. If your local chat router or embedding endpoint differs from the checked-in defaults, add `OPENAI_BASE_URL`, `EMBEDDING_BASE_URL`, `EMBEDDING_MODEL`, `CHAT_MODEL`, or `EVALUATOR_MODEL` to the same repo-root `.env`. The frontend client still falls back to `http://localhost:8088`, so keep `NEXT_PUBLIC_API_BASE_URL=http://localhost:8089` in your shell or `frontend/.env.local`; the checked-in `frontend/.env.local` already uses that local default.

5. Start the backend API.

   ```bash
   ./gradlew bootRun
   ```

   The backend listens on `http://localhost:8089`.

6. Start the frontend in a second terminal.

   ```bash
   cd frontend
   pnpm dev
   ```

   The frontend listens on `http://localhost:3000`, and `app.cors.allowed-origins` already allows that origin by default in `src/main/resources/application.properties`.

7. Verify the stack is running.

   ```bash
   curl http://localhost:8089/actuator/health
   ```

   A healthy backend returns a JSON payload whose `status` is `UP`. Open `http://localhost:3000` to verify the web UI can reach the API on port `8089`.

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
| `cd frontend && pnpm dev` | Start the Next.js development server on port `3000`. |
| `cd frontend && pnpm build` | Create the production frontend bundle. |
| `cd frontend && pnpm start` | Serve the built frontend in production mode. |
| `cd frontend && pnpm lint` | Run ESLint with the checked-in Next.js and TypeScript configuration. |
| `cd frontend && pnpm format` | Apply Prettier formatting across the frontend workspace. |
| `cd frontend && pnpm format:check` | Check frontend formatting without modifying files. |
| `cd frontend && pnpm test` | Run the Vitest suite once. |
| `cd frontend && pnpm test:watch` | Run Vitest in watch mode during UI development. |
| `cd frontend && pnpm test:ci` | Run the frontend test suite with V8 coverage enabled. |
| `cd frontend && pnpm e2e` | Run the Playwright end-to-end suite in `frontend/e2e` against `http://localhost:3000`. |
| `cd frontend && pnpm prepare` | Run `husky` from the repository root; `pnpm install` triggers this automatically. |

## Code Style

- Backend Java code does not have a checked-in repo-root linter or formatter. There is no `.editorconfig`, Checkstyle, Spotless, PMD, or formatter-specific Gradle task in the current tree, so backend consistency is enforced through review and the Gradle verification tasks.
- Frontend linting uses ESLint with [frontend/eslint.config.mjs](../frontend/eslint.config.mjs), which imports `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`. Run it with `cd frontend && pnpm lint`.
- Frontend formatting uses Prettier with [frontend/.prettierrc](../frontend/.prettierrc) and `prettier-plugin-tailwindcss`. Run `cd frontend && pnpm format` to rewrite files or `cd frontend && pnpm format:check` to verify formatting only.
- The repo-root pre-commit hook in [.husky/pre-commit](../.husky/pre-commit) runs `cd frontend && pnpm lint-staged`. Staged `*.ts` and `*.tsx` files are formatted with Prettier and then fixed with ESLint; staged `*.json`, `*.css`, and `*.md` files are formatted with Prettier.
- Follow the existing backend conventions under `com.vn.traffic.chatbot`: constructor injection via Lombok `@RequiredArgsConstructor`, Java `record` types for DTOs and small value objects, `@Transactional` boundaries in service classes, and `@Async("ingestionExecutor")` for background ingestion work.

## Branch Conventions

- The default integration branch is `main` (`origin/HEAD` points to `origin/main`).
- No contributor branch naming convention is documented in repository files.
- No `CONTRIBUTING.md`, `.github/PULL_REQUEST_TEMPLATE.md`, or root `.github/` directory is present to define release, hotfix, or review-branch rules.

## PR Process

The repository does not include a checked-in contributor guide or pull-request template, so there is no formal pull-request checklist in source control. The baseline process below is inferred from the current build, test, and schema layout.

- Open pull requests against `main`.
- Run the verification commands that match your change before requesting review: at minimum `./gradlew test` for backend work, and `cd frontend && pnpm lint && pnpm test` for frontend work. Prefer `./gradlew build` for cross-cutting backend changes, and add `cd frontend && pnpm e2e` when you change browser flows or routing.
- Keep schema changes and their Liquibase changelog entries together in the same PR under `src/main/resources/db/changelog/`.
- If you change runtime settings, ports, or required variables, update `src/main/resources/application.yaml`, `src/main/resources/application.properties`, `frontend/.env.local` expectations, and [CONFIGURATION.md](CONFIGURATION.md) in the same change.
- If you change public REST contracts or frontend/backend integration points, keep `frontend/lib/api/client.ts`, `frontend/types/api.ts`, and the relevant docs such as [API.md](API.md), [README.md](../README.md), or this file aligned with the code.
- Let the Husky pre-commit hook run on staged frontend files, but do not treat it as the only gate because backend code has no equivalent pre-commit lint step.
