# Task — IR-5.R Final Engineering Review

## 1. Scope and Authority

This review verifies that the completed IR-5 implementation, its test evidence, and its documentation satisfy the canonical behavioral contract end-to-end. It assesses engineering soundness — it does not implement fixes.

**Authoritative references:**

| Artifact | Path |
|---|---|
| Use cases + test matrix (behavior authority) | `docs/knowledge/use-cases/ir-5-score-explanation-use-cases.md` |
| R2 engineering plan (architecture authority) | `docs/engineering/agents/reports/ir-5-score-explanation-plan-r2.md` |
| Architecture review | `docs/engineering/agents/reviews/ir-5-architecture-review.md` |
| Implementation reports | `ir-5-1-domain-model.md`, `ir-5-2-ranker-migration.md`, `ir-5-3-explainable-searcher.md`, `ir-5-4-documentation.md` |
| CKF log entry | `docs/knowledge/logs/core/ir-5-score-explanation.md` |

**Build evidence:** `mvn test -pl codex-ir-core,codex-ir-web` → core `252` tests 0 failures, web `466` tests 0 failures, BUILD SUCCESS. No `codex-ir-app` source file was touched; its known environment-only PDF-rendering failures are pre-existing and out of IR-5 scope.

## 2. Verdict

**PASS** — zero FAIL findings. IR-5 is complete.

## 3. Findings by Review Question

Each of the plan's eleven questions is answered PASS / CONCERN / FAIL with evidence.

### Q1 — UC-1 through UC-6 acceptance evidence present and test-verified — PASS

All six use cases are implemented and have named, passing test evidence (see Q2 test-to-class matrix). UC-1 (explain end-to-end, T-01–T-06), UC-2 (per-ranker `TermScoring`, T-07–T-15), UC-3 (field boost, T-16–T-21), UC-4 (regression + `SearchResult` shape, T-22/T-23), UC-5 (capability, T-24–T-26), UC-6 (analysis parity, T-27–T-30). No use case lacks acceptance evidence.

### Q2 — All 30 tests present, named, and green; formula references documented — PASS

All 30 canonical tests are present and green. Spot-checked formula references:

- **T-09** TF-IDF (`RankingEvaluateTest.tfIdfRankerEvaluateShouldMatchHandComputedFormula`): expected values are independently derived from `log(3/2)`, `1+log(1)` — constant math in the test, not invocation of the ranker under test.
- **T-11** BM25 (`...ShouldMatchHandComputedFormula`): `idf=log(1.2)`, `normalization=0.5`, `base=log(1.2)·2.2/1.6` derived by hand in the comment block and independent `Math.log`/literal expressions.
- **T-12** (`...ShouldPenalizeLongerDocumentViaHigherNormalization`): compares `normalization 0.5 < 1.5` and contributions with literal expected values.
- **T-16/17/18** (`FieldAwareRankingTest`): `boostFactor` asserted against literal weights (`3.0`, `2.0`, `(1·3+1·1)/2=2.0`), not against `score()` output.

No formula test computes its expected value by calling the implementation path under test.

### Q3 — Formula tests use independently derived values — PASS

Confirmed in Q2. Expected values are hand-computed constants (`Math.log(3.0/2.0)`, `0.5`, `1.5`, `2.2/1.6`, `3.0`, `2.0`, `5/3`) and, where the ranker output is compared, it is compared **to those literals** — never to another call of the same ranker (except the explicit bit-equality invariant T-08/T-10/T-15, which is correct by design).

### Q4 — Score conservation: `Double::sum` order + `1e-9` tolerance — PASS

The implementation sums contributions in iteration order, explicitly using `Double::sum` in a loop (`SimpleSearcher.explain`, `SimpleSearcher.java:219-222`), mirroring `searchDetailed`'s `merge(..., Double::sum)`. T-02 (`assertScoreConservation`) uses `TOLERANCE = 1e-9` and iterates `searchDetailed` results asserting `explain(...).score() == result.score()` within tolerance. The report notes the observed delta is `0.0` in practice because both paths use the same sequential order. Consistent `TOLERANCE = 1e-9` is declared on each test class.

### Q5 — Field-aware explanation: six UC-3 scenarios distinguishable — PASS

`FieldAwareRankingTest` distinguishes title-only (T-16), body-only (T-17), multi-field weighted average (T-18), unknown-field default (T-19), neutral-context empty boost (T-20), and raw-content empty boost (T-21). Each asserts the `FieldBoost` via `fieldBoost().isPresent()/isEmpty()`, `boostFactor()`, `effectiveWeights()`, and `contribution == base × factor` — all six scenarios are inspectable.

### Q6 — Search regression: pre-IR-5 tests green and unmodified — PASS

The pre-IR-5 tests (`RankersTest`, `FieldAwareRankingTest`, `FieldAwarePostingsTest`, `SearchersTest`, `SnapshotSearchTest`, `VectorSearcherTest`, and the rest of the core suite) pass unchanged. The only edits to pre-existing files were `Rankers.java`, `Ranker.java`, `SimpleSearcher.java`, and `package-info.java` — the migration required by IR-5.2/5.3; existing test files that gained new test methods (`RankersTest`, `FieldAwareRankingTest`) were additive within their own classes, not modifications of pre-existing test assertions. T-22 (regression) is met.

### Q7 — Capability semantics: T-24/T-25 green — PASS

- T-24 `simpleSearcherShouldImplementExplainableSearcher` asserts `SimpleSearcher instanceof ExplainableSearcher` (`ExplainableSearcherTest.java:44-49`).
- T-25 `vectorSearcherShouldNotImplementExplainableSearcher` asserts `ExplainableSearcher.class.isAssignableFrom(VectorSearcher.class)` is false (`:55-59`).
- T-26 asserts `Searcher` declares no `explain` method (`:65-71`).

All three green.

### Q8 — Query-analysis parity: T-27 present and direct — PASS

`explainContributionTermsShouldMatchSearchDetailedMatchedTermsForMultiTermQuery` (`ExplainableSearcherTest.java:222-244`) compares the explanation's contribution terms against `SearchResult.matchedTerms()` produced by `searchDetailed` for the same query. Both paths share the private `analyzeQuery` (`SimpleSearcher.java:260`), which is the structural guarantee. T-27 is a direct end-to-end parity check.

### Q9 — Documentation completeness: CKF entry + hot-path trade-off — PASS

- CKF entry exists: `docs/knowledge/logs/core/ir-5-score-explanation.md` with `ckf_status: completed`, linked from `docs/knowledge/index.md` (line 52-53).
- It links the use-case document and all four engineering reports + architecture review.
- It documents the hot-path allocation trade-off explicitly in a "Trade-offs" section (lines 105-107), as R2 §1 requires.

### Q10 — Scope discipline: no out-of-scope item introduced — PASS

Confirmed by the architecture review (Q1/Q3/Q5) and re-verified here: no rank-position explanation (NU-1), no BM25 `k1`/`b` public factory (NU-5), no vector explanation or `VectorSearcher` change (NU-3), no IR-6 concepts (NU-2), no rank fusion (NU-4). `Searcher`, `VectorSearcher`, and `SearchResult` are unchanged in shape.

### Q11 — Build status on final implementation — PASS

`mvn test -pl codex-ir-core,codex-ir-web` → `Tests run: 252` (core) and `466` (web), `Failures: 0`, `BUILD SUCCESS`. Total 718 tests, 0 failures, matching the figure recorded in the CKF entry.

## 4. Concerns (documented, non-blocking)

- **CONCERN-01 (informational):** `T-26` uses reflection in a test to assert the negative ("`Searcher` has no `explain`"). This is test-only and is the most direct way to express the invariant; it does not violate the production "no hidden magic" rule. No remediation required. (Also raised as OBS-02 in the architecture review.)
- **CONCERN-02 (informational):** The `TOLERANCE = 1e-9` constant is repeated per test class rather than shared. Conformant with the plan ("constant on the test class"); a future shared constant is optional tidy-up.

Neither concern breaks any use case; both are at the Engineering Reviewer's discretion and neither warrants a separate IR-5.R-F slice.

## 5. Risk Assessment

- **Hot-path allocation (accepted, explicit):** one `TermScoring` per matched (term, posting). Acknowledged in JavaDoc, reports, and CKF entry; negligible at current corpus sizes. Correctly documented rather than hidden.
- **External `Ranker` implementer breakage:** additive abstract `evaluate`; accepted in R2 §13, contained (no external implementers).
- **No drift risk:** `score` delegates to `evaluate` by interface default — structurally single source of truth.
- **Snapshot staleness:** `explain` reflects the snapshot bound at searcher construction (documented R5 risk). Behavioral, accepted.

No material engineering condition fails the stated expectation.

## 6. Disposition

**PASS.** No FAIL findings; the two concerns are non-blocking. Per the remediation routing in the execution plan, a PASS verdict completes IR-5 — no IR-5.R-F / IR-5.RV slices materialize.

Recommended completion actions:
1. Update the `IR-5 Execution Plan` row status in `docs/engineering/ENGINEERING_LOG.md` from "Plan active" to complete (or add a dedicated IR-5 completion note), if not already reflected.
2. Optionally adopt a shared tolerance constant in a future maintenance pass (CONCERN-02).

---

*Engineering Review performed 2026-09-07 against the completed IR-5.1–IR-5.4 implementation. No production code, tests, or configuration were modified during this review.*
