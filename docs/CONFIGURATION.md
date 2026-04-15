<!-- generated-by: gsd-doc-writer -->
# Configuration

This repository has three live configuration layers. The Spring Boot backend reads `src/main/resources/application.yaml` and `src/main/resources/application.properties`, and `application.yaml` optionally imports a repo-root `.env` through `spring.config.import`. The Next.js frontend can override its API base URL through `frontend/.env.local`. The backend also seeds `src/main/resources/default-parameter-set.yml` into the `ai_parameter_set` table on first boot, and runtime policy classes read the active parameter-set YAML from the database through `ActiveParameterSetProvider`.

## Environment Variables

No `.env.example`, `.env.sample`, `frontend/.env.example`, or `frontend/.env.sample` file is checked in. The checked-in code reads the following variables directly or through Spring placeholders:

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `DB_URL` | Optional | `jdbc:postgresql://localhost:5432/traffic_law` | JDBC URL for the primary PostgreSQL datasource used by Liquibase, JPA, chat-memory JDBC storage, and pgvector search. |
| `DB_USERNAME` | Optional | `traffic_user` | Database username for the primary PostgreSQL connection. |
| `DB_PASSWORD` | Optional | `traffic_pass` | Database password for the primary PostgreSQL connection. |
| `OPENAI_API_KEY` | Optional | `none` | Shared API key for Spring AI OpenAI auto-configuration. The same property is reused for the embedding client and for the router-backed chat clients built in `ChatClientConfig`. |
| `EMBEDDING_BASE_URL` | Optional | `https://platform.beeknoee.com/api/v1` | Base URL for the embedding endpoint under `spring.ai.openai.embedding.base-url`. |
| `EMBEDDING_MODEL` | Optional | `text-embedding-3-small` | Default embedding model name under `spring.ai.openai.embedding.options.model`. |
| `OPENAI_BASE_URL` | Optional | `http://localhost:20128` | Base URL for the chat-router endpoint exposed through `app.ai.base-url` and consumed by `ChatClientConfig`. |
| `CHAT_MODEL` | Optional | `cx/gpt-5.4` | Default chat model ID under `app.ai.chat-model`, used when a request does not specify a model or the requested model is unknown. |
| `EVALUATOR_MODEL` | Optional | `cx/gpt-5.4` | Default evaluator model ID under `app.ai.evaluator-model`, used when the active parameter set does not override `model.evaluatorModel`. |
| `NEXT_PUBLIC_API_BASE_URL` | Optional | code fallback `http://localhost:8088` | Frontend API base URL in `frontend/lib/api/client.ts`. Set it explicitly for local full-stack runs because the backend defaults to port `8089`. |
| `CI` | Optional | unset | Frontend Playwright flag that changes `forbidOnly`, retries, worker count, and `reuseExistingServer`. |

## Config File Format

The backend's primary checked-in config lives in `src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: traffic-law-chatbot
  config:
    import: optional:file:.env[.properties]
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/traffic_law}
    username: ${DB_USERNAME:traffic_user}
    password: ${DB_PASSWORD:traffic_pass}
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
  ai:
    vectorstore:
      pgvector:
        initialize-schema: false
        schema-name: public
        dimensions: 1536
        distance-type: COSINE_DISTANCE
        index-type: HNSW
        table-name: kb_vector_store
    chat:
      memory:
        repository:
          jdbc:
            initialize-schema: never
    openai:
      api-key: ${OPENAI_API_KEY:none}
      embedding:
        base-url: ${EMBEDDING_BASE_URL:https://platform.beeknoee.com/api/v1}
        api-key: ${OPENAI_API_KEY:none}
        embeddings-path: /embeddings
        options:
          model: ${EMBEDDING_MODEL:text-embedding-3-small}
  task:
    execution:
      pool:
        core-size: 4
        max-size: 10
        queue-capacity: 100
      thread-name-prefix: ingestion-
  threads:
    virtual:
      enabled: true
app:
  ai:
    base-url: ${OPENAI_BASE_URL:http://localhost:20128}
    chat-model: ${CHAT_MODEL:cx/gpt-5.4}
    evaluator-model: ${EVALUATOR_MODEL:cx/gpt-5.4}
    models:
      - id: cx/gpt-5.4
        display-name: GPT-5.4
      - id: cc/claude-sonnet-4-6
        display-name: Claude Sonnet 4.6
      - id: cc/claude-haiku-4-5-20251001
        display-name: Claude Haiku 4.5
  chat:
    retrieval:
      top-k: 5
server:
  port: 8089
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

`src/main/resources/application.properties` currently duplicates the application name and defines the CORS allowlist:

```properties
spring.application.name=traffic-law-chatbot
app.cors.allowed-origins=http://localhost:3000,http://127.0.0.1:3000
```

Typical frontend local override:

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8089
```

`src/main/resources/default-parameter-set.yml` is not a Spring property file. `DefaultParameterSetSeeder` loads it as a classpath resource and stores the file content in `ai_parameter_set.content` when the table is empty. The checked-in seed file has this shape:

```yaml
model:
  chatModel: ""
  evaluatorModel: ""
retrieval:
  topK: 5
  similarityThreshold: 0.25
systemPrompt: "..."
messages:
  disclaimer: "..."
  refusal: "..."
  limitedNotice: "..."
  refusalNextStep1: "..."
  refusalNextStep2: "..."
  refusalNextStep3: "..."
```

- `application.yaml` defines datasource settings, Liquibase changelog location, pgvector metadata, embedding client settings, chat-router defaults, worker-pool sizing, server port, and actuator exposure.
- `application.properties` carries the local CORS allowlist and repeats `spring.application.name` with the same value used in `application.yaml`.
- `default-parameter-set.yml` seeds the initial YAML payload stored in the database. At runtime, `ActiveParameterSetProvider` rereads the active row on each access instead of caching it in memory.
- `AllowedModelsController` exposes `app.ai.models` so the frontend can populate model dropdowns from backend config instead of hardcoding IDs.
- `VectorStoreConfig` duplicates the pgvector schema, table name, dimensions, distance type, and index type from `application.yaml`. Keep both locations aligned if you change the vector-store layout.
- The checked-in runtime consumers for parameter-set YAML are `ChatPromptFactory` (`systemPrompt`), `AnswerCompositionPolicy` (`messages.*`), `RetrievalPolicy` (`retrieval.similarityThreshold`), and `LlmSemanticEvaluator` (`model.evaluatorModel`).
- `model.chatModel` and `retrieval.topK` exist in the seeded YAML and the admin parameter editor, but the checked-in request path currently falls back to `app.ai.chat-model` and `app.chat.retrieval.top-k` for live chat requests.

## Required vs Optional Settings

The repository does not implement a custom fail-fast validator for environment variables. Every checked-in `${...}` placeholder has a default, and the frontend API client also has a hardcoded fallback.

- `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are optional at property-resolution time because Spring supplies defaults. They are still operationally required for normal backend behavior because Liquibase, JPA, JDBC chat memory, and pgvector all depend on PostgreSQL when those auto-configurations are enabled.
- `OPENAI_API_KEY`, `EMBEDDING_BASE_URL`, `EMBEDDING_MODEL`, `OPENAI_BASE_URL`, `CHAT_MODEL`, and `EVALUATOR_MODEL` are optional at property-resolution time because `application.yaml` supplies defaults. Real embedding or chat traffic still depends on those default endpoints and model IDs being valid in the target environment.
- `NEXT_PUBLIC_API_BASE_URL` is optional because the frontend client falls back to `http://localhost:8088`, but the backend defaults to `server.port: 8089`, so local and non-local frontend deployments should set it explicitly.
- `app.cors.allowed-origins` is optional because `application.properties` supplies a local allowlist and `CorsConfig` also has a `http://localhost:3000` fallback.
- `app.chat.retrieval.top-k` is optional because `application.yaml` defines `5` and `ChatService` also uses `@Value("${app.chat.retrieval.top-k:5}")`.
- `app.chat.grounding.limited-threshold` is optional in binding terms because `AppProperties` initializes it to `2`, but no checked-in main runtime path currently reads that property.
- Active parameter-set keys such as `systemPrompt`, `messages.*`, `retrieval.similarityThreshold`, and `model.evaluatorModel` are optional because `ActiveParameterSetProvider` falls back to hardcoded defaults or an empty map when the active DB row is missing or incomplete.
- Treat `app.ai.models` as required in checked-in backend config. `ChatClientConfig` iterates `modelProperties.models()` without a null fallback, and `AllowedModelsController` also depends on the list being present.
- No `application-*.yaml`, `application-*.yml`, `.env.production`, or `.env.test` file is checked in.

## Defaults

| Setting | Default | Where set |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/traffic_law` | `src/main/resources/application.yaml` |
| `DB_USERNAME` | `traffic_user` | `src/main/resources/application.yaml` |
| `DB_PASSWORD` | `traffic_pass` | `src/main/resources/application.yaml` |
| `OPENAI_API_KEY` | `none` | `src/main/resources/application.yaml` |
| `EMBEDDING_BASE_URL` | `https://platform.beeknoee.com/api/v1` | `src/main/resources/application.yaml` |
| `EMBEDDING_MODEL` | `text-embedding-3-small` | `src/main/resources/application.yaml` |
| `OPENAI_BASE_URL` | `http://localhost:20128` | `src/main/resources/application.yaml`; fallback in `ChatClientConfig` |
| `CHAT_MODEL` | `cx/gpt-5.4` | `src/main/resources/application.yaml` |
| `EVALUATOR_MODEL` | `cx/gpt-5.4` | `src/main/resources/application.yaml` |
| `app.ai.models` | `cx/gpt-5.4`, `cc/claude-sonnet-4-6`, `cc/claude-haiku-4-5-20251001` | `src/main/resources/application.yaml` |
| `spring.application.name` | `traffic-law-chatbot` | `src/main/resources/application.yaml` and `src/main/resources/application.properties` |
| `app.cors.allowed-origins` | `http://localhost:3000,http://127.0.0.1:3000` | `src/main/resources/application.properties`; fallback in `CorsConfig` is `http://localhost:3000` |
| `server.port` | `8089` | `src/main/resources/application.yaml` |
| `app.chat.retrieval.top-k` | `5` | `src/main/resources/application.yaml`; fallback in `ChatService` |
| `app.chat.grounding.limited-threshold` | `2` | `src/main/java/com/vn/traffic/chatbot/common/config/AppProperties.java` |
| `retrieval.topK` | seeded as `5`, but not used by the checked-in chat request path | `src/main/resources/default-parameter-set.yml` |
| `retrieval.similarityThreshold` | seeded as `0.25`; code fallback is `0.7` if the key is missing | `src/main/resources/default-parameter-set.yml`; fallback in `RetrievalPolicy` |
| `model.chatModel` | seeded as empty string and not read by the checked-in backend chat fallback path | `src/main/resources/default-parameter-set.yml` |
| `model.evaluatorModel` | seeded as empty string; evaluator falls back to `app.ai.evaluator-model` when it is blank or missing | `src/main/resources/default-parameter-set.yml`; fallback in `LlmSemanticEvaluator` |
| `systemPrompt` | hardcoded Vietnamese fallback prompt when the active parameter set has no `systemPrompt` | `ChatPromptFactory.SYSTEM_CONTEXT_FALLBACK` |
| `messages.disclaimer` | hardcoded Vietnamese fallback string | `AnswerCompositionPolicy.DEFAULT_DISCLAIMER` |
| `messages.refusal` | hardcoded Vietnamese fallback string | `AnswerCompositionPolicy.REFUSAL_MESSAGE` |
| `messages.limitedNotice` | hardcoded Vietnamese fallback string | `AnswerCompositionPolicy.LIMITED_NOTICE` |
| `messages.refusalNextStep1` | hardcoded Vietnamese fallback string | `AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NARROW_SCOPE` |
| `messages.refusalNextStep2` | hardcoded Vietnamese fallback string | `AnswerCompositionPolicy.REFUSAL_NEXT_STEP_NAME_DOCUMENT` |
| `messages.refusalNextStep3` | hardcoded Vietnamese fallback string | `AnswerCompositionPolicy.REFUSAL_NEXT_STEP_VERIFY_SOURCE` |
| `spring.ai.vectorstore.pgvector.initialize-schema` | `false` | `src/main/resources/application.yaml`; duplicated by `VectorStoreConfig` |
| `spring.ai.chat.memory.repository.jdbc.initialize-schema` | `never` | `src/main/resources/application.yaml` |
| `spring.task.execution.pool.core-size` | `4` | `src/main/resources/application.yaml` |
| `spring.task.execution.pool.max-size` | `10` | `src/main/resources/application.yaml` |
| `spring.task.execution.pool.queue-capacity` | `100` | `src/main/resources/application.yaml` |
| `spring.task.execution.thread-name-prefix` | `ingestion-` | `src/main/resources/application.yaml` |
| `spring.threads.virtual.enabled` | `true` | `src/main/resources/application.yaml` |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | `src/main/resources/application.yaml` |
| `management.endpoint.health.show-details` | `when-authorized` | `src/main/resources/application.yaml` |
| pgvector layout | schema `public`, table `kb_vector_store`, dimensions `1536`, distance `COSINE_DISTANCE`, index `HNSW` | `src/main/resources/application.yaml` and `src/main/java/com/vn/traffic/chatbot/common/config/VectorStoreConfig.java` |
| `NEXT_PUBLIC_API_BASE_URL` | code fallback `http://localhost:8088` | `frontend/lib/api/client.ts` |
| `CI` | unset, which yields `forbidOnly: false`, `retries: 0`, `workers: 2`, and `reuseExistingServer: true` | `frontend/playwright.config.ts` |

## Per-Environment Overrides

No backend profile-specific config file is checked in. The repository currently relies on one shared backend YAML file, one shared backend properties file, an optional repo-root `.env`, and an optional `frontend/.env.local`.

- Local development: put backend secrets and endpoint overrides in the repo-root `.env`. If you run the frontend against the local backend, create `frontend/.env.local` with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8089` so the browser points at the backend's current default port. The checked-in CORS allowlist already covers `http://localhost:3000` and `http://127.0.0.1:3000`.
- Tests: no `application-test.yaml`, `application-test.yml`, or `.env.test` file is checked in. `SpringBootSmokeTest` and `AppPropertiesTest` use `@TestPropertySource` to exclude Google GenAI, Liquibase, datasource, and JPA auto-configuration instead of loading a dedicated test profile. Frontend Playwright changes retries, worker count, and server reuse only when `CI` is set.
- Staging and production: provide database credentials, API keys, router and embedding endpoint overrides if the defaults do not apply, frontend `NEXT_PUBLIC_API_BASE_URL`, and the allowed frontend origins outside the repository. If the deployed model catalog differs from the checked-in `app.ai.models`, override that property through external Spring configuration because the checked-in YAML hardcodes the list.
