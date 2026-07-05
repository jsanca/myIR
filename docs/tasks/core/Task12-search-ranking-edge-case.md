Please address the remaining search/ranking edge-case technical debt in myIR.

Scope:

1. BM25 behavior with documents whose metadata.length is null
    - Review the current Bm25Ranker behavior when a matching document has metadata.length == null.
    - Add focused tests documenting the current behavior.
    - If the current behavior is clearly undesirable, propose the smallest safe change.
    - Do not make a broad ranking refactor.
    - Important: if a document has no length metadata, decide whether BM25 should:
      a) return 0 as it does today,
      b) fall back to posting.termFrequency(),
      c) fall back to average document length,
      d) or treat the document as invalid for BM25.
    - Prefer documenting the current behavior unless there is a strong correctness reason to change it.

2. Empty/blank query behavior in SimpleSearcher
    - Add tests for null, empty, and blank queries.
    - Verify behavior is deterministic and does not throw unexpectedly.
    - Decide whether empty/blank queries should return an empty result list.
    - Prefer empty results over exceptions for user-facing search behavior.

3. Empty/blank query behavior in VectorSearcher
    - Add tests for null, empty, and blank queries.
    - Verify behavior is deterministic and does not throw unexpectedly.
    - Decide whether empty/blank vector queries should return an empty result list.
    - Prefer empty results over exceptions for user-facing search behavior.

Constraints:
- Do not implement field-aware indexing.
- Do not implement field weighting.
- Do not change tokenizer or normalizer behavior unless strictly necessary.
- Do not change DocumentPreprocessor aggregation behavior.
- Do not add dependencies.
- Keep comments and test names in English.
- Keep the task small and focused.

Expected output:
- List changed files.
- Explain BM25 null-length behavior and whether it changed.
- Explain SimpleSearcher empty/blank query behavior.
- Explain VectorSearcher empty/blank query behavior.
- Run the full test suite and report results.