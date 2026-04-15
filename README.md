<!-- generated-by: gsd-doc-writer -->
# Traffic Law Chatbot

Full-stack Vietnamese traffic-law assistant for operators who curate legal sources and users who need citation-backed answers.

This repository contains a Spring Boot `4.0.5` API in `src/` and a Next.js `16.2.3` frontend in `frontend/`. The backend ingests PDF, Word, structured regulation, and website page sources into a pgvector-backed store, then serves citation-backed chat, threaded scenario analysis, source-ingestion workflows, chunk inspection, trust-policy management, chat-log review, quality checks, and AI parameter-set management.

## Requirements

- `Java 25`
- `PostgreSQL` with the `vector`, `hstore`, and `uuid-ossp` extensions available
- `OPENAI_API_KEY` for chat and embedding requests
- `Node.js` and `pnpm` if you want to run the bundled web UI (`frontend/package.json` pins `pnpm@10.32.1`)

## Installation

No separate backend install step is required. The first Gradle command downloads `Gradle 9.4.1` and resolves backend dependencies automatically.

```bash
git clone https://github.com/kl3inIT/traffic-law-chatbot.git
cd traffic-law-chatbot
cd frontend
pnpm install
cd ..
```

## Quick start

1. Configure the database and model credentials. The backend reads a repo-root `.env` via `spring.config.import`, or you can export the variables directly.

   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/traffic_law
   export DB_USERNAME=traffic_user
   export DB_PASSWORD=traffic_pass
   export OPENAI_API_KEY=your_openai_api_key
   ```

   In PowerShell, set the same names with `$env:DB_URL=...`, `$env:DB_USERNAME=...`, `$env:DB_PASSWORD=...`, and `$env:OPENAI_API_KEY=...`.

   If your OpenAI-compatible chat router is not available at the default `http://localhost:20128`, also set `OPENAI_BASE_URL` before starting the backend.

2. Start the backend API on port `8089`.

   ```bash
   ./gradlew bootRun
   ```

   On Windows, use `gradlew.bat bootRun`.

3. In another terminal, start the frontend and point it at the current backend port.

   ```bash
   cd frontend
   export NEXT_PUBLIC_API_BASE_URL=http://localhost:8089
   pnpm dev
   ```

   In PowerShell, use `$env:NEXT_PUBLIC_API_BASE_URL='http://localhost:8089'` before `pnpm dev`. This override is required because the frontend client otherwise falls back to `http://localhost:8088`.

4. Open `http://localhost:3000` for the chat and admin UI, or verify the backend directly:

   ```bash
   curl http://localhost:8089/actuator/health
   ```

   Expected result: a JSON payload with `status` set to `UP`.

   Before expecting grounded answers from `/api/v1/chat`, ingest at least one legal source and then approve and activate it. Retrieval only uses chunks whose metadata is `APPROVED`, `trusted == true`, and `active == true`.

## Usage examples

### Ask a one-shot question

```bash
curl -X POST http://localhost:8089/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"Xe may vuot den do bi phat the nao?"}'
```

Expected result: the API responds with the standard `status` / `message` / `data` / `timestamp` envelope, and the `data` object contains the chat payload.

```json
{
  "status": 200,
  "message": "Chat answer",
  "data": {
    "groundingStatus": "GROUNDED",
    "threadId": null,
    "responseMode": "STANDARD",
    "answer": "...",
    "citations": [
      {
        "inlineLabel": "[1]",
        "sourceTitle": "...",
        "excerpt": "..."
      }
    ],
    "sources": [
      {
        "inlineLabel": "[1]",
        "sourceId": "5df9f0f0-0000-0000-0000-000000000000",
        "sourceTitle": "..."
      }
    ]
  },
  "timestamp": "2026-04-15T00:00:00Z"
}
```

### Start a threaded scenario analysis

```bash
curl -X POST http://localhost:8089/api/v1/chat/threads \
  -H "Content-Type: application/json" \
  -d '{"question":"Toi vuot den do thi sao?"}'

curl -X POST http://localhost:8089/api/v1/chat/threads/<thread-id>/messages \
  -H "Content-Type: application/json" \
  -d '{"question":"Toi di xe may"}'
```

Expected result: responses include a stable `data.threadId`. Threaded answers can return `SCENARIO_ANALYSIS`, `FINAL_ANALYSIS`, or `REFUSED` depending on grounding and whether the model produced structured scenario fields.

```json
{
  "status": 200,
  "message": "Message posted",
  "data": {
    "threadId": "2f2a8c4a-0000-0000-0000-000000000000",
    "responseMode": "FINAL_ANALYSIS",
    "answer": "...",
    "scenarioAnalysis": {
      "facts": [
        "Toi di xe may"
      ],
      "rule": "...",
      "outcome": "...",
      "actions": [
        "..."
      ],
      "sources": [
        {
          "inlineLabel": "[1]",
          "sourceId": "5df9f0f0-0000-0000-0000-000000000000",
          "sourceTitle": "..."
        }
      ]
    }
  },
  "timestamp": "2026-04-15T00:00:00Z"
}
```

### Queue a source for ingestion, then approve and activate it

```bash
curl -X POST http://localhost:8089/api/v1/admin/sources/url \
  -H "Content-Type: application/json" \
  -d '{"url":"https://vbpl.vn/","title":"Nguon phap ly","publisherName":"VBPL","createdBy":"admin"}'

curl http://localhost:8089/api/v1/admin/ingestion/jobs/<job-id>

curl -X POST http://localhost:8089/api/v1/admin/sources/<source-id>/approve \
  -H "Content-Type: application/json" \
  -d '{"reason":"Official legal source","actedBy":"admin"}'

curl -X POST "http://localhost:8089/api/v1/admin/sources/<source-id>/activate?actedBy=admin"
```

Expected result: the first call returns HTTP `202 Accepted` with the standard response envelope. Its `data` payload contains `sourceId`, `jobId`, and `status: "QUEUED"`. After approval and activation, the ingested chunks become eligible for retrieval.

```json
{
  "status": 201,
  "message": "URL ingestion accepted",
  "data": {
    "sourceId": "5df9f0f0-0000-0000-0000-000000000000",
    "jobId": "63b8a7b7-0000-0000-0000-000000000000",
    "status": "QUEUED"
  },
  "timestamp": "2026-04-15T00:00:00Z"
}
```

## License

No `LICENSE` file is present in this repository.
