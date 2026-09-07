---
type: Engineering Log Entry
title: IR-5 — Score Explanation
description: Completion record for per-term score explanation via ExplainableSearcher, Ranker.evaluate, and the TermScoring hierarchy.
tags: [core, ranking, search, explainability, ir-5]
timestamp: 2026-09-07T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---

# Summary

IR-5 added a first-class score-explanation capability to the lexical retrieval engine. Every ranked score is now derivable from typed intermediates, and callers can request a full per-term breakdown for any document without running a separate scoring pass.

The implementation follows the **single-source-of-truth** principle established by IR-4: `Ranker.evaluate(term, posting, context)` is the fundamental method. The `score` default delegates to `evaluate().contribution()` — there is no independent scoring code path.

# Capability Overview

## Ranker.evaluate — the fundamental method

```
Ranker.evaluate(term, posting, context) → TermScoring
TermScoring.contribution()              → double   (same value score() returns)
```

`evaluate` computes the formula once and returns a typed record carrying every intermediate. `score(term, posting, context)` is a default that calls `evaluate(...).contribution()`. Ranker implementers override `evaluate` only.

## TermScoring hierarchy (unsealed)

| Concrete type | Exposes |
|---|---|
| `BinaryTermScoring` | `term`, `base` (0.0 or 1.0), `fieldBoost`, `contribution` |
| `TfIdfTermScoring` | `term`, `tf`, `sublinearTf`, `idf`, `base`, `fieldBoost`, `contribution` |
| `Bm25TermScoring` | `term`, `tf`, `idf`, `documentLength`, `averageDocumentLength`, `k1`, `b`, `normalization`, `base`, `fieldBoost`, `contribution` |

`TermScoring` is intentionally **unsealed** — a future ranker can implement it without modifying any existing file.

## FieldBoost

When a non-neutral `RankingContext` is applied and the posting carries per-field frequencies, `evaluate` attaches a `FieldBoost` record exposing:

- `fieldFrequencies` — raw per-field occurrence counts from the posting
- `effectiveWeights` — configured weight per field; unknown fields default to `1.0` (observable, not hidden)
- `boostFactor` — frequency-weighted average: `Σ(freq_f × weight_f) / Σ(freq_f)`
- `contribution = base × boostFactor`

## ScoreExplanation

```
ScoreExplanation(query, documentId, score, List<TermScoring> contributions)
```

`score` is the sum of `contribution()` values from `contributions`, accumulated in encounter order using `Double::sum`. `contributions` contains only terms that produced a posting for the document — non-matching query terms are absent.

## ExplainableSearcher — capability interface

```
Searcher
    ↑
ExplainableSearcher   (codex.ir.search)
    ↑
SimpleSearcher
```

`ExplainableSearcher` extends `Searcher` with one method:

```java
Optional<ScoreExplanation> explain(String query, String documentId)
```

`SimpleSearcher` implements it. `VectorSearcher` does not — it has no discrete term postings to attribute. Callers check `instanceof` before calling `explain`.

## analyzeQuery — shared pipeline

Both `searchDetailed` and `explain` delegate to the same private `analyzeQuery(String)` method on `SimpleSearcher`. The analyzed terms are always identical for a given query, guaranteeing score conservation (T-02) and query-analysis parity (T-27).

# Usage Example

```java
// Build a searcher (any Searchers.lexical(...) overload)
Searcher searcher = Searchers.lexical(indexSnapshot, corpusSnapshot,
        tokenizer, normalizer, Rankers.bm25(corpusSnapshot, indexSnapshot));

// Retrieve results
List<SearchResult> results = searcher.searchDetailed("java search");

// Explain the top result (capability check required)
if (searcher instanceof ExplainableSearcher es) {
    results.stream().findFirst().ifPresent(r -> {
        es.explain("java search", r.documentId()).ifPresent(explanation -> {
            System.out.println("Score: " + explanation.score());
            for (TermScoring ts : explanation.contributions()) {
                System.out.printf("  %-12s base=%.4f boost=%s contribution=%.4f%n",
                        ts.term(), ts.base(),
                        ts.fieldBoost().map(fb -> String.format("%.2f", fb.boostFactor())).orElse("none"),
                        ts.contribution());
            }
        });
    });
}
```

# Trade-offs

**Hot-path allocation.** Every `score(term, posting)` call now allocates one `TermScoring` record per matched `(term, posting)` pair. This is an intentional cost of keeping a single scoring source of truth. For the current in-memory corpus sizes (thousands of documents), the allocation is negligible. If allocation becomes a bottleneck at scale, an object-pool path could be introduced without changing the `Ranker` contract.

# Acceptance Evidence

| Canonical test | What it proves | Class |
|---|---|---|
| T-01 | `explain` returns `ScoreExplanation` with correct fields for a matching pair | `ExplainableSearcherTest` |
| T-02 | Score conservation for Binary, TF-IDF, BM25 (tolerance 1e-9) | `ExplainableSearcherTest` |
| T-03 | Non-matching terms absent from contributions | `ExplainableSearcherTest` |
| T-04–T-06 | `Optional.empty()` for unknown doc / null-blank query / all-stop-word query | `ExplainableSearcherTest` |
| T-07 | `BinaryTermScoring` shape | `TermScoringModelTest` |
| T-08, T-10, T-15 | `evaluate().contribution()` bit-equals `score()` for all three rankers | `RankersTest` |
| T-09, T-11, T-12 | Formula correctness — all intermediates match hand-computed reference values | `RankingEvaluateTest` |
| T-13 | k1=1.2, b=0.75 exposed from ranker internals | `RankersTest` |
| T-14 | BM25 zero-contribution guard (missing document length) | `RankersTest` |
| T-16–T-21 | Field-boost intermediates correct for title-only, body-only, multi-field, unknown-field, neutral-context, raw-content cases | `FieldAwareRankingTest` |
| T-22 | Full pre-IR-5 suite unchanged and green | (all test classes) |
| T-23 | `SearchResult` record shape unchanged | `TermScoringModelTest` |
| T-24–T-26 | Capability hierarchy: `SimpleSearcher instanceof ExplainableSearcher == true`; `VectorSearcher` is false; `Searcher` has no `explain` | `ExplainableSearcherTest` |
| T-27–T-30 | Query-analysis parity; duplicate terms; all-stop-word; determinism | `ExplainableSearcherTest` |

Final test count: 718 tests (252 core + 466 web), 0 failures.

# References

- Use-case specification and traceability matrix: [`docs/knowledge/use-cases/ir-5-score-explanation-use-cases.md`](../../use-cases/ir-5-score-explanation-use-cases.md)
- Domain model implementation: [`docs/engineering/agents/reports/ir-5-1-domain-model.md`](../../../engineering/agents/reports/ir-5-1-domain-model.md)
- Ranker migration: [`docs/engineering/agents/reports/ir-5-2-ranker-migration.md`](../../../engineering/agents/reports/ir-5-2-ranker-migration.md)
- ExplainableSearcher: [`docs/engineering/agents/reports/ir-5-3-explainable-searcher.md`](../../../engineering/agents/reports/ir-5-3-explainable-searcher.md)
- Architecture Review: [`docs/engineering/agents/reviews/ir-5-architecture-review.md`](../../../engineering/agents/reviews/ir-5-architecture-review.md)
