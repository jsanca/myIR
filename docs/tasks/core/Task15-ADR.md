Please write a design note / ADR for future field-aware indexing and field weighting in myIR.

Context:
myIR currently supports structured Document fields, but the current indexing model intentionally aggregates fields into whole-document normalizedContent. Field provenance is lost after preprocessing. This is now documented and tested.

Goal:
Document possible future designs for true field-aware indexing and field weighting without implementing them yet.

Scope:

1. Create a design note or ADR
    - If docs/adr already exists, add a new ADR there.
    - If the project has another documentation convention, follow it.
    - Suggested title:
      ADR: Future Field-Aware Indexing and Field Weighting

2. Explain current model
    - Document fields are currently structured input only.
    - DocumentPreprocessor aggregates fields into normalizedContent.
    - InvertedIndex, Posting, Ranker, Vectorizer, and DocumentWeighter operate on whole-document content.
    - Current behavior is correct for the current feature set.

3. Compare future design options
   Include at least:

   Option A: Keep whole-document aggregation and add simple field boosts during preprocessing
    - Example: title terms repeated or weighted before indexing.
    - Pros/cons.

   Option B: Store field name in Posting
    - Posting includes fieldKey or fieldName.
    - Pros/cons.

   Option C: Maintain separate inverted indexes per field
    - Example: title index, body index, tags index.
    - Pros/cons.

   Option D: Fielded posting lists / nested field statistics
    - InvertedIndex maps term -> document -> field occurrences.
    - Pros/cons.

   Option E: BM25F-style ranking
    - Per-field length normalization.
    - Per-field boosts.
    - Different average lengths per field.
    - Pros/cons.

4. Analyze impact
   For each meaningful option, discuss impact on:
    - DocumentPreprocessor
    - Posting
    - InvertedIndex
    - Indexer
    - Searcher
    - Ranker
    - DocumentWeighter / Vectorizer
    - CorpusStatistics
    - Tests

5. Recommend an incremental path
   The recommendation should avoid a big-bang refactor.
   Suggested path:
    - Phase 1: keep current whole-document aggregation stable
    - Phase 2: introduce FieldKey / FieldPath semantics if needed
    - Phase 3: preserve field provenance in an internal representation
    - Phase 4: add field-aware postings or fielded index
    - Phase 5: implement simple field boosts
    - Phase 6: consider BM25F-like ranking

6. Explicitly state what not to implement now
    - Do not change current indexing behavior.
    - Do not add field weighting yet.
    - Do not change Posting or InvertedIndex yet.
    - Do not introduce BM25F yet.
    - This task is documentation/design only.

Constraints:
- No production code changes.
- No behavior changes.
- No new dependencies.
- Keep documentation in English.
- Keep the ADR practical and incremental, not overly academic.
- Preserve the didactic nature of myIR.

Expected output:
- List changed/added documentation files.
- Summarize the recommended future path.
- Confirm no production behavior changed.
- Run the test suite if appropriate.