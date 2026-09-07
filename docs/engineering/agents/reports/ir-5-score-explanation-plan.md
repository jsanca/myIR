---
type: Engineering Log Entry
title: "IR-5 Score Explanation — Design and Engineering Plan"
description: "Design and slice-by-slice implementation plan for IR-5. Defines use cases, non-goals, conceptual model, ranker API evolution, invariants, test matrix, and delivery slices. No production code changed."
tags: [core, ranking, explainability, design, engineering-plan]
timestamp: 2026-09-05T00:00:00Z
ckf_version: "0.1"
ckf_status: proposed
ckf_scope: core
ckf_owner: project
---

# IR-5 Score Explanation — Design and Engineering Plan

## 1. Executive Summary

IR-5 answers the question **"Why did this document receive this score and rank here for this query?"** by adding a *derived* observation layer on top of the existing scoring pipeline. It introduces no new scoring behavior, no evaluation metrics, and no independent recalculation path.

The recommended design is:

- Introduce a small, typed explanation model (`ScoreExplanation`, sealed `TermContribution` hierarchy, `FieldBoost`) that mirrors the existing scoring structure of `SimpleSearcher`.
- Evolve `Ranker` by adding a fundamental `evaluate(term, posting, context) → TermScoring` method that returns the full intermediate breakdown *and* the final numeric contribution. `score(...)` becomes a default projection: `evaluate(...).contribution()`. This guarantees a single source of truth — the search hot path and the explanation path execute the exact same code.
- Add an on-demand `Searcher.explain(query, documentId) → Optional<ScoreExplanation>` API that reconstructs the per-document explanation after search. The normal search path is unchanged and pays zero allocation cost.
- Enforce score-conservation as a test invariant: for every ranker and every query, the sum of explained term contributions equals `SearchResult.score` within numerical tolerance.

Deliverable size: **6 slices**, each independently buildable and green.

## 2. Problem Statement

Today the searcher returns `SearchResult(documentId, document, score, matchedTerms)`. Two documents may appear side-by-side with scores that differ by orders of magnitude, and no code path can explain the difference. A developer or researcher currently has to instrument rankers by hand or re-derive the formula from the interface JavaDoc.

Observability is a core project value and IR-5 makes this observable. It is also the precondition for future work — Rank Fusion (multiple signals combined into a final score) is meaningless without a way to inspect per-signal contributions.

## 3. Use Cases

**UC-1 — Explain a ranked result.** Given (query, returned document, ranker, ranking context), the caller inspects the meaningful contributors to the document's final score.

**UC-2 — Explain term-level contributions.** For lexical ranking, expose per-matched-term contributions. Each ranker exposes only meaningful fields — binary shows presence; TF-IDF shows tf/idf/base; BM25 shows tf/idf/dl/normalization/base; field boosting adds the boost breakdown on top when applicable.

**UC-3 — Explain field-aware boosting.** For documents with field provenance, show the field-frequency-weighted boost factor, the effective per-field contribution, whether unknown fields fell back to the default weight of 1.0, and whether the context was neutral.

**UC-4 — Preserve normal search behavior.** The `Searcher.search(...)` and `searchDetailed(...)` hot paths are unchanged and unaffected by IR-5. Callers who do not want explanations pay nothing.

**UC-5 — Compare two result explanations manually.** A developer can call `explain(query, docA)` and `explain(query, docB)` and inspect the term-level differences. No automatic comparison utility ships in IR-5.

**UC-6 — Support future fusion without designing fusion now.** The explanation model is per-ranker but the top-level `ScoreExplanation` structure (a list of contributions summing to the final score) is generalizable. A future `FusedRanker` can produce contributions that reference sub-ranker explanations. IR-5 does not implement fusion, dense vectors, or proximity scoring.

## 4. Non-Goals

Explicitly excluded from IR-5:

- No evaluation metrics (nDCG, MRR, precision@k) — that is IR-6.
- No qrels, benchmark queries, or experiment runner.
- No TREC-format export.
- No GUI or visualization.
- No persistent tracing, log sinks, or telemetry backend.
- No dense-vector or semantic-similarity explanation.
- No rank fusion implementation.
- No query profiling, timing, or latency tracing.
- No changes to `VectorSearcher`. Vector explanation is out of scope for IR-5 (see §14).
- No changes to indexing, tokenization, normalization, or corpus statistics.

## 5. Existing Architecture Constraints

Code review of `Ranker`, `Rankers`, `RankingContext`, `FieldWeights`, `SimpleSearcher`, `SearchResult`, `Posting`, and `TermWeightingUtils` establishes:

1. **`Ranker` today exposes two methods**: `idf(term)` and `score(term, posting)`, plus a `default score(term, posting, RankingContext)` that applies field boost. Every ranker implements only `idf` and `score(term, posting)`. Field boosting lives entirely on the interface default and computes `boostFactor = Σ(fieldFreq[f]×weight[f]) / Σ(fieldFreq[f])`.
2. **`SimpleSearcher.searchDetailed`** loops matched postings, calls `ranker.score(term, posting, rankingContext)`, and sums per document via `Double::sum`. The final document score is therefore `Σ over matched terms of ranker.score(term, posting, ctx)`.
3. **`SearchResult`** is a lean record: `documentId, document, score, matchedTerms`. Adding an optional explanation field on it would leak observation-time concerns into the result contract.
4. **Snapshots** (`CorpusSnapshot`, `IndexSnapshot`) are the frozen inputs to a ranker. Given the same snapshots and same context, all scoring is deterministic — this makes on-demand explanation trivially reconstructible.
5. **Style constraints from CLAUDE.md**: interface + factory pattern; records over bare maps; primitives first; no hidden reflection; public methods ≤20 lines; validate nulls with `Objects.requireNonNull`; sealed hierarchies and records are the preferred way to model shape variance.

These constraints rule out any approach based on `Map<String, Object>` scoring bags, mutable builders during ranking, or reflection-driven serialization.

## 6. Alternatives Considered

### Option A — parallel `score()` and `explain()` methods

Each ranker implements two methods: `score(term, posting, ctx)` for the hot path and `explain(term, posting, ctx)` returning a rich record. Both must independently produce identical numbers.

- **Pros:** zero allocation for `score`; the hot path is unchanged.
- **Cons:** two code paths per ranker. Score-vs-explain drift is possible on any change. This is exactly what the task warns against ("no hidden recomputation drift"). Enforced only by tests, not by construction.

### Option B — single `evaluate(...) → TermScoring` method, `score` is a projection

Each ranker implements `evaluate(term, posting, ctx) → TermScoring`. `TermScoring` carries intermediates *and* the final contribution. `Ranker.score(...)` becomes a default method returning `evaluate(...).contribution()`.

- **Pros:** genuinely one source of truth by construction — impossible for score and explanation to disagree. Ranker implementers write one method instead of two.
- **Cons:** the hot path allocates one small `TermScoring` per posting per matched term. In practice: 100 matched documents × 3 query terms = 300 records — trivial for a JVM. If a benchmark later shows this is a bottleneck, we can revisit.

### Option C — after-the-fact reconstruction only

`Ranker` API is unchanged. `Searcher.explain(query, docId)` re-runs the ranker's `score(...)` for the requested document, and separately calls a `stats(term, posting)` observer that recomputes the intermediates.

- **Pros:** zero hot-path cost.
- **Cons:** duplicate computation logic per ranker; the "no drift" invariant becomes a discipline problem, not a structural guarantee. Rejected for the same reason as Option A.

### Recommendation

**Option B**, combined with an **on-demand `Searcher.explain(query, documentId)` API**. Option B guarantees no drift; the on-demand searcher method keeps the standard `search(...)`/`searchDetailed(...)` hot paths completely untouched and pays for explanation only when explicitly requested. The one `TermScoring` allocation per scored posting is acceptable for the current use case; if profiling later shows a problem, a future variant of `SimpleSearcher` could call a lower-allocation path.

## 7. Recommended Conceptual Model

Four types, all records/sealed, all in `codex.ir.ranking`. Placement in the ranking package (not `search`) reflects that explanations describe *ranking* not *retrieval*; a future non-`SimpleSearcher` caller of a ranker gets the same explanation shape.

### 7.1 `TermScoring` — the ranker-level result

Sealed interface. Every ranker returns an implementation whose fields are meaningful for *that* ranker.

```
sealed interface TermScoring
    permits BinaryTermScoring, TfIdfTermScoring, Bm25TermScoring {
    String term();
    double base();                            // pre-boost contribution from the ranker
    Optional<FieldBoost> fieldBoost();        // present when a non-neutral boost was applied
    double contribution();                    // final value used in scoring — base × boostFactor
}
```

- `BinaryTermScoring(term, base, fieldBoost, contribution)` — `base` is `1.0` or `0.0`.
- `TfIdfTermScoring(term, tf, sublinearTf, idf, base, fieldBoost, contribution)`.
- `Bm25TermScoring(term, tf, idf, documentLength, averageDocumentLength, k1, b, normalization, base, fieldBoost, contribution)`.

The sealed hierarchy encodes that "meaningful statistics differ per ranker" as a type distinction, not a stringly-typed bag. Adding a future ranker (e.g., proximity) requires adding a new permit — an explicit, reviewable change.

### 7.2 `FieldBoost` — the field-boost breakdown

Record. Present on `TermScoring` only when the boost was non-neutral (`context.fieldWeights().isNeutral() == false` and `posting.fieldFrequencies().isEmpty() == false`).

```
record FieldBoost(
    Map<String, Integer> fieldFrequencies,   // from Posting
    Map<String, Double> effectiveWeights,    // per-field weight actually used (default 1.0 shown explicitly)
    double weightedSum,                      // Σ(freq × weight)
    int totalFrequency,                      // Σ(freq)
    double boostFactor                       // weightedSum / totalFrequency
) {}
```

The `effectiveWeights` map makes UC-3's "unknown field defaulted to 1.0" observable — it lists every field mentioned in the posting with the weight actually applied.

### 7.3 `TermContribution` — the per-term entry in a document explanation

Alias/wrapper record; carries the `TermScoring` produced by the ranker for one (term, posting) pair. Kept as its own record so a future fusion ranker can decorate it with a `sourceSignal` label.

```
record TermContribution(TermScoring scoring) {
    double contribution() { return scoring.contribution(); }
    String term()         { return scoring.term(); }
}
```

If keeping this thin wrapper feels redundant during implementation, we can inline it and use `TermScoring` directly in `ScoreExplanation.contributions()`. Decision deferred to IR-5.1; both shapes are compatible with the rest of the plan.

### 7.4 `ScoreExplanation` — the per-document explanation

Record. Contains the query the caller asked about, the model name, the reconstructed final score, and the ordered list of contributions.

```
record ScoreExplanation(
    String query,
    String rankerName,
    String documentId,
    double score,
    List<TermContribution> contributions
) {
    // Invariant enforced in compact constructor:
    // Math.abs(score - contributions.stream().mapToDouble(TermContribution::contribution).sum()) < TOLERANCE
}
```

Placing the score-conservation check inside the compact constructor makes the invariant *constructive* — it is impossible to build an inconsistent `ScoreExplanation`.

### Non-inclusion: `ExplainedSearchResult`

We deliberately do *not* add an `ExplainedSearchResult` type that decorates `SearchResult`. Explanations are produced on demand for a specific document; callers who want to correlate results with explanations can do so by document id. Introducing a decorated result type would either force explanation into the hot search path (rejected) or create a second search API returning it (unneeded duplication).

## 8. Ranker API Evolution

The recommended contract change is additive:

```
public interface Ranker {
    double idf(String term);

    // NEW — the fundamental scoring method returning intermediates + final contribution
    TermScoring evaluate(String term, Posting posting, RankingContext context);

    // EXISTING — projects the numeric value from evaluate; the default now delegates
    default double score(String term, Posting posting, RankingContext context) {
        return evaluate(term, posting, context).contribution();
    }

    // EXISTING — still meaningful for callers that construct a Posting directly without a context
    default double score(String term, Posting posting) {
        return score(term, posting, RankingContext.neutral());
    }
}
```

### Migration approach per ranker

- `BinaryRanker`: implement `evaluate` returning `BinaryTermScoring`. Delete the direct `score(term, posting)` implementation; keep the interface default.
- `TfIdfRanker`: same — move logic from `score(term, posting)` into `evaluate`, capturing `tf`, `sublinearTf`, and `idf` in the returned `TfIdfTermScoring`.
- `Bm25Ranker`: same — capture `tf`, `documentLength`, `averageDocumentLength`, `k1`, `b`, `normalization`, `idf`, `base`.

Field boost currently applied in the interface's `default score(term, posting, ctx)` moves into a shared helper (a `package-private` static method on `Rankers` or a `TermScorings.applyFieldBoost(base, posting, context)` helper) that every ranker's `evaluate` calls at the end. This preserves DRY and keeps boost logic in one place.

### Optional: expose BM25 k1/b via factory

The Bm25 constructor already accepts `(k1, b)` but no factory exposes it (see `docs/engineering/agents/reports/myir-state-of-the-art-2026-09.md` §12). IR-5 does not require this, but exposing `Rankers.bm25(corpus, index, k1, b)` at the same time makes the BM25 explanation more useful (the `TermScoring` reports which parameters were in effect). Treat as an opt-in extra in slice IR-5.4.

## 9. Explanation Semantics per Ranker

### Binary

- `base` = `1.0` when term matches posting, `0.0` otherwise (in practice the searcher never invokes evaluate for non-matching postings).
- `contribution` = `base × boostFactor` if a `FieldBoost` applies, else `base`.
- No other statistics are meaningful.

### TF-IDF

- `tf` = `posting.termFrequency()`
- `sublinearTf` = `1 + log(tf)` — via `TermWeightingUtils.sublinearTf(tf)`
- `idf` = `log(N / df)` — via `Ranker.idf(term)` (already cached)
- `base` = `sublinearTf × idf`
- `contribution` = `base × boostFactor` if applicable, else `base`

We deliberately do *not* separately expose `N` and `df` on `TfIdfTermScoring` — `idf` is the useful, model-consistent quantity for a human reader. Callers who want raw N/df can consult the snapshot directly.

### BM25

Include in `Bm25TermScoring`:

- `tf` — helps the reader connect to the posting.
- `idf` — the smoothed BM25 IDF (educational: value differs from TF-IDF).
- `documentLength`, `averageDocumentLength` — necessary to interpret the length-normalization effect.
- `k1`, `b` — necessary to interpret saturation and length-normalization behavior.
- `normalization` = `1 - b + b × (dl/avgdl)` — one intermediate; makes the length-normalization effect explicit.
- `base` = `idf × (tf × (k1+1)) / (tf + k1 × normalization)`
- `contribution` = `base × boostFactor` if applicable.

Explicitly excluded (as raw values on the scoring record):

- `N` (corpus size) — the reader can derive from `idf` and `df` if needed.
- `df` — same. Adding it would tempt readers to check the formula by hand, but `idf` is the invariant that matters.
- Intermediate numerator/denominator — mathematically pointless; the reader has `tf`, `k1`, `b`, `normalization`.

## 10. Field-Aware Explanation

When a boost applies, the `FieldBoost` record shows:

- **`fieldFrequencies`** — raw per-field occurrence counts from the posting. Answers "where did this term appear?"
- **`effectiveWeights`** — for every field in `fieldFrequencies`, the weight actually used (defaults to `1.0` for unknown fields). Answers "what weight was applied to each field?" and makes UC-3's "unknown field default" explicit and readable.
- **`weightedSum`** — Σ(freq × weight) — the numerator of the boost.
- **`totalFrequency`** — Σ(freq) — the denominator of the boost.
- **`boostFactor`** — `weightedSum / totalFrequency` — the multiplier applied to `base`.

Distinguishable scenarios (per UC-3):

| Scenario | Observation in `TermScoring` |
|---|---|
| Title-only match with title weight 3.0 | `fieldBoost` present; `fieldFrequencies={title:1}`, `boostFactor=3.0` |
| Body-only match with body weight 1.0 (unknown) | `fieldBoost` present; `effectiveWeights={body:1.0}`, `boostFactor=1.0` |
| Match across title+body | `fieldBoost` present with both fields, `boostFactor` = weighted average |
| Unknown field defaulting to 1.0 | `effectiveWeights` shows the unknown field mapped to `1.0` |
| No context / raw-content document | `fieldBoost` is `Optional.empty()`; `contribution == base` |
| Neutral context | `fieldBoost` is `Optional.empty()`; `contribution == base` |

## 11. Invariants

### Score conservation

For any query, document, ranker, and context:

```
SearchResult.score  ==  Σ over TermContribution in ScoreExplanation.contributions of contribution()
```

Enforced by the `ScoreExplanation` compact constructor (throws `IllegalArgumentException` if the invariant fails beyond `TOLERANCE = 1e-9`). Independently verified by test at both single-term and multi-term granularity.

### Neutral-context equivalence

For any ranker, `evaluate(term, posting, RankingContext.neutral()).contribution() == score(term, posting)`. Verified per-ranker.

### Ranker parity

Existing `Searcher.search(...)` / `searchDetailed(...)` behavior — result set, ordering, and score values — is byte-identical before and after IR-5. Verified by running the pre-IR-5 test suite unchanged (all 1,022 tests continue to pass).

### Determinism

Given identical `(IndexSnapshot, CorpusSnapshot, ranker, context)`, `Searcher.explain(query, documentId)` produces identical `ScoreExplanation` values (bit-equal for stats, string-equal for term list). Verified by executing `explain` twice on the same snapshot.

### No hidden recomputation drift

Structural: `score(...)` returns `evaluate(...).contribution()` by default. Ranker implementers cannot override `score(term, posting, ctx)` — the interface's default is the only path. (If we later find a legitimate reason to override, the invariant becomes a discipline issue; for IR-5 we forbid it and add a note in the JavaDoc.)

### Explanation must reflect actual query and match

`ScoreExplanation.contributions` contains exactly one entry per normalized query term that matched the document (same as `matchedTerms` on the `SearchResult` today). The ordering matches `searchDetailed`'s iteration order.

## 12. Test Matrix

| # | Test | Type | Verifies |
|---|---|---|---|
| T1 | Binary ranker `evaluate` returns `BinaryTermScoring(term, 1.0, empty, 1.0)` for present term | unit / formula | UC-1, UC-2 |
| T2 | Binary ranker score-conservation: `evaluate.contribution() == score(term, posting)` | invariant | §11 |
| T3 | TF-IDF single-term: `TfIdfTermScoring.base == sublinearTf(tf) × log(N/df)` to 1e-12 | formula | UC-2 |
| T4 | TF-IDF multi-term score conservation: sum of contributions == `SearchResult.score` to 1e-9 | integration | §11 |
| T5 | BM25 reference contribution: hand-computed BM25 value equals `Bm25TermScoring.base` to 1e-12 for a fixed fixture | formula | UC-2 |
| T6 | BM25 short-vs-long doc: two docs same tf, longer doc has smaller `contribution`, same `idf`, larger `normalization` | integration | UC-1 |
| T7 | BM25 `TermScoring` reports the actual `k1`, `b` used (default and custom) | unit | §9 |
| T8 | Title-vs-body boost: `FieldBoost.boostFactor` matches configured weight ratio to 1e-12 | formula | UC-3 |
| T9 | Multiple fields with mixed weights: `weightedSum`, `totalFrequency`, `boostFactor` all match hand-computed values | formula | UC-3 |
| T10 | Unknown field: `FieldBoost.effectiveWeights` maps unknown field to 1.0; `boostFactor` reflects it | formula | UC-3 |
| T11 | Neutral `RankingContext`: `TermScoring.fieldBoost().isEmpty()` and `contribution == base` | invariant | §11 neutral-context |
| T12 | Raw-content-only document (empty `fieldFrequencies`): `fieldBoost().isEmpty()` regardless of context | invariant | §11 neutral-context |
| T13 | Query term absent from document: `explain` does not include a contribution for that term | integration | §11 explanation must reflect actual match |
| T14 | Multi-term explanation contributions.size() == matchedTerms.size() and each term appears once | integration | §11 |
| T15 | Score conservation from `searchDetailed`: for each result, `explain(query, docId).score() == result.score()` within tolerance | integration | §11 score conservation |
| T16 | Snapshot consistency: calling `explain(query, docId)` twice on the same snapshot returns equal `ScoreExplanation` | invariant | §11 determinism |
| T17 | `ScoreExplanation` compact constructor rejects contributions whose sum diverges from `score` beyond tolerance | unit | §11 constructive invariant |
| T18 | Ranker parity: pre-IR-5 tests (`RankersTest`, `FieldAwareRankingTest`, `SearchersTest`) pass unchanged | regression | §11 ranker parity |
| T19 | Missing document (unknown docId): `Searcher.explain(query, "no-such-id")` returns `Optional.empty()` | unit | UC-4 |
| T20 | Blank/null query: `Searcher.explain("", docId)` returns `Optional.empty()` | unit | UC-4 |

Test-type distribution: **unit** (T1, T7, T17, T19, T20 — 5 tests); **formula-level** (T3, T5, T8, T9, T10 — 5 tests); **invariant** (T2, T11, T12, T16 — 4 tests); **integration/search-level** (T4, T6, T13, T14, T15 — 5 tests); **regression** (T18 — pre-existing suite).

## 13. Backward Compatibility

The only public-interface change is adding `TermScoring evaluate(...)` as an abstract method on `Ranker`. This *is* a breaking change for any external implementer of `Ranker`. Since myIR has three internal ranker implementations and no known external ones (the codebase is single-repo), this is acceptable.

Migration path for any hypothetical external implementer:

1. Replace `double score(term, posting)` with `TermScoring evaluate(term, posting, ctx)`.
2. Delete any override of `score(term, posting, ctx)` — the interface default now covers it.

Nothing in `SimpleSearcher`, `VectorSearcher`, `Searchers`, `SearchResult`, or client code changes signature. Callers who never invoke `explain` are entirely unaffected.

The `score(term, posting)` and `score(term, posting, ctx)` overloads remain callable and return the same numeric values as before.

## 14. IR-6 Boundary

IR-5 answers **"why did this result receive this score?"**. IR-6 will answer **"is configuration A better than configuration B?"** and depends on qrels + ranking metrics (nDCG, MRR, precision@k), not on the internals of any single explanation.

Concretely, IR-6 must be implementable *without* consuming `ScoreExplanation`. IR-6 will need access to `List<SearchResult>` (docId + score + rank) per query, plus a qrels source. It should not import `TermScoring` or `FieldBoost` to compute a metric.

If a future diagnostic tool combines both — "which query terms contributed most to nDCG?" — that is a separate layer that consumes both APIs, not a coupling between them.

### Vector search

`VectorSearcher` is not part of IR-5. Vector explanation ("which dimensions contributed most to the cosine similarity?") is a natural next step but requires its own conceptual model (`DimensionContribution`, not `TermContribution`) and lives in its own follow-up. `SimilarityResult` already carries `matchedDimensions` — this is a stub of the same idea but has not been generalized.

## 15. Engineering Slices

All slices leave the build green independently. All slice deliverables include tests and CKF documentation touchpoints.

### IR-5.1 — Explanation domain model

- **Objective:** land the pure types (`TermScoring` sealed hierarchy, `FieldBoost`, `ScoreExplanation`) with the score-conservation invariant enforced in the compact constructor. No wiring; no ranker changes.
- **Code areas:** `codex.ir.ranking` (new files: `TermScoring.java`, `BinaryTermScoring.java`, `TfIdfTermScoring.java`, `Bm25TermScoring.java`, `FieldBoost.java`, `ScoreExplanation.java`); `package-info.java` update.
- **API change:** additive types only.
- **Tests:** T17 (compact-constructor invariant); construction/equality tests for each sealed variant.
- **Acceptance criteria:** all new records instantiate; compact-constructor rejects inconsistent input; existing 1,022 tests still pass.
- **Risks:** low. Pure record layer.
- **Non-goals:** no ranker calls the new types yet; no searcher API change yet.

### IR-5.2 — `Ranker.evaluate` contract + Binary + TF-IDF

- **Objective:** add `TermScoring evaluate(term, posting, context)` to the `Ranker` interface; provide default `score(...)` delegating to `evaluate(...).contribution()`; extract field-boost helper (`Rankers.applyFieldBoost` or `TermScorings.applyFieldBoost`) called at the end of every ranker's `evaluate`; migrate `BinaryRanker` and `TfIdfRanker`.
- **Code areas:** `codex.ir.ranking.Ranker`, `codex.ir.ranking.Rankers` (Binary and TF-IDF classes only; do not touch BM25 yet).
- **API change:** `Ranker.evaluate` becomes abstract; default `score` overloads updated.
- **Tests:** T1, T2, T3, T11, T12 (all Binary/TF-IDF-relevant). Pre-existing `RankersTest` continues to pass (T18 subset).
- **Acceptance criteria:** Binary and TF-IDF now return typed `TermScoring`; scores byte-identical to pre-IR-5; all 1,022 tests still pass.
- **Risks:** field-boost helper extraction must produce the exact same numeric result as the current default in `Ranker`. Verified by T18.
- **Non-goals:** BM25 stays untouched this slice (fewer moving pieces per commit); no searcher-level `explain` yet.

### IR-5.3 — BM25 evaluate

- **Objective:** migrate `Bm25Ranker.score` → `evaluate` returning `Bm25TermScoring`. Optionally expose `Rankers.bm25(corpus, index, k1, b)` factory overload (see §8).
- **Code areas:** `codex.ir.ranking.Rankers` (BM25 class); `codex.ir.ranking.Rankers` factory (optional new overload).
- **API change:** additive factory overload if included.
- **Tests:** T5 (reference), T6 (short-vs-long), T7 (k1/b reported); T11/T12 for BM25 too; T18 regression.
- **Acceptance criteria:** BM25 scores byte-identical to pre-IR-5; `Bm25TermScoring` fields match hand-computed values.
- **Risks:** BM25 has more intermediates; the extraction of `normalization` as a stored field must not affect the score value.
- **Non-goals:** no searcher-level `explain` yet.

### IR-5.4 — Field-aware `FieldBoost` in evaluate

- **Objective:** make every ranker's `evaluate` populate `TermScoring.fieldBoost()` with a full `FieldBoost` record when the boost applies, or `Optional.empty()` otherwise. Move the current boost math from the interface default into the shared helper introduced in IR-5.2 so all three rankers benefit from the same explanation-producing path.
- **Code areas:** `codex.ir.ranking.Rankers` (helper); `codex.ir.ranking.Ranker` (default `score(term, posting, ctx)` removed since `evaluate` now handles boost end-to-end and the default `score` delegates via `evaluate`).
- **API change:** `Ranker.score(term, posting, ctx)` becomes non-abstract default returning `evaluate(...).contribution()` — behavior preserved.
- **Tests:** T8, T9, T10 (field boost formulas); T11, T12 (neutral cases); T18 regression covering `FieldAwareRankingTest`, `FieldAwarePostingsTest`.
- **Acceptance criteria:** all field-aware pre-IR-5 tests pass; `FieldBoost` records match hand-computed values.
- **Risks:** double application of boost (once by helper, once by interface default) — the interface default's boost logic must be *removed* in this slice, and every ranker's `evaluate` must apply the boost via the helper. Regression coverage is dense here so a defect surfaces immediately.
- **Non-goals:** no searcher API change yet.

### IR-5.5 — `Searcher.explain(query, documentId)` API

- **Objective:** add `default Optional<ScoreExplanation> explain(String query, String documentId)` to the `Searcher` interface with an implementation on `SimpleSearcher` that:
  1. Tokenizes + normalizes the query using the same analysis pipeline.
  2. For each normalized term, looks up the posting for `documentId` (via `IndexSnapshot.getPostings(term)` + linear scan or a helper); if absent, skip.
  3. Calls `ranker.evaluate(term, posting, rankingContext)` to build one `TermContribution`.
  4. Returns `Optional.of(new ScoreExplanation(query, ranker.getClass().getSimpleName(), documentId, sum, contributions))` if any contribution matched; else `Optional.empty()`.
  5. Returns `Optional.empty()` on null/blank query and on unknown documentId.
- **Code areas:** `codex.ir.search.Searcher` (interface default); `codex.ir.search.SimpleSearcher` (concrete impl).
- **API change:** `Searcher` interface gains `default Optional<ScoreExplanation> explain(String, String)`. `VectorSearcher` inherits the default (which returns `Optional.empty()` for now) — explicit non-goal.
- **Tests:** T4, T13, T14, T15, T16, T19, T20; T18 regression.
- **Acceptance criteria:** T15 passes for every ranker at 1e-9 tolerance; T18 unchanged.
- **Risks:** posting lookup for a specific docId is currently O(df) via a linear scan of `getPostings(term)`. Acceptable for explanation (called for one document at a time). If callers want to explain every result, they iterate; there is no bulk API.
- **Non-goals:** no dense-vector explanation; no automatic comparison; no CLI/GUI.

### IR-5.6 — CKF documentation + example

- **Objective:** land the CKF Engineering Log Entry at `docs/knowledge/logs/core/ir-5-score-explanation.md`; add a short "How to explain a result" example to the root `README.md`; update `docs/knowledge/index.md` to link the new phase.
- **Code areas:** documentation only.
- **API change:** none.
- **Tests:** none new; verifies documentation links resolve.
- **Acceptance criteria:** CKF log entry uses the standard `Engineering Log Entry` frontmatter (`ckf_status: completed`); root `README.md` code snippet compiles as illustration.
- **Risks:** low. Documentation-only.
- **Non-goals:** no ADR is required unless a design decision changes during implementation; if k1/b factory exposure is included (IR-5.3), a brief ADR-style note in the CKF entry is enough.

## 16. Risks / Open Questions

**R1 — TermScoring allocation.** One `TermScoring` per matched (term, posting) pair. For 1000 results × 5 query terms = 5000 records per search. Trivial for the current use case (in-memory, small corpora). Measure later; do not preoptimize.

**R2 — Sealed hierarchy vs. extensibility.** Adding a new ranker requires editing the `permits` clause on `TermScoring`. This is *by design* — new rankers should be a reviewable change, not a plug-in surprise — but if myIR later gains an out-of-tree ranker use case, the sealing may need to loosen to `non-sealed`.

**R3 — Score-conservation tolerance.** IEEE 754 addition is not associative. The invariant uses absolute-tolerance `1e-9`. For very small scores this may be too loose; for very large scores potentially too tight. Suggested pragma: absolute tolerance for scores ≥ 1.0, relative tolerance `1e-9` for smaller. Decide during IR-5.1.

**R4 — Ranker name in `ScoreExplanation`.** Currently proposes `ranker.getClass().getSimpleName()`, which is stringly-typed and refactor-sensitive. Alternative: add `String name()` to `Ranker`. Deferred to implementation; leaning toward `getSimpleName()` for slice minimality.

**R5 — VectorSearcher out of scope.** Confirmed. Follow-up work (dense/sparse vector explanation) needs its own model. Do not force `Searcher.explain` to be generic across lexical and vector; the interface-default returning `Optional.empty()` for vector is the honest signal that vector explanation is not implemented.

**R6 — Coupling of `TermContribution` to `TermScoring`.** If we skip `TermContribution` and use `TermScoring` directly in `ScoreExplanation.contributions()` (see §7.3), we simplify the model but reduce future flexibility for fusion decoration. Decision made in IR-5.1; both are structurally compatible.

**R7 — Explanation of a document that no longer matches after snapshot rotation.** `Searcher.explain` binds to the snapshots the searcher was constructed with. If the caller passes a `documentId` from a stale search result and the searcher was rebuilt with a new snapshot, the explanation reflects the new snapshot, not the one that produced the original result. Documented behavior; no code change needed.

## 17. Definition of Done

IR-5 as a whole is done when:

- All 6 slices are merged; build is green with all pre-existing tests plus the new T1–T20.
- Score conservation is enforced structurally (compact constructor) *and* verified by test at both formula and integration level.
- Binary, TF-IDF, and BM25 each return typed `TermScoring` implementations exposing only meaningful fields per §9.
- Field-aware boosting is fully explainable via `FieldBoost`, distinguishing every scenario in the UC-3 table.
- `Searcher.explain(query, documentId)` is the only new caller-facing API; `search(...)` and `searchDetailed(...)` are unchanged.
- A CKF Engineering Log Entry documents the delivered phase and links from `docs/knowledge/index.md`.
- Root `README.md` shows a short usage example.
- No changes to indexing, vectorization, corpus, or crawler; no changes to `VectorSearcher`.

This report itself is done when the plan above is complete and no production code has been changed. Both conditions hold.

---

*Report produced 2026-09-05. Design constraints derived from repository code (`Ranker`, `Rankers`, `RankingContext`, `FieldWeights`, `SimpleSearcher`, `SearchResult`, `Posting`, `TermWeightingUtils`) and from `docs/engineering/agents/reports/myir-state-of-the-art-2026-09.md`.*
