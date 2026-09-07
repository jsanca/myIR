---
type: Engineering Log Entry
title: "IR-5.1 — Explanation Domain Model"
description: "Implements the pure type layer for IR-5 Score Explanation: TermScoring interface, BinaryTermScoring, TfIdfTermScoring, Bm25TermScoring, FieldBoost, and ScoreExplanation records. No ranker or searcher wiring."
tags: [core, ranking, explainability, ir-5, implementation]
timestamp: 2026-09-07T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---

## Task 1 — IR-5.1 Explanation Domain Model

### Summary

Implemented the pure explanation type layer for IR-5 Score Explanation. Six new types were added to `codex.ir.ranking`, none of which are wired to any existing ranker or searcher. The slice is purely additive — no production file other than `package-info.java` was modified.

### Scope

**Included:**
- `TermScoring` unsealed interface with `term()`, `base()`, `fieldBoost()`, `contribution()`
- `BinaryTermScoring` record (exposes only the statistics meaningful to binary ranking)
- `TfIdfTermScoring` record (exposes tf, sublinearTf, idf, base)
- `Bm25TermScoring` record (exposes tf, idf, dl, avgdl, k1, b, normalization, base)
- `FieldBoost` record (fieldFrequencies, effectiveWeights, weightedSum, totalFrequency, boostFactor)
- `ScoreExplanation` record (query, documentId, score, contributions)
- `TermScoringModelTest` — 25 tests covering T-07, T-13, T-19, T-23 and structural contracts
- Updated `package-info.java`

**Excluded (deferred to IR-5.2/5.3):**
- `Ranker.evaluate` abstract method
- Field-boost computation logic (FieldBoost is only a data holder here)
- `ExplainableSearcher` interface and `SimpleSearcher.explain`
- Any change to `Rankers`, `SimpleSearcher`, `VectorSearcher`, `Searchers`, or `SearchResult`

### Deliverables

| Type | File |
|---|---|
| New interface | `codex-ir-core/src/main/java/codex/ir/ranking/TermScoring.java` |
| New record | `codex-ir-core/src/main/java/codex/ir/ranking/BinaryTermScoring.java` |
| New record | `codex-ir-core/src/main/java/codex/ir/ranking/TfIdfTermScoring.java` |
| New record | `codex-ir-core/src/main/java/codex/ir/ranking/Bm25TermScoring.java` |
| New record | `codex-ir-core/src/main/java/codex/ir/ranking/FieldBoost.java` |
| New record | `codex-ir-core/src/main/java/codex/ir/ranking/ScoreExplanation.java` |
| Modified | `codex-ir-core/src/main/java/codex/ir/ranking/package-info.java` |
| New test class | `codex-ir-core/src/test/java/codex/ir/ranking/TermScoringModelTest.java` |

### Changed Files

- Created: 6 production files, 1 test file
- Modified: `package-info.java` (added Score Explanation section)
- No other file in `codex-ir-core`, `codex-ir-web`, or `codex-ir-app` was modified by this slice

### Validation

```
mvn compile -pl codex-ir-core           → BUILD SUCCESS (nothing to compile = classes up to date)
mvn test -pl codex-ir-core
  -Dtest=codex.ir.ranking.TermScoringModelTest
                                         → Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
mvn test -pl codex-ir-core,codex-ir-web → Tests run: 197+466=663, Failures: 0 (BUILD SUCCESS)
```

The `codex-ir-app` module reports pre-existing failures in PDF-rendering tests (`ReaderHtmlWriterTest`, `ManifestOrderPdfAssemblyStrategyTest`, `Pdf2HtmlExAwarePdfRendererTest`, `ExistingMirrorLoaderTest`). These are environment failures caused by missing OpenHtmlToPdf / pdf2htmlex binaries on this machine; they are unrelated to IR-5 and were present before this slice began. No app source file was touched.

### Tests

**Added — `TermScoringModelTest` (25 tests):**

| Test | Canonical ID | What it proves |
|---|---|---|
| `binaryTermScoringShouldExposeTermBaseAndContributionForMatchedTerm` | T-07 | `BinaryTermScoring` shape for matched term |
| `binaryTermScoringShouldExposeZeroBaseAndContributionForNoMatch` | T-07 | `BinaryTermScoring` zero-match shape |
| `binaryTermScoringShouldRejectInvalidBase` | structural | base must be 0.0 or 1.0 |
| `binaryTermScoringShouldRejectNullTerm` | structural | null-check term |
| `binaryTermScoringShouldRejectNullFieldBoost` | structural | null-check fieldBoost |
| `binaryTermScoringShouldExposeFieldBoostWhenPresent` | structural | FieldBoost attaches correctly |
| `tfIdfTermScoringShouldInstantiateAndExposeAllFields` | structural | TfIdf record fields |
| `tfIdfTermScoringShouldRejectNullTerm` | structural | null-check |
| `tfIdfTermScoringShouldRejectNullFieldBoost` | structural | null-check |
| `bm25TermScoringShouldExposeK1AndB` | T-13 | k1=1.2 and b=0.75 readable from record |
| `bm25TermScoringShouldInstantiateAndExposeAllFields` | structural | BM25 record fields |
| `bm25TermScoringShouldRejectNullTerm` | structural | null-check |
| `bm25TermScoringShouldRejectNullFieldBoost` | structural | null-check |
| `fieldBoostShouldMakeUnknownFieldWeightObservable` | T-19 | unknown field mapped to 1.0 in effectiveWeights |
| `fieldBoostShouldInstantiateAndExposeAllFields` | structural | FieldBoost record fields |
| `fieldBoostShouldRejectNullFieldFrequencies` | structural | null-check |
| `fieldBoostShouldRejectNullEffectiveWeights` | structural | null-check |
| `fieldBoostShouldDefensiveCopyFieldFrequencies` | structural | Map.copyOf enforced |
| `fieldBoostShouldDefensiveCopyEffectiveWeights` | structural | Map.copyOf enforced |
| `scoreExplanationShouldInstantiateAndExposeAllFields` | structural | ScoreExplanation record fields |
| `scoreExplanationShouldRejectNullQuery` | structural | null-check |
| `scoreExplanationShouldRejectNullDocumentId` | structural | null-check |
| `scoreExplanationShouldRejectNullContributions` | structural | null-check |
| `scoreExplanationShouldDefensiveCopyContributions` | structural | List.copyOf enforced |
| `searchResultRecordShouldRetainItsPreIr5Shape` | T-23 | SearchResult unchanged |

**Not added:** no changes to `RankersTest`, `FieldAwareRankingTest`, `SearchersTest` — those belong to IR-5.2/5.3.

### Engineering Notes

- **`TermScoring` is intentionally unsealed.** No `sealed` keyword or `permits` clause. A future experimental ranker can implement `TermScoring` without modifying any existing file.
- **`BinaryTermScoring` validates `base` in the compact constructor.** Binary semantics strictly require 0.0 or 1.0; the guard documents the invariant rather than leaving it to callers.
- **`ScoreExplanation` has no score-conservation assertion** in the compact constructor. Per R2 C4: IEEE 754 non-associativity means `Stream.sum()` and a sequential `Double::sum` loop can differ, and a constructor assertion would throw on valid data. Conservation is enforced by test T-02 in IR-5.3.
- **Tolerance constant `TOLERANCE = 1e-9`** is defined as `static final` on `TermScoringModelTest`. Subsequent test classes (IR-5.2, IR-5.3) should use the same value — either by referencing this class or by repeating the constant.

### Decisions

- Placed `FieldBoost` and all `*TermScoring` records in `codex.ir.ranking` (not `codex.ir.search`). Explanations describe ranking behavior; the search layer consumes them. Consistent with the R2 design.
- `BinaryTermScoring` carries `Optional<FieldBoost> fieldBoost` rather than a primitive boost flag. Consistent with the other two ranker records; `TermScoring` interface can be traversed uniformly.
- `documentLength` on `Bm25TermScoring` is `int` (matches aggregated term counts). `averageDocumentLength` is `double` because it is a corpus-average ratio.

### Tradeoffs

- All types are records rather than hand-rolled classes. Records provide `equals`, `hashCode`, `toString`, and component accessors automatically — no boilerplate, no risk of omitting a field in `equals`.
- `FieldBoost` uses `Map.copyOf` for both maps. This prevents aliasing bugs when the caller reuses a mutable map, at the cost of one allocation per boost computation. Acceptable for an explanation path.

### Risks

- **No risk to existing behavior.** This slice is purely additive; it introduces no caller of the new types.
- **T-13 is shape-only in this slice.** The actual `k1=1.2` and `b=0.75` values come from the ranker's internal state, verified in IR-5.2 when `Bm25Ranker.evaluate` populates `Bm25TermScoring`. In IR-5.1 the test constructs `Bm25TermScoring` directly with those constants; IR-5.2 tests will verify the ranker supplies them.

### Known Limitations

- `FieldBoost` is a data holder only. No computation logic yet. `computeFieldBoost` is deferred to IR-5.2.
- `ScoreExplanation` and `ExplainableSearcher` are not yet connected. That wiring is IR-5.3.

### Follow-ups

- IR-5.2: Add `Ranker.evaluate`, migrate all three rankers atomically, extract field-boost helper.
- IR-5.3: Add `ExplainableSearcher`, implement `SimpleSearcher.explain`, extract `analyzeQuery`.
- Score conservation (T-02) is tested in IR-5.3 when both `searchDetailed` and `explain` are implemented.

### Next Step

IR-5.2 — Ranker evaluation contract and atomic migration of all three rankers.
