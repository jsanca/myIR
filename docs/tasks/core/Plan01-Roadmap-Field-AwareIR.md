# myIR Roadmap — Field-Aware Retrieval Foundation

> **Historical delivery plan — reconciled 2026-09-04.** IR-0 through IR-4
> below are complete. This plan is retained as execution history; use the
> [current roadmap](../../roadmap/ROADMAP.md) for present sequence and the
> [candidate map](../../knowledge/research/candidate-capabilities.md) for
> non-committed directions. IR-5 and IR-6 remain unselected candidates.

## Goal

Prepare myIR for field-aware indexing/ranking without breaking the current whole-document aggregation model.

## Guiding principles

- `Document` remains an immutable input.
- Analysis produces artifacts: `PreprocessedDocument`, `IndexSnapshot`, etc.
- Core has no knowledge of HTML.
- Fields remain flexible for now.
- Clean boundaries first; add fields second.
- Each slice must leave tests green.

---

# IR-0 — Read/Write Boundary Cleanup

## Goal

Separate index/corpus writes from reads.

## Concept

```text
IndexWriter / CorpusWriter
        ↓
commit / publish
        ↓
IndexSnapshot / CorpusSnapshot
        ↓
Searcher / Ranker
```

## Deliverables

* `IndexWriter` or `CorpusWriter`
* `IndexSnapshot`
* `CorpusSnapshot` if applicable
* Searcher reads a stable snapshot
* Rankers do not consult the mutable corpus directly

## Validation

* Index documents
* Publish snapshot
* Search against snapshot
* Document replacement updates statistics correctly
* Ranking uses stats from the snapshot

-----------

# IR-0.5 — Batch Index Build Pipeline

## Goal

Replace per-document vector snapshotting with a batch-oriented build flow.

## Desired workflow

```text
documents
  ↓
preprocess all
  ↓
corpus.add(all)
  ↓
invertedIndex.add(all)
  ↓
CorpusSnapshot + IndexSnapshot
  ↓
vectorize all documents using the same snapshots
  ↓
publish searchable snapshots
````

## Required changes

* Introduce `IndexBuildPipeline` or `IndexBuildSession`.
* Add batch method: `indexAll(List<Document>)`.
* Preprocess each document once.
* Add all preprocessed docs to corpus.
* Add all lexical postings.
* Create `CorpusSnapshot` once.
* Vectorize all docs using that snapshot.
* Remove `corpus.snapshot()` per document from the batch path.

## Constraints

* Keep current single-document `Indexer` API for compatibility.
* Do not implement fields yet.
* Do not change ranking formulas.
* Do not remove existing tests.

## Validation

* Vector weighter receives the same `CorpusSnapshot` for all documents in one batch.
* Lexical search still works.
* Vector search still works.
* Existing incremental flow still works.
* No per-document snapshot call in batch path.

-----------

---

# IR-1 — PreprocessedDocument Token Artifact

## Goal

Eliminate the repeated cycle:

```text
join → split → join → split
```

## Deliverables

* `PreprocessedDocument`
* Normalized tokens as `List<String>`
* `normalizedContent` retained for compatibility
* Metadata term frequencies derived from tokens, not re-tokenized

## Validation

* Current behavior preserved
* Documents without fields use rawContent
* Documents with fields aggregate non-blank values
* Tokens and normalizedContent are consistent

---
----------
# IR-2 — Field Provenance Artifact

## Goal
Preserve per-field token provenance without changing ranking or postings yet.

## Design
Do not overload `Document` or keep growing `PreprocessedDocument`.

Introduce a new pipeline artifact:


FieldAnalyzedDocument

containing:

PreprocessedDocument base
field token sequences, e.g. List<FieldTokenSequence>

Each FieldTokenSequence should carry:

field name
ordered normalized tokens
        Constraints
DocumentMetadata.termFrequencies remains whole-document only.
No ranking changes.
No posting changes yet.
No field boosts yet.
Whole-document search behavior must remain identical.
Validation
title/body field tokens are preserved separately.
blank fields are ignored.
rawContent fallback still works.
whole-document tokens remain unchanged.
lexical and vector search still pass existing tests.


# IR-3 — Field-Aware Postings

## Goal

Index tokens with field provenance.

## Deliverables

* `Posting.field()`
* `InvertedIndex.add(term, docId, position, field)`
* Existing overload continues to work with `field=null`
* `LexicalIndexer` uses `fieldTokens` when available

## Validation

* Title postings have `field="title"`
* Body postings have `field="body"`
* Old add produces `field=null`
* Search without boosts preserves compatibility

---
# IR-4 — Field-Aware Ranking / Boosting

## Goal

Use `Posting.fieldFrequencies()` to apply configurable field boosts during ranking, without changing postings again.

## Required design

Introduce:

```java
RankingContext
FieldWeights
````

Suggested shape:

```java
record FieldWeights(Map<String, Double> weights) {
    static FieldWeights neutral()
    double weightFor(String fieldName)
}

record RankingContext(FieldWeights fieldWeights) {
    static RankingContext neutral()
}
```

## Required behavior

* Default ranking remains unchanged.
* If no field weights are provided, scores must match current scores.
* If a term appears in boosted fields, score increases proportionally.
* Raw-content documents with empty `fieldFrequencies` must behave exactly as before.
* Do not add any new fields to `Posting`.

## Integration

* Add ranker overload/default method:

```java
score(String term, Posting posting, RankingContext context)
```

* Existing `score(term, posting)` delegates to neutral context or remains equivalent.
* `SimpleSearcher` can optionally accept `RankingContext`.
* Existing search factories use neutral context by default.
* Add factory overloads for field-aware ranking/search.

## Constraints

Do not implement BM25F yet.

Do not add:

* field positions
* field statistics
* score explanations
* query syntax
* changes to vector search

## Validation

Add tests:

* neutral context gives identical scores to old ranking
* title boost can make title match rank above body-only match
* unknown field uses weight 1.0
* empty fieldFrequencies behaves like whole-document ranking
* TF-IDF field boost works
* BM25 field boost works, if BM25 ranker shares the same path
* existing tests remain green

## Documentation

* Add CKF engineering log for IR-4
* Update docs/knowledge/index.md
* Update package-info.java if ranking package semantics change



---

# IR-5 — Score Explanation

## Goal

Explain why a document won.

## Deliverables

* `ScoreExplanation`
* Per-term contribution
* Per-field contribution
* Applied boost
* Final score

## Validation

* Explanation sums to the final score
* Shows field/title/body breakdown
* Works with BM25 and TF-IDF

---

# IR-6 — Evaluation Harness

## Goal

Prevent imaginary improvements.

## Deliverables

* Document fixtures
* Expected queries
* Expected top-k results
* Simple metrics: precision@k / hit@k

## Validation

* Current baseline runs
* Field boost improves expected cases
* Future changes are validated against the harness

---

# Future

```text
IR-7  N-grams
IR-8  Dense vectors
IR-9  Hybrid retrieval
IR-10 Document centroids
IR-11 Site centroids
IR-12 Summaries
IR-13 Syntax trees
```
