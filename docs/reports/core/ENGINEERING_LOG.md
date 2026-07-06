# Core IR Engine — Engineering Log

---

## Task 1 — IR-0: Read/Write Boundary Cleanup

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

## Task 2 — IR-0.5: Batch Index Build Pipeline

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

## Task 3 — IR-1: PreprocessedDocument Token Artifact

### Summary
Introduced `PreprocessedDocument` as an analysis artifact that carries the enriched `Document` alongside the ordered `List<String> tokens` produced during preprocessing. The token list is threaded through the internal indexing pipeline, eliminating two redundant tokenization cycles that previously occurred after preprocessing: one in `LexicalIndexer` (which re-split `normalizedContent`) and one in `Weighters` (which re-tokenized `normalizedContent` to compute term frequencies).

### Scope
**Included:**
- New `PreprocessedDocument` record in `codex.ir.indexer`
- `DocumentPreprocessor.preprocess()` now returns `PreprocessedDocument`
- `PipelineDocumentResolver` interface removed; replaced by `PreprocessedDocumentConsumer`
- `PipelineIndexer.index()` dispatches `PreprocessedDocument` to consumers and `Document` to legacy stages
- `LexicalIndexer` implements `PreprocessedDocumentConsumer` — uses token list directly for positional posting insertion; retains `index(Document)` fallback for standalone callers
- `VectorIndexer` drops `PipelineDocumentResolver`; its `resolveDocument` no-op removed
- `Weighters.TermFrequencyDocumentWeighter` and `TfIdfDocumentWeighter` use `metadata().termFrequencies()` when available, falling back to re-tokenization only for query documents
- `BatchPipelineIndexer.indexAll()` uses `List<PreprocessedDocument>` internally
- `package-info.java` updated
- `PreprocessedDocumentTest` with 11 tests

**Excluded:**
- Per-field token lists (deferred to IR-2)
- Changes to public `DocumentWeighter` interface
- Changes to `VectorSearcher.preprocessQuery()` — query documents have no cached term frequencies; weighter fallback path handles them correctly

### Deliverables
- `codex/ir/indexer/PreprocessedDocument.java` (new public record)
- `PreprocessedDocumentTest.java` (11 tests)

### Changed Files
| File | Change |
|---|---|
| `indexer/PreprocessedDocument.java` | Created |
| `indexer/Indexers.java` | `DocumentPreprocessor` → `PreprocessedDocument`; replaced `PipelineDocumentResolver` with `PreprocessedDocumentConsumer`; `LexicalIndexer` implements consumer; `VectorIndexer` drops resolver; `BatchPipelineIndexer` uses `List<PreprocessedDocument>` |
| `weight/Weighters.java` | Both weighters check `metadata().termFrequencies()` before tokenizing |
| `indexer/package-info.java` | Documents `PreprocessedDocument` |
| `test/PreprocessedDocumentTest.java` | Created (11 tests) |

### Validation
```
mvn test -pl codex-ir-core
Tests run: 165, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

### Tests
| Test | Purpose |
|---|---|
| `tokensShouldBeConsistentWithNormalizedContent` | Stored normalizedContent matches token sequence |
| `metadataTermFrequenciesShouldReflectTokenCounts` | Repeated tokens produce correct TF in metadata |
| `documentLengthShouldEqualTokenCount` | `metadata.length()` equals token count |
| `lexicalIndexShouldContainCorrectPositionalPostings` | Positions are correct (0-based from token list) |
| `tfIdfWeighterShouldUseMetadataTermFrequenciesWhenAvailable` | TF-IDF uses cached frequencies, not re-tokenization |
| `termFrequencyWeighterShouldUseMetadataTermFrequenciesWhenAvailable` | TF weighter uses cached frequencies |
| `lexicalSearchShouldFindDocumentsAfterIR1Changes` | End-to-end lexical search still works |
| `vectorSearchShouldFindDocumentsAfterIR1Changes` | End-to-end vector search still works |
| `fieldsAggregationShouldWorkWithTokenArtifact` | Field content tokens are searchable |
| `documentsWithoutFieldsShouldUseRawContentForTokens` | rawContent fallback still works |
| `stopWordsShouldBeExcludedFromTokens` | Stop words excluded from metadata termFrequencies |

### Engineering Notes
- `PreprocessedDocument` is a pipeline-internal artifact: it is produced by `DocumentPreprocessor`, flows through `PipelineIndexer`, and is consumed by `LexicalIndexer`. It is not stored in the corpus or the index. The corpus stores the enriched `Document` (with normalizedContent and metadata), not the `PreprocessedDocument`.
- `LexicalIndexer` now has two `index` overloads: `index(PreprocessedDocument)` (preferred, no split) and `index(Document)` (fallback, splits normalizedContent). The pipeline always calls the former; standalone callers hit the latter. Both paths produce identical postings.
- `Weighters` retain the tokenizer-based fallback path unchanged. `VectorSearcher.preprocessQuery()` builds a `Document` with no metadata termFrequencies, so it naturally hits the fallback and re-tokenizes the query string — this is correct behavior.
- `PipelineDocumentResolver` is gone. It was used only by `VectorIndexer` to return the document unchanged (a no-op). `PreprocessedDocumentConsumer` is the clean replacement; stages that don't implement it simply receive `preprocessed.document()`.

### Decisions
- `PreprocessedDocument` is public (not package-private) to enable IR-2 field-token work to build on it without copying.
- The `id()` convenience delegate on `PreprocessedDocument` keeps logging calls readable without `preprocessed.document().id()`.
- `Weighters` check the cached frequencies before tokenizing rather than requiring callers to pass the token list explicitly — this avoids changing the public `DocumentWeighter` interface.

### Tradeoffs
- The `LexicalIndexer.index(Document)` fallback keeps backward compatibility for any caller that bypasses `PipelineIndexer`. The cost is two code paths for what is effectively the same logic. Removing the fallback would be cleaner but would break external indexers that call `LexicalIndexer` directly (which shouldn't happen since it's private, but `BatchPipelineIndexer.indexAll` does call it via the `PreprocessedDocumentConsumer` path).
- `isAlreadyPreprocessed` guard in `DocumentPreprocessor` now splits `normalizedContent` on whitespace to reconstruct a token list. This is a one-time cost for pre-preprocessed documents and only occurs in the "already preprocessed" branch, which is rare.

### Risks
- **Weighter cache invalidation:** If a caller mutates a `Document`'s `metadata().termFrequencies()` after preprocessing (not possible since the map is immutable via `Map.copyOf`), the weighter would produce stale results. The immutability guarantee eliminates this risk.
- **Weighter fallback correctness:** The fallback in `TfIdfDocumentWeighter.resolveTermFrequencies` uses `tokenizer.tokenize()` only — it does not normalize. For the query path in `VectorSearcher`, `preprocessQuery()` already normalizes tokens before building the `Document`, so `normalizedContent` contains only normalized terms and the tokenizer split is sufficient.

### Known Limitations
- `PreprocessedDocument.tokens()` is the whole-document aggregate of normalized terms. Per-field token lists are not yet preserved — that is IR-2.
- The `isAlreadyPreprocessed` path reconstructs tokens from `normalizedContent.split("\\s+")` rather than the original token list. This is correct but loses the exact tokenizer semantics for edge cases with multiple consecutive spaces (which `join(" ", tokens)` would not produce anyway).

### Follow-ups
- IR-2: Add `fieldTokens: Map<String, List<String>>` to `PreprocessedDocument` for field provenance
- IR-3: Field-aware postings using field tokens from `PreprocessedDocument`

### Next Step
IR-2: Extend `PreprocessedDocument` with per-field token lists so that field provenance is not lost at the preprocessing boundary.

---

## Task 4 — IR-2: Field Provenance Artifact

### Summary
Introduced `FieldTokenSequence` and `FieldAnalyzedDocument` as analysis artifacts that carry per-field token sequences alongside the whole-document `PreprocessedDocument`. `DocumentPreprocessor.preprocess()` now returns `FieldAnalyzedDocument` instead of `PreprocessedDocument`. The whole-document model (normalizedContent, termFrequencies, document length, postings) is completely unchanged. Per-field tokens are preserved purely for downstream field-aware features; no ranking or search behavior was altered.

### Scope
**Included:**
- New `FieldTokenSequence(String fieldName, List<String> tokens)` public record
- New `FieldAnalyzedDocument(PreprocessedDocument base, List<FieldTokenSequence> fieldSequences)` public record with `hasFieldSequences()`, `id()`, `document()`, `tokens()` delegates
- `DocumentPreprocessor.preprocess()` return type promoted from `PreprocessedDocument` to `FieldAnalyzedDocument`
- `analyzeFields(Document)` helper: iterates non-blank field entries, normalizes each independently, produces `List<FieldTokenSequence>`
- `normalizeTokens(String text)` helper extracted from preprocessing loop
- `FieldAnalyzedDocumentConsumer` private pipeline interface (highest priority in dispatch)
- `PipelineIndexer.index()` three-level dispatch: `FieldAnalyzedDocumentConsumer` → `PreprocessedDocumentConsumer` → `Document`
- `BatchPipelineIndexer.indexAll()` updated to use `List<FieldAnalyzedDocument>`
- `package-info.java` updated
- `FieldAnalyzedDocumentTest` with 12 tests

**Excluded:**
- Field-aware postings (postings still carry no field tag — deferred to IR-3)
- Field-weight boosting in rankers — deferred to IR-4
- Changes to `InvertedIndex`, `Corpus`, or `DocumentMetadata` — whole-document model unchanged

### Deliverables
- `codex/ir/indexer/FieldTokenSequence.java` (new public record)
- `codex/ir/indexer/FieldAnalyzedDocument.java` (new public record)
- `FieldAnalyzedDocumentTest.java` (12 tests)

### Changed Files
| File | Change |
|---|---|
| `indexer/FieldTokenSequence.java` | Created |
| `indexer/FieldAnalyzedDocument.java` | Created |
| `indexer/Indexers.java` | `DocumentPreprocessor.preprocess()` returns `FieldAnalyzedDocument`; `analyzeFields()` + `normalizeTokens()` helpers added; `FieldAnalyzedDocumentConsumer` interface added; `PipelineIndexer` three-level dispatch; `BatchPipelineIndexer.indexAll()` uses `List<FieldAnalyzedDocument>` |
| `indexer/package-info.java` | Documents `FieldAnalyzedDocument` and `FieldTokenSequence` |
| `test/FieldAnalyzedDocumentTest.java` | Created (12 tests) |

### Validation
```
mvn test -pl codex-ir-core
Tests run: 177, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

### Tests
| Test | Purpose |
|---|---|
| `fieldDocumentShouldProduceOneSequencePerNonBlankField` | Two non-blank fields → both produce tokens in whole-document content |
| `blankFieldShouldProduceNoFieldSequence` | Blank title → no blank-title contribution to termFrequencies |
| `rawContentDocumentShouldProduceNoFieldSequences` | rawContent-only document still indexes normally |
| `allBlankFieldsShouldFallBackToRawContent` | All-blank fields → rawContent fallback produces correct normalizedContent |
| `wholeDocumentTermFrequenciesMustBeUnchangedByFieldAnalysis` | "java" in both fields → whole-document TF=2; field analysis does not alter this |
| `documentLengthMustReflectWholeDocumentTokenCount` | Four tokens across two fields → `metadata.length()=4` |
| `stopWordsExcludedFromWholeDocumentTokensShouldNotAppearInFieldFrequencies` | "the" / "a" excluded from whole-document TF |
| `lexicalSearchBehaviorMustBeUnchangedAfterIR2` | Field-indexed docs found by term; relative ranking unchanged |
| `rawContentDocumentShouldBeSearchableAfterIR2` | rawContent docs still searchable |
| `mixedCorpusWithFieldAndRawContentDocumentsShouldSearchCorrectlyAfterIR2` | Mixed corpus: both field-doc and raw-doc findable by distinct terms |
| `fieldAnalyzedDocumentShouldReportHasFieldSequencesCorrectly` | `hasFieldSequences()` true/false; `fieldSequences()` size and names correct |
| `fieldTokenSequenceTokensShouldBeImmutable` | `FieldTokenSequence.tokens()` returns correct immutable list |

### Engineering Notes
- `FieldAnalyzedDocument` wraps `PreprocessedDocument` as `base` rather than extending it. This keeps both records flat and avoids inheritance. Delegates (`id()`, `document()`, `tokens()`) are one-liners that forward to `base`.
- `analyzeFields()` and `normalizeTokens()` are private static helpers inside `DocumentPreprocessor` (a private inner class of `Indexers`). They are not visible outside the factory, keeping the public API unchanged.
- The `rawContent` fallback path in `DocumentPreprocessor.preprocess()` produces an empty `fieldSequences` list. `analyzeFields()` is only called when at least one field is non-blank; when all fields are blank, `resolveContent()` has already fallen back to `rawContent` and `analyzeFields()` returns `List.of()`.
- `FieldAnalyzedDocumentConsumer` is currently implemented by no stage — it is the reserved extension point for IR-3 field-aware posting insertion. No pipeline changes will be needed in IR-3 to wire it in.
- `PipelineIndexer` dispatch order: `FieldAnalyzedDocumentConsumer` checked first (instanceof), then `PreprocessedDocumentConsumer` (LexicalIndexer), then raw `Document` (VectorIndexer). The three-level hierarchy adds zero overhead for the common case where only `LexicalIndexer` and `VectorIndexer` are present.
- `BatchPipelineIndexer.indexAll()` was updated: `List<PreprocessedDocument>` → `List<FieldAnalyzedDocument>`; `lexicalIndexer.index(fa.base())` passes the `PreprocessedDocument` to the `PreprocessedDocumentConsumer` path; vectorization still uses `fa.document()` (unchanged).

### Decisions
- `FieldAnalyzedDocument` is a record wrapping `PreprocessedDocument`, not a modified `PreprocessedDocument` with an added field. This avoids touching the IR-1 artifact and keeps each slice's type independent.
- Per-field tokens use the same normalizer as the whole-document path so that field tokens are consistent with whole-document postings — no double normalization occurs because each field value is normalized once.
- `FieldTokenSequence` is a public record (not package-private) to allow future IR-3+ consumers outside the pipeline to inspect field sequences without reflection.

### Tradeoffs
- Field sequences are computed eagerly during `preprocess()` even when no downstream stage consumes them (current state — no `FieldAnalyzedDocumentConsumer` exists yet). The cost is one `tokenize()` + `normalize()` call per non-blank field. This is acceptable; lazy evaluation would require a supplier and add complexity not yet justified.
- An alternative was to extend `PreprocessedDocument` directly (add `Map<String, List<String>> fieldTokens`). Rejected: it would reopen the IR-1 artifact, add nullability concerns, and mix two conceptually distinct slices in one type.

### Risks
- **Field tokenizer consistency:** `analyzeFields()` uses the same `tokenizer` and `normalizer` instances as the whole-document path. If a future caller constructs a pipeline with field-specific normalizers, this assumption breaks. There is no provision for per-field normalization at this layer — that would require IR-5+ work.
- **Empty fieldSequences for rawContent docs:** Callers checking `hasFieldSequences()` cannot distinguish "rawContent document" from "all-blank-fields document." Both return `false`. If this distinction matters later, a separate flag or an enum `ContentSource` would be needed.

### Known Limitations
- Field sequences are analysis artifacts only — they are not stored in the corpus or the inverted index. Postings have no field tag yet (deferred to IR-3).
- `FieldAnalyzedDocument` is not included in the JPMS `module-info.java` exports review — it is already in the `codex.ir.indexer` package which is exported; no change needed.

### Follow-ups
- IR-3: Field-aware posting insertion — add a `fieldName` tag to `Posting` and have `LexicalIndexer` implement `FieldAnalyzedDocumentConsumer` to insert per-field postings
- IR-4: Field-weight boosting — use per-field postings in `Rankers.bm25`/`tfIdf` with configurable field boost multipliers
- Consider whether `analyzeFields()` should skip fields whose names appear in a configured "excluded fields" set (e.g., internal metadata fields)

### Next Step
IR-3: Field-aware posting insertion — tag postings with their source field so the ranker can apply field-specific boost multipliers.
