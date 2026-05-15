Please address the remaining DocumentPreprocessor.isAlreadyPreprocessed() edge-case technical debt in myIR.

Context:
myIR now has a stable preprocessing contract:
- fields, when present and non-blank, take precedence over rawContent
- field values are aggregated into whole-document normalizedContent
- normalizedContent is then used by lexical indexing, vector indexing, ranking, and corpus statistics
- field provenance is intentionally lost after preprocessing

We need to ensure DocumentPreprocessor does not incorrectly skip preprocessing when a Document is only partially preprocessed.

Goal:
Add focused tests for DocumentPreprocessor.isAlreadyPreprocessed() edge cases, and make the smallest safe fix if preprocessing is skipped too aggressively.

Scope:

1. Review current isAlreadyPreprocessed() behavior
    - Determine what conditions currently cause preprocessing to be skipped.
    - Check whether it only looks at normalizedContent.
    - Check whether it also verifies metadata such as length, uniqueTerms, and termFrequencies.

2. Add focused tests
   Cover documents such as:

    - normalizedContent present but metadata empty
      Expected concern: should probably not be considered fully preprocessed if term metadata is missing.

    - normalizedContent present but metadata.length is null
      Expected concern: BM25 and corpus statistics depend on length.

    - normalizedContent present but termFrequencies missing or empty while normalizedContent has terms
      Expected concern: vectorization/ranking may be affected if metadata is incomplete.

    - metadata length present but normalizedContent blank
      Expected concern: should not be considered preprocessed.

    - rawContent and normalizedContent both present
      Expected concern: clarify whether normalizedContent wins because document is considered already preprocessed.

    - fields present and normalizedContent already present
      Expected concern: clarify whether preprocessing is skipped and fields are ignored because normalizedContent is treated as authoritative.

    - fully preprocessed document
      Expected: preprocessing should be skipped.

    - raw document with only rawContent or fields
      Expected: preprocessing should run.

3. Decide intended contract
   Prefer a strict definition:
   A document should be considered already preprocessed only if:
    - normalizedContent is non-blank, and
    - metadata is present, and
    - metadata.length is present, and
    - metadata.termFrequencies are present/consistent enough for the current model.

   If the existing code uses a different definition, document the current behavior in tests unless it creates a real correctness bug.

4. Keep changes minimal
    - Do not change field aggregation behavior.
    - Do not change tokenizer or normalizer behavior.
    - Do not change indexing, ranking, search, or corpus APIs.
    - Do not add logging yet.
    - Do not introduce IndexWriter/read-write-index infrastructure yet.
    - Do not add dependencies.

Expected output:
- List changed files.
- Explain the previous isAlreadyPreprocessed() behavior.
- Explain whether behavior changed.
- Summarize the final isAlreadyPreprocessed() contract.
- Run the full test suite and report the result.