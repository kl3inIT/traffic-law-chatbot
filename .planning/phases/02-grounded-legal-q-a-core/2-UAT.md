---
status: partial
phase: 02-grounded-legal-q-a-core
source: [02-grounded-legal-q-a-core-03-SUMMARY.md]
started: 2026-04-08T13:52:45Z
updated: 2026-04-10T02:25:00Z
---

## Current Test

[testing paused — 1 items outstanding]

## Tests

### 1. Grounded Vietnamese chat answer
expected: Send a Vietnamese traffic-law question to the public chat endpoint. You should receive a relevant answer instead of a server error or empty payload.
result: pass

### 2. Source references are visible
expected: The chat response should include visible source references/citations so you can see what legal source the answer is grounded on.
result: blocked
blocked_by: prior-phase
reason: "Live endpoint returned REFUSED with empty citations and sources for the tested question because no sufficient approved retrieval hits were found."

### 3. Legal basis is included when supported
expected: For a question that maps to a specific rule, the response should include the relevant legal basis/căn cứ pháp lý.
result: issue
reported: "Live API test returned 500 Internal Server Error for a legal-basis question, and Postgres inspection showed 0 retrievable vectors matching the approval/trusted/active retrieval filter."
severity: blocker

### 4. Penalty and procedure details appear when relevant
expected: For a violation-style question, the response should include likely penalty/consequence and any relevant required documents or procedure steps when the source supports them.
result: blocked
blocked_by: prior-phase
reason: "Live API test returned 500 Internal Server Error, and Postgres inspection still showed 0 retrievable vectors matching the approval/trusted/active retrieval filter, so penalty/procedure output could not be validated live."

### 5. Disclaimer and next steps are present
expected: The response should clearly state that this is informational legal guidance, not formal legal advice, and should include recommended next steps.
result: issue
reported: "Live REFUSED response included the disclaimer but returned no recommended next steps."
severity: major

## Summary

total: 5
passed: 1
issues: 2
pending: 0
skipped: 0
blocked: 2

## Gaps

- truth: "The response should clearly state that this is informational legal guidance, not formal legal advice, and should include recommended next steps."
  status: failed
  reason: "User reported: Live REFUSED response included the disclaimer but returned no recommended next steps."
  severity: major
  test: 5
  artifacts: []
  missing: []
- truth: "For a question that maps to a specific rule, the response should include the relevant legal basis/căn cứ pháp lý."
  status: failed
  reason: "User reported: Live API test returned 500 Internal Server Error for a legal-basis question, and Postgres inspection showed 0 retrievable vectors matching the approval/trusted/active retrieval filter."
  severity: blocker
  test: 3
  artifacts: []
  missing: []
- truth: "For a violation-style question, the response should include likely penalty/consequence and any relevant required documents or procedure steps when the source supports them."
  status: failed
  reason: "User reported: Live API test returned 500 Internal Server Error, and Postgres inspection still showed 0 retrievable vectors matching the approval/trusted/active retrieval filter, so penalty/procedure output could not be validated live."
  severity: blocker
  test: 4
  artifacts: []
  missing: []

## Next Step

Resume with diagnosis and patch planning for ingestion/retrieval/chat failures before continuing manual UAT.
