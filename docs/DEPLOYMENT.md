<!-- generated-by: gsd-doc-writer -->
# Deployment

## Deployment Targets

The repository currently builds a Spring Boot executable JAR, so the deployable unit is the application artifact produced by Gradle rather than a checked-in container image or platform-specific bundle.

| Target | Status | Repository evidence |
| --- | --- | --- |
| Java 25 runtime on a VM, bare-metal host, or externally built container image | Supported | [`build.gradle`](../build.gradle) applies the Spring Boot plugin, pins the Java toolchain to `25`, and `bootJar` produces `build/libs/traffic-law-chatbot-0.0.1-SNAPSHOT.jar`. |
| Docker Compose | Not ready as an application deployment target | [`compose.yaml`](../compose.yaml) contains only a fully commented PostgreSQL example, and [`application.yaml`](../src/main/resources/application.yaml) sets `spring.docker.compose.enabled=false`. |
| Platform-specific services such as Vercel, Netlify, Fly.io, Railway, or Serverless Framework | No in-repo target detected | No `Dockerfile`, `vercel.json`, `netlify.toml`, `fly.toml`, `railway.json`, `serverless.yml`, or root `.github/workflows/*.yml` files are present. |

## Build Pipeline

No CI/CD pipeline detected.

The current in-repo artifact pipeline is manual:

1. Build the executable JAR with the Gradle wrapper.

   ```bash
   ./gradlew bootJar
   ```

   On Windows:

   ```bash
   gradlew.bat bootJar
   ```

2. Publish or copy the generated artifact from `build/libs/traffic-law-chatbot-0.0.1-SNAPSHOT.jar` to the target runtime.
3. Start the service with a Java 25 runtime.

   ```bash
   java -jar build/libs/traffic-law-chatbot-0.0.1-SNAPSHOT.jar
   ```

If you prefer the broader verification path before release, `./gradlew build` also runs the test suite and packages the same application.

## Environment Setup

See [CONFIGURATION.md](CONFIGURATION.md) for the full settings matrix. For production deployment, the minimum runtime setup is:

| Setting | Purpose |
| --- | --- |
| `DB_URL` | Points Spring Data JPA, Liquibase, and the pgvector store at the production PostgreSQL instance. |
| `DB_USERNAME` | Database username for the primary datasource. |
| `DB_PASSWORD` | Database password for the primary datasource. |
| `OPENAI_API_KEY` | Required for OpenAI-backed chat completions. The checked-in config binds `spring.ai.openai.api-key` to this variable. |
| `app.cors.allowed-origins` | Must be overridden from the default `http://localhost:3000` so production clients can call `/api/**`. |
| `server.port` | Optional override if the target runtime should not listen on the default port `8088`. |

Additional production requirements verified from the codebase:

- Liquibase is enabled in [`application.yaml`](../src/main/resources/application.yaml), so application startup will apply the changelog in [`db.changelog-master.xml`](../src/main/resources/db/changelog/db.changelog-master.xml) automatically.
- The PostgreSQL database must support the `vector`, `hstore`, and `uuid-ossp` extensions used by the Liquibase changelogs in [`001-schema-foundation.xml`](../src/main/resources/db/changelog/001-schema-foundation.xml) and [`003-vector-store-schema.xml`](../src/main/resources/db/changelog/003-vector-store-schema.xml).
- The current defaults in `application.yaml` are local-development values. Production deployments should provide externalized values instead of relying on the fallback datasource credentials.

## Rollback Procedure

No automated rollback workflow is defined in the repository.

1. Keep the previous application JAR available when promoting a new build.
2. If a deployment fails at runtime, stop the current process and restart the previous JAR with the same environment configuration.
3. If the failed release already applied Liquibase changes, handle the database rollback separately before or during the application rollback.
4. No Liquibase rollback blocks are defined in `src/main/resources/db/changelog`, so database recovery currently depends on your operational backup or restore procedure rather than an in-repo rollback command.

## Monitoring

This project includes Spring Boot Actuator via [`build.gradle`](../build.gradle) and exposes a small set of HTTP management endpoints in [`application.yaml`](../src/main/resources/application.yaml).

- `/actuator/health` for service health checks
- `/actuator/info` for basic application metadata
- `/actuator/metrics` for in-process metrics

The repository does not contain Sentry, Datadog, New Relic, Prometheus, or OpenTelemetry configuration for external monitoring or alert routing. `management.endpoint.health.show-details=when-authorized` is enabled, so detailed health output depends on how access control is configured in the runtime environment.
