---
type: Engineering Log Entry
title: "Task 3 — IR-1: PreprocessedDocument Token Artifact"
description: "Canonical engineering evidence for Task 3 — IR-1: PreprocessedDocument Token Artifact."
tags: [core, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---
# Task 3 — IR-1: PreprocessedDocument Token Artifact

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
