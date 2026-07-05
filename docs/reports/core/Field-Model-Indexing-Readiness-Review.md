# myIR Document/Field Model Review — Field-Aware Indexing Readiness

## Verdict

The current model is correctly scoped for whole-document aggregation (per ADR-004). Field-aware indexing is achievable but requires coordinated changes across at least five components. The map-based fields are sufficient for Phase IR-1. Do not introduce strong types yet — the cost outweighs the benefit until the pipeline actually uses field provenance.

---

## Current State

`Document.fields` is `Map<String, String>`, populated by `Mappers.webPage()` with two keys: `"title"` and `"body"`. `DocumentPreprocessor.resolveContent()` joins all non-blank field values with spaces, discards the keys, and sets the result as `normalizedContent`. Downstream components (`InvertedIndex`, `Ranker`, `DocumentWeighter`, `Vectorizer`) see only this flat string. Field boundaries are lost after preprocessing.

---

## Strengths

- **ADR-004 is accurate.** The code matches the documented contract exactly.
- **Backward compat is free.** Documents without fields fall through to `rawContent`.
- **Isolated integration point.** `DocumentPreprocessor` is the only place that touches field aggregation. No downstream component needs to change for whole-document mode.
- **`Document.Builder` is well-designed for the current model.** No changes needed there until fields become typed.
- **`Mappers.webPage()` correctly wires title/body fields and sets `rawContent`.** It preserves the original content as a fallback.
- **JsonLd/product extraction code is cleanly separated** in internal packages and doesn't leak into core.

---

## Warnings

### 1. `DocumentPreprocessor` re-tokenizes what it already has

`resolveContent()` joins field values with `Collectors.joining(" ")`, creating a single string. Then `preprocess()` parses that string back into tokens via `this.tokenizer.tokenize(content)`. When fields are present, we join text we already own → split → normalize → rejoin → split again (in `LexicalIndexer`). This is correct but wasteful.

### 2. `LexicalIndexer` re-splits `normalizedContent` by regex

`normalizedContent.split("\\s+")` runs after the preprocessor already had the tokenized terms. The preprocessor constructs `normalizedContent` by `String.join(" ", ...)`, which the lexical indexer then parses again. This works but means every document's tokens are serialized → deserialized through string form.

### 3. `Weighters` re-derive `termFrequencies` from `normalizedContent`

Both `TermFrequencyDocumentWeighter` and `TfIdfDocumentWeighter` tokenize `normalizedContent` to rebuild term-frequency maps. But `Document.metadata.termFrequencies` already contains these exact counts, computed by `DocumentPreprocessor`. The metadata is ignored by the weighter.

### 4. `Bm25Ranker` looks up corpus per document per term

`extractDocumentLength()` calls `corpus.get(posting.documentId())` for each `(term, posting)` pair. For a query with 3 matching terms across 100 documents, this is 300 `ConcurrentHashMap.get()` calls on the corpus. The document length could be stored on `Posting` or cached per search invocation.

### 5. `VectorSearcher` iterates all vectors

`vectorStore.iterateAll()` runs every query. No pruning, no top-k acceleration. Fine for small corpora. Will degrade linearly with corpus size.

---

## Blockers (pre-Phase IR-1)

Issues that would prevent or complicate field-aware indexing. Not all need to be fixed before Phase IR-1 starts, but all must be resolved before field provenance reaches the index.

### B1 — `Posting` has no field identifier

`Posting(docId, tf, positions)` stores only document-level information. Per-field term frequency, per-field positions, and field identity are absent. Any approach (Option B/C/D from ADR-005) requires adding field info to either `Posting` or `InvertedIndex`.

**Required for:** Phase IR-1 lexical indexing with field awareness.

### B2 — `InvertedIndex.add()` has no field parameter

`add(term, documentId, position)` accepts only a term key. The index is flat `term → List<Posting>`. Field tagging (e.g., `title:java` vs `body:java`) cannot be represented.

**Required for:** Phase IR-1 lexical indexing.

### B3 — `DocumentPreprocessor` discards field provenance

`resolveContent()` joins field values and discards field names. Even if the index were field-aware, the preprocessor cannot deliver the information it does not keep.

**Required for:** All field-aware approaches (A through E).

### B4 — `LexicalIndexer` consumes `normalizedContent` as a flat string

`normalizedContent.split("\\s+")` has no concept of field boundaries. Even if `Posting` and `InvertedIndex` supported field-aware add(), the indexer could not supply field-tagged tokens.

**Required for:** Phase IR-1 lexical indexing with field awareness.

### B5 — `Ranker.score(term, posting)` has no field context

Score is computed from `(term, posting)` only. There is no field parameter, no field weight map, no field-frequency vector. Field-weighted scoring (even simple boost) requires an extension point.

**Required for:** Phase IR-1 ranking with field awareness.

---

## Recommended Field Architecture

### Phase IR-1 Target

A minimally invasive change that enables field-distinct indexing without breaking existing whole-document mode:

| Component | Change |
|---|---|
| `DocumentPreprocessor` | Produce a `Map<String, List<String>> fieldTokens` alongside (or replacing) `normalizedContent`. Keep the aggregated string for backward compat. |
| `Document` | No change yet. Keep `Map<String, String> fields` as-is. |
| `Posting` | Add optional `field` attribute (nullable `String`). Default `null` = whole-document mode. |
| `InvertedIndex` | Add overload `add(term, documentId, position, field)`. Backward compat: existing callers pass `null`. |
| `LexicalIndexer` | If preprocessor emits `fieldTokens`, iterate per-field tokens and call `add(term, docId, pos, fieldName)`. Else fall back to current flat behavior. |
| `Ranker` | Add `score(term, posting, Map<String, Double> fieldWeights)` default method that delegates to `score(term, posting)`. |
| `Searcher` | Add `searchDetailed(String query, Map<String, Double> fieldWeights)` default method. `SimpleSearcher` passes weights to ranker. |

### Field Key Naming

**Keep strings for Phase IR-1.** `"title"`, `"body"`, `"url"`, `"headings"`, `"anchors"` as string keys in both `Document.fields` and `Posting.field`. An enum or sealed interface adds ceremony without benefit until field names need to be enumerated at compile time (e.g., for `UrlClassifier` integration or field-specific normalizers).

If the string approach becomes error-prone in practice, add a `FieldKey` record in `codex.ir`:

```java
public record FieldKey(String name) {
    // No predefined constants yet — web / app layers define them.
}
```

But this is premature for Phase IR-1. Strings are fine.

### Where Should Core Know Fields?

**Core should not define standard fields.** `Document` in `codex-ir-core` treats fields as opaque key-value pairs. The web layer (`Mappers.webPage()`, `PageMetadataExtractor`) defines which fields exist and populates them. If core ever needs field semantics (e.g., for BM25F), introduce a lightweight `FieldKey` or `FieldMetadata` in core — but only when the ranker actually uses it.

### Where Should Field Normalization Happen?

The `DocumentPreprocessor` (in `codex-ir-core/indexer/Indexers.java`) is the correct place. It already owns content resolution. For Phase IR-1:

- Run the same `tokenizer` and `normalizer` independently per field
- Store per-field normalized tokens as `Map<String, List<String>> fieldTokens` on the preprocessed document (or as a separate output)
- Keep the aggregated `normalizedContent` for backward compat with existing downstream consumers

Do NOT move normalization into individual mappers (web layer). Normalization logic (stop-word removal, stemming) belongs to `codex-ir-core` regardless of field awareness.

---

## Minimal Phase IR-1 Slice

These are the smallest set of changes that deliver field-distinct indexing:

1. **`DocumentPreprocessor`** — emit `Map<String, List<String>> fieldTokens`. Define a simple inner record or use the existing fields map keys.
2. **`Posting`** — add `String field()` with compact constructor defaulting `null` → `null`.
3. **`InvertedIndex`** — add `add(term, docId, position, field)`. InMemoryInvertedIndex stores field-aware postings. Keep `add(term, docId, position)` for backward compat.
4. **`LexicalIndexer`** — check if preprocessed doc has `fieldTokens`; if yes, index per field with field-aware `add()`. If no, use existing flat path.
5. **`SimpleSearcher`** — optionally accept field weights. For now, ignore weights (score as-is). The pipeline is field-tagged but not yet field-weighted.
6. **Tests** — see section below.

**Explicitly defer from Phase IR-1:**
- BM25F ranking
- Per-field `CorpusStatistics`
- Field-specific query syntax (`search(field, query)`)
- Vector search field awareness
- `FieldKey` enum/record
- `Document` record changes
- `DocumentWeighter` changes

---

## Performance Notes

| Issue | Severity | When to Fix |
|---|---|---|
| Preprocessor join → split → rejoin → re-split | Medium | Phase IR-1 (field tokens replace the string roundtrip naturally) |
| `Weighters` re-derive TF from normalizedContent | Low | After Phase IR-1, before scaling |
| `Bm25Ranker` calls corpus.get() per (term, posting) | Medium | Phase IR-1 (embed doc length in Posting or cache per query) |
| `VectorSearcher` full scan per query | Low | When corpus exceeds ~10K docs (not yet) |
| `InvertedIndex` HashMap has poor memory density | Low | When corpus exceeds ~100K docs (not yet) |
| Regex split in LexicalIndexer | Low | Preprocessor already has tokens — pass `List<String>` instead of joining. Fix in Phase IR-1. |

### Special attention: the join → split → join → split pipeline

Current flow when fields are present:

```
fields["title"] = "Java Programming"
fields["body"]  = "Java is a programming language"

resolveContent():
  → "Java Programming Java is a programming language"    (join, discard field names)

preprocess():
  → tokenize → ["java", "programming", "java", "is", "a", "programming", "language"]
  → normalize → ["java", "programming", "java", "programming", "language"]
  → join      → "java programming java programming language"   (normalizedContent)
  → metadata.termFrequencies = {java:2, programming:2, language:1}

LexicalIndexer:
  → split("\\s+") → ["java", "programming", "java", "programming", "language"]
  → add each to InvertedIndex

Weighters (TfIdf / TF):
  → split("\\s+") → tokenize again to derive term frequencies
```

Each document has its tokens serialized to a string and parsed back three times. For Phase IR-1, refactoring `DocumentPreprocessor` to emit field-tagged token lists eliminates the first re-split in the LexicalIndexer and provides field provenance. The Weighter still re-tokenizes (can fix later by reading `metadata.termFrequencies` directly).

---

## Tests to Add

For Phase IR-1:

| Test | What It Validates |
|---|---|
| `documentPreprocessorShouldPreserveFieldTokens` | `DocumentPreprocessor` emits `fieldTokens` map with correct per-field normalized tokens |
| `documentPreprocessorShouldFallBackToRawContentWhenNoFields` | Existing behavior preserved when fields are absent |
| `documentPreprocessorShouldAggregateFieldTokensOnlyForNonBlankValues` | Blank fields excluded from field-token output |
| `invertedIndexShouldStoreFieldAwarePostings` | `InvertedIndex.add(term, docId, pos, "title")` yields postings with `field()="title"` |
| `invertedIndexBackwardCompatFlatAddShouldStoreNullField` | Existing `add(term, docId, pos)` produces `field() == null` postings |
| `lexicalIndexerShouldIndexFieldTokensWhenPresent` | `LexicalIndexer` indexes per-field tokens with correct field value |
| `lexicalIndexerShouldFallBackToFlatContentWhenNoFieldTokens` | Existing whole-document indexing still works |
| `searcherShouldScoreSameWithFieldAwareFlatBackwardCompat` | Same corpus indexed with field-aware and flat paths produces identical scores with no field weights |
| `postingRecordShouldAcceptNullField` | `Posting("d1", 3, [0,1,2], null)` is valid |
| `postingRecordShouldPreserveFieldName` | `Posting("d1", 2, [0,1], "title").field()` returns `"title"` |

---

## Follow-ups

- **Phase IR-1** — Per-field postings in `InvertedIndex` + `LexicalIndexer` field-aware indexing (blockers B1–B4 above).
- **Prompt optimization/deferred work** — `Bm25Ranker` corpus lookup micro-optimization (cache doc lengths).
- **Prompt optimization/deferred work** — `Weighters` should read `metadata.termFrequencies` instead of re-tokenizing `normalizedContent`.
- **Prompt optimization/deferred work** — `LexicalIndexer` should consume `List<String>` from preprocessor instead of `String.split()`.
- **Beyond IR-1** — BM25F ranking (ADR-005 Option E).
- **Beyond IR-1** — Field-specific query syntax on `Searcher`.
- **Beyond IR-1** — Field-weighted vector search.
- **Beyond IR-1** — `FieldKey` record if string-based keys prove error-prone.
