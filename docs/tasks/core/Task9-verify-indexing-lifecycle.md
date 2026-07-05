Please implement Option B: review and stabilize the lexicalAndVector indexing lifecycle.

Context:
myIR now has a documented contract where document fields are aggregated into whole-document normalizedContent by DocumentPreprocessor. The current indexing model depends on lexical indexing, vector indexing, corpus storage, and ranking all using the same preprocessed representation.

Goal:
Ensure that Indexers.lexicalAndVector(...) preprocesses each document exactly once and uses the resulting preprocessed Document consistently for:
- Corpus storage
- InvertedIndex population
- DocumentVectorStore population

Scope:

1. Review Indexers.lexicalAndVector(...)
    - Confirm whether the document is preprocessed once.
    - Confirm whether both lexical and vector indexing use the preprocessed document.
    - Confirm whether the stored corpus document is the same normalized representation used for both indexes.

2. Add or improve focused tests
    - Add a test using a document with fields, mixed case, and stop words.
    - Index it through Indexers.lexicalAndVector(...).
    - Verify lexical search works using normalized field terms.
    - Verify vector search works using the same normalized field terms.
    - Verify terms removed by the normalizer/stop-word pipeline do not appear in the searchable/vectorized representation, if that is consistent with the existing normalizer pipeline.

3. Detect lifecycle divergence
    - Add a test that would fail if vector indexing used the original raw document instead of the preprocessed document.
    - For example, use raw field content like "THE Java Platform" and verify the vector representation/search behaves according to normalizedContent, not rawContent.

4. Documentation
    - Add a small JavaDoc/comment if helpful, explaining that lexicalAndVector is the preferred path when both indexes are needed because it shares one preprocessing lifecycle.
    - Do not over-document.

5. Keep changes minimal
    - If the lifecycle is already correct, prefer tests/documentation over production refactoring.
    - Only change production code if a test reveals an actual lifecycle bug.

Constraints:
- Do not implement per-field indexing.
- Do not implement field weighting.
- Do not change DocumentPreprocessor aggregation behavior.
- Do not change Ranker behavior.
- Do not change Corpus behavior.
- Do not add dependencies.
- Keep comments and code comments in English.

Expected output:
- Explain whether the current lexicalAndVector lifecycle was already correct or needed adjustment.
- List changed files.
- Summarize the new tests and what bug they would catch.
- Run the full test suite and report the result.