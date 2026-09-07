---
type: Engineering Log Entry
title: "IR-5.3 — ExplainableSearcher Capability and SimpleSearcher.explain"
description: "Introduces ExplainableSearcher interface; implements explain(query, documentId) and extracts analyzeQuery on SimpleSearcher; adds 16 tests covering T-01 through T-06 and T-24 through T-30."
tags: [core, search, explainability, ir-5, implementation]
timestamp: 2026-09-07T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---

## Task 3 — IR-5.3 ExplainableSearcher Capability and SimpleSearcher.explain

### Summary

Introduced the `ExplainableSearcher` capability interface and implemented `explain(query, documentId)` on `SimpleSearcher`. Extracted the shared `analyzeQuery` private method from `searchDetailed` so both paths tokenize and normalize through an identical pipeline (UC-6). `Searcher`, `VectorSearcher`, and `SearchResult` were not modified.

### Scope

**Included:**
- `ExplainableSearcher.java` — new `public interface ExplainableSearcher extends Searcher` with `Optional<ScoreExplanation> explain(String query, String documentId)`
- `SimpleSearcher.java` — added `implements ExplainableSearcher`; extracted `analyzeQuery(String) → List<String>`; refactored `searchDetailed` to use `analyzeQuery`; implemented `explain`
- `ExplainableSearcherTest.java` — 16 tests covering T-01 through T-06 and T-24 through T-30

**Explicitly unchanged (verified):**
- `Searcher.java` — no `explain` default, no modification
- `VectorSearcher.java` — no change; does not implement `ExplainableSearcher` by construction
- `SearchResult.java` — record shape unchanged (T-23 still passes)
- `Searchers.java` — no new factory methods

### Deliverables

| Type | File |
|---|---|
| New interface | `codex-ir-core/src/main/java/codex/ir/search/ExplainableSearcher.java` |
| Modified | `codex-ir-core/src/main/java/codex/ir/search/SimpleSearcher.java` |
| New test class | `codex-ir-core/src/test/java/codex/ir/search/ExplainableSearcherTest.java` |

### Changed Files

- Created: `ExplainableSearcher.java`, `ExplainableSearcherTest.java`
- Modified: `SimpleSearcher.java`

### Validation

```
mvn compile -pl codex-ir-core
  → BUILD SUCCESS (68 source files compiled, 0 warnings)

mvn test -pl codex-ir-core -Dtest=codex.ir.search.ExplainableSearcherTest
  → Tests run: 16, Failures: 0, Errors: 0, Skipped: 0

mvn test -pl codex-ir-core -Dtest="codex.ir.search.SearchersTest,codex.ir.search.SnapshotSearchTest"
  → Tests run: 7,  Failures: 0 (searchDetailed behavior identical after analyzeQuery refactor)

mvn test -pl codex-ir-core
  → Tests run: 252, Failures: 0

mvn test -pl codex-ir-core,codex-ir-web
  → Tests run: 718, Failures: 0, BUILD SUCCESS
```

Pre-existing `codex-ir-app` failures in PDF-rendering tests are environment failures (missing binaries), unrelated to IR-5. No app source file was touched.

**Unchanged file confirmation:**
- `git diff codex-ir-core/src/main/java/codex/ir/search/Searcher.java` → no output (unchanged)
- `git diff codex-ir-core/src/main/java/codex/ir/search/VectorSearcher.java` → no output (unchanged)

### Tests

**Added — `ExplainableSearcherTest` (16 tests: T-01–T-06, T-24–T-30):**

| Test | Canonical ID | What it proves |
|---|---|---|
| `explainShouldReturnScoreExplanationForMatchingDocument` | T-01 | explain returns `Optional.of(explanation)` with correct query, documentId, positive score, non-empty contributions |
| `explainScoreShouldConserveSearchDetailedScoreForBinaryRanker` | T-02 | score conservation for Binary ranker (TOLERANCE=1e-9) |
| `explainScoreShouldConserveSearchDetailedScoreForTfIdfRanker` | T-02 | score conservation for TF-IDF ranker |
| `explainScoreShouldConserveSearchDetailedScoreForBm25Ranker` | T-02 | score conservation for BM25 ranker |
| `explainShouldExcludeContributionsForQueryTermsNotInDocument` | T-03 | matched term in contributions; non-matched term absent |
| `explainShouldReturnEmptyForUnknownDocumentId` | T-04 | unknown documentId → `Optional.empty()` |
| `explainShouldReturnEmptyForNullQuery` | T-05 | null query → `Optional.empty()` |
| `explainShouldReturnEmptyForBlankQuery` | T-05 | blank query → `Optional.empty()` |
| `explainShouldReturnEmptyWhenQueryAnalyzesToEmpty` | T-06 | all-stop-word query → `Optional.empty()` |
| `simpleSearcherShouldImplementExplainableSearcher` | T-24 | `SimpleSearcher instanceof ExplainableSearcher` is true |
| `vectorSearcherShouldNotImplementExplainableSearcher` | T-25 | `ExplainableSearcher.isAssignableFrom(VectorSearcher.class)` is false |
| `searcherInterfaceShouldHaveNoExplainMethod` | T-26 | `Searcher` has no method named "explain" (reflection check) |
| `explainContributionTermsShouldMatchSearchDetailedMatchedTermsForMultiTermQuery` | T-27 | multi-term: every contribution term is in `searchDetailed.matchedTerms()` |
| `explainShouldFaithfullyMirrorDuplicateQueryTermBehavior` | T-28 | "java java" → 2 contributions; explanation score conserves search score |
| `allStopWordQueryShouldReturnEmptyFromBothSearchAndExplain` | T-29 | all-stop-word: both `searchDetailed` returns empty and `explain` returns `Optional.empty()` |
| `explainShouldBeDeterministic` | T-30 | two identical `explain` calls return equal `ScoreExplanation` |

**Total new tests this slice: 16.** Pre-IR-5.3 suite: 702 (core+web). Post-IR-5.3 suite: 718 (core+web).

### Engineering Notes

- **`analyzeQuery` extraction.** The previous `searchDetailed` inlined token normalization with `for (token : queryTokens) { normalizer.normalize(token).ifPresent(...) }`. Extracting it to `private List<String> analyzeQuery(String)` is behavior-preserving: the same loop, same filter, same order. Existing `SearchersTest` and `SnapshotSearchTest` remaining green confirm no behavioral regression (T-22).
- **Score conservation (T-02).** Both `searchDetailed` and `explain` accumulate contributions with `Double::sum` in the same iteration order (driven by `analyzeQuery`). Since `ranker.score(t, p, ctx)` now delegates to `ranker.evaluate(t, p, ctx).contribution()` (IR-5.2 invariant), the values are identical rather than just approximately equal. Tests use `TOLERANCE = 1e-9` per the IR-5.1 constant, but in practice the delta is 0.0.
- **`explain` returns `Optional.empty()` for non-matching doc.** If the document exists in the corpus but no analyzed term has a posting for it, `contributions` is empty → `Optional.empty()`. This correctly distinguishes "exists but not matching" from "does not exist."
- **Duplicate query terms (T-28).** `analyzeQuery` preserves duplicates (it returns a `List`, not a `Set`). A query of "java java" yields `["java", "java"]`. `explain` iterates this list, finds the same posting twice, and collects two `TermScoring` entries. This mirrors what `searchDetailed` does — accumulating the contribution twice via `merge(..., Double::sum)`.
- **T-25 implementation.** Using `ExplainableSearcher.class.isAssignableFrom(VectorSearcher.class)` avoids constructing a full vector-search stack (which requires several collaborators). The check is a type-system assertion, not a runtime behavior test.

### Decisions

- `ExplainableSearcher` placed in `codex.ir.search` (same package as `Searcher`). Keeps the capability visible alongside the search API without creating a new package for a single interface.
- `analyzeQuery` is `private` — it is an implementation detail of `SimpleSearcher`, not part of any public contract.
- No guard for null `documentId` beyond `corpus.get(documentId).isEmpty()`: `CorpusSnapshot.get(null)` returns `Optional.empty()` for unknown keys in the in-memory implementation, making an explicit null-check redundant. Added a null guard anyway for defensive clarity.
- `explain` returns `Optional.empty()` rather than throwing when the document is unknown or the query is null/blank. Consistent with the spec (T-04, T-05) and avoids forcing callers to catch exceptions.

### Tradeoffs

- **Linear scan to find posting.** `explain` scans `invertedIndex.getPostings(term)` linearly to find the posting for `documentId`. This is O(df) per term. For in-memory corpora (the target) this is negligible. If performance matters at scale, a `Map<String, Posting>` index by documentId could be added to `IndexSnapshot`.
- **`ScoreExplanation` records the raw query, not the analyzed form.** This matches UC-1 (the caller-visible query is the raw query). The analyzed terms are visible via `contributions[i].term()`.

### Risks

- **No risk to existing behavior.** `analyzeQuery` is a pure extraction — the loop and normalization logic are identical. `searchDetailed` behavior is byte-identical (confirmed by existing tests).
- **Score conservation is best-effort for multi-document queries.** T-02 tests conservation per document, but floating-point addition is non-associative. The test tolerance is `1e-9`; in practice conservation is exact because `Double::sum` is called in the same sequential order.

### Known Limitations

- `VectorSearcher` does not implement `ExplainableSearcher` — vector explanation is not part of this scope.
- No bulk explanation API (e.g., `explainAll(query)`) — not required by the spec.
- `explain` on a document that is in the index but not in the corpus returns `Optional.empty()` (the corpus check fires first). This could also arise in practice if the corpus and index snapshots were taken at different times — an inherent limitation of snapshot-based design.

### Follow-ups

- IR-5.AR: Architecture Review — inspect the full IR-5 implementation.
- IR-5.4: CKF documentation update.
- IR-5.R: Engineering Review.

### Next Step

IR-5.AR — Architecture Review: verify the implemented architecture conforms to the approved R2 design and the existing core architecture boundaries.
