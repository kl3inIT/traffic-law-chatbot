# Feature Landscape

**Domain:** Vietnam traffic-law AI chatbot
**Project:** Vietnam Traffic Law Chatbot
**Researched:** 2026-04-07
**Focus:** v1 should prioritize Vietnam traffic laws/regulations and support case analysis for real-life scenarios
**Overall confidence:** MEDIUM

## Research Memo

This domain is closer to a legal-assistant product than a generic customer-support chatbot. Users do not just want a short answer; they want an answer they can trust, tied to the relevant rule, with practical consequences explained in plain Vietnamese. In legal/regulatory domains, trust is the product. That makes source grounding, structured case analysis, and explicit uncertainty handling table stakes, not premium extras.

The strongest pattern across legal-assistant products is a combination of: conversational question answering, document-aware analysis, source-backed outputs, and controlled admin tooling around knowledge quality. For this project, the winning v1 is not “ChatGPT but about traffic.” It is a Vietnam traffic-law assistant that can: understand a real incident, identify the relevant regulation, explain likely penalty/procedure/documents, and show where the answer came from.

Because the project is greenfield and traffic-condition features are out of scope, v1 should stay narrow and credible. Build deeply for traffic-law scenarios, not broadly for all legal workflows. The best differentiator is not breadth; it is high-confidence, Vietnam-specific case guidance grounded in trusted legal sources.

## Feature Classification Principles

### Table stakes
Must-have capabilities. If missing, users will not trust or keep using the product.

### Differentiators
Valuable features that create competitive advantage after the foundation is solid.

### Anti-features
Tempting scope that should be deliberately excluded from v1 because it increases complexity faster than it increases trust or learning.

## Table Stakes

Features users will expect from a regulation-focused legal assistant. Missing these makes the product feel incomplete or unsafe.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Vietnamese-first legal Q&A | Core use case is asking traffic-law questions in Vietnamese | Low | Must handle colloquial phrasing, local legal terms, and common abbreviations |
| Source-backed answers with citations/reference links | Legal answers without sources are hard to trust | Medium | Every answer should cite regulation/article/decree/circular or source document chunk |
| Real-life case analysis | Project requirement explicitly says users describe scenarios, not just FAQs | High | Should extract facts, identify likely violation, explain applicable rule, and note uncertainty if facts are missing |
| Structured answer format | Users need actionable guidance, not a long paragraph | Medium | Recommended structure: conclusion, legal basis, likely fine/penalty, required documents, procedure, next steps |
| Clarifying-question flow | Real traffic incidents are incomplete or ambiguous | Medium | Ask follow-up questions when facts affect the legal outcome, e.g. vehicle type, BAC level, license status, location, injury/damage |
| Explain penalties and administrative consequences | Users care about fines, license suspension, vehicle impoundment, deadlines | Medium | Separate “likely outcome” from “guaranteed outcome” |
| Explain required documents and procedures | Users need practical compliance guidance after the legal answer | Medium | Include what to bring, which authority handles it, typical sequence of steps |
| Conversation memory within a session | Case analysis usually unfolds over multiple turns | Medium | Must preserve scenario facts through follow-up questions |
| Trusted-source retrieval only | Legal domain requires controlled knowledge quality | High | Prioritize official/state/legal publisher sources and approved admin-ingested materials |
| Basic answer disclaimer and uncertainty signaling | Legal guidance can be misunderstood as guaranteed legal advice | Low | Should clearly distinguish informational guidance from formal legal representation |
| Admin knowledge ingestion | System depends on curated regulatory sources | High | Must ingest PDF, Word, structured regulation docs, and website content |
| Admin retrieval inspection / answer check | Legal RAG needs ongoing trust validation | High | Admin should inspect answer quality, retrieved chunks, and failure cases |
| Chat logging for audit/review | Needed to improve prompts, retrieval quality, and detect bad answers | Medium | Especially important for high-stakes legal queries |
| AI parameter management | Required by project context and Jmix-like backend concept | Medium | Temperature, prompt templates, chunking/retrieval settings, answer policies |

## Differentiators

These features make the product meaningfully better than a basic law FAQ bot. They should be considered after table stakes are reliable, but one or two can be included in MVP if they directly support the core value proposition.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Case-analysis mode with fact extraction | Converts messy user narratives into structured legal analysis | High | Extract actors, vehicle type, conduct, place, timing, documents, enforcement action, injury/damage |
| Answer breakdown by “facts -> rule -> consequence -> action” | Increases trust and helps users understand reasoning | Medium | Best differentiator for a legal assistant because it explains rather than merely answers |
| Missing-facts detector | Prevents overconfident wrong answers | Medium | Example: “Outcome depends on whether the driver has a valid license class” |
| Regulation-version awareness / effective-date handling | Critical in law because old and new rules differ | High | Must show which regulation version/effective date answer relies on |
| Scenario templates for common incidents | Speeds time-to-value for non-expert users | Medium | Examples: DUI checkpoint, no license, expired registration, lane violation, traffic accident paperwork |
| Multi-source answer cross-checking | Improves reliability for overlapping rules and procedures | High | Compare retrieved sources and surface conflicts or ambiguity |
| Admin source quality scoring | Helps maintain a cleaner legal knowledge base | Medium | Mark source trust level, freshness, jurisdiction relevance, duplication |
| Answer quality evaluation dashboard | Enables iterative improvement and safer launch | High | Track groundedness, citation presence, unanswered questions, and admin review outcomes |
| Escalation recommendation | Safer handling of edge cases or disputes | Low | Suggest contacting lawyer/authority when injury, criminal exposure, conflicting facts, or outdated source risk appears |
| Side-by-side “simple explanation” and “legal basis” | Balances accessibility and legal precision | Medium | Especially useful for Vietnamese public users |
| Document-assisted case review | Lets user upload a ticket, notice, or form for explanation | High | Strong differentiator, but should be tightly scoped to traffic-law documents only |
| Comparative scenario analysis | Helps user ask “what changes if…” | Medium | Good for educational use: with license vs without license, first offense vs repeat offense |

## Anti-Features

These are tempting but should be deliberately excluded from v1.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Live traffic conditions, maps, congestion, route advice | Explicitly out of scope and distracts from legal/regulatory trust | Stay focused on laws, fines, procedure, and documents |
| Broad all-domain legal assistant | Expands retrieval, prompt design, and legal validation too early | Keep scope to Vietnam traffic law and related administrative procedure |
| Full lawyer-style document drafting suite | High complexity and weak fit for initial user value | Provide concise action guidance and explanation first |
| Automated appeal/complaint generation for every case | High legal/compliance risk; requires careful jurisdictional nuance | Offer procedural guidance and checklist, not full legal filing automation |
| Real-time government system integrations | High integration cost and likely unreliable/permission-bound | Start with static trusted sources and admin-managed updates |
| User role/permission matrix beyond simple public/admin | Explicitly deferred in project context | Keep a practical single-admin operational model for v1 |
| Manual human-in-the-loop curated Q&A workflow | Explicitly excluded and slows product learning loop | Use chat logs, answer checks, and admin quality review instead |
| Voice-first assistant | Adds UX and ASR complexity without improving legal accuracy | Build strong text-first Vietnamese experience |
| General conversational small talk persona features | Adds noise to a high-trust product | Keep tone professional, concise, and legally grounded |
| Predictive case-outcome promises | Dangerous in legal contexts because facts are often incomplete | Provide likely consequence ranges with caveats and source basis |

## Admin Capabilities Expected for a Jmix-like AI Admin Panel

These are not optional “back office nice-to-haves.” They are core product capabilities because legal-answer quality depends on admin control.

| Admin Capability | Why It Matters | Complexity | Notes |
|------------------|----------------|------------|-------|
| Source ingestion pipeline | Core operational capability for building the knowledge base | High | Support PDF, Word, structured legal docs, and websites |
| Source catalog / repository view | Admin needs to know what content is active | Medium | Include title, source URL/file, status, trust level, effective date, last updated |
| Re-index / sync controls | Legal content changes over time | Medium | Allow selective refresh rather than full reprocessing every time |
| Source approval / activation toggle | Prevents bad content entering production answers | Medium | Keep draft vs active states |
| Chunk/retrieval preview | Lets admin inspect whether legal text is retrievable correctly | High | Important for long decrees and multi-article documents |
| Prompt / answer-template management | Controls consistency of legal output | Medium | Templates for case-analysis and direct Q&A modes |
| Model and parameter settings | Needed for quality/cost tuning | Medium | Retrieval top-k, temperature, max context, citation requirements |
| Chat log explorer | Core for QA and product learning | Medium | Filter by date, query type, answer status, source coverage |
| Answer-check workflow | Required by project context | High | Run test questions, compare outputs, inspect citations/retrieval, mark pass/fail |
| Evaluation dataset / benchmark set | Allows regression testing across common legal scenarios | Medium | Should include canonical traffic-law questions and case narratives |
| Failure tagging / issue taxonomy | Makes improvement work systematic | Low | Tags like “no citation,” “wrong penalty,” “outdated rule,” “missed follow-up question” |
| Source freshness monitoring | Legal changes can invalidate answers | Medium | Flag potentially stale sources based on effective date or last review |
| Basic audit trail | Important for trust and admin accountability | Medium | Record source changes, parameter changes, and evaluation actions |

## Feature Dependencies

```text
Trusted-source ingestion -> Retrieval quality -> Source-backed answers
Trusted-source ingestion -> Source catalog/admin controls -> Freshness monitoring
Retrieval quality -> Case analysis -> Structured answer format
Clarifying-question flow -> Reliable case analysis -> Better penalties/procedure guidance
Conversation memory -> Multi-turn case analysis
Source-backed answers -> User trust -> Safe escalation recommendations
Prompt/answer-template management -> Consistent legal answer structure
Chat logging -> Answer-check workflow -> Evaluation dashboard
Source approval + freshness monitoring -> Regulation-version awareness
Document upload/analysis -> Document-assisted case review
```

## Recommended v1 MVP

Prioritize these first:

1. **Vietnamese-first traffic-law Q&A with source-backed answers**
   - This is the trust foundation.
2. **Structured case analysis for real-life scenarios**
   - This is the core product promise and should not be deferred.
3. **Penalty, required-document, and procedure guidance**
   - This turns an answer into practical user value.
4. **Clarifying-question flow for ambiguous scenarios**
   - Without this, case analysis becomes overconfident and brittle.
5. **Admin ingestion + source management + answer checks + chat logs**
   - This is required to keep the legal assistant reliable.

### One differentiator worth including in MVP

If the team includes only one differentiator in MVP, it should be:

- **Answer breakdown by facts -> rule -> consequence -> action**

Why: it directly improves trust, readability, and auditability while staying close to the core RAG/case-analysis architecture. It is more valuable for this domain than flashy breadth features.

## Recommended v1 Answer Shape

For both direct Q&A and case-analysis mode, answers should aim to include:

1. **Short conclusion**
2. **Relevant legal basis**
3. **Likely fine / penalty / administrative consequence**
4. **Required documents / procedure**
5. **What facts could change the outcome**
6. **Recommended next steps**
7. **Source references**

This answer shape should be treated as a product feature, not just a prompt detail.

## What to Defer to v1.1 or Later

| Feature | Why Defer |
|---------|-----------|
| Traffic ticket / notice upload analysis | Valuable, but requires careful OCR/document parsing and narrower validation |
| Comparative scenario simulation | Useful, but not essential before core case analysis is stable |
| Rich analytics dashboard | Start with operational answer-check and logs first |
| Deep source conflict resolution UI | Add once content volume and inconsistency actually justify it |
| Broader legal domains outside traffic | Avoid scope dilution before traffic-law trust is proven |

## Confidence Notes

| Area | Confidence | Notes |
|------|------------|-------|
| Source-backed legal Q&A as table stakes | HIGH | Supported by project context and current legal-assistant product patterns |
| Case analysis as core v1 requirement | HIGH | Explicit in project context and central to domain value |
| Admin ingestion/evaluation/logging as required capability | HIGH | Explicit in project context and consistent with legal-assistant operational needs |
| Document upload analysis as differentiator, not table stakes | MEDIUM | Common in advanced legal products, but not necessary for first trustworthy launch |
| Regulation-version awareness as differentiator trending toward table stakes over time | MEDIUM | Strongly justified by legal-domain reality, but may be staged after core MVP if source control is still simple |

## Bottom-Line Recommendation

For this project, treat the product as a **source-grounded traffic-law case assistant**, not as a broad chatbot. The real MVP is:

- ask in Vietnamese,
- describe a real incident,
- get a structured, cited explanation,
- understand likely penalties and procedure,
- and let admins continuously improve answer quality.

That is the minimum credible product. Everything else should be judged by whether it improves legal trust for Vietnam traffic-law scenarios.

## Sources

### High confidence
- Project context: `D:/ai/traffic-law-chatbot/.planning/PROJECT.md`
- Harvey official site: https://www.harvey.ai/
- Lexis+ AI official product page: https://www.lexisnexis.com/en-us/products/lexis-plus-ai.page

### Medium confidence
- General 2026 ecosystem discovery via web search on legal-assistant, regulation-chatbot, and government legal-chatbot patterns. Findings were used only to shape the market framing and were not treated as authoritative without official-product corroboration.
