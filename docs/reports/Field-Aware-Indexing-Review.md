# Review — Field-Aware Indexing Implementation

**Date:** 2026-05-06
**Review scope:** `DocumentPreprocessor.resolveContent()` logic and associated tests

## Verdict

**Sufficient as an intermediate migration step.** The implementation correctly aggregates searchable fields into the existing lexical pipeline with no changes to rankers, searchers, or vectorizers. All edge cases are handled.

## Implementation review

### `resolveContent()` logic (`Indexers.java`)

| Edge case | Expected behavior | Actual behavior | Covered by test? |
|-----------|-------------------|-----------------|------------------|
| No fields (empty map) | Fall back to `rawContent` | `fields.isEmpty()` → returns `rawContent()` | bodyOnly test |
| Null fields map | Not reachable (Document canonical constructor guarantees `Map.of()`) | Defensive null check exists, harmless | N/A |
| All field values blank | Fall back to `rawContent` | Filtered out, aggregated is blank → falls back | all-blank fallback test |
| Mixed blank/non-blank values | Use non-blank values only | `filter` removes blanks, non-blanks joined | mixed-blank test |
| Fields only, no rawContent | Use aggregated field values | Fields present → aggregated → used | fields-only test |
| rawContent only, no fields | Use rawContent directly | `fields.isEmpty()` → returns `rawContent` | bodyOnly test |

### Design observation

The `fields == null` check on line 293 is **dead code** per the Document canonical constructor (`fields = fields == null ? Map.of() : Map.copyOf(fields)`). It is harmless defensive code and can remain.

## Tests added during review

Two edge-case tests were added to `FieldAwareIndexingTest`:

| Test | Covers |
|------|--------|
| `documentWithMixedBlankAndNonBlankFieldsShouldUseNonBlankValues` | Blank fields (`""`, `"   "`) discarded, non-blank `"summary"` field indexed |
| `documentWithOnlyFieldsAndNoRawContentShouldIndexCorrectly` | Document with only fields (no rawContent) is fully searchable |

## Full test suite

```
Tests run: 87, Failures: 0, Errors: 0, Skipped: 0
```

6 field-aware tests (4 original + 2 added):
1. `bodyOnlyDocumentShouldStillIndexAndSearchCorrectly`
2. `documentWithTitleAndBodyFieldsShouldIndexAndSearchCorrectly`
3. `termOnlyInTitleShouldBeSearchable`
4. `documentWithAllBlankFieldsShouldFallbackToRawContent`
5. `documentWithMixedBlankAndNonBlankFieldsShouldUseNonBlankValues` (new)
6. `documentWithOnlyFieldsAndNoRawContentShouldIndexCorrectly` (new)

## Remaining TODOs

- **Field weighting** — title terms weighted higher than body terms (e.g., BM25F)
- **Field-specific queries** — search only within a named field
- **Field-aware vector weighting** — different weight contributions per field in sparse vectors
- The `fields == null` check in `resolveContent` is dead code; remove if desired

## Conclusion

The field-aware indexing is a clean, minimal change that aggregates field values into the existing lexical pipeline. No ranker, searcher, or vectorizer was modified. The implementation is ready for the next phase (field weighting or field-specific search) whenever desired.
