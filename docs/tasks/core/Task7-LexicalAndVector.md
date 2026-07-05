Good. Now please add the next focused test layer around fields and vector indexing.

Scope:

1. Add an integration test for Indexers.lexicalAndVector(...)
    - Use documents with fields, not only rawContent.
    - Verify that lexical search finds terms coming from fields.
    - Verify that vector search also works from the same preprocessed content.
    - Ensure the document is preprocessed only once through the combined indexer path.

2. Add a field-aware vector search test using fields-only documents
    - Create documents where rawContent is blank or absent.
    - Populate fields such as title and body.
    - Index them using the current whole-document aggregation model.
    - Verify that vector search can retrieve them using terms from those fields.

3. Add a duplicate-term-across-fields test
    - Example:
      title = "java"
      body = "java portable language"
    - Assert that the current model treats this as aggregated whole-document content.
    - The term frequency for "java" should reflect both field occurrences.
    - This documents the current behavior before any future field-weighting work.

4. Add a rawContent plus fields precedence test
    - Create a document with rawContent containing one term and fields containing another.
    - Verify that fields take precedence over rawContent in the current DocumentPreprocessor behavior.
    - This should be explicit so future refactors do not accidentally change it.

5. Add a mixed corpus test
    - Some documents should use fields.
    - Some documents should use rawContent only.
    - Verify both are searchable after indexing.

Constraints:
- Do not implement per-field indexing.
- Do not implement field weighting.
- Do not change DocumentPreprocessor behavior.
- Do not change production code unless a test exposes a real bug.
- Keep this as a test-focused task.
- Comments and code comments should remain in English.

Expected output:
- List new/modified test files.
- Explain what current behavior is now documented by tests.
- Run the full test suite.