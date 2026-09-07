---
type: Engineering Log Entry
title: "Task 4 — IR-2: Field Provenance Artifact"
description: "Canonical engineering evidence for Task 4 — IR-2: Field Provenance Artifact."
tags: [core, engineering-log, migration]
timestamp: 2026-07-05T00:00:00Z
ckf_version: "0.1"
ckf_status: completed
ckf_scope: core
ckf_owner: project
---
# Task 4 — IR-2: Field Provenance Artifact

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
