# Task — Adversarial Review of IR-5 Score Explanation Engineering Plan

## 1. Executive Assessment
The proposed IR-5 plan correctly identifies the need for score explanation and accurately restricts its scope to avoid premature evaluation or fusion logic. The structural choice to derive explanation from the same `evaluate` method that drives scoring is theoretically sound. However, the plan contains critical contradictions regarding allocation costs, API design flaws concerning capability semantics, closed-world type sealing that violates the Open/Closed Principle, and an implementation slice order that breaks the build.

## 2. Verdict
CHANGES REQUIRED

## 3. Critical Findings

### BLOCKER: Implementation Slices Break the Build
* **Evidence**: Slice IR-5.2 makes `Ranker.evaluate` abstract and migrates `BinaryRanker` and `TfIdfRanker`. `Bm25Ranker` is not migrated until IR-5.3.
* **Failure Mode**: `Bm25Ranker` implements `Ranker`. If IR-5.2 merges, `Bm25Ranker` will lack the new abstract `evaluate` method, failing compilation.
* **Recommendation**: Merge IR-5.2 and IR-5.3 into a single slice, or introduce `evaluate` as a `default` method in IR-5.2 (throwing `UnsupportedOperationException`) and make it abstract in IR-5.3. Given the small number of rankers, migrating all three simultaneously is the simplest and safest approach.

### BLOCKER: Hot-Path Allocation Contradiction
* **Evidence**: Section 1 claims "The normal search path is unchanged and pays zero allocation cost." Section 6 (Option B) states "the hot path allocates one small TermScoring per posting per matched term." Code review of `SimpleSearcher.java` confirms that `ranker.score(term, posting, ctx)` is called in the inner loop. If this delegates to `evaluate().contribution()`, it allocates.
* **Failure Mode**: The plan's executive summary makes a false guarantee. A reviewer or maintainer might accept the plan believing there is zero allocation cost.
* **Recommendation**: Correct the plan to acknowledge the per-term, per-posting allocation cost on the hot path. Accept this as an intentional trade-off for a learning platform to guarantee a single source of truth.

## 4. Major Findings

### MAJOR: Conflation of "Not Found" and "Unsupported"
* **Evidence**: Section 7 and 15 propose adding `default Optional<ScoreExplanation> explain(String query, String documentId)` to `Searcher`. `VectorSearcher` will inherit a default returning `Optional.empty()`.
* **Failure Mode**: Callers cannot distinguish between a searcher that does not support explanations and a valid searcher queried for a document that did not match. This forces callers to guess intent.
* **Recommendation**: Introduce an `ExplainableSearcher` interface that extends `Searcher`. `SimpleSearcher` implements it; `VectorSearcher` does not. 

### MAJOR: Score Conservation Invariant in Constructor
* **Evidence**: Section 7 proposes `Math.abs(score - sum(contributions)) < TOLERANCE` inside the `ScoreExplanation` constructor.
* **Failure Mode**: `SimpleSearcher.searchDetailed` accumulates scores into a Map using `Double::sum` within a loop. `ScoreExplanation` uses `Stream.mapToDouble(...).sum()`. The Java Stream API's summation algorithm differs from naive loop addition (e.g., Kahan summation). This will lead to floating-point mismatches that cause the constructor to throw runtime exceptions on perfectly valid data.
* **Recommendation**: Remove the invariant enforcement from the domain constructor. Enforce it strictly in integration tests (like T15).

### MAJOR: Sealed `TermScoring` Hierarchy
* **Evidence**: Section 7.1 proposes `sealed interface TermScoring permits BinaryTermScoring, TfIdfTermScoring, Bm25TermScoring`.
* **Failure Mode**: myIR is a research platform. External experimental rankers implementing `Ranker` cannot provide explanations because they cannot implement the sealed `TermScoring` interface without modifying core framework code.
* **Recommendation**: Remove the `sealed` modifier. Allow `TermScoring` to be a standard interface to support future and external experimentation.

## 5. Minor Findings

### MINOR: Query Analysis Duplication
* **Evidence**: Section 15 (IR-5.5) states `explain` will "Tokenize + normalize the query using the same analysis pipeline."
* **Failure Mode**: Re-implementing the tokenization/normalization loop in `explain` creates a shadow pipeline. If `searchDetailed` adds synonym expansion, `explain` will silently diverge.
* **Recommendation**: Extract an `analyzeQuery(String query)` helper in `SimpleSearcher` that returns the normalized terms, shared by both methods.

### MINOR: Unstable Ranker Identity
* **Evidence**: Section 16 proposes `rankerName = ranker.getClass().getSimpleName()`.
* **Failure Mode**: Anonymous classes return empty strings. This breaks if external rankers are implemented via lambdas or anonymous classes.
* **Recommendation**: Remove `rankerName` from `ScoreExplanation`. It is not strictly necessary for IR-5.

### MINOR: Scope Creep in BM25 Factory
* **Evidence**: Section 8 proposes an optional `Rankers.bm25(corpus, index, k1, b)` factory overload.
* **Failure Mode**: Unrelated API expansion.
* **Recommendation**: Exclude it. The explanation can report the internal `k1` and `b` constants without requiring public configurability.

### MINOR: Unnecessary Wrapper
* **Evidence**: Section 7.3 proposes `TermContribution` as a wrapper for `TermScoring`.
* **Failure Mode**: Premature abstraction for hypothetical rank fusion.
* **Recommendation**: Remove `TermContribution` and use `TermScoring` directly in `ScoreExplanation`.

## 6. Mandatory Question Review

1. **Single-source-of-truth vs hot-path allocation**: The claim of zero allocation is false. The allocation is acceptable for this stage, but the plan must honestly state the trade-off.
2. **`sealed TermScoring`**: A closed-world model conflicts with a research platform. `TermScoring` must not be sealed.
3. **`Searcher.explain` capability semantics**: Putting `explain` on `Searcher` conflates "not found" with "unsupported". Use `ExplainableSearcher`.
4. **BM25 parameter scope creep**: Does not belong in IR-5. Exclude it.
5. **Explanation model granularity**: `TermContribution` is speculative. Remove it.
6. **Score-conservation invariant**: Placing this in the constructor is dangerous due to floating-point summation differences. Enforce in tests only.
7. **Snapshot consistency**: The assumption that the same snapshot produces the same result holds, provided the searcher hasn't been reconstructed. The documented race condition is an acceptable limit of the API.
8. **Query analysis duplication**: Duplicating the pipeline is a risk. Extract a shared helper.
9. **Ranker identity**: `getSimpleName()` is unstable. Remove `rankerName` from the model.
10. **Rank semantics**: The model explains *score*, not *rank*. Narrow the phrasing.

## 7. Test Matrix Review

* **T4 / T15 (Score Conservation)**: Will likely fail if Stream summation is compared against loop summation due to IEEE 754 non-associativity. Adjust assertions to use a safe relative tolerance or ensure identical summation algorithms.
* **Missing Tests**: 
  * Behavior when an anonymous ranker class is used (if `rankerName` is kept).
  * Explicit test proving `VectorSearcher` does not implement `ExplainableSearcher` (if the recommendation is adopted).

## 8. Slice-by-Slice Review

* **IR-5.1**: Good. (Update: remove constructor invariant, unseal interface, drop `TermContribution`).
* **IR-5.2**: **BLOCKER**. Adding an abstract method breaks `Bm25Ranker`. Must be combined with IR-5.3 or use a temporary `default` method.
* **IR-5.3**: Combine with IR-5.2. Exclude the factory overload.
* **IR-5.4**: Safe and cohesive.
* **IR-5.5**: Introduce `ExplainableSearcher` here instead of adding a default to `Searcher`. Extract `analyzeQuery` helper.
* **IR-5.6**: Good.

## 9. Proposed Corrections

1. **Merge IR-5.2 and IR-5.3** into a single slice to maintain build integrity.
2. **Remove the `sealed` modifier** from `TermScoring`.
3. **Create `ExplainableSearcher`** interface; do not pollute `Searcher`.
4. **Remove `TermContribution`**; use `TermScoring` directly.
5. **Remove the constructor invariant** in `ScoreExplanation`; rely on integration tests.
6. **Extract `analyzeQuery(String)`** in `SimpleSearcher`.
7. **Remove `rankerName`** from `ScoreExplanation`.
8. **Drop the BM25 factory overload** scope creep.
9. **Correct the executive summary** to acknowledge the hot-path allocation cost.

## 10. What Should Explicitly Remain Unchanged

* The `SearchResult` record (no `ExplainedSearchResult`).
* The use of Option B (evaluate -> TermScoring) as the structural mechanism to prevent drift.
* The explicit lack of Vector Search explanation.
* The lack of fusion logic.

## 11. Risks Accepted Intentionally

* **Hot-path allocation**: Allocating `TermScoring` objects during normal search is accepted as a worthwhile cost to structurally guarantee score parity in a learning platform.
* **Snapshot Race Conditions**: A caller attempting to explain a stale `documentId` after snapshot rotation may get different results or no explanation. This is inherent to the snapshot architecture and is acceptable.

## 12. Final Recommendation

The plan's core conceptual model (Option B) is the right architectural choice for myIR. However, the build-breaking slice order, false claims about allocation, API capability conflation, and dangerous constructor invariants require revision before implementation begins. Implement the proposed corrections and proceed.

---

### Harness Observation

* The role instructions (`osk-role-engineering-reviewer`) were discoverable and clearly defined the boundaries of the review (assess architecture, risks, evidence gaps; do not silently rewrite implementation).
* The adversarial-review skill instructions (`osk-adversarial-analysis`) were available and successfully guided the methodology (identifying assumptions, proposing counterexamples, and classifying failure modes).
* The OSK context successfully constrained the agent to output a structured review report challenging the plan, rather than attempting to write the Java code for IR-5.
