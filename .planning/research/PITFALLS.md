# Domain Pitfalls

**Domain:** Vietnam traffic-law AI chatbot with legal-document RAG, case analysis, public chat, and admin operations
**Researched:** 2026-04-07
**Overall confidence:** MEDIUM

## Research Memo

This project sits in an unusually failure-prone corner of the AI product space: legal-risk content, constantly amended source material, user-supplied fact patterns, and admin-driven ingestion over mixed document types and websites. The biggest mistakes are not generic "AI quality" mistakes. They are domain-specific failures of legal source control, citation integrity, case-analysis boundaries, and admin governance.

For this product, the core design principle should be: **never let the model act like the source of truth; the legal source package must be the source of truth.** That means retrieval must preserve document provenance, amendment status, effective dates, article-level citations, and confidence/coverage signals. It also means the UI and admin tooling must make uncertainty visible instead of hiding it.

The most dangerous failure mode is a believable but outdated or legally mismatched answer. In a Vietnam traffic-law assistant, users will often ask about penalties, confiscation, license suspension, required documents, administrative procedure, and edge-case factual scenarios. If the system retrieves an old decree, a commentary page instead of the controlling text, or a partial chunk that drops exceptions and thresholds, the answer can sound excellent while being legally wrong.

## Critical Pitfalls

Mistakes here can force architectural rework, create legal exposure, or make the product untrustworthy.

### Pitfall 1: Treating "fresh enough" legal content as acceptable
**What goes wrong:** The chatbot answers using superseded, partially amended, unofficially consolidated, or not-yet-effective legal text.

**Why it happens:** Teams build ingestion around file uploads and crawling, but not around legal-document lifecycle metadata such as issuer, document type, promulgation date, effective date, amendment relationships, replaced articles, and source authority.

**Consequences:** Users receive wrong fines, wrong procedures, or wrong document requirements. Trust collapses because the chatbot looks authoritative while citing stale law.

**Warning signs:**
- Same question returns different answers after re-ingestion of the same source set.
- Citations point to a decree/law number but omit effective date or amendment status.
- Admins cannot answer "which exact version of this article was in force on date X?"
- Retrieved chunks mix original text with amendment text without showing relationship.
- Website crawl content outranks official legal text in retrieval.

**Prevention:**
- Model legal sources as versioned records, not generic documents.
- Store source authority, promulgation date, effective date, status, amendment links, and jurisdiction metadata.
- Prefer official Vietnam legal portals and official ministry/government sources as primary authorities.
- Keep unofficial explanatory sources in a separate lower-trust collection.
- Require every answer to attach source provenance and effective-date context.
- Add freshness checks in admin workflows: "newer source exists", "source superseded", "effective date in future", "duplicate consolidated version".

**Detection:**
- Build regression questions around recently amended traffic-law provisions.
- Add monitoring for answers citing sources with missing or conflicting effective-date metadata.
- Sample answers weekly for "citation exists but controlling text is newer".

**Phase should address it:** Foundation / ingestion architecture phase.

### Pitfall 2: Weak citation integrity at article/clause level
**What goes wrong:** The system cites a document correctly but the cited article, clause, point, threshold, or exception does not actually support the generated answer.

**Why it happens:** Chunking is optimized for embedding recall instead of legal citation fidelity. Generation summarizes across chunks and invents legal linkages.

**Consequences:** The chatbot appears grounded while still hallucinating the legal basis. This is more dangerous than uncited hallucination because users trust the citation.

**Warning signs:**
- Citations consistently reference document titles without article/clause numbers.
- Answers include numeric penalties that are absent from the cited chunk.
- Long chunks contain many adjacent provisions and the model blends them.
- Internal reviewers need to manually open the full document to verify every answer.

**Prevention:**
- Chunk by legal structure: chapter → section → article → clause → point where possible.
- Preserve canonical citation fields in metadata and return them to the model explicitly.
- Require generation to quote or paraphrase only from retrieved support spans.
- Add answer-check pipelines that verify each claim has at least one supporting citation span.
- Reject or downgrade responses when support is only document-level, not provision-level.

**Detection:**
- Evaluate on a dataset where gold answers require precise article/clause citation.
- Track "citation mismatch rate" separately from generic answer quality.
- Flag answers containing numbers, sanctions, durations, thresholds, or confiscation claims without exact support.

**Phase should address it:** Retrieval design and answer-check phase.

### Pitfall 3: Using case analysis without explicit factual uncertainty handling
**What goes wrong:** Users describe real-life incidents, but the chatbot fills in missing facts and gives a deterministic legal conclusion.

**Why it happens:** Case-analysis prompts are designed like FAQ answering. The model tries to be helpful by inferring facts such as vehicle type, BAC level, license class, road type, repeat offense, injury severity, or whether documents were presented.

**Consequences:** Wrong penalty analysis, overconfident legal guidance, and serious user misunderstanding about likely outcomes.

**Warning signs:**
- Answers use definitive language despite ambiguous user input.
- The model rarely asks clarifying questions.
- Similar scenarios produce very different legal outcomes based on hidden assumptions.
- Reviewers notice phrases like "in this case you will be fined..." when key facts are missing.

**Prevention:**
- Split workflow into: fact extraction → missing-fact detection → clarifying questions → legal analysis.
- Force structured outputs for case analysis: known facts, assumed facts, unknown facts, relevant rules, possible outcomes.
- Ban deterministic penalty statements when required facts are unknown.
- Show users alternative branches: "if A, then...; if B, then...".
- Add prompts and UI language that clearly distinguish information from legal conclusion.

**Detection:**
- Test scenario sets with intentionally omitted facts.
- Measure clarifying-question rate before final analysis.
- Audit for answers containing conclusions unsupported by explicit facts.

**Phase should address it:** Case-analysis orchestration phase.

### Pitfall 4: Failing to separate controlling law from commentary and web content
**What goes wrong:** The retriever pulls law-firm articles, news summaries, SEO traffic-law explainers, or scraped blog posts alongside official text and treats them as equivalent.

**Why it happens:** All sources are embedded into one collection with similar ranking weight, or website crawling is added early because it looks easy.

**Consequences:** Non-authoritative interpretation outranks controlling law. Users get plausible but non-binding summaries.

**Warning signs:**
- Top retrieval results often come from general websites instead of official legal portals.
- Answers cite explanatory pages more often than source instruments.
- Admins cannot filter by authority tier in search or answer review.
- Crawled websites create many near-duplicate chunks with legal keywords.

**Prevention:**
- Separate collections or ranking tiers: official law, official guidance, trusted secondary explanation.
- Use authority-aware reranking so official legal text wins when both exist.
- Label all non-controlling sources as explanatory only.
- Require case analysis to ground primary conclusions in controlling law first.
- Crawl websites only after official-source ingestion and governance exist.

**Detection:**
- Track share of answers grounded primarily in official sources.
- Review top-k retrieval composition for common traffic-law queries.
- Alert when secondary sources outrank official sources for exact-law questions.

**Phase should address it:** Source-governance and retrieval-ranking phase.

### Pitfall 5: Prompt injection and hostile instructions hidden inside crawled sources
**What goes wrong:** Retrieved web content or uploaded documents contain text that tries to override system instructions, exfiltrate hidden prompts, or manipulate the answer.

**Why it happens:** RAG pipelines treat retrieved text as knowledge only, not as untrusted input. Website ingestion is especially exposed.

**Consequences:** The model may follow instructions embedded in source text, reveal internal behavior, suppress citations, or output irrelevant/unsafe content.

**Warning signs:**
- Strange answers appear only when certain crawled sources are retrieved.
- The model starts following instructions like "ignore previous guidance" from source text.
- Internal prompt fragments appear in outputs.
- A single document causes broad answer-quality degradation.

**Prevention:**
- Treat all retrieved content as untrusted.
- Separate system instructions from retrieved data in prompts and templates.
- Strip or quarantine obvious instruction-like patterns during ingestion.
- Use allowlists for crawl domains and prefer official domains.
- Log source IDs used in each answer so bad documents can be isolated quickly.
- Add automated adversarial tests against the retrieval corpus.

**Detection:**
- Seed a test document with instruction-like text and verify the model ignores it.
- Monitor for output patterns suggesting hidden-instruction compliance.
- Track answer anomalies by source/document ID.

**Phase should address it:** Ingestion security and evaluation phase.

### Pitfall 6: No legal-domain answer abstention policy
**What goes wrong:** The chatbot answers every question, including ones outside traffic-law scope, beyond source coverage, or requiring licensed legal judgment.

**Why it happens:** Product teams optimize for response rate and user delight instead of safe refusal and escalation.

**Consequences:** The bot overreaches into criminal liability, insurance disputes, court strategy, evidence assessment, or personalized legal advice beyond what the source package supports.

**Warning signs:**
- Few or no refusals despite broad user queries.
- The system gives advice on disputes, appeals strategy, or outcome predictions.
- Reviewers see answers based on "general knowledge" without retrieved support.
- Out-of-scope questions are answered in the same tone as covered topics.

**Prevention:**
- Define supported question classes explicitly: traffic-law explanation, likely administrative penalty ranges, document/procedure guidance, source-backed scenario branching.
- Define refusal/escalation rules for unsupported legal advice, missing-source cases, and low-confidence retrieval.
- Surface "I cannot determine from the available sources" as a valid outcome.
- Add UI disclaimers, but do not rely on disclaimers alone.

**Detection:**
- Track abstention rate for low-coverage queries.
- Review answers with zero or weak citations.
- Add eval cases for out-of-scope and legally sensitive asks.

**Phase should address it:** Public-answer policy and safety phase.

### Pitfall 7: Vietnamese legal language is chunked and normalized poorly
**What goes wrong:** Retrieval misses the right provision because the corpus lost diacritics, article numbering, tabular structure, point labels, appendices, or OCR quality from PDFs/Word files.

**Why it happens:** Generic document processing pipelines are used without legal-format preservation. OCR and conversion quality are treated as a backend detail rather than a retrieval dependency.

**Consequences:** Low recall, wrong citations, and brittle behavior on realistic Vietnamese queries.

**Warning signs:**
- Searches for known article numbers fail.
- Clauses/points disappear or merge during extraction.
- Tables of penalties become flattened unreadable text.
- Vietnamese queries with diacritics work differently from without-diacritic queries.

**Prevention:**
- Preserve document structure during extraction: titles, articles, clauses, points, tables, appendices.
- Store normalized and original text forms for retrieval.
- Use OCR quality checks and manual review for scanned legal documents.
- Build ingestion QA dashboards showing extraction errors before indexing.
- Keep article numbers and legal citations as first-class searchable fields.

**Detection:**
- Gold-set retrieval tests using article numbers, clause labels, and Vietnamese paraphrases.
- Extraction diff review between original document and indexed text.
- Monitoring for high failure rates on scanned PDFs and Word imports.

**Phase should address it:** Ingestion pipeline and corpus-QA phase.

### Pitfall 8: Answer quality evaluation is generic, not law-specific
**What goes wrong:** The team measures thumbs-up, latency, or generic groundedness, but not whether the answer identified the right legal instrument, provision, date, and uncertainty.

**Why it happens:** Standard RAG evaluation templates are reused without legal-domain criteria.

**Consequences:** The product can look healthy in dashboards while remaining legally unsafe.

**Warning signs:**
- No benchmark dataset for traffic-law scenarios.
- "Good answer" is judged mainly by fluency.
- Evaluation does not distinguish citation correctness from answer style.
- Recently amended regulations are not part of regression testing.

**Prevention:**
- Create a legal QA benchmark covering direct questions, procedural questions, and ambiguous case scenarios.
- Score separately for: source authority, citation precision, legal correctness, freshness, uncertainty handling, and abstention correctness.
- Include edge cases involving amendments, thresholds, repeat offenses, missing facts, and document procedures.
- Keep answer-check as a mandatory release gate for corpus and prompt changes.

**Detection:**
- Regression dashboard by legal topic and regulation version.
- Track failure clusters after each ingestion refresh.
- Review "highly fluent but legally unsupported" answers explicitly.

**Phase should address it:** Evaluation and release-governance phase.

### Pitfall 9: Admin functions are treated as convenience tooling instead of safety-critical controls
**What goes wrong:** Ingestion, parameter editing, answer checks, and source management are exposed through admin screens/API without strong guardrails, auditability, or operational separation.

**Why it happens:** Teams think the risky part is only the public chatbot. But in this architecture, admin operations directly change legal behavior.

**Consequences:** A bad prompt change, source upload, reranker tweak, or accidental deletion can degrade every answer immediately.

**Warning signs:**
- No audit trail for who changed prompts, retrieval settings, or source status.
- Production and test corpora are mixed.
- Source publish/unpublish actions have no review step.
- There is no rollback path for ingestion or parameter changes.

**Prevention:**
- Treat admin changes as configuration releases with audit logs and rollback.
- Separate draft vs published source states.
- Require review for high-impact changes: prompts, ranking weights, source trust tier, bulk ingestion, deletion.
- Keep immutable chat logs and answer-check records tied to configuration version.
- Even if full RBAC is deferred, do not defer basic admin gating and environment separation.

**Detection:**
- Verify every answer can be traced to corpus version + prompt/config version.
- Alert on bulk admin changes and unexpected retrieval-quality drops.
- Periodically rehearse rollback of a bad source publication.

**Phase should address it:** Admin-control and operations phase.

### Pitfall 10: Public UX hides uncertainty and provenance
**What goes wrong:** The interface presents confident narrative answers but makes the legal basis, limitations, and unresolved ambiguities hard to see.

**Why it happens:** Teams optimize for a smooth chat experience and keep citations, confidence, and assumptions collapsed or absent.

**Consequences:** Users over-trust the bot, especially in penalty and procedure scenarios.

**Warning signs:**
- Users copy the conclusion but ignore the cited source.
- Few clicks on citations because they are buried.
- Answers do not label assumptions or unknowns.
- Ambiguous scenarios read like final advice.

**Prevention:**
- Make source cards, effective dates, and article/clause references first-class UI elements.
- For case analysis, visibly separate facts provided, assumptions, missing facts, and possible outcomes.
- Use strong uncertainty language where coverage is partial.
- Show refusal/escalation states clearly, not as generic errors.

**Detection:**
- UX tests checking whether users can identify the controlling source and effective date.
- Track citation-click and source-open rates.
- Review support complaints for overconfidence or misunderstanding.

**Phase should address it:** Frontend answer-experience phase.

## Moderate Pitfalls

### Pitfall 11: Indexing everything into one embedding strategy
**What goes wrong:** Laws, decrees, FAQs, forms, procedures, and website pages are all chunked and embedded identically.

**Prevention:** Use source-type-specific chunking, metadata, and retrieval weights. Keep legal provisions separate from process guides and FAQs.

**Warning signs:** Retrieval quality varies wildly by source type; forms and announcements outrank laws.

**Phase should address it:** Retrieval implementation phase.

### Pitfall 12: No temporal reasoning for "what law applied at that time?"
**What goes wrong:** The system answers historical scenarios using current law only.

**Prevention:** Capture effective-date intervals and support query-time temporal filtering when users mention incident dates.

**Warning signs:** The bot ignores user-provided dates or always cites the latest version.

**Phase should address it:** Case-analysis and source-modeling phase.

### Pitfall 13: Procedure guidance ignores local/administrative workflow variance
**What goes wrong:** The bot overgeneralizes procedural steps that may differ by authority, document channel, or implementation practice.

**Prevention:** Distinguish controlling national rule from implementation guidance; label operational guidance separately and attach source scope.

**Warning signs:** Users report "the police office told me something different" and the system cannot explain the difference.

**Phase should address it:** Procedure-content design phase.

## Minor Pitfalls

### Pitfall 14: Treating chat logs only as analytics, not legal-quality evidence
**What goes wrong:** Logs are kept for usage metrics but not structured for retrieval/error investigation.

**Prevention:** Log query, retrieved sources, config version, answer-check result, and reviewer outcome in a privacy-conscious way.

**Warning signs:** Post-incident review cannot reconstruct why a bad answer happened.

**Phase should address it:** Observability phase.

### Pitfall 15: Deferring corpus curation because "admins can fix it later"
**What goes wrong:** The initial corpus becomes noisy and inconsistent, making later cleanup expensive.

**Prevention:** Start with a narrow, high-trust traffic-law corpus and explicit source inclusion criteria.

**Warning signs:** Large ingestion volume but low confidence in what is authoritative.

**Phase should address it:** Initial corpus phase.

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Source ingestion foundation | Outdated/amended law mixed together | Model legal-document versioning, authority, and effective dates from day one |
| Document extraction | Vietnamese legal structure lost in parsing | Preserve article/clause/point/table structure and run extraction QA |
| Retrieval design | Commentary outranks controlling law | Authority-tier retrieval and provision-level chunking |
| Public chat MVP | Overconfident answers to unsupported questions | Add abstention policy and mandatory citations |
| Case analysis | Deterministic analysis with missing facts | Structured fact-gap detection and clarifying questions |
| Admin panel | Unsafe prompt/source/config changes | Audit logs, review workflow, rollback, draft vs published states |
| Website crawling | Prompt injection and SEO-noise contamination | Domain allowlists, source quarantine, untrusted-content handling |
| Evaluation | Generic RAG metrics hide legal failures | Law-specific benchmark and regression suite |
| Frontend UX | Users miss provenance and uncertainty | Promote source cards, effective dates, and assumption labels |
| Production operations | Bad ingestion silently degrades answers | Monitor by corpus version, answer-check score, and source-authority mix |

## Recommended Planning Priorities

1. **First:** Build source governance, versioning, and metadata before broad ingestion.
2. **Second:** Build provision-level retrieval and citation verification before rich case analysis.
3. **Third:** Add case-analysis orchestration with fact-gap handling and abstention.
4. **Fourth:** Add admin controls, auditability, and safe publish workflows before scaling corpus updates.
5. **Fifth:** Improve UX only after provenance and uncertainty can be shown honestly.

## Confidence Notes

- **HIGH:** Prompt injection as a top risk for RAG systems that consume external content; need to treat retrieved content as untrusted. Supported by OWASP and broader secure-LLM guidance.
- **HIGH:** Need governance, monitoring, documentation, and human oversight for generative AI in high-impact settings. Supported by NIST AI RMF guidance.
- **MEDIUM:** Official Vietnam legal portals should be treated as primary authorities for controlling text. Supported by official-source indicators and domain context, but operational source hierarchy still needs project-specific validation.
- **MEDIUM:** Specific traffic-law workflow risks, citation-fidelity risks, and temporal-law modeling needs are domain-grounded conclusions based on legal RAG patterns plus project requirements; they should be validated during phase-specific benchmark creation.

## Sources

- Vietnam official legal document portal (official source indicator): https://vbpl.vn/
- OWASP GenAI Top 10, prompt injection risk page: https://genai.owasp.org/llmrisk/llm01-prompt-injection/
- NIST AI Risk Management Framework: https://www.nist.gov/itl/ai-risk-management-framework
- Anthropic prompt injection guide: https://www.anthropic.com/engineering/a-guide-to-prompt-injection
