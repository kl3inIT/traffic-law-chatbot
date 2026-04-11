<!-- generated-by: gsd-doc-writer -->
# Traffic Law Chatbot

Source-grounded Spring Boot backend for Vietnamese traffic-law Q&A, threaded case analysis, and admin-managed legal-source ingestion.

This repository contains the backend API. It ingests PDF, Word, structured regulation, and website-page sources into a pgvector-backed store, then answers chat requests with citations and source references.

## Requirements

- `Java 25`
- `PostgreSQL` with the `vector`, `hstore`, and `uuid-ossp` extensions available
- `OPENAI_API_KEY` for model-backed chat responses

## Installation

The Gradle wrapper downloads the required Gradle version automatically.

```bash
git clone https://github.com/kl3inIT/traffic-law-chatbot.git
cd traffic-law-chatbot
./gradlew build
```

On Windows, use:

```bash
git clone https://github.com/kl3inIT/traffic-law-chatbot.git
cd traffic-law-chatbot
gradlew.bat build
```

## Quick start

1. Configure the database and model credentials. The app reads `.env` via `spring.config.import`, or you can export the variables directly.

   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/traffic_law
   export DB_USERNAME=traffic_user
   export DB_PASSWORD=traffic_pass
   export OPENAI_API_KEY=your_openai_api_key
   ```

2. Start the API on port `8088`.

   ```bash
   ./gradlew bootRun
   ```

3. Verify the service is up.

   ```bash
   curl http://localhost:8088/actuator/health
   ```

   Expected result: a JSON payload with `status` set to `UP`.

4. Ingest and activate at least one legal source before expecting grounded answers from `/api/v1/chat`. Retrieval only uses chunks whose metadata is `APPROVED`, `trusted == true`, and `active == true`.

## Usage examples

### Ask a one-shot question

```bash
curl -X POST http://localhost:8088/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"Xe may vuot den do bi phat the nao?"}'
```

Expected result: a JSON response with fields such as `groundingStatus`, `responseMode`, `answer`, `citations`, and `sources`.

```json
{
  "groundingStatus": "GROUNDED",
  "responseMode": "STANDARD",
  "answer": "...",
  "citations": [
    {
      "label": "[Nguon 1]"
    }
  ],
  "sources": [
    {
      "sourceId": "..."
    }
  ]
}
```

### Start a threaded case analysis

```bash
curl -X POST http://localhost:8088/api/v1/chat/threads \
  -H "Content-Type: application/json" \
  -d '{"question":"Toi vuot den do thi sao?"}'

curl -X POST http://localhost:8088/api/v1/chat/threads/<thread-id>/messages \
  -H "Content-Type: application/json" \
  -d '{"question":"Toi di xe may"}'
```

Expected result: responses include a stable `threadId`. When the service needs more facts, it returns `responseMode: "CLARIFICATION_NEEDED"` with `pendingFacts`; once enough facts are present, it can return scenario analysis fields alongside the answer.

```json
{
  "threadId": "2f2a8c4a-0000-0000-0000-000000000000",
  "responseMode": "CLARIFICATION_NEEDED",
  "pendingFacts": [
    {
      "key": "vehicleType",
      "question": "..."
    }
  ]
}
```

### Queue a source for ingestion, then approve and activate it

```bash
curl -X POST http://localhost:8088/api/v1/admin/sources/url \
  -H "Content-Type: application/json" \
  -d '{"url":"https://vbpl.vn/","title":"Nguon phap ly","publisherName":"VBPL","createdBy":"admin"}'

curl http://localhost:8088/api/v1/admin/ingestion/jobs/<job-id>

curl -X POST http://localhost:8088/api/v1/admin/sources/<source-id>/approve \
  -H "Content-Type: application/json" \
  -d '{"reason":"Official legal source","actedBy":"admin"}'

curl -X POST "http://localhost:8088/api/v1/admin/sources/<source-id>/activate?actedBy=admin"
```

Expected result: the first call returns `202 Accepted` with `sourceId`, `jobId`, and `status: "QUEUED"`. After approval and activation, the ingested chunks become eligible for retrieval.

```json
{
  "sourceId": "5df9f0f0-0000-0000-0000-000000000000",
  "jobId": "63b8a7b7-0000-0000-0000-000000000000",
  "status": "QUEUED"
}
```

## License

No `LICENSE` file is present in this repository.
