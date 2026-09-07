# Task — IR-5.AR Score Explanation Architecture Review

## 1. Scope and Authority

This review inspects the implemented IR-5 architecture — it does not redesign it. It verifies conformance of the delivered source against the approved R2 design, the canonical use cases, and the existing core architecture boundaries.

**Authoritative references:**

| Artifact | Path |
|---|---|
| R2 engineering plan (architecture authority) | `docs/engineering/agents/reports/ir-5-score-explanation-plan-r2.md` |
| Use cases + test matrix (behavior authority) | `docs/knowledge/use-cases/ir-5-score-explanation-use-cases.md` |
| Implementation reports | `ir-5-1-domain-model.md`, `ir-5-2-ranker-migration.md`, `ir-5-3-explainable-searcher.md` |

**Reviewed implementation files:** `Ranker.java`, `Rankers.java`, `TermScoring.java`, `TermScorings.java`, `BinaryTermScoring.java`, `TfIdfTermScoring.java`, `Bm25TermScoring.java`, `FieldBoost.java`, `ScoreExplanation.java`, `RankingContext.java`, `FieldWeights.java`, `ExplainableSearcher.java`, `SimpleSearcher.java`, `Searcher.java`, `VectorSearcher.java`, `Searchers.java`, and both `package-info.java` files.

**Build evidence:** `mvn test -pl codex-ir-core` → `Tests run: 252, Failures: 0, Errors: 0` (BUILD SUCCESS).

## 2. Verdict

**PASS** — zero BLOCKER and zero MAJOR findings. IR-5.4 is unblocked.

## 3. Findings by Review Question

Each question is answered against the implemented source, with the severity policy from the execution plan: BLOCKER (use-case violation / test failure), MAJOR (architectural contract wrong), MINOR (non-blocking style/documentation).

### Q1 — Ranking boundary: `evaluate` is the single scoring source of truth — PASS

- `Ranker.evaluate(String, Posting, RankingContext)` is the only abstract scoring method; `Ranker.score(term, posting, ctx)` default body is exactly `return evaluate(term, posting, context).contribution();` (`Ranker.java:94-96`).
- `Ranker.score(term, posting)` delegates through `RankingContext.neutral()` (`Ranker.java:107-109`).
- None of `BinaryRanker`, `TfIdfRanker`, `Bm25Ranker` overrides `score(...)`; they override only `idf` and `evaluate` (`Rankers.java`). No second scoring code path exists.

### Q2 — Explainability capability — PASS

- Hierarchy is correct: `Searcher` → `ExplainableSearcher extends Searcher` (`ExplainableSearcher.java:20`) → `SimpleSearcher implements ExplainableSearcher` (`SimpleSearcher.java:32`).
- `Searcher` has no `explain` method (`Searcher.java` exposes only `search` and `searchDetailed`).
- `VectorSearcher implements Searcher` only (`VectorSearcher.java:17`) — it does not implement `ExplainableSearcher`.
- `Optional.empty()` semantics are unambiguous: the JavaDoc on `ExplainableSearcher.explain` and `SimpleSearcher.explain` enumerates the exact "no match / unknown / blank / empty-query" cases (`ExplainableSearcher.java:31-33`, `SimpleSearcher.java:183-187`). "Unsupported" is distinguished via `instanceof ExplainableSearcher`, not via `Optional`.

### Q3 — Extensibility: unsealed `TermScoring` — PASS

- `TermScoring` is a plain `public interface` with no `sealed` keyword or `permits` clause (`TermScoring.java:22`). A grep for `sealed`/`permits` across `codex-ir-core/src/main/java` finds only JavaDoc prose, no source declaration.
- A future ranker can provide its own `TermScoring` implementation in a new file without modifying any existing file in `codex.ir.ranking`.

### Q4 — Domain boundaries: no explanation-type leakage — PASS

- All explanation types live in `codex.ir.ranking` (`TermScoring`, `BinaryTermScoring`, `TfIdfTermScoring`, `Bm25TermScoring`, `FieldBoost`, `ScoreExplanation`, `TermScorings`) or `codex.ir.search` (`ExplainableSearcher`).
- A repo-wide grep for `TermScoring|ScoreExplanation|FieldBoost|ExplainableSearcher` outside those two packages returns zero matches in `codex-ir-core`, `codex-ir-web`, and `codex-ir-app`. No explanation concept leaked into `indexer`, `corpus`, `vector`, `web`, or `app`.

### Q5 — Scope discipline — PASS

- No rank-position explanation (NU-1): `ScoreExplanation` carries a reconstructed *score*, never a rank.
- No BM25 `k1`/`b` public factory overload (NU-5): the only public factory is `Rankers.bm25(corpus, index)`; the `k1`/`b` constructor is package-private and private to `Bm25Ranker` (`Rankers.java:78-80, 195`).
- No vector explanation / `VectorSearcher` change (NU-3): `VectorSearcher.java` is unmodified relative to pre-IR-5.
- No IR-6 evaluation concepts (NU-2), no rank fusion (NU-4).

### Q6 — `analyzeQuery` isolation — PASS

- `analyzeQuery` is `private` on `SimpleSearcher` (`SimpleSearcher.java:260`). It is not `protected`, not package-private, not a public utility. Both `searchDetailed` and `explain` consume it (`SimpleSearcher.java:126, 242`).

### Q7 — Allocation honesty — PASS

- The hot-path allocation is explicitly acknowledged in production JavaDoc (`Ranker.java:31-35`, "Hot-path allocation (accepted trade-off)") and in the IR-5.2 report's Tradeoffs section. No report or JavaDoc claims zero allocation. This satisfies the R2 C2 correction.

## 4. Positive Findings

- **POS-01** Single source of truth is structural, not discipline-only: `score` delegates to `evaluate` by interface default, so search and explanation cannot drift (Q1).
- **POS-02** `TermScorings` (field-boost helper) is package-private and final, keeping the boost formula in exactly one code path across all three rankers (Q1/Q5).
- **POS-03** `FieldBoost.effectiveWeights` makes the unknown-field `1.0` default observable rather than silently applied, satisfying UC-3's observability requirement.
- **POS-04** `ScoreExplanation` deliberately omits the score-conservation assertion from its compact constructor (C4), enforcing it via tests only, avoiding IEEE 754 summation-order false failures.

## 5. Observations (non-blocking)

- **OBS-01** `T-26` verifies "`Searcher` has no `explain` method" using reflection in the test class. This is a test-only structural assertion, not production reflection, and does not violate the project's "no hidden magic" convention. It is the most direct way to assert the negative; acceptable.
- **OBS-02** The `TOLERANCE = 1e-9` constant is declared independently on each test class rather than in one shared package-private constant. The execution plan explicitly permitted a "constant on the test class," so this is conformant; a shared constant is a possible future tidy-up, not a defect.

## 6. Risk Assessment

- **Hot-path allocation (accepted).** One `TermScoring` record per matched (term, posting) even when no explanation is requested. Acknowledged in JavaDoc and reports; negligible at current in-memory corpus sizes. Low risk, correctly documented.
- **External ranker breakage.** Adding abstract `evaluate` breaks hypothetical external `Ranker` implementers; accepted in R2 §13 for a single-repo research platform. Contained — no external implementers exist.
- **No drift risk.** The structural delegation (Q1) removes the primary historical risk (parallel score/explain paths). Residual numerical risk is confined to test tolerance, not architecture.

## 7. Disposition

No remediation required. Proceed to **IR-5.4** (Knowledge and Usage Documentation). The two observations above are informational and may be deferred without affecting the PASS verdict.

---

*Architecture Review performed 2026-09-07 against the IR-5.1–IR-5.3 implementation. No production code, tests, or configuration were modified during this review.*
