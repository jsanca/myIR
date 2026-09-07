---
type: Engineering Plan
title: "IR-5 Score Explanation — Execution Plan"
description: "Slice-by-slice execution plan for implementing IR-5 Score Explanation. Translates the approved R2 design into actionable engineering slices with ownership, gates, and completion criteria."
tags: [core, ranking, explainability, plan, execution]
timestamp: 2026-09-07T00:00:00Z
ckf_version: "0.1"
ckf_status: active
ckf_scope: core
ckf_owner: project
---

# IR-5 Score Explanation — Execution Plan

## Authoritative References

This plan translates the approved R2 design into executable slices. An implementing agent must read the following before starting any slice:

| Artifact | Path | Authority |
|---|---|---|
| Use cases + test matrix (T-01–T-30) | [`docs/knowledge/use-cases/ir-5-score-explanation-use-cases.md`](../../../knowledge/use-cases/ir-5-score-explanation-use-cases.md) | Behavioral contract — wins when this plan and the use-case doc conflict on behavior |
| R2 engineering plan | [`docs/engineering/agents/reports/ir-5-score-explanation-plan-r2.md`](../agents/reports/ir-5-score-explanation-plan-r2.md) | Architecture — wins when the above conflict on architecture |
| Adversarial review | [`docs/engineering/agents/reviews/ir-5-score-explanation-adversarial-review.md`](../agents/reviews/ir-5-score-explanation-adversarial-review.md) | Historical corrections; already incorporated into R2 |
| Coding conventions | [`CLAUDE.md`](../../../CLAUDE.md) and [`AGENTS.md`](../../../AGENTS.md) | Style, pattern, and verification rules |

---

## Execution Flow

```
IR-5.1  Engineering — Explanation domain model
   ↓
IR-5.2  Engineering — Ranker.evaluate + atomic ranker migration
   ↓
IR-5.3  Engineering — ExplainableSearcher + SimpleSearcher.explain
   ↓
IR-5.AR Architecture Review — inspect implemented architecture
   │
   ├─ PASS ──────────────────────────────────────────────┐
   │                                                      ↓
   └─ findings requiring code changes                   IR-5.4  Engineering — CKF documentation
          ↓                                                ↓
       IR-5.AR-F  Engineering (remediation)            IR-5.R  Engineering Review
          ↓           ↑
       Architecture re-verification                    ├─ PASS → IR-5 COMPLETE
                                                        │
                                                        └─ findings → IR-5.R-F → IR-5.RV → COMPLETE
```

---

## Status Model

| Slice | Status | Blocked By |
|---|---|---|
| IR-5.1 | **READY** | — |
| IR-5.2 | BLOCKED | IR-5.1 |
| IR-5.3 | BLOCKED | IR-5.2 |
| IR-5.AR | BLOCKED | IR-5.3 |
| IR-5.4 | BLOCKED | IR-5.AR (PASS) |
| IR-5.R | BLOCKED | IR-5.4 |
| IR-5.AR-F | CONDITIONAL | IR-5.AR findings |
| IR-5.R-F | CONDITIONAL | IR-5.R findings |
| IR-5.RV | CONDITIONAL | IR-5.R-F |

Conditional slices do not materialize unless a review produces findings requiring production changes.

---

## QA Participation Decision

No separate QA Engineer slice is planned for IR-5.

IR-5 is a core/backend library with no deployment boundary, no database, no browser surface, no cross-service workflow, and no externally observable user journey. All acceptance evidence is formulaic (independent hand-computed references), unit, invariant, integration, and regression testing fully owned by the Engineering Role.

This decision should be revisited if IR-5 is later surfaced through a REST API, a CLI tool, a deployed service, or a multi-component integration that requires environment-specific validation outside the standard Maven test suite.

---

## IR-5.1 — Explanation Domain Model

- **ID:** IR-5.1
- **Name:** Explanation Domain Model
- **Owner / OSK Role:** Engineering Role
- **Status:** READY
- **Purpose:** Land the pure type layer — `TermScoring` interface, the three concrete `*TermScoring` records, `FieldBoost`, and `ScoreExplanation` — with no wiring to any ranker or searcher. Every subsequent slice depends on these types existing and compiling.
- **Depends On:** None (additive, no existing code changes).

### Canonical Use Cases

- UC-2 (record shapes per ranker)
- UC-3 (`FieldBoost` shape)
- UC-1 (advances: `ScoreExplanation` shape)

### Canonical Test Evidence

| Test | Behavior | Level |
|---|---|---|
| T-07 | `BinaryTermScoring` exposes `term`, `base==1.0`, `contribution==1.0`; no TF/IDF fields | Unit |
| T-13 | `Bm25TermScoring` exposes `k1` and `b` from construction | Unit |
| T-19 | Unknown-field default of `1.0` is visible in `FieldBoost.effectiveWeights` | Formula |
| T-22 | Full pre-IR-5 test suite (all 1,022 tests) passes unmodified | Regression |
| T-23 | `SearchResult` record shape unchanged — existing constructor signature and field types match | Unit |

Structural tests added in this slice (not numbered in the matrix but supporting readiness for IR-5.2):
- All six new records/interface instantiate correctly.
- Defensive copies: modifying the source collection after construction does not mutate the record.
- `contribution()` and `base()` return the values supplied at construction.

### Implementation Scope

Create the following files in `codex.ir.ranking`:

1. **`TermScoring.java`** — unsealed `public interface TermScoring` with methods `String term()`, `double base()`, `Optional<FieldBoost> fieldBoost()`, `double contribution()`. JavaDoc: explains the three-way relationship (base × boostFactor = contribution, or base when no boost).

2. **`BinaryTermScoring.java`** — `public record BinaryTermScoring(String term, double base, Optional<FieldBoost> fieldBoost, double contribution) implements TermScoring`. Compact constructor: null-check `term` and `fieldBoost`; `base` must be `0.0` or `1.0`.

3. **`TfIdfTermScoring.java`** — `public record TfIdfTermScoring(String term, double tf, double sublinearTf, double idf, double base, Optional<FieldBoost> fieldBoost, double contribution) implements TermScoring`. Compact constructor: null-check `term` and `fieldBoost`.

4. **`Bm25TermScoring.java`** — `public record Bm25TermScoring(String term, double tf, double idf, int documentLength, double averageDocumentLength, double k1, double b, double normalization, double base, Optional<FieldBoost> fieldBoost, double contribution) implements TermScoring`. Compact constructor: null-check `term` and `fieldBoost`.

5. **`FieldBoost.java`** — `public record FieldBoost(Map<String,Integer> fieldFrequencies, Map<String,Double> effectiveWeights, double weightedSum, int totalFrequency, double boostFactor)`. Compact constructor: null-check both maps; `Map.copyOf` both. See R2 §7.2 for the exact shape.

6. **`ScoreExplanation.java`** — `public record ScoreExplanation(String query, String documentId, double score, List<TermScoring> contributions)`. Compact constructor: null-check all fields; `List.copyOf(contributions)`. **No score-conservation assertion in the constructor** (per R2 C4 / adversarial review).

7. **`package-info.java`** — update to mention the new explanation types and their role.

**Numerical tolerance constant:** define `static final double TOLERANCE = 1e-9` in a test-accessible location (e.g., a package-private `ExplanationTestConstants` class or a constant on the test class). All formula and invariant tests use this constant.

### Expected Files / Areas

| Action | Path |
|---|---|
| Create | `codex-ir-core/src/main/java/codex/ir/ranking/TermScoring.java` |
| Create | `codex-ir-core/src/main/java/codex/ir/ranking/BinaryTermScoring.java` |
| Create | `codex-ir-core/src/main/java/codex/ir/ranking/TfIdfTermScoring.java` |
| Create | `codex-ir-core/src/main/java/codex/ir/ranking/Bm25TermScoring.java` |
| Create | `codex-ir-core/src/main/java/codex/ir/ranking/FieldBoost.java` |
| Create | `codex-ir-core/src/main/java/codex/ir/ranking/ScoreExplanation.java` |
| Modify | `codex-ir-core/src/main/java/codex/ir/ranking/package-info.java` |
| Create | `codex-ir-core/src/test/java/codex/ir/ranking/TermScoringModelTest.java` |

### Acceptance Criteria

1. All six new types compile with `mvn compile`.
2. `TermScoringModelTest` passes, covering T-07, T-13, T-19, T-23, defensive copies, and the structural instantiation tests.
3. `mvn test` (full suite) passes with 0 failures — all 1,022 pre-IR-5 tests are unmodified and green (T-22).
4. No existing file other than `package-info.java` is modified.
5. The tolerance constant is established and used in T-07/T-13/T-19 where numerical assertions are made.

### Validation

```bash
mvn compile
mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.TermScoringModelTest
mvn test
```

All three commands must complete with BUILD SUCCESS and 0 failures.

### Required Artifact / Report

Engineering report at `docs/engineering/agents/reports/ir-5-1-domain-model.md` following the standard template in CLAUDE.md. Must document:
- files created;
- validation command output (test counts);
- tolerance constant value and rationale;
- confirmation that no existing file other than `package-info.java` was modified.

### Non-Goals

- No `Ranker.evaluate` method yet.
- No `ExplainableSearcher`.
- No field-boost computation logic — `FieldBoost` is only a data holder in this slice.
- No changes to `Rankers`, `SimpleSearcher`, `VectorSearcher`, `Searchers`, `SearchResult`, or any indexing code.

### Exit Gate

All acceptance criteria met; engineering report filed; `mvn test` green. Engineering Role marks IR-5.1 complete and IR-5.2 unblocked.

### Next Slice

IR-5.2 — Ranker evaluation contract and atomic ranker migration.

---

## IR-5.2 — Ranker Evaluation Contract + Atomic Migration

- **ID:** IR-5.2
- **Name:** Ranker Evaluation Contract and Atomic Migration
- **Owner / OSK Role:** Engineering Role
- **Status:** BLOCKED (by IR-5.1)
- **Purpose:** Add `evaluate(term, posting, context) → TermScoring` as an abstract method on `Ranker`; simultaneously migrate all three existing rankers (`BinaryRanker`, `TfIdfRanker`, `Bm25Ranker`) in a single commit so the build is never in an intermediate uncompilable state. Make `score(term, posting, ctx)` a default that delegates via `evaluate(...).contribution()`. Extract the shared field-boost computation as a package-private helper so every ranker computes the same `FieldBoost` using the same code path.
- **Depends On:** IR-5.1 (all six new types must exist).

### Canonical Use Cases

- UC-2 — per-ranker scoring evidence (formula semantics fully implemented)
- UC-3 — field-aware scoring effects (boost computation moved here)
- UC-4 — existing search behavior preserved (score semantics unchanged)

### Canonical Test Evidence

| Test | Behavior | Level |
|---|---|---|
| T-07 | `BinaryTermScoring` shape (now verified via `evaluate` return value) | Unit |
| T-08 | `BinaryRanker.evaluate(...).contribution() == score(...)` bit-equal | Invariant |
| T-09 | `TfIdfTermScoring` fields match independently hand-computed reference values | Formula |
| T-10 | `TfIdfRanker.evaluate(...).contribution() == score(...)` | Invariant |
| T-11 | `Bm25TermScoring` fields match independently hand-computed reference values | Formula |
| T-12 | Shorter document has larger BM25 contribution than longer with equal `tf`; `normalization` is larger on longer doc | Formula |
| T-13 | `Bm25TermScoring.k1 == 1.2`, `b == 0.75` (read from ranker internals) | Unit |
| T-14 | BM25 with `documentLength <= 0` or `averageDocumentLength <= 0` → `contribution == 0.0`; `TermScoring` still well-formed | Unit |
| T-15 | `Bm25Ranker.evaluate(...).contribution() == score(...)` | Invariant |
| T-16 | Title-only occurrence: `boostFactor == titleWeight`, `contribution == base × titleWeight` | Formula |
| T-17 | Body-only occurrence: `boostFactor == bodyWeight`, `contribution == base × bodyWeight` | Formula |
| T-18 | Multi-field occurrence: `boostFactor == (freq_t×w_t + freq_b×w_b)/(freq_t+freq_b)` | Formula |
| T-19 | Unknown field: `effectiveWeights` shows unknown field mapped to `1.0` | Formula |
| T-20 | Neutral `RankingContext`: `fieldBoost().isEmpty()`, `contribution == base` | Invariant |
| T-21 | Raw-content document (empty `fieldFrequencies`): `fieldBoost().isEmpty()`, `contribution == base` | Invariant |
| T-22 | Full pre-IR-5 test suite unchanged and green | Regression |

### Implementation Scope

**Step 1 — `Ranker` interface** (`codex-ir-core/src/main/java/codex/ir/ranking/Ranker.java`):

```java
// NEW abstract method
TermScoring evaluate(String term, Posting posting, RankingContext context);

// EXISTING default — now delegates to evaluate
default double score(String term, Posting posting, RankingContext context) {
    return evaluate(term, posting, context).contribution();
}

// EXISTING — unchanged
default double score(String term, Posting posting) {
    return score(term, posting, RankingContext.neutral());
}
```

Remove the existing field-boost calculation from the `score(term, posting, RankingContext)` default body — that logic moves into the shared helper.

**Step 2 — shared field-boost helper** (package-private, in `codex.ir.ranking`):

Extract a static helper — either a `TermScorings` utility class or a `private static` method in `Rankers` — that computes:

```java
Optional<FieldBoost> computeFieldBoost(Posting posting, RankingContext context)
double applyBoost(double base, Optional<FieldBoost> fieldBoost)
```

`computeFieldBoost` returns `Optional.empty()` when the context is neutral or when `posting.fieldFrequencies()` is empty. Otherwise it computes `weightedSum`, `totalFrequency`, `boostFactor`, and records `effectiveWeights` with unknown fields mapped to `1.0`.

**Step 3 — migrate all three rankers atomically** (all changes in `Rankers.java`):

Each inner ranker class (`BinaryRanker`, `TfIdfRanker`, `Bm25Ranker`):
- Add `@Override public TermScoring evaluate(String term, Posting posting, RankingContext context)`.
- Move scoring formula into `evaluate`; compute the appropriate `*TermScoring` record.
- Remove any previously overridden `score(term, posting)` body — the interface default now covers it.
- Call the shared boost helper at the end of `evaluate` to attach `FieldBoost` and compute the final `contribution`.

All three rankers must be modified in the same commit.

**Step 4 — new tests** (`FieldAwareRankingTest.java` for field-boost tests; new `RankingEvaluateTest.java` for formula and parity tests):

Formula expected values for T-09, T-11, T-12, T-16, T-17, T-18 must be derived independently by hand computation (or a separate calculator), not by calling the implementation under test.

Reference fixture for BM25 formula tests: a corpus with known `N`, `df`, `tf`, `dl`, `avgdl`, `k1=1.2`, `b=0.75` values from which `idf`, `normalization`, and `base` are calculated by hand.

### Expected Files / Areas

| Action | Path |
|---|---|
| Modify | `codex-ir-core/src/main/java/codex/ir/ranking/Ranker.java` |
| Modify | `codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java` |
| Create (if helper class) | `codex-ir-core/src/main/java/codex/ir/ranking/TermScorings.java` |
| Modify | `codex-ir-core/src/test/java/codex/ir/ranking/RankersTest.java` (add T-08, T-10, T-13, T-14, T-15) |
| Modify or create | `codex-ir-core/src/test/java/codex/ir/ranking/FieldAwareRankingTest.java` (add T-16 through T-21) |
| Create | `codex-ir-core/src/test/java/codex/ir/ranking/RankingEvaluateTest.java` (T-09, T-11, T-12 formula tests) |

### Acceptance Criteria

1. `mvn compile` succeeds — the abstract `evaluate` method has three implementations from the moment the diff lands; no transient uncompilable state.
2. All T-08, T-09, T-10, T-11, T-12, T-13, T-14, T-15 pass.
3. All T-16, T-17, T-18, T-19, T-20, T-21 pass at the tolerance from IR-5.1.
4. T-22: `mvn test` (full suite) passes with 0 failures — all pre-IR-5 tests unmodified and green.
5. Formula expected values for T-09 / T-11 / T-12 / T-16 / T-17 / T-18 are documented in the test as comments showing the hand-computation.
6. `Ranker.score(term, posting, ctx)` default body contains only `return evaluate(term, posting, context).contribution();` — no inline boost logic.

### Validation

```bash
mvn compile
mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.RankersTest
mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.RankingEvaluateTest
mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.FieldAwareRankingTest
mvn test
```

All must complete with BUILD SUCCESS and 0 failures.

### Required Artifact / Report

Engineering report at `docs/engineering/agents/reports/ir-5-2-ranker-migration.md`. Must document:
- hand-computed reference values for every formula test (T-09, T-11, T-12, T-16, T-17, T-18);
- confirmation that all three rankers were migrated in a single commit;
- confirmation that field-boost logic was removed from `Ranker.score(term, posting, ctx)` default and now lives only in the shared helper;
- `mvn test` output (BUILD SUCCESS + test counts).

### Non-Goals

- No `ExplainableSearcher` or `SimpleSearcher.explain` yet.
- No `Rankers.bm25(corpus, index, k1, b)` factory overload (R2 C9).
- No changes to `Searcher`, `SimpleSearcher`, `VectorSearcher`, `Searchers`, `SearchResult`, or any indexing code.
- No new public API beyond `Ranker.evaluate`.

### Exit Gate

All acceptance criteria met; formula references documented; engineering report filed; `mvn test` green. Engineering Role marks IR-5.2 complete and IR-5.3 unblocked.

### Next Slice

IR-5.3 — ExplainableSearcher capability and `SimpleSearcher.explain`.

---

## IR-5.3 — ExplainableSearcher Capability + SimpleSearcher.explain

- **ID:** IR-5.3
- **Name:** ExplainableSearcher Capability and SimpleSearcher.explain
- **Owner / OSK Role:** Engineering Role
- **Status:** BLOCKED (by IR-5.2)
- **Purpose:** Introduce the `ExplainableSearcher` capability interface, implement `explain(query, documentId)` on `SimpleSearcher`, and extract the shared `analyzeQuery` primitive that prevents search and explanation from using different query-analysis pipelines. This slice completes the functional implementation of UC-1 through UC-6.
- **Depends On:** IR-5.2 (all rankers must implement `evaluate` before `explain` can call it).

### Canonical Use Cases

- UC-1 — explain the score of a matching document (end-to-end)
- UC-5 — explanation support is an explicit capability
- UC-6 — explanation uses the same analyzed query semantics as search

### Canonical Test Evidence

| Test | Behavior | Level |
|---|---|---|
| T-01 | `explain(query, docId)` on a matching pair returns `Optional.of(ScoreExplanation)` with correct document id, analyzed terms, and total score | Integration |
| T-02 | Score conservation: for every `SearchResult` from `searchDetailed(q)`, `explain(q, r.documentId()).score() ≈ r.score()` using sequential `Double::sum` | Invariant |
| T-03 | Explanation contains no contribution for query terms that did not match the document | Integration |
| T-04 | Unknown `documentId` → `Optional.empty()` | Unit |
| T-05 | Null or blank query → `Optional.empty()` | Unit |
| T-06 | Query analyzed to empty (all stop-words) → `Optional.empty()` | Unit |
| T-24 | `SimpleSearcher instanceof ExplainableSearcher` is `true` | Unit |
| T-25 | `VectorSearcher instanceof ExplainableSearcher` is `false` | Unit |
| T-26 | `Searcher` interface has no `explain` method | Unit |
| T-27 | Multi-term query: ordered analyzed terms from `analyzeQuery(q)` are identical for `searchDetailed` and `explain` paths | Invariant |
| T-28 | Duplicate query terms: explanation faithfully mirrors scoring behavior of `searchDetailed` | Integration |
| T-29 | All-stop-word query: `searchDetailed` returns empty; `explain` returns `Optional.empty()` | Integration |
| T-30 | Determinism: two `explain` calls with identical searcher state return equal `ScoreExplanation` | Invariant |
| T-22 | Full pre-IR-5 test suite unchanged and green | Regression |

### Implementation Scope

**Step 1 — `ExplainableSearcher` interface** (new file `codex.ir.search.ExplainableSearcher`):

```java
public interface ExplainableSearcher extends Searcher {
    /**
     * Returns a score explanation for the given document.
     *
     * @return Optional.of(explanation) if the analyzed query matches the document;
     *         Optional.empty() if the document is unknown, the query is null/blank,
     *         or no analyzed term matches.
     */
    Optional<ScoreExplanation> explain(String query, String documentId);
}
```

`Searcher` must not be modified. No `explain` default on `Searcher`.

**Step 2 — `SimpleSearcher`** (`codex.ir.search.SimpleSearcher`):

- Add `implements ExplainableSearcher` to the class declaration.
- Extract `private List<String> analyzeQuery(String query)`: tokenize + normalize + collect into a list of non-empty strings. Call this from `searchDetailed` (replacing its inline analysis) and from `explain`.
- Implement `explain`:
  ```
  if (query == null || query.isBlank()) → Optional.empty()
  if (corpus snapshot does not contain documentId) → Optional.empty()
  terms = analyzeQuery(query)
  if (terms.isEmpty()) → Optional.empty()
  for each term (in order):
      find posting for documentId in invertedIndex (linear scan over the term's postings)
      if found: collect ranker.evaluate(term, posting, rankingContext) → TermScoring
  if contributions is empty → Optional.empty()
  sum = contributions accumulated with Double::sum in iteration order
  return Optional.of(new ScoreExplanation(query, documentId, sum, contributions))
  ```

The summation order must match `searchDetailed`'s per-document accumulation order. This is what T-02 verifies.

**Step 3 — `VectorSearcher`**: no changes. Its `instanceof ExplainableSearcher` check returns `false` by construction.

**Step 4 — `Searchers` factory**: no `ExplainableSearcher`-specific factory methods needed; the factory creates `SimpleSearcher` instances via the existing `Searcher` return type. Callers who need `ExplainableSearcher` use `instanceof`.

**Step 5 — new tests** (`ExplainableSearcherTest.java`):

A self-contained test class covering T-01 through T-06, T-24 through T-30. Uses the same BM25/TF-IDF/Binary fixtures established in earlier slices. Score conservation test (T-02) must sum explanation contributions using `Double::sum` in the same iteration order.

### Expected Files / Areas

| Action | Path |
|---|---|
| Create | `codex-ir-core/src/main/java/codex/ir/search/ExplainableSearcher.java` |
| Modify | `codex-ir-core/src/main/java/codex/ir/search/SimpleSearcher.java` |
| Create | `codex-ir-core/src/test/java/codex/ir/search/ExplainableSearcherTest.java` |
| No change | `codex-ir-core/src/main/java/codex/ir/search/Searcher.java` |
| No change | `codex-ir-core/src/main/java/codex/ir/search/VectorSearcher.java` |
| No change | `codex-ir-core/src/main/java/codex/ir/search/Searchers.java` |

### Acceptance Criteria

1. `mvn compile` succeeds with no warnings on `SimpleSearcher`.
2. `ExplainableSearcherTest` passes covering T-01 through T-06 and T-24 through T-30.
3. T-02 score conservation passes at the tolerance constant from IR-5.1 for Binary, TF-IDF, and BM25 rankers.
4. T-22: `mvn test` (full suite) passes with 0 failures. `searchDetailed` behavior is byte-identical before and after the `analyzeQuery` refactor (verified implicitly by the existing `SearchersTest` and `SnapshotSearchTest` remaining green).
5. `Searcher` interface is unmodified — confirmed by reading the file after the diff lands.
6. `VectorSearcher` is unmodified — confirmed by reading the file.
7. `SearchResult` record shape is unchanged — confirmed by T-23 from IR-5.1 still passing.

### Validation

```bash
mvn compile
mvn test -pl codex-ir-core -Dtest=codex.ir.search.ExplainableSearcherTest
mvn test -pl codex-ir-core -Dtest=codex.ir.search.SearchersTest
mvn test -pl codex-ir-core -Dtest=codex.ir.search.SnapshotSearchTest
mvn test
```

All must complete with BUILD SUCCESS and 0 failures.

### Required Artifact / Report

Engineering report at `docs/engineering/agents/reports/ir-5-3-explainable-searcher.md`. Must document:
- confirmation that `Searcher`, `VectorSearcher`, and `SearchResult` are unchanged;
- evidence of T-02 score conservation (rankers tested, tolerance used, values observed);
- confirmation that `searchDetailed` refactor to `analyzeQuery` produced identical results (via existing test green status);
- `mvn test` output (BUILD SUCCESS + test counts including new tests).

### Non-Goals

- No `explain` default on `Searcher`.
- No bulk explanation API.
- No vector explanation — `VectorSearcher` unchanged.
- No changes to indexing, corpus, or normalizer pipeline.
- No new factory methods in `Searchers`.

### Exit Gate

All acceptance criteria met; engineering report filed; `mvn test` green across all three modules. Engineering Role marks IR-5.3 complete and IR-5.AR unblocked.

### Next Slice

IR-5.AR — Architecture Review.

---

## IR-5.AR — Score Explanation Architecture Review

- **ID:** IR-5.AR
- **Name:** Score Explanation Architecture Review
- **Owner / OSK Role:** Architecture Reviewer
- **Status:** BLOCKED (by IR-5.3)
- **Purpose:** Inspect the implemented IR-5 architecture — not redesign it — and verify that it conforms to the approved R2 design, the canonical use cases, and the existing core architecture boundaries. Issue a verdict that either unblocks IR-5.4 or routes to IR-5.AR-F remediation.
- **Depends On:** IR-5.1, IR-5.2, IR-5.3 (all functional implementation complete).

### Evidence Inputs

- Implemented source files: `Ranker.java`, `Rankers.java`, `TermScoring.java`, `BinaryTermScoring.java`, `TfIdfTermScoring.java`, `Bm25TermScoring.java`, `FieldBoost.java`, `ScoreExplanation.java`, `ExplainableSearcher.java`, `SimpleSearcher.java`.
- Engineering reports from IR-5.1, IR-5.2, IR-5.3.
- R2 plan (authoritative architecture reference).
- Use-case document (behavioral reference).
- Existing ADRs in `docs/adrs/`.

### Review Questions

#### 1. Ranking boundary

Verify that `evaluate(term, posting, context) → TermScoring → contribution()` is the single scoring source of truth. Confirm `Ranker.score(term, posting, ctx)` default body contains only the delegation; no ranker implementation overrides `score(term, posting, ctx)` with independent logic.

#### 2. Explainability capability

Verify the hierarchy:
```
Searcher
    ↑
ExplainableSearcher
    ↑
SimpleSearcher
```
Confirm `Searcher` has no `explain` method. Confirm `VectorSearcher` does not implement `ExplainableSearcher`. Confirm that `Optional.empty()` from an `ExplainableSearcher` unambiguously means "no match for this input" rather than "unsupported."

#### 3. Extensibility

Verify `TermScoring` is an unsealed interface. Confirm no `sealed` keyword or `permits` clause was introduced. Confirm a hypothetical future ranker could implement `TermScoring` without modifying any existing file in `codex.ir.ranking`.

#### 4. Domain boundaries

Verify the explanation types (`TermScoring`, `FieldBoost`, `ScoreExplanation`, `ExplainableSearcher`) are placed in `codex.ir.ranking` or `codex.ir.search` only. Confirm no explanation concept leaked into: `codex.ir.indexer`, `codex.ir.corpus`, `codex.ir.vector`, `codex.ir.web`, `codex.ir.app`.

#### 5. Scope discipline

Confirm the implementation did not introduce:
- rank-position explanation (NU-1);
- BM25 `k1`/`b` public factory overload (NU-5);
- vector explanation or changes to `VectorSearcher` (NU-3);
- IR-6 evaluation concepts;
- rank fusion.

#### 6. `analyzeQuery` isolation

Verify the shared `analyzeQuery` helper is `private` on `SimpleSearcher`. Confirm it is not accessible from outside the class (no `protected`, no package-private visibility, no extraction into a public utility).

#### 7. Allocation honesty

Verify that neither the engineering reports nor any JavaDoc claims zero hot-path allocation. The hot path allocates one `TermScoring` per matched (term, posting) — this must be acknowledged, not hidden.

### Finding Severity Policy

- **BLOCKER:** implementation violates a use-case acceptance criterion or will cause test failures.
- **MAJOR:** architectural contract is wrong (e.g., sealed hierarchy reintroduced, `explain` on `Searcher`, capability semantics broken).
- **MINOR:** style, documentation, or non-functional concern that does not block correctness.

BLOCKER and MAJOR findings require remediation before IR-5.4.

MINOR findings may be deferred to IR-5.4 or noted without remediation.

### Verdict Expectations

- **PASS:** all BLOCKER and MAJOR questions have satisfactory answers; IR-5.4 is unblocked.
- **CHANGES REQUIRED:** one or more BLOCKER or MAJOR findings; route to IR-5.AR-F.

### Review Output

Architecture Review artifact at `docs/engineering/agents/reviews/ir-5-architecture-review.md`.

Minimum sections: findings by ID and severity, verdict, and for CHANGES REQUIRED findings: what must change and why.

### Remediation Routing

```
PASS          → IR-5.4 is unblocked
CHANGES REQUIRED → IR-5.AR-F (Engineering Role remediation)
                   → Architecture Reviewer re-verifies implemented changes
                   → then IR-5.4 unblocked
```

### Non-Goals

- The Architecture Reviewer does not implement production changes.
- The Architecture Reviewer does not redesign IR-5.
- MINOR findings do not block forward progress.

### Exit Gate

Architecture Review artifact filed with a PASS verdict (or PASS after IR-5.AR-F remediation). IR-5.4 unblocked.

### Next Slice

IR-5.4 (on PASS) or IR-5.AR-F (on CHANGES REQUIRED).

---

## IR-5.AR-F — Architecture Review Remediation (Conditional)

- **ID:** IR-5.AR-F
- **Name:** Architecture Review Remediation
- **Owner / OSK Role:** Engineering Role
- **Status:** CONDITIONAL — materializes only if IR-5.AR verdict is CHANGES REQUIRED.
- **Purpose:** Implement the specific code changes identified as BLOCKER or MAJOR findings in IR-5.AR. Report evidence. Return to Architecture Reviewer for re-verification.
- **Depends On:** IR-5.AR findings.

After remediation is implemented and `mvn test` is green, the Architecture Reviewer re-verifies the specific findings. If the re-verification passes, IR-5.4 is unblocked. If new BLOCKER/MAJOR findings surface, a further IR-5.AR-F iteration may occur.

No standard schema is prescribed here because the scope is determined by the findings at review time.

---

## IR-5.4 — Knowledge and Usage Documentation

- **ID:** IR-5.4
- **Name:** Knowledge and Usage Documentation
- **Owner / OSK Role:** Engineering Role
- **Status:** BLOCKED (by IR-5.AR PASS)
- **Purpose:** Document the capability IR-5 actually delivered. Create the CKF Engineering Log entry, update the knowledge index, and add a short usage example to the README. This slice introduces no production behavior.
- **Depends On:** IR-5.AR (must be PASS before documentation is written against the final implementation).

### Canonical Use Cases

- UC-1, UC-5, UC-6 — documentation surface (user-facing usage illustration).

### Canonical Test Evidence

None new. Documentation must be consistent with passing tests from IR-5.1 through IR-5.3. Code snippets in the README must compile correctly.

### Implementation Scope

1. **CKF Engineering Log entry** at `docs/knowledge/logs/core/ir-5-score-explanation.md`:
   - Frontmatter: `type: Engineering Log Entry`, `ckf_status: completed`, `ckf_scope: core`.
   - Sections: summary of what IR-5 delivered, capability overview (`ExplainableSearcher`, `explain` call pattern), link to use-case document, link to engineering reports (IR-5.1, IR-5.2, IR-5.3), acceptance evidence (test counts per use case).
   - Note the hot-path allocation trade-off explicitly (per R2 §1).

2. **Knowledge index update** at `docs/knowledge/index.md`:
   - Add a link to the new CKF entry.

3. **README usage example** at root `README.md`:
   - Add a short "Explaining a score" section illustrating:
     - capability check (`if (searcher instanceof ExplainableSearcher es)`);
     - `es.explain(query, documentId)` call;
     - iterating over `contributions` to read per-term scoring.
   - The example must compile as written (use real type names from the implementation).

4. **Engineering Log update** at `docs/engineering/ENGINEERING_LOG.md`:
   - Add or update the IR-5 entry to link the CKF log.

### Expected Files / Areas

| Action | Path |
|---|---|
| Create | `docs/knowledge/logs/core/ir-5-score-explanation.md` |
| Modify | `docs/knowledge/index.md` |
| Modify | `README.md` |
| Modify | `docs/engineering/ENGINEERING_LOG.md` |

No source files. No test files.

### Acceptance Criteria

1. CKF log entry exists with `ckf_status: completed` and links to the use-case document and engineering reports.
2. `docs/knowledge/index.md` links the new entry.
3. README usage example uses the actual type names and compiles (verified by reading and checking against the source).
4. `mvn compile` remains green (no documentation change can break this; verify as a sanity check).

### Validation

```bash
mvn compile
# Manual: verify all links in the new CKF entry resolve to existing files
# Manual: verify README snippet uses real type names matching the implementation
```

### Required Artifact / Report

Engineering report at `docs/engineering/agents/reports/ir-5-4-documentation.md`. Must document:
- files created/modified;
- confirmation that no production code was changed;
- confirmation that README snippet type names match the implementation.

### Non-Goals

- No new ADR unless a design decision changed during implementation that was not already captured.
- No new production behavior.
- No changes to test files.

### Exit Gate

Documentation complete; CKF entry filed; knowledge index updated; README snippet uses real types; `mvn compile` green. Engineering Role marks IR-5.4 complete and IR-5.R unblocked.

### Next Slice

IR-5.R — Final Engineering Review.

---

## IR-5.R — IR-5 Engineering Review

- **ID:** IR-5.R
- **Name:** IR-5 Engineering Review
- **Owner / OSK Role:** Engineering Reviewer
- **Status:** BLOCKED (by IR-5.4)
- **Purpose:** Verify that the completed IR-5 implementation, its test evidence, and its documentation satisfy the canonical behavioral contract end-to-end. Issue a verdict.
- **Depends On:** IR-5.1, IR-5.2, IR-5.3, IR-5.AR (PASS), IR-5.4 all complete.

### Evidence Inputs

- All implementation source files.
- Engineering reports from IR-5.1 through IR-5.4.
- Architecture review artifact from IR-5.AR.
- Use-case document (T-01 through T-30 traceability).
- R2 plan.
- CKF Engineering Log entry from IR-5.4.
- `mvn test` output (must show 0 failures before the review begins).

### Review Questions

For each item below, the reviewer must record a finding (PASS / CONCERN / FAIL):

1. **UC-1 through UC-6** — is each use case's acceptance evidence present and verified by the named tests?
2. **T-01 through T-30** — are all 30 tests present, named, and green? Spot-check formula expected values (T-09, T-11, T-12, T-16, T-17, T-18) against the documentation of hand-computed references in the IR-5.2 report.
3. **Formula correctness** — do the formula tests use independently derived expected values, or do any tests compute the expected value by calling the implementation?
4. **Score conservation** — does T-02 use `Double::sum` accumulation in the same iteration order as `searchDetailed`? Is the tolerance `1e-9` used consistently?
5. **Field-aware explanation** — are all six UC-3 scenarios distinguishable by inspection of `FieldBoost` in the tests?
6. **Search regression** — are all pre-IR-5 tests (T-22) passing? Were any modified?
7. **Capability semantics** — is T-24 (`SimpleSearcher instanceof ExplainableSearcher == true`) and T-25 (`VectorSearcher instanceof ExplainableSearcher == false`) present and green?
8. **Query-analysis parity** — is T-27 present and does it directly compare the `analyzeQuery` output used by `searchDetailed` and `explain`?
9. **Documentation completeness** — does the CKF log entry exist, link the use-case doc, and report the hot-path allocation trade-off?
10. **Scope discipline** — was any out-of-scope item introduced (rank-position explanation, BM25 tuning factory, vector explanation, IR-6 concepts)?
11. **Build status** — is `mvn test` green on the final implementation?

### Finding Severity Policy

- **FAIL:** use-case acceptance criterion not met, test absent, formula expected value derived from the same implementation under test, regression broken, out-of-scope item introduced.
- **CONCERN:** documentation gap, naming inconsistency, test coverage gap that does not break a use case.

FAIL findings require remediation (IR-5.R-F). CONCERN findings are documented; remediation is at the Engineering Reviewer's discretion.

### Verdict Expectations

- **PASS:** all eleven questions answered satisfactorily; IR-5 is complete.
- **CHANGES REQUIRED:** one or more FAIL findings.

### Review Output

Engineering Review artifact at `docs/engineering/agents/reviews/ir-5-engineering-review.md`.

Minimum sections: findings by question (severity + evidence), verdict, and for CHANGES REQUIRED findings: what must change.

### Remediation Routing

```
PASS → IR-5 COMPLETE (update ENGINEERING_LOG.md status)
CHANGES REQUIRED →
    IR-5.R-F: Engineering Role implements fixes (mvn test must be green after each fix)
    IR-5.RV:  Engineering Reviewer verifies the specific findings are resolved
    → COMPLETE if verification passes
    → further IR-5.R-F iteration if new FAILs surface
```

### Non-Goals

- The Engineering Reviewer does not implement fixes.
- The Engineering Reviewer does not redesign IR-5.
- CONCERN findings do not block the PASS verdict unless the Engineering Reviewer judges them FAIL.

### Exit Gate

PASS verdict (original or after IR-5.R-F/RV). Engineering Log updated to reflect IR-5 complete. Roadmap entry for IR-5 updated if applicable.

---

## IR-5.R-F — Engineering Review Remediation (Conditional)

- **ID:** IR-5.R-F
- **Name:** Engineering Review Remediation
- **Owner / OSK Role:** Engineering Role
- **Status:** CONDITIONAL — materializes only if IR-5.R verdict is CHANGES REQUIRED.
- **Purpose:** Implement the specific fixes identified as FAIL findings in IR-5.R. Run `mvn test` after each fix. Return to Engineering Reviewer for targeted re-verification.
- **Depends On:** IR-5.R findings.

---

## IR-5.RV — Engineering Review Verification (Conditional)

- **ID:** IR-5.RV
- **Name:** Engineering Review Verification
- **Owner / OSK Role:** Engineering Reviewer
- **Status:** CONDITIONAL — materializes only if IR-5.R-F was executed.
- **Purpose:** Verify that the FAIL findings from IR-5.R have been resolved. Issue final verdict.
- **Depends On:** IR-5.R-F complete.

---

## Traceability Summary

| Slice | Canonical UCs | Canonical Tests |
|---|---|---|
| IR-5.1 | UC-2 (shapes), UC-3 (shape), UC-1 (shape) | T-07, T-13, T-19, T-22, T-23 |
| IR-5.2 | UC-2 (formulas), UC-3 (boost), UC-4 (regression) | T-07–T-22 |
| IR-5.3 | UC-1 (end-to-end), UC-5, UC-6 | T-01–T-06, T-22, T-24–T-30 |
| IR-5.AR | All UC-1–UC-6 (review) | — |
| IR-5.4 | UC-1, UC-5, UC-6 (documentation surface) | — |
| IR-5.R | All UC-1–UC-6 (verification) | T-01–T-30, T-22 |

All 30 canonical tests (T-01 through T-30) are owned by the use-case document at [`docs/knowledge/use-cases/ir-5-score-explanation-use-cases.md`](../../../knowledge/use-cases/ir-5-score-explanation-use-cases.md). This plan references them by ID only.

---

*Created 2026-09-07. Translates IR-5 R2 approved design into executable engineering slices. No production code was changed in producing this plan.*
