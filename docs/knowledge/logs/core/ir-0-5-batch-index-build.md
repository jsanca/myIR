---
type: Engineering Log Entry
title: "Task 2 — IR-0.5: Batch Index Build Pipeline"
description: "Canonical engineering evidence for Task 2 — IR-0.5: Batch Index Build Pipeline."
tags: [core, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---
# Task 2 — IR-0.5: Batch Index Build Pipeline

### Summary
Introduced a batch-aware indexing path that preprocesses all documents, lexically indexes all of them, takes a single `CorpusSnapshot`, and then vectorizes all documents using that shared snapshot. This eliminates per-document `corpus.snapshot()` calls in the batch path and ensures all IDF values reflect the full batch before any vector is built.

### Scope
**Included:**
- `default indexAll(List<Document>)` on the `Indexer` interface (sequential fallback for all existing indexers)
- `VectorIndexer.indexWithSnapshot(Document, CorpusSnapshot)` for caller-supplied snapshots
- `BatchPipelineIndexer` private class orchestrating the four-phase batch flow
- `Indexers.batchLexicalAndVector(...)` public factory
- `BatchIndexerTest` with 7 tests covering batch lexical search, batch vector search, corpus completeness, IDF correctness, incremental fallback, empty batch, and default `indexAll` delegation

**Excluded:**
- Fields, per-field weighting, or any IR-1+ concerns
- Ranking formula changes
- Removal of existing incremental tests

### Deliverables
- `Indexer.default indexAll(List<Document>)` — backward-compatible default
- `VectorIndexer.indexWithSnapshot(Document, CorpusSnapshot)` — package-private batch hook
- `BatchPipelineIndexer` private inner class in `Indexers`
- `Indexers.batchLexicalAndVector(...)` factory
- `BatchIndexerTest` (7 tests)

### Changed Files
| File | Change |
|---|---|
| `indexer/Indexer.java` | Added default `indexAll(List<Document>)` |
| `indexer/Indexers.java` | Added `batchLexicalAndVector` factory, `BatchPipelineIndexer`, `VectorIndexer.indexWithSnapshot`, `CorpusSnapshot` import |
| `test/BatchIndexerTest.java` | Created (7 tests) |

### Validation
```
mvn test -pl codex-ir-core
Tests run: 154, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

### Tests
| Test | Purpose |
|---|---|
| `batchIndexAllShouldSupportLexicalSearch` | Lexical search works after `indexAll` |
| `batchIndexAllShouldSupportVectorSearch` | Vector search works after `indexAll` |
| `batchIndexAllShouldIndexAllDocumentsInTheCorpus` | All batch docs appear in corpus snapshot |
| `batchIndexAllShouldUseFullCorpusStatisticsForVectorWeighting` | Rare term ranks first — correct IDF from batch snapshot |
| `singleDocumentIndexShouldStillWorkAfterBatchIndexer` | Incremental `index()` still works via `batchLexicalAndVector` indexer |
| `emptyBatchShouldBeHandledGracefully` | Empty list produces no side effects |
| `defaultIndexAllOnLexicalIndexerShouldDelegateToIndex` | Default `indexAll` on non-batch indexers delegates sequentially |

### Engineering Notes
- `BatchPipelineIndexer` holds a `PipelineIndexer` internally for the single-doc `index()` path, avoiding any duplication of the incremental logic.
- `VectorIndexer.indexWithSnapshot` is package-private (no interface, no visibility beyond `Indexers`). It is only callable from `BatchPipelineIndexer`, which is a sibling private class. This keeps the batch contract internal and hidden from public callers.
- The `default indexAll` on `Indexer` ensures all existing indexers — `lexical`, `vector`, `lexicalAndVector` — gain a working (if unoptimized) `indexAll` with zero code changes.
- `CorpusSnapshot` is taken after all lexical indexing completes, so `statistics().documentCount()` reflects every document in the batch when IDF is computed.

### Decisions
- Named the factory `batchLexicalAndVector` (not `IndexBuildPipeline` or `IndexBuildSession`) to stay consistent with the existing `lexicalAndVector` naming convention in `Indexers`.
- `BatchPipelineIndexer` is a private inner class rather than a top-level class to keep it hidden behind the factory and consistent with `PipelineIndexer`, `LexicalIndexer`, `VectorIndexer`.
- The batch path does not call `invertedIndex.snapshot()` — it is not needed during vectorization. Only the corpus snapshot is required by `DocumentWeighter.weigh`.

### Tradeoffs
- `BatchPipelineIndexer.index()` delegates to an internal `PipelineIndexer` for the incremental path. This means the `singleDocIndexer` uses `VectorIndexer.index()` which calls `this.corpus.snapshot()` per document — identical to the existing behavior. The per-document snapshot cost is only eliminated in the `indexAll` path, which is the stated goal.
- An alternative was to have `BatchPipelineIndexer` also implement `PipelineDocumentResolver` and integrate into the existing `PipelineIndexer` loop. Rejected: the stage-loop abstraction is not batch-aware, and retrofitting it would add complexity without reducing code size.

### Risks
- **Single-snapshot IDF accuracy:** The batch snapshot is taken after all lexical postings are in. Documents added to the same batch all see each other in the IDF denominator. Documents added via subsequent `indexAll` calls or `index()` calls will use a later (larger) snapshot. This is the intended semantic; it's not a bug, but callers should be aware that IDF is batch-scoped, not corpus-global.
- **No thread safety in `BatchPipelineIndexer.indexAll`:** Sequential phases assume no concurrent writes during the batch. This is consistent with the current single-threaded indexing contract.

### Known Limitations
- `indexAll` on the default `Indexer` falls back to sequential single-document indexing — it does not get the per-document snapshot optimization. Only `batchLexicalAndVector` overrides with the optimized path.
- Field-based documents are supported (they go through `DocumentPreprocessor`) but the batch path has no per-field boost — that is deferred to IR-4.

### Follow-ups
- IR-1: `PreprocessedDocument` token artifact — eliminate repeated tokenize/join cycles
- Extend `batchLexicalAndVector` with a parallel preprocessing option using virtual threads once field-aware features stabilize

### Next Step
IR-1: Introduce `PreprocessedDocument` carrying `List<String> tokens` to eliminate the repeated tokenize→join→split cycle across the pipeline (now implemented — see Task 3 below).

---
