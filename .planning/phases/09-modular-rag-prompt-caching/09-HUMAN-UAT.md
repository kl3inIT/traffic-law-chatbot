---
status: partial
phase: 09-modular-rag-prompt-caching
source: [09-VERIFICATION.md]
started: 2026-04-18T21:47:35+07:00
updated: 2026-04-18T21:47:35+07:00
---

## Current Test

[awaiting human testing]

## Tests

### 1. VietnameseRegressionIT — 20-query live regression
expected: ≥95% accuracy on 20-query Vietnamese benchmark; refusal parity preserved; two-turn memory holds across follow-up query. Run with `OPENROUTER_API_KEY` set.
result: [pending]

### 2. CitationFormatRegressionIT — byte-for-byte JSON contract parity (ARCH-05)
expected: `ChatAnswerResponse` JSON output byte-identical to Phase-8 baseline for representative queries. Run with `OPENROUTER_API_KEY` set.
result: [pending]

### 3. EmptyContextRefusalIT — verbatim refusal wording (T-9-02)
expected: When retriever returns empty context, response refusal text matches verbatim the Phase-8 wording. Run with `OPENROUTER_API_KEY` set.
result: [pending]

### 4. Post-deploy 50-sample trust-tier audit (D-03 manual review)
expected: Manually review 50 production samples post-deploy; confirm trust-tier filtering and citation provenance behave per policy. Monitor retry rate for Pitfall 6 tightening decision.
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
