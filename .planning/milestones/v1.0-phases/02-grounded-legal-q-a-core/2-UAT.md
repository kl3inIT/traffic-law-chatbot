---
status: pass
phase: 02-grounded-legal-q-a-core
source: [02-grounded-legal-q-a-core-03-SUMMARY.md]
started: 2026-04-08T13:52:45Z
updated: 2026-04-10T07:07:00Z
---

## Current Test

[live UAT completed]

## Tests

### 1. Grounded Vietnamese chat answer
expected: Send a Vietnamese traffic-law question to the public chat endpoint. You should receive a relevant answer instead of a server error or empty payload.
result: pass
notes: "Posted a UTF-8 JSON request with the Vietnamese question 'Xe máy vượt đèn đỏ bị phạt thế nào?' and received HTTP 200 with a structured LIMITED_GROUNDING answer."

### 2. Source references are visible
expected: The chat response should include visible source references/citations so you can see what legal source the answer is grounded on.
result: pass
notes: "Live response returned citations and sources with sourceId=7d3791ef-7c22-4043-bdf0-1fd7f7af9186 and sourceVersionId=2a5a3bee-3d12-43dd-89b4-d7be6ff890a2."

### 3. Legal basis is included when supported
expected: For a question that maps to a specific rule, the response should include the relevant legal basis/căn cứ pháp lý.
result: pass
notes: "Live response included a legalBasis entry grounded in Điều 7 Nghị định 168/2024/NĐ-CP."

### 4. Penalty and procedure details appear when relevant
expected: For a violation-style question, the response should include likely penalty/consequence and any relevant required documents or procedure steps when the source supports them.
result: pass
notes: "After re-uploading and re-indexing the DOCX source, the live response included penalties and requiredDocuments from the grounded source."

### 5. Disclaimer and next steps are present
expected: The response should clearly state that this is informational legal guidance, not formal legal advice, and should include recommended next steps.
result: pass
notes: "The live answer included the default disclaimer. Weak-grounding refusal/next-step behavior is also covered by chat tests; the readiness endpoint now returns 200 and no longer masks diagnostics behind a route conflict."

### 6. Retrieval readiness endpoint reports live counts
expected: Admin readiness diagnostics should be reachable so live UAT can verify approved/trusted/active eligibility.
result: pass
notes: "GET /api/v1/admin/chunks/readiness returned HTTP 200 with approvedChunks=1, trustedChunks=1, activeChunks=1, eligibleChunks=1 after the rerun source was approved and activated."

## Summary

total: 6
passed: 6
issues: 0
pending: 0
skipped: 0
blocked: 0

## Notes

- The original live failure was not the chat formatter itself. The active process on port 8088 was serving stale behavior while the database claimed the first DOCX ingestion had succeeded even though kb_vector_store remained empty.
- A fresh upload of the stored DOCX under the current code produced job bec9ffef-7f26-43d3-bb96-a6cd8393cf71, which completed successfully and inserted 1 vector row.
- After approve + activate, both `/api/v1/admin/index/summary` and `/api/v1/admin/chunks/readiness` reported the source as eligible for retrieval.
- The duplicate `Kết luận:` prefix observed during live chat UAT was fixed in `AnswerComposer` by normalizing draft conclusions before composing the final answer.

## Next Step

Phase 2 live UAT passed for the exercised ingestion -> approval -> activation -> readiness -> Vietnamese chat flow.
Use verification/commit artifacts to close the remaining phase tracking items.
