<!-- generated-by: gsd-doc-writer -->
# Configuration

This repository has two runtime configuration surfaces. The Spring Boot backend reads `src/main/resources/application.yaml`, supplements it with `src/main/resources/application.properties`, and optionally imports a repo-root `.env` file through `spring.config.import: optional:file:.env[.properties]`. The Next.js frontend reads `frontend/.env.local` when it exists and otherwise falls back to the base URL hardcoded in `frontend/lib/api/client.ts`. The repository also ships `src/main/resources/default-parameter-set.yml`, which `DefaultParameterSetSeeder` inserts into `ai_parameter_set` when the table is empty.

## Environment Variables

No `.env.example` file is present in the repository. The backend imports a repo-root `.env`, and the frontend can use `frontend/.env.local` for local overrides.

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `DB_URL` | Optional | `jdbc:postgresql://localhost:5432/traffic_law` | Primary JDBC URL for the PostgreSQL database used by JPA, Liquibase, and the pgvector-backed vector store. |
| `DB_USERNAME` | Optional | `traffic_user` | Database username for the primary PostgreSQL connection. |
| `DB_PASSWORD` | Optional | `traffic_pass` | Database password for the primary PostgreSQL connection. |
| `OPENAI_API_KEY` | Optional | empty string | API key used by Spring AI's OpenAI chat model integration. A valid key is needed for OpenAI-backed chat requests. |
| `NEXT_PUBLIC_API_BASE_URL` | Optional | code fallback `http://localhost:8088` | Frontend API base URL in `frontend/lib/api/client.ts`. For local full-stack runs, set it to `http://localhost:8089` so the browser targets the backend's current default port. |
| `CI` | Optional | unset | Frontend Playwright config uses this flag to change retries, worker count, and `reuseExistingServer`. It affects test behavior, not backend runtime. |

## Config File Format

The backend runtime config is centered on `src/main/resources/application.yaml`:

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/traffic_law}
    username: ${DB_USERNAME:traffic_user}
    password: ${DB_PASSWORD:traffic_pass}
  ai:
    vectorstore:
      pgvector:
        schema-name: public
        table-name: kb_vector_store
        dimensions: 1536
    openai:
      api-key: ${OPENAI_API_KEY:}
  task:
    execution:
      pool:
        core-size: 4
        max-size: 10
        queue-capacity: 100
app:
  chat:
    retrieval:
      top-k: 5
    grounding:
      limited-threshold: 2
    case-analysis:
      max-clarifications: 2
server:
  port: 8089
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

`src/main/resources/application.properties` adds the backend application name and local CORS default:

```properties
spring.application.name=traffic-law-chatbot
app.cors.allowed-origins=http://localhost:3000
```

Typical frontend local override:

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8089
```

`src/main/resources/default-parameter-set.yml` is not a Spring property file. `DefaultParameterSetSeeder` reads it as a classpath resource and stores the file content in the `ai_parameter_set` table when the application starts with no parameter-set records.

Minimal structure of `default-parameter-set.yml`:

```yaml
model:
  name: openai
  temperature: 0.3
  maxTokens: 2048
retrieval:
  topK: 5
  similarityThreshold: 0.7
  groundingLimitedThreshold: 0.5
systemPrompt: "..."
caseAnalysis:
  maxClarifications: 2
  requiredFacts:
    - key: vehicleType
messages:
  disclaimer: "..."
```

- `src/main/resources/application.yaml` defines datasource settings, Liquibase changelog location, Spring AI OpenAI integration, pgvector metadata, async executor sizing, server port, and actuator exposure.
- `src/main/resources/application.properties` sets `spring.application.name` and the default backend CORS allowlist.
- `src/main/resources/default-parameter-set.yml` seeds the admin-managed parameter-set content stored in the database.
- The active parameter set is live runtime input for `ChatPromptFactory`, `AnswerCompositionPolicy`, `ClarificationPolicy`, and `RetrievalPolicy`.
- `VectorStoreConfig` duplicates the pgvector schema, table name, dimensions, distance type, and index type. If you change the vector-store layout, keep `application.yaml` and `VectorStoreConfig` aligned.
- `model.*` and `retrieval.groundingLimitedThreshold` exist in the seeded YAML, but no current application code reads those keys.
- `app.chat.case-analysis.max-clarifications` is still present in `application.yaml`, but `ClarificationPolicy` currently reads `caseAnalysis.maxClarifications` from the active parameter set instead.

## Required vs Optional Settings

The repository does not implement custom fail-fast validation for environment variables. Every `${...}` placeholder in `application.yaml` has a default value, so missing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `OPENAI_API_KEY` do not trigger a property-binding error on their own.

- `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are optional at property-resolution time because Spring falls back to local defaults. In practice, the application still needs a reachable PostgreSQL instance for normal database, Liquibase, and vector-store behavior.
- `OPENAI_API_KEY` is optional at property-resolution time and resolves to an empty string when unset. A repository smoke test still boots with a blank key, but a valid key is required for real OpenAI-backed chat completions.
- `NEXT_PUBLIC_API_BASE_URL` is optional because the frontend client falls back to `http://localhost:8088`, but the backend now defaults to `server.port: 8089`, so local frontend and backend runs should set this explicitly or provide a matching `frontend/.env.local`.
- `app.cors.allowed-origins` is optional because `application.properties` sets `http://localhost:3000` for local development. Any non-local frontend origin should override it.
- `app.chat.retrieval.top-k` and `app.chat.grounding.limited-threshold` are optional because they are defined in `application.yaml` and also have `@Value(...)` fallbacks in `ChatService`.
- Active parameter-set keys such as `systemPrompt`, `messages.*`, `caseAnalysis.maxClarifications`, `caseAnalysis.requiredFacts`, and `retrieval.similarityThreshold` are optional because `ActiveParameterSetProvider` falls back to hardcoded defaults or an empty map when the database has no active record or the YAML is missing a key.
- No `application-*.yaml`, `.env.production`, or `.env.test` files are checked in. The only environment-specific file in the repo is `frontend/.env.local`.

## Defaults

| Setting | Default | Where set |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/traffic_law` | `src/main/resources/application.yaml` |
| `DB_USERNAME` | `traffic_user` | `src/main/resources/application.yaml` |
| `DB_PASSWORD` | `traffic_pass` | `src/main/resources/application.yaml` |
| `OPENAI_API_KEY` | empty string | `src/main/resources/application.yaml` |
| `spring.application.name` | `traffic-law-chatbot` | `src/main/resources/application.properties` |
| `app.cors.allowed-origins` | `http://localhost:3000` | `src/main/resources/application.properties`; fallback in `CorsConfig` |
| `server.port` | `8089` | `src/main/resources/application.yaml` |
| `app.chat.retrieval.top-k` | `5` | `src/main/resources/application.yaml`; fallback in `ChatService` |
| `app.chat.grounding.limited-threshold` | `2` | `src/main/resources/application.yaml`; fallback in `ChatService` |
| `caseAnalysis.maxClarifications` | `2` | `src/main/resources/default-parameter-set.yml`; fallback in `ClarificationPolicy` |
| `retrieval.similarityThreshold` | `0.7` | `src/main/resources/default-parameter-set.yml`; fallback in `RetrievalPolicy` |
| `systemPrompt` | hardcoded Vietnamese fallback prompt | `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` |
| `messages.*` | hardcoded Vietnamese fallback strings | `AnswerCompositionPolicy` |
| `spring.task.execution.pool.core-size` | `4` | `src/main/resources/application.yaml` |
| `spring.task.execution.pool.max-size` | `10` | `src/main/resources/application.yaml` |
| `spring.task.execution.pool.queue-capacity` | `100` | `src/main/resources/application.yaml` |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | `src/main/resources/application.yaml` |
| `management.endpoint.health.show-details` | `when-authorized` | `src/main/resources/application.yaml` |
| pgvector layout | schema `public`, table `kb_vector_store`, dimensions `1536`, distance `COSINE_DISTANCE`, index `HNSW` | duplicated in `src/main/resources/application.yaml` and `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java` |
| `NEXT_PUBLIC_API_BASE_URL` | code fallback `http://localhost:8088`; recommended local override `http://localhost:8089` | `frontend/lib/api/client.ts`; optional `frontend/.env.local` |
| `CI` | unset, which means Playwright uses `retries: 0`, default worker count, and `reuseExistingServer: true` | `frontend/playwright.config.ts` |

If the active parameter set does not define `caseAnalysis.requiredFacts`, `ClarificationPolicy` falls back to built-in requirements for `vehicleType` and `violationType`, then adds `injuryStatus`, `alcoholStatus`, `licenseStatus`, or `documentStatus` when the question text indicates those scenarios.

## Per-Environment Overrides

No backend profile-specific config files are checked in. The repository currently uses one shared backend `application.yaml`, an optional root `.env`, and an optional frontend `frontend/.env.local`.

- Local development: place `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `OPENAI_API_KEY` in the repo-root `.env`. Create or update `frontend/.env.local` with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8089` so the frontend matches the backend's current default port, and leave `app.cors.allowed-origins` at `http://localhost:3000` unless the frontend runs on a different origin.
- Tests: no dedicated `.env.test` or `application-test.yaml` is present. `SpringBootSmokeTest` uses `@TestPropertySource` to exclude Google GenAI, Liquibase, datasource, and JPA auto-configuration rather than loading a separate test profile. Frontend Playwright uses `CI` when present to tighten retries and worker settings while keeping its dev server on `http://localhost:3000`.
- Staging and production: provide backend database credentials, `OPENAI_API_KEY`, frontend `NEXT_PUBLIC_API_BASE_URL`, and non-local CORS origins outside the repository. Because the frontend code fallback is still `http://localhost:8088` while the backend default port is `8089`, set the frontend API base URL explicitly in every non-local environment.
