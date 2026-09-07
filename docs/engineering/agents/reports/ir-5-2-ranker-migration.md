---
type: Engineering Log Entry
title: "IR-5.2 — Ranker Evaluation Contract and Atomic Migration"
description: "Adds Ranker.evaluate abstract method; atomically migrates BinaryRanker, TfIdfRanker, Bm25Ranker; extracts shared field-boost helper TermScorings; adds 21 tests covering formula, invariant, and field-boost contracts."
tags: [core, ranking, explainability, ir-5, implementation]
timestamp: 2026-09-07T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---

## Task 2 — IR-5.2 Ranker Evaluation Contract and Atomic Migration

### Summary

Added `evaluate(term, posting, context) → TermScoring` as an abstract method on `Ranker`. All three existing rankers (`BinaryRanker`, `TfIdfRanker`, `Bm25Ranker`) were migrated atomically in a single change so the build was never in an uncompilable intermediate state. Extracted shared field-boost computation into a package-private helper `TermScorings`. Made `score(term, posting, ctx)` a default that delegates via `evaluate(...).contribution()`.

### Scope

**Included:**
- `Ranker.java` — added abstract `evaluate(term, posting, context)` method; `score(term, posting, ctx)` default now delegates to `evaluate`; `score(term, posting)` default delegates through neutral context; hot-path allocation trade-off documented in JavaDoc
- `Rankers.java` — all three inner ranker classes implement `evaluate`; scoring formula moved into `evaluate`; removed previously overridden `score(term, posting)` bodies
- `TermScorings.java` — new package-private helper with `computeFieldBoost` and `applyBoost`; single location for boost computation across all rankers
- `RankersTest.java` — added T-08, T-10, T-13, T-14, T-15
- `FieldAwareRankingTest.java` — added T-16 through T-21
- `RankingEvaluateTest.java` — new class with T-09, T-11, T-12 (formula tests with hand-computed reference values)

**Excluded (deferred to IR-5.3):**
- `ExplainableSearcher` interface
- `SimpleSearcher.explain`
- `analyzeQuery` helper
- `ScoreExplanation` wiring to searcher

### Deliverables

| Type | File |
|---|---|
| Modified | `codex-ir-core/src/main/java/codex/ir/ranking/Ranker.java` |
| Modified | `codex-ir-core/src/main/java/codex/ir/ranking/Rankers.java` |
| New helper | `codex-ir-core/src/main/java/codex/ir/ranking/TermScorings.java` |
| Modified test | `codex-ir-core/src/test/java/codex/ir/ranking/RankersTest.java` |
| Modified test | `codex-ir-core/src/test/java/codex/ir/ranking/FieldAwareRankingTest.java` |
| New test class | `codex-ir-core/src/test/java/codex/ir/ranking/RankingEvaluateTest.java` |

### Changed Files

- Modified: `Ranker.java`, `Rankers.java`
- Created: `TermScorings.java`, `RankingEvaluateTest.java`
- Modified test: `RankersTest.java`, `FieldAwareRankingTest.java`

### Validation

```
mvn compile -pl codex-ir-core                → BUILD SUCCESS
mvn test-compile -pl codex-ir-core           → BUILD SUCCESS (22 source files)
mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.RankersTest
                                             → Tests run: 17, Failures: 0
mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.RankingEvaluateTest
                                             → Tests run: 3,  Failures: 0
mvn test -pl codex-ir-core -Dtest=codex.ir.ranking.FieldAwareRankingTest
                                             → Tests run: 16, Failures: 0
mvn test -pl codex-ir-core                  → Tests run: 236, Failures: 0
mvn test -pl codex-ir-core,codex-ir-web     → Tests run: 702, Failures: 0 (BUILD SUCCESS)
```

Pre-existing `codex-ir-app` failures in PDF-rendering tests are environment failures (missing OpenHtmlToPdf / pdf2htmlex binaries), unrelated to IR-5. No app source file was touched.

### Tests

**Added — `RankersTest` (5 tests: T-08, T-10, T-13, T-14, T-15):**

| Test | Canonical ID | What it proves |
|---|---|---|
| `binaryRankerEvaluateShouldReturnBinaryTermScoringWithContributionEqualToScore` | T-08 | `evaluate(...).contribution()` bit-equals `score(...)` for binary ranker |
| `tfIdfRankerEvaluateShouldReturnTfIdfTermScoringWithContributionEqualToScore` | T-10 | `evaluate(...).contribution()` bit-equals `score(...)` for TF-IDF ranker |
| `bm25RankerEvaluateShouldReturnK1AndBFromRankerInternals` | T-13 | k1=1.2 and b=0.75 are populated from ranker internals (not hardcoded in test) |
| `bm25RankerEvaluateShouldReturnWellFormedZeroScoringWhenDocumentLengthIsZero` | T-14 | Zero-contribution guard: `contribution==0.0`, `TermScoring` non-null, `term` preserved |
| `bm25RankerEvaluateShouldReturnContributionEqualToScore` | T-15 | `evaluate(...).contribution()` bit-equals `score(...)` for BM25 ranker |

**Added — `RankingEvaluateTest` (3 tests: T-09, T-11, T-12):**

| Test | Canonical ID | What it proves |
|---|---|---|
| `tfIdfRankerEvaluateShouldMatchHandComputedFormula` | T-09 | All `TfIdfTermScoring` fields match independently hand-computed values (tf=1, idf=log(3/2), sublinearTf=1.0) |
| `bm25RankerEvaluateShouldMatchHandComputedFormula` | T-11 | All `Bm25TermScoring` fields match independently hand-computed values (idf=log(1.2), norm=0.5, base=idf×2.2/1.6) |
| `bm25RankerEvaluateShouldPenalizeLongerDocumentViaHigherNormalization` | T-12 | Shorter doc has larger contribution; normalization_short(0.5) < normalization_long(1.5) |

**Added — `FieldAwareRankingTest` (6 tests: T-16–T-21):**

| Test | Canonical ID | What it proves |
|---|---|---|
| `evaluateShouldExposeTitleBoostFactorWhenTermOccursOnlyInTitle` | T-16 | title-only: `boostFactor==titleWeight`, `contribution==base×titleWeight` |
| `evaluateShouldExposeBodyBoostFactorWhenTermOccursOnlyInBody` | T-17 | body-only: `boostFactor==bodyWeight`, `contribution==base×bodyWeight` |
| `evaluateShouldComputeFrequencyWeightedAverageBoostForMultiFieldOccurrence` | T-18 | multi-field: `boostFactor==(freq_t×w_t + freq_b×w_b)/(freq_t+freq_b)` |
| `evaluateShouldMapUnknownFieldToWeightOneInEffectiveWeightsAndLeaveScoreUnchanged` | T-19 | unknown field maps to `effectiveWeights["custom"]=1.0`; boostFactor=1.0; contribution=base |
| `evaluateWithNeutralContextShouldReturnEmptyFieldBoostAndContributionEqualToBase` | T-20 | neutral context → `fieldBoost().isEmpty()`, `contribution==base` |
| `evaluateWithRawContentDocumentShouldReturnEmptyFieldBoostAndContributionEqualToBase` | T-21 | empty fieldFrequencies → `fieldBoost().isEmpty()`, `contribution==base` |

**Total new tests this slice: 14.** Pre-IR-5.2 suite: 688 (core+web). Post-IR-5.2 suite: 702 (core+web).

### Engineering Notes

- **Atomic migration requirement.** Adding `evaluate` as abstract while migrating rankers one-by-one would leave the build uncompilable. All three rankers were migrated in a single diff — no intermediate state was ever uncompilable.
- **`score(term, posting)` override removal.** The previous `Rankers.java` contained overridden `score(String, Posting)` methods in some inner ranker classes that duplicated scoring logic. These were removed; the interface defaults now cover them correctly.
- **`TermScorings` helper.** The field-boost logic previously lived inline in `Ranker.default score(term, posting, ctx)`. Extracting it to a package-private class ensures every ranker computes the same `FieldBoost` using the same code path. Unknown fields are assigned effective weight `1.0` via `FieldWeights.weightFor()` and recorded in `FieldBoost.effectiveWeights()`, making the default observable.
- **Tolerance constant.** Formula and boost tests use `TOLERANCE = 1e-9`. Invariant tests (contribution bit-equals score) use `delta=0.0` — the double values must be identical since `score()` now delegates to `evaluate()` and returns `contribution()` directly.

### Decisions

- `TermScorings` placed in `codex.ir.ranking` as a package-private final class (not a private static method in `Rankers`). Keeps `Rankers.java` focused on ranker construction; allows future tests to access `TermScorings` from the same package.
- `Ranker.score(term, posting, ctx)` default body contains only `return evaluate(term, posting, context).contribution()` — no inline boost logic, satisfying acceptance criterion 6.
- T-09 fixture uses `corpus.add()` + `index.add()` directly (not the Indexer pipeline) to control exact tf=1 and N=3 values without normalization side effects.
- T-11/T-12 fixture uses documents with explicit `.length()` so avgdl is exactly `(2+10)/2 = 6.0`, enabling clean hand-computed reference values.

### Tradeoffs

- **Hot-path allocation.** Every `score(term, posting)` call now allocates one `TermScoring` record. Documented as an intentional trade-off in `Ranker` JavaDoc. For current in-memory corpus sizes (thousands of documents) the allocation is negligible.
- **Package-private helper vs. private inner class.** `TermScorings` is package-private so future test-only ranker implementations in the same test package can reach it without reflection.

### Risks

- **Score preservation.** The old `Rankers.java` had inline field-boost logic in the `score(term, posting, ctx)` default on `Ranker`. Moving this logic to `TermScorings` and calling it from each ranker's `evaluate` method preserves identical numeric behavior — verified by bit-equal invariant tests T-08, T-10, T-15.
- **No risk to existing behavior.** All pre-IR-5.2 tests (702 after this slice) pass unchanged.

### Known Limitations

- `ExplainableSearcher` and `SimpleSearcher.explain` are not implemented. That is IR-5.3.
- `ScoreExplanation` exists as a record but is not yet returned by any searcher method.

### Follow-ups

- IR-5.3: Add `ExplainableSearcher extends Searcher`, implement `SimpleSearcher.explain`, extract `analyzeQuery` helper.
- Score conservation (T-02) is tested in IR-5.3 when both `searchDetailed` and `explain` are implemented.

### Next Step

IR-5.3 — ExplainableSearcher + SimpleSearcher.explain + analyzeQuery helper.
