# myIR Roadmap — Field-Aware Retrieval Foundation

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

# IR-2 — Field Provenance

## Goal

Preserve per-field tokens without changing ranking yet.

## Deliverables

* `fieldTokens: Map<String, List<String>>`
* Blank fields ignored
* rawContent fallback intact
* Field strings remain allowed

## Validation

* title/body produce separate tokens
* Field boundaries are not lost in the artifact
* Whole-document mode remains unchanged

---

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

Apply simple per-field boosts.

## Deliverables

* `FieldWeights`
* Ranking with per-field weights
* Neutral defaults
* Configurable title boost

## Validation

* A title match can outscore a body-only match
* Without weights, ranking is identical to before
* Minimal explanation of the applied boost

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
