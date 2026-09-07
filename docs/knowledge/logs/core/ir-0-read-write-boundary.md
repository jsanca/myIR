---
type: Engineering Log Entry
title: "Task 1 — IR-0: Read/Write Boundary Cleanup"
description: "Canonical engineering evidence for Task 1 — IR-0: Read/Write Boundary Cleanup."
tags: [core, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---
# Task 1 — IR-0: Read/Write Boundary Cleanup

### Summary
Introduced `CorpusSnapshot` and `IndexSnapshot` as immutable, point-in-time views of the live mutable `Corpus` and `InvertedIndex`. All read-path components — `Ranker`, `Searcher`, and `DocumentWeighter` — now consume snapshots rather than live mutable structures. This enforces a clean write→commit→read flow and prevents searchers from observing in-progress writes.

### Scope
**Included:**
- New `CorpusSnapshot` interface and `InMemoryCorpusSnapshot` implementation inside `Corpora`
- New `IndexSnapshot` interface and `InMemoryIndexSnapshot` implementation inside `InvertedIndexes`
- `snapshot()` factory methods on `Corpus` and `InvertedIndex`
- Updated `DocumentWeighter.weigh(CorpusSnapshot, Document)` signature
- Updated `Rankers.tfIdf(CorpusSnapshot, IndexSnapshot)` and `Rankers.bm25(CorpusSnapshot, IndexSnapshot)` factory signatures
- Updated `Searchers.lexical(IndexSnapshot, CorpusSnapshot, ...)` and `Searchers.vector(..., CorpusSnapshot, ...)`
- Updated `SimpleSearcher` and `VectorSearcher` fields
- Updated `VectorIndexer.index()` to call `corpus.snapshot()` per document at index time
- Updated all affected tests (7 test files + 1 new test file)

**Excluded:**
- Persistent storage, serialization, or off-heap snapshots
- Lazy/copy-on-write snapshot strategies
- Per-field indexing (deferred to IR-1+)

### Deliverables
- `codex-ir-core/src/main/java/codex/ir/corpus/CorpusSnapshot.java` (new interface)
- `codex-ir-core/src/main/java/codex/ir/indexer/IndexSnapshot.java` (new interface)
- `InMemoryCorpusSnapshot` private inner class in `Corpora.java`
- `InMemoryIndexSnapshot` private inner class in `InvertedIndexes.java`
- `SnapshotSearchTest.java` (4 new behavioural tests)

### Changed Files
| File | Change |
|---|---|
| `corpus/CorpusSnapshot.java` | Created |
| `indexer/IndexSnapshot.java` | Created |
| `corpus/Corpus.java` | Added `snapshot()` method |
| `corpus/Corpora.java` | Added `snapshot()` impl + `InMemoryCorpusSnapshot` inner class |
| `indexer/InvertedIndex.java` | Added `snapshot()` method |
| `indexer/InvertedIndexes.java` | Added `snapshot()` impl + `InMemoryIndexSnapshot` inner class |
| `weight/DocumentWeighter.java` | Changed `weigh(Corpus, Document)` → `weigh(CorpusSnapshot, Document)` |
| `weight/Weighters.java` | Updated both weighter impls to `CorpusSnapshot` |
| `ranking/Rankers.java` | `tfIdf`/`bm25` factories now take snapshots |
| `search/SimpleSearcher.java` | Fields changed to `IndexSnapshot`/`CorpusSnapshot` |
| `search/VectorSearcher.java` | Field changed to `CorpusSnapshot` |
| `search/Searchers.java` | Factory signatures updated |
| `indexer/Indexers.java` | `VectorIndexer.index()` calls `corpus.snapshot()` internally |
| `app/Main.java` | Updated all demo methods to snapshot after indexing |
| `test/RankersTest.java` | Ranker creation moved after indexing; uses snapshots |
| `test/SearchersTest.java` | Searcher creation uses snapshots |
| `test/VectorSearcherTest.java` | Snapshots in all tests |
| `test/DocumentWeighterTest.java` | `weigh(corpus.snapshot(), ...)` throughout |
| `test/WeightersTest.java` | `weigh(corpus.snapshot(), ...)` throughout |
| `test/FieldAwareIndexingTest.java` | Ranker/searcher creation after indexing; uses snapshots |
| `test/FieldAwareVectorIndexingTest.java` | Same; searcher moved after indexing in test 4 |
| `test/SnapshotSearchTest.java` | Created (4 tests) |

### Validation
```
mvn test -pl codex-ir-core
Tests run: 147, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

### Tests
| Test | Status |
|---|---|
| `RankersTest` (8 tests) | Updated — rankers now created after indexing with snapshots |
| `SearchersTest` (3 tests) | Updated — searchers use snapshots |
| `VectorSearcherTest` (4 tests) | Updated — snapshots throughout |
| `DocumentWeighterTest` (5 tests) | Updated — `weigh(corpus.snapshot(), ...)` |
| `WeightersTest` (4 tests) | Updated — `weigh(corpus.snapshot(), ...)` |
| `FieldAwareIndexingTest` (6 tests) | Updated — ranker/searcher after indexing |
| `FieldAwareVectorIndexingTest` (7 tests) | Updated — searcher after indexing, snapshots |
| `SnapshotSearchTest` (4 tests) | **New** — validates IR-0 isolation semantics |

### Engineering Notes
- `InMemoryCorpusSnapshot` captures `Map.copyOf(documentMap)` and the current `CorpusStatistics` under the `statisticsMutationLock`, ensuring document list and statistics are atomically consistent.
- `InMemoryIndexSnapshot` uses the existing `asMap()` method, which already performs a deep copy of the postings map.
- `VectorIndexer` holds a live `Corpus` reference (write side) and calls `corpus.snapshot()` internally before delegating to `DocumentWeighter.weigh()`. `TfIdfDocumentWeighter` still holds a live `InvertedIndex` reference for DF lookups — this is intentional because vector weighting happens at index time and must see current DF values as each document is added.
- Snapshot isolation means a searcher built on snapshot N will never see documents indexed after snapshot N, regardless of subsequent writes to the live index.

### Decisions
- `CorpusSnapshot` and `IndexSnapshot` are interfaces (not final classes) so future implementations can add lazy/copy-on-write semantics without changing callers.
- `snapshot()` methods live on the mutable interfaces (`Corpus`, `InvertedIndex`) rather than on a separate factory, keeping the API minimal and obvious.
- `VectorIndexer` calls `corpus.snapshot()` per document rather than once at construction time, so each document's TF-IDF weights reflect the corpus state at the moment of its indexing (consistent with incremental indexing semantics).

### Tradeoffs
- Calling `corpus.snapshot()` per indexed document in `VectorIndexer` adds a `Map.copyOf` allocation per document. The alternative — storing a snapshot at `Indexer` construction time — would produce stale IDF values for later-indexed documents. Correctness wins.
- `InMemoryIndexSnapshot` deep-copies the postings. This is O(n) in total postings size. Acceptable for the current in-memory-only design (ADR-003).

### Risks
- **Stale statistics in snapshots:** If callers take a snapshot before indexing is complete, the snapshot's `CorpusStatistics` will be stale. All tests were restructured to snapshot after indexing to reflect the correct usage pattern.
- **No concurrent snapshot safety for multi-threaded indexing:** `InMemoryCorpusSnapshot` synchronizes on `statisticsMutationLock`, but `InMemoryIndexSnapshot` uses `asMap()` which iterates the postings map without a global lock. Multi-threaded indexing during snapshot is not yet a supported use case (ADR-003).

### Known Limitations
- `IndexSnapshot.asMap()` exposes a `Map<String, List<Posting>>` — the list values are defensive copies but the map itself is a new `HashMap`. Callers should not retain references across indexing cycles.
- `CorpusSnapshot` does not expose the same `statistics()` refresh semantics as `Corpus` — it always returns the statistics captured at snapshot time.

### Follow-ups
- IR-1: Field-weight boosting (per-field scoring multipliers in the ranker)
- IR-2: Incremental snapshot refresh (delta-based updates rather than full copy)
- Add `SnapshotSearchTest` coverage for vector searcher isolation once `lexicalAndVector` snapshot semantics are stabilized

### Next Step
IR-0.5: Batch Index Build Pipeline (now implemented — see Task 2 below).

---
