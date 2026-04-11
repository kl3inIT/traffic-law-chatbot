<!-- generated-by: gsd-doc-writer -->
# API

This service exposes public chat endpoints under `/api/v1/chat` and admin endpoints under `/api/v1/admin/*`. Examples below assume the local default base URL `http://localhost:8088`.

Public chat answers are retrieval-gated: the service only searches chunks whose metadata matches `approvalState == 'APPROVED' && trusted == 'true' && active == 'true'`.

## Authentication

The repository does not configure Spring Security, API keys, JWT, session cookies, or controller-level authorization annotations.

- No `Authorization` header, bearer token, or `x-api-key` header is required by the current code.
- All routes in this document are currently unauthenticated at the application layer.
- The `/api/v1/admin/*` prefix is a naming convention only. It does not enforce admin access by itself.

## Endpoints Overview

### Public Chat

| Method | Path | Description | Auth Required |
| --- | --- | --- | --- |
| `POST` | `/api/v1/chat` | Ask a one-shot question and receive a structured answer with citations and source references. | No |
| `GET` | `/api/v1/chat/threads` | List saved chat threads as summary objects. | No |
| `POST` | `/api/v1/chat/threads` | Create a thread from the first user question and return the first threaded answer. | No |
| `POST` | `/api/v1/chat/threads/{threadId}/messages` | Post a follow-up message to an existing thread and receive the next threaded answer. | No |

### Source And Ingestion Admin

| Method | Path | Description | Auth Required |
| --- | --- | --- | --- |
| `POST` | `/api/v1/admin/sources` | Create a source record directly with initial state `DRAFT` + `PENDING` + `UNTRUSTED`. | No |
| `GET` | `/api/v1/admin/sources` | List sources in a paged envelope. The `status` query parameter is accepted but currently ignored by the controller. | No |
| `GET` | `/api/v1/admin/sources/{sourceId}` | Fetch source detail. The response currently includes `versions: []`. | No |
| `POST` | `/api/v1/admin/sources/{sourceId}/approve` | Approve a pending source and update chunk approval metadata. | No |
| `POST` | `/api/v1/admin/sources/{sourceId}/reject` | Reject a pending source and update chunk approval metadata. | No |
| `POST` | `/api/v1/admin/sources/{sourceId}/activate` | Activate an approved source and mark its chunks `trusted=true` and `active=true`. | No |
| `POST` | `/api/v1/admin/sources/{sourceId}/deactivate` | Disable a source and revoke chunk trust/activity flags. | No |
| `POST` | `/api/v1/admin/sources/upload` | Upload a file for async ingestion via `multipart/form-data`. | No |
| `POST` | `/api/v1/admin/sources/url` | Queue a URL for async ingestion after scheme and host validation. | No |
| `GET` | `/api/v1/admin/ingestion/jobs` | List ingestion jobs in a paged envelope, optionally filtered by `status`. | No |
| `GET` | `/api/v1/admin/ingestion/jobs/{jobId}` | Fetch a single ingestion job. | No |
| `POST` | `/api/v1/admin/ingestion/jobs/{jobId}/retry` | Retry a failed ingestion job and return the updated job. | No |
| `POST` | `/api/v1/admin/ingestion/jobs/{jobId}/cancel` | Cancel a queued or running ingestion job and return the updated job. | No |

### Inspection And Parameter Admin

| Method | Path | Description | Auth Required |
| --- | --- | --- | --- |
| `GET` | `/api/v1/admin/chunks` | List indexed chunks, optionally filtered by `sourceId` and `sourceVersionId`, in a paged envelope. | No |
| `GET` | `/api/v1/admin/chunks/readiness` | Return retrieval-readiness counts for approved, trusted, active, and eligible chunks. | No |
| `GET` | `/api/v1/admin/chunks/{chunkId}` | Return a single chunk with content and metadata fields. | No |
| `GET` | `/api/v1/admin/index/summary` | Return aggregate index totals and retrieval-readiness totals. | No |
| `GET` | `/api/v1/admin/parameter-sets` | List AI parameter sets as a plain JSON array. | No |
| `GET` | `/api/v1/admin/parameter-sets/{parameterSetId}` | Fetch one AI parameter set. | No |
| `POST` | `/api/v1/admin/parameter-sets` | Create a new AI parameter set. | No |
| `PUT` | `/api/v1/admin/parameter-sets/{parameterSetId}` | Update an existing AI parameter set. | No |
| `DELETE` | `/api/v1/admin/parameter-sets/{parameterSetId}` | Delete an inactive AI parameter set. | No |
| `POST` | `/api/v1/admin/parameter-sets/{parameterSetId}/activate` | Mark one parameter set as active and deactivate all others. | No |
| `POST` | `/api/v1/admin/parameter-sets/{parameterSetId}/copy` | Copy an existing parameter set and return the new record. | No |

## Request/Response Formats

JSON endpoints accept UTF-8 request bodies. Validation is handled with Jakarta Bean Validation plus controller/service checks.

### Public Chat Payloads

All public chat write endpoints accept the same JSON body shape:

```json
{
  "question": "Xe may vuot den do bi phat the nao?"
}
```

Representative `ChatAnswerResponse`:

```json
{
  "groundingStatus": "GROUNDED",
  "threadId": "11111111-1111-1111-1111-111111111111",
  "responseMode": "FINAL_ANALYSIS",
  "answer": "Ket luan [Nguon 1]",
  "conclusion": "Nguoi dieu khien xe may co the bi xu phat.",
  "disclaimer": "Thong tin chi nham muc dich tham khao, khong thay the tu van phap ly chinh thuc.",
  "uncertaintyNotice": null,
  "legalBasis": [
    "Dieu 6 [Nguon 1]"
  ],
  "penalties": [
    "Phat tien tu 800.000 dong den 1.000.000 dong [Nguon 1]"
  ],
  "requiredDocuments": [
    "Giay phep lai xe"
  ],
  "procedureSteps": [
    "Chuan bi giay to theo yeu cau"
  ],
  "nextSteps": [
    "Doi chieu tinh huong voi co quan co tham quyen"
  ],
  "pendingFacts": [
    {
      "code": "vehicleType",
      "prompt": "Ban dieu khien loai phuong tien nao?",
      "reason": "Thieu loai phuong tien"
    }
  ],
  "rememberedFacts": [
    {
      "key": "vehicleType",
      "value": "xe may",
      "status": "ACTIVE"
    }
  ],
  "scenarioAnalysis": {
    "facts": [
      "Nguoi dung dieu khien xe may"
    ],
    "rule": "Ap dung Dieu 6",
    "outcome": "Co the bi xu phat",
    "actions": [
      "Doi chieu bien ban"
    ],
    "sources": [
      {
        "inlineLabel": "[Nguon 1]",
        "sourceId": "source-1",
        "sourceVersionId": "version-1",
        "sourceTitle": "Nghi dinh 100",
        "origin": "https://vbpl.vn/nd100",
        "pageNumber": 4,
        "sectionRef": "Dieu 6"
      }
    ]
  },
  "citations": [
    {
      "inlineLabel": "[Nguon 1]",
      "sourceId": "source-1",
      "sourceVersionId": "version-1",
      "sourceTitle": "Nghi dinh 100",
      "origin": "https://vbpl.vn/nd100",
      "pageNumber": 4,
      "sectionRef": "Dieu 6",
      "excerpt": "Nguoi dieu khien xe may..."
    }
  ],
  "sources": [
    {
      "inlineLabel": "[Nguon 1]",
      "sourceId": "source-1",
      "sourceVersionId": "version-1",
      "sourceTitle": "Nghi dinh 100",
      "origin": "https://vbpl.vn/nd100",
      "pageNumber": 4,
      "sectionRef": "Dieu 6"
    }
  ]
}
```

Notes:

- `threadId` is `null` for one-shot `POST /api/v1/chat` responses and populated for threaded responses.
- `groundingStatus` is one of `GROUNDED`, `LIMITED_GROUNDING`, or `REFUSED`.
- `responseMode` is one of `STANDARD`, `CLARIFICATION_NEEDED`, `SCENARIO_ANALYSIS`, `FINAL_ANALYSIS`, or `REFUSED`.
- `GET /api/v1/chat/threads` returns a plain JSON array of `ChatThreadSummaryResponse` objects rather than a paged envelope.

### Admin Write Payloads

Representative source-creation request:

```json
{
  "sourceType": "PDF",
  "title": "Nghi dinh 100",
  "originKind": "URL_IMPORT",
  "originValue": "https://vbpl.vn/nd100.pdf",
  "publisherName": "Chinh phu",
  "languageCode": "vi",
  "createdBy": "admin"
}
```

Representative approval request:

```json
{
  "reason": "Official legal source",
  "actedBy": "admin"
}
```

`POST /api/v1/admin/sources/upload` is the only multipart endpoint. It expects:

- `file`: the uploaded binary file
- `metadata`: JSON matching `UploadSourceRequest`

Representative `metadata` part:

```json
{
  "title": "Nghi dinh 100",
  "publisherName": "Chinh phu",
  "createdBy": "admin"
}
```

Representative URL-ingestion request:

```json
{
  "url": "https://vbpl.vn/traffic",
  "title": "Traffic source",
  "publisherName": "VBPL",
  "createdBy": "admin"
}
```

Representative async-acceptance response from upload or URL ingestion:

```json
{
  "sourceId": "5df9f0f0-0000-0000-0000-000000000000",
  "jobId": "63b8a7b7-0000-0000-0000-000000000000",
  "status": "QUEUED"
}
```

Representative AI parameter-set request:

```json
{
  "name": "Lower temperature",
  "content": "model:\n  name: openai\nretrieval:\n  topK: 5\n"
}
```

Notes:

- `POST /api/v1/admin/sources/{sourceId}/activate` and `POST /api/v1/admin/sources/{sourceId}/deactivate` take `actedBy` as a query parameter, not a JSON body.
- URL ingestion validates the URL before any DB write. Only `http` and `https` schemes are allowed, and loopback, link-local, site-local, and other private/internal hosts are rejected.
- `CreateAiParameterSetRequest` and `UpdateAiParameterSetRequest` require non-blank `name` and non-blank `content`; `content` is capped at 65536 characters.

### List Response Envelope

`GET /api/v1/admin/sources`, `GET /api/v1/admin/ingestion/jobs`, and `GET /api/v1/admin/chunks` return the shared `PageResponse<T>` envelope:

```json
{
  "content": [],
  "pageNumber": 0,
  "number": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

The duplicate `pageNumber` and `number` fields are both populated from the same Spring `Page.getNumber()` value.

## Error Codes

Errors are returned as Spring `ProblemDetail` responses from `GlobalExceptionHandler`.

Validation problems use an `errors` map:

```json
{
  "detail": "Validation failed",
  "instance": "/api/v1/chat",
  "properties": {
    "errors": {
      "question": "must not be blank"
    }
  }
}
```

Application exceptions use an `errorCode` property:

```json
{
  "detail": "Chat thread not found: 11111111-1111-1111-1111-111111111111",
  "instance": "/api/v1/chat/threads/11111111-1111-1111-1111-111111111111/messages",
  "properties": {
    "errorCode": "CHAT_THREAD_NOT_FOUND"
  }
}
```

| Status | Meaning | Notes |
| --- | --- | --- |
| `400 Bad Request` | Bean-validation failures, malformed UUID path values, blank upload title/file, disallowed URL schemes/hosts, or invalid state transitions such as retrying a non-`FAILED` job. | Validation responses expose `properties.errors`; application-level 400s expose `properties.errorCode` such as `VALIDATION_ERROR` or `URL_NOT_ALLOWED`. |
| `404 Not Found` | Missing source, ingestion job, chat thread, or AI parameter set. | Returned with `SOURCE_NOT_FOUND`, `JOB_NOT_FOUND`, `CHAT_THREAD_NOT_FOUND`, or `PARAMETER_SET_NOT_FOUND`. |
| `500 Internal Server Error` | Ingestion, parsing, unexpected runtime errors, and current chunk lookup failures. | `GET /api/v1/admin/chunks/{chunkId}` currently throws `INGESTION_FAILED` when the chunk does not exist, so a missing chunk is documented as a 500 rather than a 404. |

Additional observations:

- `ErrorCode` defines `CHAT_GROUNDING_INSUFFICIENT` with HTTP `422`, but the current chat endpoints do not throw it. When grounding is insufficient, they return `200 OK` with `groundingStatus: "REFUSED"` instead.
- `ErrorCode` also defines `DUPLICATE_SOURCE` with HTTP `409`, but no current controller path was found that throws it.

## Rate Limits

No application-level rate limiting was found in this repository.

- `build.gradle` does not include a rate-limiting dependency such as Bucket4j or a gateway SDK.
- No controller, filter, interceptor, or middleware in `src/main/java` applies per-route throttling.
- The API does not emit `X-RateLimit-*` headers.

If this service is exposed beyond a trusted internal boundary, add throttling outside the app or introduce an explicit Spring-side rate-limiting layer before relying on the `/api/v1/admin/*` routes in production.
