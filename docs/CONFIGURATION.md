<!-- generated-by: gsd-doc-writer -->
# Configuration

This service reads runtime settings from `src/main/resources/application.yaml` and optionally imports a repo-root `.env` file through `spring.config.import: optional:file:.env[.properties]`. It also ships `src/main/resources/default-parameter-set.yml`, which is seeded into the `ai_parameter_set` table on first startup when no parameter-set records exist.

## Environment Variables

No `.env.example` file is present in the repository. The variables below are the ones referenced by `application.yaml`; the checked-in `.env` uses the same names.

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `DB_URL` | Optional | `jdbc:postgresql://localhost:5432/traffic_law` | Primary JDBC URL for the PostgreSQL database used by JPA, Liquibase, and the pgvector-backed vector store. |
| `DB_USERNAME` | Optional | `traffic_user` | Database username for the primary PostgreSQL connection. |
| `DB_PASSWORD` | Optional | `traffic_pass` | Database password for the primary PostgreSQL connection. |
| `OPENAI_API_KEY` | Optional | empty string | API key used by Spring AI's OpenAI chat model integration. A valid key is needed for OpenAI-backed chat requests. |

## Config File Format

The main application config is defined in `src/main/resources/application.yaml`:

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/traffic_law}
    username: ${DB_USERNAME:traffic_user}
    password: ${DB_PASSWORD:traffic_pass}
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
app:
  chat:
    retrieval:
      top-k: 5
    grounding:
      limited-threshold: 2
    case-analysis:
      max-clarifications: 2
server:
  port: 8088
```

- `src/main/resources/application.yaml` is the main runtime config file. It defines datasource settings, Liquibase changelog location, Spring AI OpenAI integration, pgvector metadata, async executor sizing, server port, and actuator exposure.
- `src/main/resources/application.properties` only sets `spring.application.name=traffic-law-chatbot`.
- `src/main/resources/default-parameter-set.yml` is not a Spring property file. `DefaultParameterSetSeeder` reads it as a classpath resource and stores the file content in the `ai_parameter_set` table when the application starts with no existing parameter sets.

Minimal structure of `default-parameter-set.yml`:

```yaml
model:
  name: openai
  temperature: 0.3
  maxTokens: 2048
retrieval:
  topK: 5
  similarityThreshold: 0.7
caseAnalysis:
  maxClarifications: 2
messages:
  disclaimer: "..."
```

- The parameter-set YAML is currently seed data for the admin parameter-set feature. The chat runtime still gets its live behavior from code in `ChatPromptFactory`, `AnswerCompositionPolicy`, `ChatService`, `ClarificationPolicy`, and `RetrievalPolicy`.
- `VectorStoreConfig` also hardcodes the pgvector schema, table name, dimensions, distance type, and index type. If you change the vector-store layout, keep `application.yaml` and `VectorStoreConfig` aligned.

## Required vs Optional Settings

The repository does not implement custom fail-fast validation for environment variables. Every `${...}` placeholder in `application.yaml` has a default value, so missing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `OPENAI_API_KEY` do not trigger a property-binding error on their own.

- `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are optional at property-resolution time because Spring falls back to local defaults. In practice, the application still needs a reachable PostgreSQL instance for normal database, Liquibase, and vector-store behavior.
- `OPENAI_API_KEY` is optional at property-resolution time and resolves to an empty string when unset. A repository smoke test still boots with a blank key, but a valid key is required for real OpenAI-backed chat completions.
- `app.chat.retrieval.top-k`, `app.chat.grounding.limited-threshold`, and `app.chat.case-analysis.max-clarifications` are optional because they are defined in `application.yaml` and also have code-level fallbacks in `ChatService` or `ClarificationPolicy`.
- No `application-*.yaml`, `.env.*`, or other profile-specific config files were found, so there is no separate in-repo required/optional matrix for dev, test, or production.

## Defaults

| Setting | Default | Where set |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/traffic_law` | `src/main/resources/application.yaml` |
| `DB_USERNAME` | `traffic_user` | `src/main/resources/application.yaml` |
| `DB_PASSWORD` | `traffic_pass` | `src/main/resources/application.yaml` |
| `OPENAI_API_KEY` | empty string | `src/main/resources/application.yaml` |
| `server.port` | `8088` | `src/main/resources/application.yaml` |
| `app.chat.retrieval.top-k` | `5` | `src/main/resources/application.yaml`; fallback in `ChatService` |
| `app.chat.grounding.limited-threshold` | `2` | `src/main/resources/application.yaml`; fallback in `ChatService` |
| `app.chat.case-analysis.max-clarifications` | `2` | `src/main/resources/application.yaml`; fallback in `ClarificationPolicy` |
| retrieval similarity threshold | `0.7` | `RetrievalPolicy.DEFAULT_SIMILARITY_THRESHOLD` |
| `spring.task.execution.pool.core-size` | `4` | `src/main/resources/application.yaml` |
| `spring.task.execution.pool.max-size` | `10` | `src/main/resources/application.yaml` |
| `spring.task.execution.pool.queue-capacity` | `100` | `src/main/resources/application.yaml` |
| pgvector layout | schema `public`, table `kb_vector_store`, dimensions `1536`, distance `COSINE_DISTANCE`, index `HNSW` | duplicated in `src/main/resources/application.yaml` and `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java` |

## Per-Environment Overrides

No environment-specific config files are checked in. The repository currently uses one shared `application.yaml` plus an optional root `.env`.

- Local development: place `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `OPENAI_API_KEY` in the repo-root `.env`. Spring imports that file automatically when it exists.
- Tests: no dedicated `.env.test` or `application-test.yaml` is present. Some tests build the relevant beans directly, and `SpringBootSmokeTest` uses `@TestPropertySource` to exclude database and Liquibase auto-configuration rather than loading a separate test config file.
- Staging and production: provide different values for the same environment variable names outside the repository. If you need different port, actuator, or task-executor settings, override the values from `application.yaml` in your deployment configuration rather than editing source defaults per environment.
