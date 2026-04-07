---
created: 2026-04-07T19:51:10.644Z
title: Why no dynamic AI parameter management like Jmix AI backend
area: planning
files:
  - .planning/ROADMAP.md
  - .planning/REQUIREMENTS.md
  - .planning/STATE.md
---

## Problem

The current roadmap and completed Phase 1 backend foundation do not yet include dynamic AI parameter management comparable to the Jmix AI backend. This creates a gap between the reference system and the current delivery plan, especially for admin-controlled tuning of AI behavior without code changes.

The user wants this concern recorded in English for later follow-up: why the project currently lacks dynamic AI parameter management similar to the Jmix AI backend, and whether that capability should be added explicitly in a future phase or requirement.

## Solution

Review the Jmix AI backend's AI parameter management capabilities and compare them against the current roadmap, requirements, and phase sequencing.

Decide whether this is:
- already implicitly deferred to a later admin phase,
- missing from the v1 requirements and roadmap, or
- intentionally excluded and should stay out of scope.

If the feature should exist in v1, add a concrete requirement and plan work for admin-managed dynamic AI parameter sets, runtime activation, and backend APIs consistent with the Jmix-inspired admin scope.
