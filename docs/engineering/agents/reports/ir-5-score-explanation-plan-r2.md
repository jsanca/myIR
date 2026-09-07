---
type: Engineering Log Entry
title: "IR-5 Score Explanation — Engineering Plan R2 (Reconciled)"
description: "Revision 2 of the IR-5 engineering plan. Reconciles the adversarial review findings while preserving the parts explicitly marked as sound. No production code changed."
tags: [core, ranking, explainability, design, engineering-plan, revision]
timestamp: 2026-09-05T00:00:00Z
ckf_version: "0.1"
ckf_status: proposed
ckf_scope: core
ckf_owner: project
---

# IR-5 Score Explanation — Engineering Plan R2 (Reconciled)

> **Behavioral contract:** the authoritative list of use cases and the test matrix live in
> [`../../../knowledge/use-cases/ir-5-score-explanation-use-cases.md`](../../../knowledge/use-cases/ir-5-score-explanation-use-cases.md).
> This plan implements those use cases. Where wording differs, the use-case document wins on
> behavior and this plan wins on architecture.

## 0. Reconciliation Summary

This revision supersedes [`ir-5-score-explanation-plan.md`](ir-5-score-explanation-plan.md) (R1) after the adversarial review at [`ir-5-score-explanation-adversarial-review.md`](../reviews/ir-5-score-explanation-adversarial-review.md) issued verdict **CHANGES REQUIRED**.

R2 applies the 9 proposed corrections and preserves the 4 items explicitly marked "should remain unchanged."

**Later reconciliation (2026-09-05, use-case reconciliation task):** the R2 slice list has been updated to:

- Remove the no-op IR-5.3 anchor slice (empty slices are prohibited).
- Renumber the four active slices as IR-5.1, IR-5.2, IR-5.3, IR-5.4.
- Add explicit `Implements:` / `Proves:` traceability blocks to every slice.
- Replace the inline R2 test matrix (T1–T24) with the use-case-derived matrix (T-01–T-30) owned by the companion use-case document. Slice `Proves:` blocks reference the new numbering.

The architecture itself is unchanged by the use-case reconciliation — every use case validated against R2.

### Corrections applied

| # | Change | Reason | Section |
|---|---|---|---|
| C1 | Merge R1 IR-5.2 and R1 IR-5.3 into a single slice migrating **all three** rankers together | BLOCKER: adding an abstract method leaves `Bm25Ranker` uncompilable in R1's slice IR-5.2 | §15 (IR-5.2) |
| C2 | Executive summary honestly reports the hot-path allocation cost | BLOCKER: R1 falsely claimed "zero allocation cost" | §1, §6 |
| C3 | Introduce `ExplainableSearcher` interface; do **not** add `explain` as a default on `Searcher` | MAJOR: `Optional.empty()` on `VectorSearcher` conflates "unsupported" with "not found" | §8, §15 (IR-5.4) |
| C4 | Remove the score-conservation invariant from the `ScoreExplanation` compact constructor; keep it as a test-only invariant | MAJOR: IEEE 754 summation-order differences between the searcher's loop and `Stream.sum()` would throw on valid data | §7.3, §11 |
| C5 | Remove `sealed` from `TermScoring`; keep it as a plain interface | MAJOR: closed-world hierarchy conflicts with a research/experimentation platform | §7.1 |
| C6 | Remove the `TermContribution` wrapper; `ScoreExplanation.contributions` holds `TermScoring` directly | MINOR: premature abstraction for hypothetical fusion | §7 |
| C7 | Extract `analyzeQuery(String)` helper in `SimpleSearcher`; both `searchDetailed` and the explain implementation call it | MINOR: prevents shadow-pipeline drift | §15 (IR-5.4) |
| C8 | Remove `rankerName` from `ScoreExplanation` | MINOR: `getSimpleName()` is unstable for lambdas/anonymous classes and unnecessary for IR-5 | §7.3 |
| C9 | Drop the optional `Rankers.bm25(corpus, index, k1, b)` factory overload from IR-5 scope | MINOR: unrelated capability expansion | §8, §15 (IR-5.3) |

### Preserved (per reviewer §10)

- Option B as the structural mechanism against drift (`evaluate` fundamental, `score` default projection).
- `SearchResult` record unchanged; no `ExplainedSearchResult`.
- `VectorSearcher` explicitly excluded.
- No rank fusion logic.

### Semantic narrowing (per reviewer question 10)

IR-5 explains **score**, not **rank**. Rank is a positional artifact of the *whole* result list; explaining rank requires comparing scores across documents. That comparison is out of scope. Wording throughout R2 uses "score" where R1 conflated "score and rank."

---

## 1. Executive Summary

IR-5 answers **"Why did this document receive this score for this query?"** by adding a derived observation layer on top of the existing scoring pipeline. It introduces no new scoring behavior, no evaluation metrics, and no independent recalculation path.

Recommended design:

- Introduce a small, typed explanation model (`TermScoring` interface with concrete records per ranker, `FieldBoost` record, `ScoreExplanation` record) mirroring the existing scoring structure of `SimpleSearcher`.
- Evolve `Ranker` by adding `evaluate(term, posting, context) → TermScoring` as the fundamental method. `score(...)` becomes a default projection returning `evaluate(...).contribution()`. This guarantees a single source of truth by construction — the hot search path and the explanation path execute the exact same code.
- Introduce a capability interface `ExplainableSearcher extends Searcher` with `explain(query, documentId) → Optional<ScoreExplanation>`. `SimpleSearcher` implements it; `VectorSearcher` does not. Callers can distinguish "unsupported" (`instanceof ExplainableSearcher` is false) from "no match" (returns `Optional.empty()`).
- **Honest allocation trade-off (per reviewer C2):** the hot search path allocates one small `TermScoring` per matched (term, posting) pair regardless of whether an explanation is ever requested. This is accepted as a worthwhile cost in a learning/experimentation platform to structurally guarantee no drift. Explanation reconstruction is on-demand, but the underlying allocation is not.
- Score conservation is enforced by **test** (formula-level and integration), not by domain-object constructor.

Deliverable size: **5 slices** (down from R1's 6, per C1), each independently green.

## 2. Problem Statement

Today the searcher returns `SearchResult(documentId, document, score, matchedTerms)`. Two documents may appear side-by-side with scores that differ by orders of magnitude, and no code path can explain the difference. A developer or researcher currently instruments rankers by hand or re-derives the formula from JavaDoc.

Observability is a core project value; IR-5 makes scoring observable. It is also the precondition for future work — Rank Fusion (multiple signals combined) is meaningless without a way to inspect per-signal contributions.

## 3. Use Cases

**UC-1 — Explain a scored result.** Given (query, returned document, ranker, ranking context), the caller inspects the meaningful contributors to the document's final score.

**UC-2 — Explain term-level contributions.** For lexical ranking, expose per-matched-term contributions. Each ranker exposes only meaningful fields: binary shows presence; TF-IDF shows tf/idf/base; BM25 shows tf/idf/dl/normalization/base; field boosting adds the boost breakdown when applicable.

**UC-3 — Explain field-aware boosting.** For documents with field provenance, show the field-frequency-weighted boost factor, per-field effective weights, and whether unknown fields fell back to the default weight of 1.0.

**UC-4 — Preserve normal search behavior.** `Searcher.search(...)` and `searchDetailed(...)` are unchanged in signature and semantics. Numeric results are byte-identical to pre-IR-5. Callers who never use explanations pay only the internal `TermScoring` allocation (see §1 / §10).

**UC-5 — Compare two score explanations manually.** A developer can call `explain(query, docA)` and `explain(query, docB)` and read the term-level differences. No automatic comparison utility ships in IR-5.

**UC-6 — Support future scoring signals.** The `TermScoring` interface is *not* sealed, so a future ranker (phrase, proximity, sparse-vector) provides its own implementation without editing core types. `ScoreExplanation.contributions` holds `List<TermScoring>` — additive by construction.

## 4. Non-Goals

- No evaluation metrics (nDCG, MRR, precision@k) — that is IR-6.
- No qrels, benchmark queries, or experiment runner.
- No TREC-format export.
- No GUI or visualization.
- No persistent tracing, log sinks, or telemetry backend.
- No dense-vector or semantic-similarity explanation.
- No rank fusion implementation.
- No query profiling, timing, or latency tracing.
- **No changes to `VectorSearcher`** (does not implement `ExplainableSearcher`).
- No changes to indexing, tokenization, normalization, or corpus statistics.
- **No BM25 k1/b public factory overload** (per C9).
- No rank-position explanation — IR-5 explains *score*, not *rank* (per reviewer question 10).

## 5. Existing Architecture Constraints

Code review of `Ranker` (114 lines), `Rankers` (312 lines), `RankingContext` (30 lines), `FieldWeights` (44 lines), `SimpleSearcher` (183 lines), `SearchResult` (27 lines), `Posting` (111 lines), and `TermWeightingUtils` (64 lines) establishes:

1. **`Ranker` today** exposes `idf(term)`, `score(term, posting)`, and `default score(term, posting, RankingContext)` that applies field boost. Every ranker implements only `idf` and `score(term, posting)`. Field boost lives on the interface default: `boostFactor = Σ(fieldFreq[f]×weight[f]) / Σ(fieldFreq[f])`.
2. **`SimpleSearcher.searchDetailed`** loops matched postings, calls `ranker.score(term, posting, rankingContext)`, and sums per document via `Double::sum`. The final document score is `Σ over matched terms of ranker.score(term, posting, ctx)` in the loop's iteration order.
3. **`SearchResult`** is a lean record: `documentId, document, score, matchedTerms`.
4. **Snapshots** (`CorpusSnapshot`, `IndexSnapshot`) are the frozen inputs. Given identical snapshots and context, all scoring is deterministic.
5. **Style constraints from CLAUDE.md**: interface + factory pattern; records over bare maps; primitives first; no reflection; public methods ≤20 lines; `Objects.requireNonNull` for validation; sealed hierarchies are preferred when a shape is *genuinely* closed, but interfaces are preferred when third-party or experimental implementations are anticipated.

These rule out `Map<String, Object>` scoring bags, reflection-driven serialization, and — per the reviewer's C5 — a `sealed` scoring hierarchy that would block experimental rankers from providing explanations.

## 6. Alternatives Considered

### Option A — parallel `score()` and `explain()`

Each ranker implements two methods; both must independently produce identical numbers.

- Pros: no hot-path allocation for `score`.
- Cons: two code paths per ranker; drift is only test-enforced. Rejected in R1 and R2 for the same reason: the task explicitly warns against a "second independent recalculation path."

### Option B — single `evaluate(...) → TermScoring`, `score` is a projection

Each ranker implements `evaluate`. `Ranker.score(...)` is a default returning `evaluate(...).contribution()`.

- Pros: single source of truth by construction. Impossible to drift.
- Cons: **one `TermScoring` allocation per (term, posting) pair on the hot path — even when no explanation is requested.** This is a real cost that R1's executive summary incorrectly denied (reviewer BLOCKER C2). In R2 it is stated plainly.
- Cost sizing: 100 matched documents × 5 query terms ≈ 500 record allocations per search. `TermScoring` records are tiny (a few doubles and a `String` reference). Trivial for the JVM at current corpus sizes; measurable if myIR ever runs at scale. If profiling later shows a problem, a low-allocation variant (Option A with rigorous drift testing) is a compatible retrofit.

### Option C — after-the-fact reconstruction only

`Ranker` API unchanged. `Searcher.explain` re-runs scoring for one document plus separately calls a stats observer.

- Pros: zero hot-path cost.
- Cons: duplicate computation logic per ranker; the "no drift" invariant is discipline, not construction. Rejected.

### Recommendation

**Option B**, combined with the new **`ExplainableSearcher.explain(query, documentId)`** capability interface. This is the same core choice as R1 but delivered through a capability interface (C3) rather than a default method on `Searcher`. Hot-path allocation is acknowledged (C2), not hidden.

## 7. Recommended Conceptual Model

Three types, all records or plain interfaces, all in `codex.ir.ranking`. Placement in the ranking package reflects that explanations describe *ranking*, not retrieval mechanics.

### 7.1 `TermScoring` — the ranker-level result (unsealed)

```
public interface TermScoring {
    String term();
    double base();                       // pre-boost ranker contribution
    Optional<FieldBoost> fieldBoost();   // present only when a non-neutral boost applied
    double contribution();               // final value used in scoring = base × boostFactor (or base)
}
```

Concrete implementations shipped in IR-5 (records in `codex.ir.ranking`):

- `BinaryTermScoring(term, base, fieldBoost, contribution)` — `base` is `1.0` or `0.0`.
- `TfIdfTermScoring(term, tf, sublinearTf, idf, base, fieldBoost, contribution)`.
- `Bm25TermScoring(term, tf, idf, documentLength, averageDocumentLength, k1, b, normalization, base, fieldBoost, contribution)`.

**Unsealed** (per C5): a future proximity, phrase, or vector-explanation ranker adds its own `TermScoring` implementation without editing existing files. This preserves myIR's research-platform character.

### 7.2 `FieldBoost` — the field-boost breakdown

Record. Present on `TermScoring` only when the boost was non-neutral (context has explicit weights *and* posting has field frequencies).

```
public record FieldBoost(
    Map<String, Integer> fieldFrequencies,   // copied from Posting
    Map<String, Double>  effectiveWeights,   // per-field weight actually used; unknown fields shown as 1.0
    double weightedSum,                      // Σ(freq × weight)
    int totalFrequency,                      // Σ(freq)
    double boostFactor                       // weightedSum / totalFrequency
) {
    public FieldBoost {
        Objects.requireNonNull(fieldFrequencies);
        Objects.requireNonNull(effectiveWeights);
        fieldFrequencies = Map.copyOf(fieldFrequencies);
        effectiveWeights = Map.copyOf(effectiveWeights);
    }
}
```

`effectiveWeights` makes UC-3's "unknown field defaulted to 1.0" observable — every field mentioned in the posting appears with the weight actually applied.

### 7.3 `ScoreExplanation` — the per-document explanation

Record. Carries the query the caller asked about, the reconstructed final score, and the ordered list of `TermScoring` entries.

```
public record ScoreExplanation(
    String query,
    String documentId,
    double score,
    List<TermScoring> contributions
) {
    public ScoreExplanation {
        Objects.requireNonNull(query);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(contributions);
        contributions = List.copyOf(contributions);
    }
}
```

Changes from R1 (per C4, C6, C8):

- **No `rankerName` field.** Removing it dodges the `getSimpleName()` instability for anonymous classes and lambdas. Callers who need the ranker identity can pass their own ranker reference.
- **No `TermContribution` wrapper.** `contributions` holds `TermScoring` directly.
- **No score-conservation assertion in the compact constructor.** The R1 constructor would have thrown on legitimate data because `Stream.mapToDouble(...).sum()` uses a different algorithm than the searcher's loop-based `Double::sum` (IEEE 754 is not associative). Score conservation is enforced by **tests** T4, T15, T17 (§12) and by the structural guarantee that `score(...)` returns `evaluate(...).contribution()`.

## 8. Ranker + Searcher API Evolution

### 8.1 `Ranker` — additive contract change

```
public interface Ranker {
    double idf(String term);

    // NEW — fundamental: returns intermediates and final contribution
    TermScoring evaluate(String term, Posting posting, RankingContext context);

    // EXISTING — projects the numeric value from evaluate; default now delegates
    default double score(String term, Posting posting, RankingContext context) {
        return evaluate(term, posting, context).contribution();
    }

    // EXISTING — remains callable
    default double score(String term, Posting posting) {
        return score(term, posting, RankingContext.neutral());
    }
}
```

Adding `evaluate` as abstract is a breaking change for external implementers of `Ranker`. Per §13, this is acceptable in a single-repo research platform.

### 8.2 `ExplainableSearcher` — capability interface (C3)

```
public interface ExplainableSearcher extends Searcher {
    /**
     * Returns an on-demand score explanation for the given document.
     *
     * @return Optional.of(explanation) when the query matches the document under this searcher's
     *         analysis pipeline; Optional.empty() when there is no match, the document is unknown,
     *         or the query is null/blank.
     */
    Optional<ScoreExplanation> explain(String query, String documentId);
}
```

- `SimpleSearcher implements ExplainableSearcher`.
- `VectorSearcher` does **not** implement it. A caller can check `if (searcher instanceof ExplainableSearcher es) …` to distinguish "unsupported" from "not found."
- `Searcher` itself is untouched. Callers who do not need explanations continue programming against `Searcher` and see no change.

### 8.3 What R2 explicitly does **not** add

- **No `Rankers.bm25(corpus, index, k1, b)` factory** (C9). BM25's private constructor already accepts `k1` and `b`; `Bm25TermScoring` reports the values in effect. Exposing a public factory is a separate concern properly owned by a future BM25-tuning task or by IR-6.
- **No `rankerName` on `ScoreExplanation`** (C8).
- **No default `explain` on `Searcher`** (C3).

## 9. Explanation Semantics per Ranker

### Binary

- `base` = `1.0` when term matches posting; `0.0` otherwise.
- `contribution` = `base × boostFactor` if a `FieldBoost` applies, else `base`.
- No other statistics are meaningful.

### TF-IDF

- `tf` = `posting.termFrequency()`
- `sublinearTf` = `TermWeightingUtils.sublinearTf(tf)` = `1 + log(tf)`
- `idf` = `TermWeightingUtils.classicIdf(N, df)` = `log(N / df)` (cached in ranker)
- `base` = `sublinearTf × idf`
- `contribution` = `base × boostFactor` if applicable, else `base`

Deliberately excluded on the record: raw `N` and `df` — `idf` is the invariant that matters. Callers who want raw counts can inspect the snapshot.

### BM25

- `tf` — connects the reader to the posting
- `idf` — smoothed BM25 IDF (differs from TF-IDF; useful to expose)
- `documentLength`, `averageDocumentLength` — necessary to interpret length normalization
- `k1`, `b` — necessary to interpret saturation and length normalization
- `normalization` = `1 - b + b × (dl / avgdl)` — makes the length-normalization effect explicit
- `base` = `idf × (tf × (k1+1)) / (tf + k1 × normalization)`
- `contribution` = `base × boostFactor` if applicable

Deliberately excluded: raw `N`, `df`, numerator/denominator intermediates.

## 10. Field-Aware Explanation

When a boost applies, `FieldBoost` records:

- **`fieldFrequencies`** — raw per-field occurrence counts from the posting.
- **`effectiveWeights`** — for every field in `fieldFrequencies`, the weight actually used (`1.0` for unknown fields).
- **`weightedSum`** — Σ(freq × weight).
- **`totalFrequency`** — Σ(freq).
- **`boostFactor`** — `weightedSum / totalFrequency`.

Distinguishable scenarios (per UC-3):

| Scenario | Observation |
|---|---|
| Title-only match with title weight 3.0 | `fieldBoost` present; `fieldFrequencies={title:1}`, `boostFactor=3.0` |
| Body-only match with body unweighted | `fieldBoost` present; `effectiveWeights={body:1.0}`, `boostFactor=1.0` |
| Match across title+body | `fieldBoost` present with both fields; `boostFactor` = weighted average |
| Unknown field defaulting to 1.0 | `effectiveWeights` shows the unknown field mapped to `1.0` |
| Raw-content document (no field frequencies) | `fieldBoost` is `Optional.empty()`; `contribution == base` |
| Neutral context | `fieldBoost` is `Optional.empty()`; `contribution == base` |

## 11. Invariants

### Score conservation

For any query, document, ranker, and context:

```
SearchResult.score  ≈  Σ over TermScoring in ScoreExplanation.contributions of contribution()
```

Enforced by **tests only** (T4, T15) at a tolerance chosen per §16 R3. **Not enforced in `ScoreExplanation`'s compact constructor** (per C4).

To keep the numeric comparison meaningful, T15 sums explanation contributions using **the same algorithm as `SimpleSearcher.searchDetailed`** — a sequential `Double::sum`, not `Stream.mapToDouble(...).sum()`. This eliminates the summation-order class of false failures.

### Neutral-context equivalence

For any ranker, `evaluate(term, posting, RankingContext.neutral()).contribution() == score(term, posting)`. Verified per ranker (T11, T12).

### Ranker parity

Existing `Searcher.search(...)` / `searchDetailed(...)` behavior — result set, ordering, and score values — is byte-identical before and after IR-5. Verified by running the pre-IR-5 test suite unchanged (T18).

### Determinism

Given identical `(IndexSnapshot, CorpusSnapshot, ranker, context)`, `ExplainableSearcher.explain(query, documentId)` produces equal `ScoreExplanation` values. Verified by T16.

### No hidden recomputation drift

Structural. `score(...)` returns `evaluate(...).contribution()` via the interface default. Ranker implementers should not override `score(term, posting, ctx)` — the interface default is the only path. Enforced by convention and by T18 regression.

### Explanation must reflect actual match

`ScoreExplanation.contributions` contains exactly one entry per normalized query term that matched the document; the same set as `SearchResult.matchedTerms`. Ordering matches `searchDetailed`'s iteration order. Verified by T13, T14.

## 12. Test Matrix

The authoritative, use-case-derived test matrix (**T-01 – T-30**) is defined in
[`../../../knowledge/use-cases/ir-5-score-explanation-use-cases.md`](../../../knowledge/use-cases/ir-5-score-explanation-use-cases.md#test-matrix-derived-from-use-cases).

**Counts:** 30 tests total. Per use case: UC-1 (6), UC-2 (9), UC-3 (6), UC-4 (2), UC-5 (3), UC-6 (4). Per level: Formula (6), Unit (10), Invariant (8), Integration (5), Regression (1).

**Numerical tolerance policy** (from the use-case doc): relative tolerance `1e-9 × |s|` for `|s| ≥ 1.0`; absolute tolerance `1e-9` for `|s| < 1.0`.

Slices below reference tests by the T-nn IDs from the companion document.

## 13. Backward Compatibility

The only breaking public change is adding `TermScoring evaluate(...)` as an abstract method on `Ranker`. myIR has three internal ranker implementations and no known external ones; the break is contained.

Migration for any hypothetical external implementer:

1. Replace the current `score(term, posting)` with `evaluate(term, posting, ctx) → TermScoring`.
2. Do not override `score(term, posting, ctx)` — the interface default now covers it.
3. Return an ad-hoc `TermScoring` implementation (the interface is unsealed per C5) or one of the built-in records.

`SimpleSearcher`, `VectorSearcher`, `Searchers`, `SearchResult`, `Ranker.score(term, posting)`, and `Ranker.score(term, posting, ctx)` all remain callable with unchanged numeric behavior.

The new `ExplainableSearcher` interface is additive.

## 14. IR-6 Boundary

IR-5 answers **"why did this document receive this score?"**. IR-6 will answer **"is configuration A better than configuration B?"** using qrels and metrics (nDCG, MRR, precision@k). IR-6 must not depend on `TermScoring`, `FieldBoost`, or `ScoreExplanation` — it needs only `List<SearchResult>` per query and a qrels source.

If a future diagnostic tool combines both ("which query terms contributed most to nDCG loss?") it is a *separate* layer that consumes both APIs — not a coupling between IR-5 and IR-6.

### Vector search

`VectorSearcher` is not part of IR-5 (per §4). Vector explanation requires a distinct conceptual model (dimension contributions, not term contributions). `SimilarityResult.matchedDimensions` is a stub of the same idea and will inform that future work.

## 15. Engineering Slices

Reconciled to **4 active slices** (the R2 no-op IR-5.3 anchor is removed). Each leaves the build green independently. Every slice declares which use cases it implements and which tests prove completion, using the T-nn IDs from the [use-case document](../../../knowledge/use-cases/ir-5-score-explanation-use-cases.md).

### IR-5.1 — Explanation domain model

**Implements:** UC-2 (record shapes), UC-3 (`FieldBoost` shape). Advances UC-1 (record shape).
**Proves:** T-07 (Binary shape), T-13 (BM25 exposes k1/b), T-19 (unknown-field default in `effectiveWeights`), T-22 (regression suite still green), T-23 (`SearchResult` unchanged shape).

- **Objective:** land the pure types (`TermScoring` interface, `FieldBoost` record, `ScoreExplanation` record) and the three concrete `*TermScoring` records. No wiring; no ranker or searcher changes.
- **Code areas (new files):** `codex-ir-core/src/main/java/codex/ir/ranking/TermScoring.java`, `BinaryTermScoring.java`, `TfIdfTermScoring.java`, `Bm25TermScoring.java`, `FieldBoost.java`, `ScoreExplanation.java`; update `codex/ir/ranking/package-info.java`.
- **Contract changes:** additive; introduces new types with no callers. No changes to existing interfaces.
- **Test evidence:** constructor accepts inputs regardless of summation-order jitter (positive test); defensive-copy verified for `List`/`Map` fields; `TermScoring.contribution()` and `base()` return the values passed in.
- **Acceptance criteria:** all new records instantiate; defensive copies verified; all 1,022 pre-IR-5 tests still pass (T-22).
- **Risks:** low. Pure type layer. Fix the numerical tolerance constant here (`1e-9`) and reuse it in every subsequent slice.
- **Non-goals:** no ranker calls the new types; no searcher API changes; no field-boost helper extraction yet.
- **Build integrity:** additive types with no consumers. The build compiles and every existing test passes without modification.

### IR-5.2 — `Ranker.evaluate` contract + migrate all three rankers atomically

**Implements:** UC-2 (per-ranker semantics), UC-3 (field-aware evidence attached), UC-4 (search-behavior preservation).
**Proves:** T-07 through T-15 (all UC-2 behavior), T-16 through T-21 (all UC-3 behavior), T-22 (regression).

- **Objective:**
  1. Add `TermScoring evaluate(String, Posting, RankingContext)` as an abstract method on `Ranker`; make `score(term, posting, ctx)` a default that delegates via `evaluate(...).contribution()`.
  2. Migrate `BinaryRanker`, `TfIdfRanker`, and `Bm25Ranker` **in the same commit** (atomic per adversarial-review C1). Each ranker implements `evaluate` and its previous `score(term, posting)` body moves into `evaluate`; the interface default supersedes the direct `score` implementation.
  3. Extract a shared package-private helper `TermScorings.applyFieldBoost(...)` (or a static method on `Rankers`) that computes `FieldBoost` and returns the boost-adjusted contribution. Every ranker's `evaluate` calls it exactly once at the end.
  4. Remove the boost logic from `Ranker.score(term, posting, ctx)`'s default body — that default becomes purely `return evaluate(term, posting, ctx).contribution();`.
- **Code areas:** `codex.ir.ranking.Ranker`, `codex.ir.ranking.Rankers` (all three inner classes), new package-private `TermScorings` (or helper in `Rankers`).
- **Contract changes:** `Ranker.evaluate` abstract; `Ranker.score(term, posting, ctx)` default body replaced. Breaking for any external ranker implementer (see §13).
- **Test evidence:** every field of every `TermScoring` variant verified against independently hand-computed references (T-09, T-11, T-16, T-17, T-18, T-19); `evaluate(...).contribution() == score(...)` per ranker (T-08, T-10, T-15); neutral / raw-content cases (T-20, T-21); T-22 regression.
- **Acceptance criteria:** every pre-IR-5 test passes byte-identically; new formula tests match hand-computed references at the numerical tolerance policy.
- **Risks:** field-boost helper extraction must produce numerically identical results to the previous interface default. `FieldAwareRankingTest`, `FieldAwarePostingsTest`, and `RankersTest` provide dense regression coverage; drift surfaces immediately.
- **Non-goals:** no `Rankers.bm25(k1, b)` factory overload (per C9); no searcher-level `explain`; no query-analysis helper extraction (comes in IR-5.3).
- **Build integrity:** all three rankers migrate together in one commit, so the abstract `evaluate` method always has three implementations from the moment it is added. No transient uncompilable state.

### IR-5.3 — `ExplainableSearcher` capability + `SimpleSearcher.explain` + shared query analysis

**Implements:** UC-1 (explain a scored document end-to-end), UC-5 (capability semantics), UC-6 (query-analysis parity).
**Proves:** T-01 through T-06 (all UC-1 behavior), T-24 through T-26 (all UC-5 behavior), T-27 through T-30 (all UC-6 behavior), plus T-02 score conservation across all rankers.

- **Objective:**
  1. Introduce `codex.ir.search.ExplainableSearcher extends codex.ir.search.Searcher` with `Optional<ScoreExplanation> explain(String query, String documentId)`.
  2. Implement it on `SimpleSearcher`. `VectorSearcher` does **not** implement it (per C3 / UC-5).
  3. Extract `private List<String> analyzeQuery(String query)` on `SimpleSearcher` (per C7 / UC-6) that tokenizes + normalizes and returns the ordered list of normalized terms (empties dropped). Refactor `searchDetailed` to call it; `explain` calls the same primitive. Single source of query analysis.
  4. `explain(query, documentId)` implementation:
     - `if (query == null || query.isBlank()) return Optional.empty();`
     - `if (corpus.get(documentId).isEmpty()) return Optional.empty();`
     - `List<String> terms = analyzeQuery(query);`
     - `if (terms.isEmpty()) return Optional.empty();`
     - For each term (in order), look up `invertedIndex.getPostings(term)` and find the posting for `documentId` (linear scan; postings for one term are typically small).
     - For every match, call `ranker.evaluate(term, posting, rankingContext)` and collect the `TermScoring`.
     - Sum contributions with `Double::sum` in the iteration order (matches `searchDetailed`'s summation order per §11).
     - If no contribution matched, return `Optional.empty()`.
     - Otherwise return `Optional.of(new ScoreExplanation(query, documentId, sum, contributions))`.
- **Code areas:** `codex.ir.search.ExplainableSearcher` (new); `codex.ir.search.SimpleSearcher` (implements new interface; adds `private analyzeQuery`).
- **Contract changes:** additive interface `ExplainableSearcher`; `SimpleSearcher` now `implements ExplainableSearcher`. `Searcher` untouched. `VectorSearcher` untouched.
- **Test evidence:** UC-1 tests T-01 to T-06 (return shape, score conservation, no false contributions, unknown-doc, null/blank query, empty analyzed query); UC-5 tests T-24 to T-26 (capability semantics, `Searcher` has no `explain`); UC-6 tests T-27 to T-30 (analysis parity with `searchDetailed`, duplicate handling, empty-analysis parity, determinism). T-22 regression must remain green.
- **Acceptance criteria:** T-02 passes at the tolerance from IR-5.1 for every ranker; T-24 and T-25 verify capability semantics; T-27 confirms no shadow pipeline; T-22 unchanged.
- **Risks:**
  - Posting lookup for one docId is O(df) per term. Acceptable for on-demand explanation. No bulk API in IR-5.
  - `searchDetailed` refactor to call `analyzeQuery` must produce identical iteration order. T-22 regression is the guard.
- **Non-goals:** no `explain` method on `Searcher`; no default returning `Optional.empty()`; no bulk explanation; no vector explanation.
- **Build integrity:** additive interface + a new implementation method + a private helper refactor. The refactor keeps `searchDetailed`'s observable behavior byte-identical (guarded by T-22).

### IR-5.4 — CKF documentation + README example

**Implements:** documentation surface for UC-1, UC-5, UC-6 (user-facing usage).
**Proves:** documentation-only; verifies example code snippets compile as illustration.

- **Objective:** create `docs/knowledge/logs/core/ir-5-score-explanation.md` with `Engineering Log Entry` frontmatter (`ckf_status: completed`); update `docs/knowledge/index.md` to link the new phase; add a short "How to explain a score" example to root `README.md` using `ExplainableSearcher`; link the use-case document from the CKF entry.
- **Code areas:** documentation only.
- **Contract changes:** none.
- **Test evidence:** none new; documentation links must resolve; the README code snippet compiles as illustration.
- **Acceptance criteria:** CKF log entry exists and is linked; use-case document is cited; README snippet illustrates the capability check + `explain` call.
- **Risks:** low.
- **Non-goals:** no new ADR unless a design decision changed during implementation. If the tolerance policy is refined during IR-5.1 or IR-5.2 based on measurement, note the change in the CKF entry rather than creating a new ADR.
- **Build integrity:** documentation-only slice; nothing to break.

## 16. Risks / Open Questions

**R1 — TermScoring allocation on hot path (acknowledged, per C2).** Every `ranker.score(term, posting, ctx)` call now allocates one `TermScoring` record. For the current use case (in-memory corpora, thousands of docs) this is negligible. Documented as an accepted trade-off (§1, §6). Measure with JMH later if needed.

**R2 — Score-conservation tolerance policy.** IEEE 754 addition is not associative; even with matching summation order, small residuals can appear. Recommended: absolute tolerance `1e-9` when `|score| < 1.0`; relative tolerance `1e-9 × |score|` otherwise. Decide the constant in IR-5.1 and use it consistently across T4 and T15.

**R3 — Unsealed `TermScoring` (per C5).** A malicious or careless external implementer could return `contribution` that does not equal `base × boostFactor`. This is acceptable in a research platform — the invariant is a per-ranker responsibility, and pre-existing rankers satisfy it structurally via the shared boost helper.

**R4 — Duplicate query terms.** Query `"java java"` tokenizes to two `java` tokens. `searchDetailed` today merges by document id and sums via `Double::sum`, so a duplicate term inflates the score. `explain` must produce a single contribution to match the `matchedTerms` list — verified by T22. Whether duplication *should* inflate is a separate design decision outside IR-5.

**R5 — Snapshot rotation.** `SimpleSearcher` binds to snapshots at construction. `explain` reflects the snapshot the searcher currently holds. Calling `explain` on a `documentId` from a stale search result after a searcher rebuild yields the current-snapshot answer, not the historical one. Documented behavior; no code change (reviewer §11 risk 2).

**R6 — Anonymous/lambda ranker identity (per C8).** Fixed by removing `rankerName` entirely from `ScoreExplanation`.

**R7 — Analysis pipeline extraction.** `analyzeQuery` is a `private` method on `SimpleSearcher`; it is not part of any public contract and can be inlined again if it turns out to be over-engineering. Keeping it `private` (not `protected`) prevents subclass leakage.

## 17. Definition of Done

IR-5 as a whole is done when:

- All **4 active slices** (IR-5.1 → IR-5.4) are merged; build is green with all pre-existing tests plus the 30 new use-case-derived tests (T-01 through T-30 in the [use-case document](../../../knowledge/use-cases/ir-5-score-explanation-use-cases.md)).
- Every use case UC-1 through UC-6 has passing acceptance evidence (see the traceability blocks on each slice).
- Score conservation (UC-1 acceptance evidence) is enforced by test (T-02, T-15) at the tolerance policy `1e-9` fixed in IR-5.1.
- Binary, TF-IDF, and BM25 each return typed `TermScoring` implementations exposing only meaningful fields (UC-2).
- Field-aware boosting is fully explainable via `FieldBoost`, covering every UC-3 scenario.
- `ExplainableSearcher.explain(query, documentId)` is the only new caller-facing API (UC-5); `SimpleSearcher` implements it; `VectorSearcher` does not; `Searcher` is unchanged; `SearchResult` is unchanged (UC-4).
- Query analysis is single-sourced via `SimpleSearcher.analyzeQuery(String)` (UC-6).
- A CKF Engineering Log Entry documents the delivered phase and is linked from `docs/knowledge/index.md`, and links the use-case document.
- Root `README.md` shows a short usage example (capability check + `explain` call).
- No changes to indexing, vectorization, corpus, or crawler; no changes to `VectorSearcher`; no BM25 factory overload.

This R2 report itself is done when the reconciliation above (including the use-case reconciliation of 2026-09-05) is complete and no production code has been changed. Both conditions hold.

---

*R2 produced 2026-09-05. Supersedes R1 (`ir-5-score-explanation-plan.md`). Reconciles adversarial review (`ir-5-score-explanation-adversarial-review.md`, verdict CHANGES REQUIRED). Use-case reconciliation applied 2026-09-05 against `../../../knowledge/use-cases/ir-5-score-explanation-use-cases.md`: no-op IR-5.3 anchor removed, slices renumbered to 4 active slices, test matrix moved to use-case doc, traceability blocks added.*
