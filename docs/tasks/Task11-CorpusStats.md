Please add focused tests for CorpusStatistics.from(...).

Context:
CorpusStatistics.from(corpus) computes statistics by scanning the full corpus. InMemoryCorpus maintains statistics incrementally. We want to ensure both paths produce consistent results and document the expected behavior.

Goal:
Add tests that verify CorpusStatistics.from(...) matches the current statistics semantics used by InMemoryCorpus.

Scope:

1. Add or update CorpusStatisticsTest
   Cover CorpusStatistics.from(...) with:
    - empty corpus
    - corpus with one document with length metadata
    - corpus with multiple documents with length metadata
    - corpus with some documents having null length metadata
    - corpus with zero-length documents if supported by the current model
    - replacement scenario if the current Corpus API supports replacement through add with same id

2. Verify consistency
    - Compare CorpusStatistics.from(corpus) with corpus.statistics() for an EAGER in-memory corpus.
    - Make sure documentCount, totalDocumentLength, documentsWithLength, and averageDocumentLength match expected values.

3. Document null-length behavior
    - Tests should make explicit that documents with null length:
        - count toward documentCount
        - do not contribute to totalDocumentLength
        - do not contribute to documentsWithLength
        - therefore do not affect averageDocumentLength

4. Keep behavior unchanged
    - Do not change production code unless the tests expose a real bug.
    - If a behavior seems questionable but existing code depends on it, document it in the test instead of changing it.

Constraints:
- Do not change Corpus API.
- Do not add remove().
- Do not change CorpusStatistics mutability model.
- Do not change Corpora.inMemory() default.
- Do not add dependencies.
- Keep comments and test names in English.
- Keep the task small and focused.

Expected output:
- List changed files.
- Summarize what CorpusStatistics.from(...) now guarantees.
- Explain whether it matches InMemoryCorpus.statistics().
- Run the full test suite and report the result.