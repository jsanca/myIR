# Task — IR-5 Score Explanation: Use Cases and Engineering Plan

## Context

myIR has completed IR-0 through IR-4.

Current relevant capabilities include:

* lexical retrieval with BM25, TF-IDF and binary ranking;
* sparse TF/TF-IDF vector retrieval with cosine similarity;
* positional postings;
* field-aware postings;
* field-aware lexical boosting via `RankingContext` / `FieldWeights`;
* snapshot-based reads;
* batch indexing;
* 1,022 passing tests across core/web/app.

IR-5 Score Explanation is the next candidate we want to design.

Observability is a core project value.

The purpose of IR-5 is not merely debugging convenience.

We want myIR to be able to answer:

> Why did this document receive this score and rank here for this query?

This is also groundwork for future Rank Fusion, where multiple signals may contribute to a final ranking.

IR-6 Evaluation Harness will come after IR-5 and should remain separate. IR-6 answers:

> Is configuration A better than configuration B?

IR-5 answers:

> Why did this result receive this score?

Do not conflate these responsibilities.

## Important constraints

* Do not implement code.
* Do not redesign unrelated parts of myIR.
* Do not introduce dense vectors or fusion yet.
* Do not turn this into a generic observability framework.
* Keep the first IR-5 scope small, explicit, and testable.
* Preserve current architecture and idioms.
* The explanation must be derived from the same scoring path that produces the final score. Avoid a second independent recalculation path that can drift.

## Required investigation

Review the current implementation around:

* `Ranker`
* `Rankers`
* `RankingContext`
* `FieldWeights`
* `SimpleSearcher`
* `SearchResult`
* BM25 scoring
* TF-IDF scoring
* binary ranking
* field-aware boosting
* any existing score/matched-term structures

Use repository evidence to determine the narrowest architecture that fits the existing design.

## 1. Define IR-5 use cases

Produce a concise set of use cases.

At minimum consider:

### UC-1 — Explain a ranked result

Given:

* a query;
* a returned document;
* a ranking model;
* current ranking context;

the caller can inspect why the document received its final score.

The explanation should make visible the meaningful contributors to that result.

### UC-2 — Explain term-level contributions

For lexical ranking, expose how each matched query term contributed to the final document score.

Examples of potentially relevant information:

* query term;
* base ranker contribution;
* term frequency;
* document frequency / IDF-related contribution where relevant;
* document length normalization where relevant;
* field frequencies;
* field boost factor;
* final contribution after field weighting.

Do not require every ranker to expose meaningless fields.

### UC-3 — Explain field-aware boosting

For documents with field provenance, show how field matches affected the score.

The explanation should make it possible to distinguish cases such as:

* title match;
* body-only match;
* match across multiple fields;
* unknown field using default weight;
* no field-aware context.

### UC-4 — Preserve normal search behavior

A caller who does not request explanations should continue using search normally without having to know about explanation internals.

Determine whether explanation should be:

* always produced;
* optionally produced;
* produced through a dedicated detailed search path;
* or another mechanism.

Recommend one approach based on the current architecture.

### UC-5 — Compare two result explanations manually

A developer/researcher should be able to inspect why two documents ranked differently for the same query.

This does not mean implementing automatic comparison yet.

### UC-6 — Support future fusion without designing fusion now

The explanation model should not prevent future extension where score components may originate from multiple signals, such as:

* lexical score;
* proximity score;
* sparse vector score;
* dense vector score;
* field boosts;
* fusion strategies.

Do not implement those signals now.

The goal is only to avoid an explanation model that is irreparably lexical-specific if a small generalization can preserve clarity.

## 2. Define non-goals

Explicitly identify what IR-5 should NOT include.

Strong candidates:

* no evaluation metrics;
* no qrels;
* no experiment runner;
* no TREC export;
* no GUI;
* no persistent tracing;
* no production telemetry backend;
* no dense-vector explanation;
* no rank fusion implementation;
* no query profiling/latency tracing unless strictly needed.

## 3. Define the conceptual model

Propose the smallest useful model for score explanation.

Potential concepts to evaluate:

* `ScoreExplanation`
* `TermContribution`
* `FieldContribution`
* `ScoreComponent`
* `ExplainedSearchResult`
* or alternatives that better match current project style.

Do not assume these exact names.

For each proposed concept explain:

* responsibility;
* immutability expectations;
* relationship to existing `SearchResult`;
* whether it belongs in ranking, search, or another package;
* how it avoids coupling to a specific ranker unnecessarily.

## 4. Scoring API strategy

Analyze how the current `Ranker` API should evolve.

We need to preserve one source of truth for scoring.

Compare reasonable alternatives such as:

### Option A

```text
score(...)
explain(...)
```

with shared internal calculation.

### Option B

```text
evaluate(...) -> ScoreEvaluation
```

where the numeric score is one projection of a richer scoring result.

### Option C

Another design that better matches existing myIR patterns.

Discuss trade-offs:

* compatibility;
* accidental duplicate calculation;
* allocation overhead;
* readability;
* extensibility;
* testability;
* ranker implementation burden.

Recommend one.

## 5. Explainability granularity

Decide what the first slice should explain for each current lexical ranker:

### Binary

What is meaningful to expose?

### TF-IDF

What is meaningful to expose?

### BM25

What is meaningful to expose?

For BM25, determine which of the following belong in first-slice explanation:

* TF;
* DF;
* IDF;
* corpus document count;
* document length;
* average document length;
* k1;
* b;
* raw/base contribution.

Avoid exposing every intermediate variable merely because it exists mathematically.

Prefer information that helps a human understand ranking behavior.

## 6. Field-aware explanation

Design how field boosting should appear.

Today the effective behavior is conceptually:

```text
base term score
    ×
field boost factor
    =
final term contribution
```

The explanation should allow the user to understand where the boost factor came from.

Determine whether to expose:

* field frequency map;
* configured weight map;
* weighted aggregate;
* default field weight behavior;
* only the effective multiplier;
* or a subset.

Choose the smallest model that remains educational.

## 7. Invariants

Define important invariants, including at least:

### Score conservation

For an explained result, the explanation must reconcile exactly with the score used for ranking.

Conceptually:

```text
sum(final term contributions) == SearchResult.score
```

within the project's numerical tolerance.

### Neutral-context equivalence

Explanation must not alter scores.

### Ranker parity

Existing search behavior without explanation remains unchanged.

### Determinism

Same snapshot, query, ranker, and context should produce the same explanation.

### No hidden recomputation drift

There should not be one formula for ranking and another independently maintained formula for explanation.

## 8. Test cases

Define a concrete test matrix before implementation.

Include at least:

* binary ranker explanation;
* TF-IDF single-term explanation;
* TF-IDF multi-term score conservation;
* BM25 reference contribution;
* BM25 short vs long document explanation;
* title-vs-body field boost;
* multiple fields;
* unknown field default weight;
* no `RankingContext`;
* raw-content-only document;
* query term absent from document;
* multiple matched query terms;
* exact reconciliation of explained score vs ranked score;
* snapshot consistency.

Identify which tests are:

* unit;
* formula-level;
* integration/search-level.

## 9. Backward compatibility

Assess how much public API change IR-5 should introduce.

The current project strongly favors interface + factory patterns and incremental evolution.

Prefer:

* additive APIs;
* defaults where semantically safe;
* minimal caller breakage.

Do not preserve compatibility at the cost of creating a confused model, but explain any breakage clearly.

## 10. Performance / allocation considerations

IR-5 is an observability capability.

Explain whether producing explanations:

* should be opt-in;
* can allocate substantially more objects;
* should avoid impacting the normal search hot path.

Do not prematurely optimize.

State what should be measured later.

## 11. Relationship to IR-6

Document the boundary explicitly.

IR-5 output may later be useful for diagnosing why two runs differ, but IR-6 must not depend on score-explanation internals in order to calculate ranking metrics such as nDCG.

Conceptually:

```text
IR-5:
query + result
    -> why?

IR-6:
query + ranked docs + qrels
    -> better?
```

Keep them separable.

## 12. Engineering plan

Produce an implementation plan divided into small slices.

A likely shape might resemble:

* IR-5.1 — explanation domain model;
* IR-5.2 — ranker-level scoring/explanation contract;
* IR-5.3 — Binary/TF-IDF support;
* IR-5.4 — BM25 explanation;
* IR-5.5 — field-aware explanation;
* IR-5.6 — search API integration;
* IR-5.7 — documentation/examples.

Do not copy this structure mechanically.

Derive the slices from the architecture you recommend.

For every slice provide:

* objective;
* code areas affected;
* expected API change;
* tests required;
* acceptance criteria;
* risks;
* explicit non-goals.

Prefer slices that leave the build green independently.

## 13. Deliverable

Produce an IR-5 design / engineering-plan report containing:

1. Executive Summary
2. Problem Statement
3. Use Cases
4. Non-Goals
5. Existing Architecture Constraints
6. Alternatives Considered
7. Recommended Conceptual Model
8. Ranker API Evolution
9. Explanation Semantics per Ranker
10. Field-Aware Explanation
11. Invariants
12. Test Matrix
13. Backward Compatibility
14. IR-6 Boundary
15. Engineering Slices
16. Risks / Open Questions
17. Definition of Done

## Definition of Done

The task is complete when:

* IR-5 has explicit user/research use cases;
* explanation semantics are clear for Binary, TF-IDF, and BM25;
* field-aware boosting is explainable;
* score and explanation have a single computational source of truth;
* the normal search path remains clean;
* the model is extensible enough for future scoring signals without prematurely designing rank fusion;
* IR-5 and IR-6 responsibilities are clearly separated;
* a test-first engineering plan exists;
* implementation can proceed in independently verifiable slices;
* no production code has been changed.
