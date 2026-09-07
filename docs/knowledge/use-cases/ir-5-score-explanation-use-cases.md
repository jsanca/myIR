---
type: Use Case Specification
title: "IR-5 Score Explanation — Use Cases and Test Traceability"
description: "Behavioral use cases IR-5 delivers and the derived test matrix that proves them. Companion to ir-5-score-explanation-plan-r2.md."
tags: [core, ranking, explainability, use-cases]
timestamp: 2026-09-05T00:00:00Z
ckf_version: "0.1"
ckf_status: proposed
ckf_scope: core
ckf_owner: project
---

# IR-5 Score Explanation — Use Cases and Test Traceability

**Companion documents**

- Engineering plan: [`../agents/reports/ir-5-score-explanation-plan-r2.md`](../agents/reports/ir-5-score-explanation-plan-r2.md)
- Adversarial review: [`../agents/reviews/ir-5-score-explanation-adversarial-review.md`](../agents/reviews/ir-5-score-explanation-adversarial-review.md)

**Purpose**

Fix the behavioral contract IR-5 must deliver *before* implementation. Every test and every engineering slice traces back to a use case here. Architecture serves these use cases, not the reverse.

---

## Scope reminder

IR-5 answers **"Why did this document receive this score for this query?"**

IR-5 does **not**: explain rank position, evaluate retrieval quality (IR-6), touch vector search, implement fusion, or tune BM25.

---

## UC-1 — Explain the score of a matching document

- **ID:** UC-1
- **Name:** Explain the score of a matching document
- **Actor:** Developer or IR researcher using myIR as a library.
- **Intent:** Obtain a structured account of what the ranker produced for one specific (query, document) pair.
- **Preconditions:**
  - A lexical searcher supporting explanation (`ExplainableSearcher`) is constructed against a `CorpusSnapshot` + `IndexSnapshot`.
  - A `Ranker` (Binary, TF-IDF, or BM25) and a `RankingContext` (neutral or with explicit `FieldWeights`) are configured on the searcher.
  - The target document is present in the corpus and matches at least one analyzed query term under the searcher's tokenizer + normalizer.
- **Trigger:** Caller invokes `explain(query, documentId)`.
- **Main Flow:**
  1. The searcher analyzes the query using the same tokenizer + normalizer used by `searchDetailed`.
  2. For each analyzed term, the searcher retrieves the posting for `documentId` (if present).
  3. For each matched posting, the ranker's `evaluate(term, posting, context)` produces one `TermScoring`.
  4. The searcher sums the `TermScoring.contribution()` values using the same summation order as `searchDetailed` (sequential `Double::sum`).
  5. The searcher returns `Optional.of(new ScoreExplanation(query, documentId, totalScore, contributions))`.
- **Alternative / Edge Flows:**
  - Document unknown → `Optional.empty()`.
  - Query null/blank → `Optional.empty()`.
  - Analyzed query is empty (e.g., all stop-words) → `Optional.empty()`.
  - No analyzed term matches the document → `Optional.empty()`.
- **Observable Result:** A `ScoreExplanation` that identifies the document id, the analyzed matched terms, the per-term contribution, and the total score. Or `Optional.empty()` in the edge cases above.
- **Acceptance Evidence:**
  - For any result returned by `searchDetailed`, `explain(query, documentId).score() ≈ SearchResult.score` under the numerical tolerance policy defined in the engineering plan.
  - `ScoreExplanation.contributions` contains exactly one entry per analyzed term that matched — never for terms that did not match.
- **Non-Goals:** No rank position; no cross-document comparison; no explanation for vector search.

---

## UC-2 — Explain ranker-specific term scoring

- **ID:** UC-2
- **Name:** Explain ranker-specific term scoring
- **Actor:** Developer or IR researcher inspecting lexical ranking behavior.
- **Intent:** Understand *what the ranker actually did* for one `(term, posting)` pair — with only the statistics meaningful to that ranker.
- **Preconditions:** A matching `(term, posting)` exists; the ranker is one of Binary, TF-IDF, or BM25.
- **Trigger:** Ranker's `evaluate(term, posting, context)` is invoked (directly, or transitively via `search`, `searchDetailed`, or `explain`).
- **Main Flow:** The ranker computes its scoring formula once and returns a `TermScoring` implementation that surfaces the intermediates *and* the final contribution.
- **Alternative / Edge Flows:**
  - `posting.termFrequency() <= 0` → `contribution == 0.0`; the `TermScoring` still identifies the term.
  - BM25 with document length missing or `<= 0`, or with `averageDocumentLength <= 0` → `contribution == 0.0` (preserves current pre-IR-5 behavior).
- **Observable Result:** A ranker-specific `TermScoring` record with the fields listed below.

  **Binary** exposes:
  - `term`
  - `base` (`1.0` when matched, `0.0` otherwise)
  - `contribution`
  - No TF/IDF-like fields (would be meaningless).

  **TF-IDF** exposes:
  - `term`
  - `tf`
  - `sublinearTf` (= `1 + log(tf)`)
  - `idf` (= `log(N/df)`, from the ranker's cache)
  - `base` (= `sublinearTf × idf`)
  - `contribution` (= `base × boostFactor` if a `FieldBoost` applies, else `base`)

  **BM25** exposes:
  - `term`
  - `tf`
  - `idf` (BM25 smoothed IDF from the ranker's cache)
  - `documentLength`
  - `averageDocumentLength`
  - `k1`
  - `b`
  - `normalization` (= `1 - b + b × (dl / avgdl)`)
  - `base` (= `idf × (tf × (k1+1)) / (tf + k1 × normalization)`)
  - `contribution` (= `base × boostFactor` if a `FieldBoost` applies, else `base`)
- **Acceptance Evidence:**
  - For every ranker, `evaluate(term, posting, ctx).contribution() == score(term, posting, ctx)` bit-equal.
  - Per-ranker formulas match independently derived reference values within numerical tolerance.
  - There is only one scoring code path — `score(...)` is a projection over `evaluate(...)` by construction.
- **Non-Goals:** No exposure of raw `N`, `df`, or numerator/denominator intermediates that add no explanatory value. No parameter-tuning API.

---

## UC-3 — Explain field-aware scoring effects

- **ID:** UC-3
- **Name:** Explain field-aware scoring effects
- **Actor:** Developer or researcher investigating why two otherwise similar term matches produced different scores.
- **Intent:** Make the field-weight boost mechanism observable at the (term, posting) level.
- **Preconditions:** A posting whose `fieldFrequencies` is non-empty; a `RankingContext` with a non-neutral `FieldWeights`.
- **Trigger:** Ranker `evaluate(term, posting, context)` is invoked for that posting/context.
- **Main Flow:**
  1. The shared boost helper reads `posting.fieldFrequencies()` and `context.fieldWeights()`.
  2. For each field in `fieldFrequencies`, it records the effective weight actually applied (defaulting unknown fields to `1.0`).
  3. It computes `weightedSum = Σ(freq × weight)` and `totalFreq = Σ(freq)`.
  4. It computes `boostFactor = weightedSum / totalFreq`.
  5. `contribution = base × boostFactor`.
  6. A `FieldBoost` record is attached to the `TermScoring`.
- **Alternative / Edge Flows:**
  - Neutral context (`FieldWeights.neutral()`) → no `FieldBoost` attached; `contribution == base`.
  - Posting has empty `fieldFrequencies` (raw-content document) → no `FieldBoost` attached; `contribution == base`.
  - Unknown field → included in `effectiveWeights` with value `1.0`, visibly documenting the default.
  - `totalFreq == 0` (defensive, shouldn't happen when `fieldFrequencies` is non-empty) → no boost, `contribution == base`.
- **Observable Result:** A `FieldBoost` on the `TermScoring`, distinguishing every required scenario below, or `Optional.empty()` for the neutral cases.

  **Required scenarios (must all be distinguishable in the explanation):**
  - Title-only occurrence (weighted).
  - Body-only occurrence (weighted or defaulted).
  - Occurrence in multiple fields with mixed weights.
  - Unknown field using default weight.
  - Neutral context on a field-provenance document.
  - Document without field provenance.
- **Acceptance Evidence:**
  - `FieldBoost.boostFactor × TermScoring.base == TermScoring.contribution` per ranker within numerical tolerance.
  - Neutral context or absent field provenance → `TermScoring.fieldBoost().isEmpty()` and `contribution == base`.
  - Unknown-field default is visible in `effectiveWeights`, not silently applied.
- **Non-Goals:** No BM25F. No per-field length normalization. No field-aware vector scoring.

---

## UC-4 — Explanation must not change search semantics

- **ID:** UC-4
- **Name:** Explanation must not change search semantics
- **Actor:** Existing caller of `Searcher.search(...)` or `Searcher.searchDetailed(...)`.
- **Intent:** Preserve every observable behavior of the pre-IR-5 search API so existing callers can adopt IR-5 without adapting their code or their expectations.
- **Preconditions:** Existing code using `Searcher.search(...)` or `searchDetailed(...)`.
- **Trigger:** IR-5 is released; the caller runs their existing search paths.
- **Main Flow:** Search executes exactly as before. The internal scoring path now allocates `TermScoring` records via `evaluate(...)`; the final numeric result is unchanged.
- **Alternative / Edge Flows:** None — behavior parity is the whole point.
- **Observable Result:**
  - Same result set for any (query, snapshots, ranker, context).
  - Same ordering.
  - Same numeric scores.
  - Same `SearchResult` record shape and field values.
- **Acceptance Evidence:**
  - All 1,022 pre-IR-5 tests continue to pass without modification.
  - Field-by-field equality of `SearchResult` before and after (verified by the pre-IR-5 test suite as regression).
- **Trade-off (accepted, explicit):** The internal `evaluate(...)` path allocates one `TermScoring` per matched `(term, posting)` even when no explanation is requested. This is accepted as the cost of a single source of truth for scoring on a research/learning platform. IR-5 must not claim zero allocation overhead.
- **Non-Goals:** No new methods added to `Searcher`. No breaking change to `SearchResult`. No change in `VectorSearcher` behavior.

---

## UC-5 — Explanation support is an explicit capability

- **ID:** UC-5
- **Name:** Explanation support is an explicit capability
- **Actor:** Caller selecting a search implementation.
- **Intent:** Structurally distinguish "this implementation supports score explanation" from "this query/document had no explanation."
- **Preconditions:** myIR ships more than one searcher (`SimpleSearcher`, `VectorSearcher`) and only some support explanation.
- **Trigger:** Caller wishes to obtain an explanation for a result.
- **Main Flow:**
  1. Caller checks `searcher instanceof ExplainableSearcher`.
  2. If true, caller downcasts and invokes `explain(query, documentId)`.
  3. If false, caller knows explanation is *unsupported* — no ambiguous `Optional.empty()`.
- **Alternative / Edge Flows:**
  - `ExplainableSearcher.explain(...)` returns `Optional.empty()` → the searcher *supports* explanation but this specific (query, document) produced none.
- **Observable Result:** Two distinct states are observable via the type system + `Optional`: "unsupported" (interface not implemented) vs. "no explanation for this input" (`Optional.empty()` from an `ExplainableSearcher`).
- **Acceptance Evidence:**
  - `SimpleSearcher instanceof ExplainableSearcher` is true.
  - `VectorSearcher instanceof ExplainableSearcher` is false.
  - `Searcher` itself is unchanged; no `explain` method with `Optional.empty()` default on `Searcher`.
- **Non-Goals:** No vector explanation in IR-5. No forcing every future searcher to answer whether it supports explanation.

---

## UC-6 — Explanation uses the same analyzed query semantics as search

- **ID:** UC-6
- **Name:** Explanation uses the same analyzed query semantics as search
- **Actor:** Developer expecting the explanation to describe *the search that actually occurred*.
- **Intent:** Prevent a shadow query-analysis pipeline in `explain(...)` that could drift from `searchDetailed(...)`.
- **Preconditions:** `SimpleSearcher` exposes both `searchDetailed(query)` and `explain(query, documentId)`.
- **Trigger:** Both operations are invoked with the same query.
- **Main Flow:** Both operations obtain their analyzed terms from a single shared `analyzeQuery(String)` primitive on `SimpleSearcher`. Any current or future analysis change (e.g., synonym expansion) is picked up by both simultaneously.
- **Alternative / Edge Flows:**
  - Query that reduces to zero terms after normalization (all stop-words) → `analyzeQuery` returns empty; both `searchDetailed` and `explain` treat this as no match.
  - Duplicate query terms (e.g., `"java java"`) → both paths observe both tokens; whatever scoring behavior `searchDetailed` produces, `explain` faithfully reflects the same.
  - Terms removed by normalization → both paths agree they are absent.
- **Observable Result:** For a fixed query and searcher, `analyzeQuery` output is a single ordered sequence of normalized terms, consumed identically by search and explanation.
- **Acceptance Evidence:**
  - For any query `q`, the ordered analyzed terms consumed by `searchDetailed(q)` equal the ordered analyzed terms consumed by `explain(q, docId)`.
  - If duplicate terms currently inflate scores, `explain` faithfully reproduces the inflation rather than silently deduplicating.
- **Non-Goals:** IR-5 does not change tokenizer, normalizer, or duplicate-term handling. Only prevents drift.

---

## Explicit Non-Use-Cases

| ID | Statement | Rationale |
|---|---|---|
| **NU-1** | IR-5 does not explain relative rank (e.g., "why is doc A rank 2 instead of rank 3?"). | Rank explanation requires cross-document comparison; out of scope. |
| **NU-2** | IR-5 does not evaluate retrieval quality (no nDCG, DCG, MAP, MRR, qrels, experiment runner). | Belongs to IR-6. |
| **NU-3** | IR-5 does not explain vector similarity. `VectorSearcher` is unchanged. | Requires a distinct dimension-level model; separate future phase. |
| **NU-4** | IR-5 does not implement rank fusion (RRF, lexical/vector fusion, signal normalization). | Future work; explicitly deferred. |
| **NU-5** | IR-5 does not tune BM25 — no new `k1`/`b` public factory. Existing `k1` and `b` appear only as explanatory evidence on `Bm25TermScoring`. | Configuration concern is unrelated to observing existing behavior. |

---

## Test Matrix (derived from use cases)

Tests renumbered from R2's T1–T24 to derive strictly from use cases (per task §"Test Derivation" — "Do not preserve R2's T1–T24 numbering merely for historical reasons"). Each test names its use case and evidence level.

For **formula** tests, the expected value must be derived independently — a hand computation or an external calculator — not by calling the same implementation path being tested.

| Test | Use Case | Behavior Proved | Level |
|---|---|---|---|
| **T-01** | UC-1 | `explain(query, docId)` on a matching (query, doc) pair returns `Optional.of(ScoreExplanation)` identifying document id, analyzed matched terms, and total score | Integration |
| **T-02** | UC-1 | Score conservation, per ranker: for every `SearchResult` from `searchDetailed(q)`, `explain(q, r.documentId()).score() ≈ r.score()` using the same sequential `Double::sum` order | Invariant |
| **T-03** | UC-1 | Explanation contains no contribution for query terms that did not match the document | Integration |
| **T-04** | UC-1 | Unknown documentId → `Optional.empty()` | Unit |
| **T-05** | UC-1 | Null / blank query → `Optional.empty()` | Unit |
| **T-06** | UC-1 | Query whose analyzed representation is empty (e.g., all stop-words) → `Optional.empty()` | Unit |
| **T-07** | UC-2 (Binary) | `BinaryTermScoring` exposes `term`, `base==1.0`, `contribution==1.0` for a present term; no TF/IDF fields present | Unit |
| **T-08** | UC-2 (Binary) | `evaluate(term, posting, ctx).contribution() == score(term, posting, ctx)` for Binary | Invariant |
| **T-09** | UC-2 (TF-IDF) | `TfIdfTermScoring.tf`, `sublinearTf`, `idf`, `base` equal independently hand-computed reference values on a fixed 3-doc fixture | Formula |
| **T-10** | UC-2 (TF-IDF) | `evaluate(...).contribution() == score(...)` for TF-IDF | Invariant |
| **T-11** | UC-2 (BM25) | `Bm25TermScoring.tf`, `idf`, `documentLength`, `averageDocumentLength`, `k1`, `b`, `normalization`, `base` equal independently hand-computed reference values on a fixed fixture | Formula |
| **T-12** | UC-2 (BM25) | Short-vs-long document with equal `tf`: shorter doc has larger `contribution`; same `idf`; larger `normalization` on the longer doc | Formula |
| **T-13** | UC-2 (BM25) | `Bm25TermScoring` reports the ranker's internal `k1=1.2` and `b=0.75` | Unit |
| **T-14** | UC-2 (BM25) | BM25 with `documentLength <= 0` or `averageDocumentLength <= 0` → `contribution == 0.0`; `TermScoring` is still well-formed | Unit |
| **T-15** | UC-2 | `evaluate(...).contribution() == score(...)` for BM25 | Invariant |
| **T-16** | UC-3 | Title-only occurrence with title weight `3.0`: `FieldBoost.boostFactor == 3.0`, `contribution == base × 3.0` (independent hand computation) | Formula |
| **T-17** | UC-3 | Body-only occurrence with body weight `1.5`: `FieldBoost.boostFactor == 1.5`, `contribution == base × 1.5` | Formula |
| **T-18** | UC-3 | Occurrence in multiple fields (title=1, body=2) with weights (title=3.0, body=1.0): `boostFactor == (1×3 + 2×1)/(1+2) == 5/3` | Formula |
| **T-19** | UC-3 | Unknown field: `FieldBoost.effectiveWeights` shows the unknown field mapped to `1.0`; `boostFactor` reflects this default | Formula |
| **T-20** | UC-3 | Neutral `RankingContext`: `TermScoring.fieldBoost().isEmpty()`, `contribution == base` | Invariant |
| **T-21** | UC-3 | Raw-content document (posting with empty `fieldFrequencies`): `fieldBoost().isEmpty()`, `contribution == base` regardless of context | Invariant |
| **T-22** | UC-4 | The full pre-IR-5 test suite (`RankersTest`, `FieldAwareRankingTest`, `FieldAwarePostingsTest`, `SearchersTest`, and every other `codex-ir-core` test) passes byte-identically | Regression |
| **T-23** | UC-4 | `SearchResult` record shape is unchanged; existing constructor signature and field types verified | Unit |
| **T-24** | UC-5 | `SimpleSearcher instanceof ExplainableSearcher` is `true` | Unit |
| **T-25** | UC-5 | `VectorSearcher instanceof ExplainableSearcher` is `false` | Unit |
| **T-26** | UC-5 | `Searcher` interface has no `explain` method — capability lives on `ExplainableSearcher` only | Unit |
| **T-27** | UC-6 | Multi-term query: the ordered list of analyzed terms observed by `searchDetailed(q)` equals that observed by `explain(q, docId)` (verified by capturing what `analyzeQuery(q)` returns) | Invariant |
| **T-28** | UC-6 | Duplicate query terms (`"java java"`): `SearchResult.matchedTerms` and `ScoreExplanation.contributions` agree on whether the duplication was preserved by the current pipeline; explanation faithfully mirrors search | Integration |
| **T-29** | UC-6 | Query analyzed to empty (all stop-words): `searchDetailed` returns empty; `explain` returns `Optional.empty()` — both agree | Integration |
| **T-30** | UC-6 | Determinism: two `explain(q, docId)` calls on the same searcher (same snapshots) return equal `ScoreExplanation` values | Invariant |

**Total: 30 tests.** Coverage counts per use case: UC-1 (6), UC-2 (9), UC-3 (6), UC-4 (2), UC-5 (3), UC-6 (4).

Coverage counts per level: Formula (6), Unit (10), Invariant (8), Integration (5), Regression (1).

### Removed from R2 test list

- The negative constructor-invariant test (R2 T17) — the invariant is enforced by test only, not by the compact constructor, so a rejection test is meaningless.
- R2 T7 "reports k1/b" is now T-13 with the additional constraint that `Rankers.bm25(...)` does not expose a factory overload — the constants are read from the ranker's internal state only.

---

## Traceability rules

- Every implementation slice in `ir-5-score-explanation-plan-r2.md` must list, in a top block:
  - `Implements: UC-X, UC-Y`
  - `Proves: T-nn, T-mm`
- Adding a test that does not trace to a use case above requires either (a) an amendment to this document adding the missing use case, or (b) explicit justification in the slice.
- Removing a use case requires updating this document and the plan together.

## Numerical tolerance policy

Fixed here so tests share one policy. For a score `s`:

- If `|s| >= 1.0`: relative tolerance `1e-9 × |s|`.
- If `|s| < 1.0`: absolute tolerance `1e-9`.

All formula and invariant tests use this policy unless explicitly overridden with justification.

---

*Companion to R2 engineering plan. If this document and R2 disagree, this document wins on behavior and R2 on architecture.*
