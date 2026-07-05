We are working on the myIR Java project.

Context:
The project is migrating from a single-document-body model to a field-aware document model.

The current goal is to finish the field migration cleanly.

Important background:
- Document now supports canonical fields plus custom fields.
- Existing code used to assume a single body/content string.
- normalizedContent/body may still exist as a compatibility helper, but the long-term model should be field-aware.
- The ranking/search/indexing pipeline should not break.
- The current project now compiles and tests pass after the latest refactor, but the field migration is not complete.

Main goal:
Finish the field-aware migration across the indexing and search pipeline.

Please inspect the project first and produce a short implementation plan before editing files.

Areas to review:

1. Document model
    - Confirm how fields are represented.
    - Confirm whether body/normalizedContent is now a derived compatibility field.
    - Avoid duplicating field state unnecessarily.
    - Preserve backward compatibility where tests or existing APIs still rely on body/normalizedContent.

2. Indexers
    - Ensure indexers use the field-aware document content.
    - If the current indexers still only index body/normalizedContent, identify it.
    - Decide whether the first implementation should:
      a) aggregate all searchable fields into a single lexical stream, or
      b) index fields separately.
    - For this task, prefer the simplest safe implementation:
      aggregate searchable fields into the current lexical pipeline unless field-specific inverted indexes already exist.

3. Fetchers / crawlers / document creation
    - Ensure fetched HTML documents populate the appropriate fields.
    - At minimum, map:
        - title
        - body
        - url
        - description/meta if already available
    - Do not over-engineer a full schema system yet.

4. Searchers
    - Ensure SimpleSearcher and related search code continue working.
    - Queries can still be single-text queries for now.
    - Do not implement advanced field queries yet unless already partially present.
    - Keep the external search API stable unless there is a clear reason to change it.

5. Vectorization
    - Review Vectorizer / SparseDocumentVector usage.
    - Ensure vectors are created from the same field-aware lexical representation used by indexing.
    - Do not introduce dense vectors yet.
    - Do not change cosine similarity behavior unless required by the field migration.

6. Ranking
    - Ensure TF-IDF and BM25 still work.
    - Document length should reflect the indexed lexical representation.
    - Avoid breaking existing ranker tests.
    - Do not introduce field weighting yet unless it is already trivial and isolated.

7. Tests
    - Add or update tests proving that:
        - A document with body-only content is still indexed/searchable.
        - A document with multiple fields is indexed/searchable.
        - Search finds terms from non-body fields if those fields are considered searchable.
        - Existing ranking tests still pass.
    - Keep tests focused and small.

Rules:
- Do not rewrite the architecture.
- Do not introduce external dependencies.
- Do not introduce dense embeddings.
- Do not implement Lucene-like field queries yet.
- Preserve existing public APIs where possible.
- Prefer small cohesive changes.
- Run the full test suite after changes.
- If something is ambiguous, leave a TODO and explain the tradeoff instead of inventing a large design.

Expected output after implementation:
1. Summary of the field-aware model after your changes.
2. Files changed.
3. Tests added/updated.
4. Any compatibility decisions.
5. Any TODOs for later field weighting or field-specific search.
6. Full build/test result.

Acceptance criteria:

- `mvn test` passes.
- Existing body-based documents still work.
- A document with fields such as title/body/description can be indexed.
- Searching for a term that appears in title or description returns the document.
- Ranking still behaves as before for body-only documents.
- No field-specific query syntax is required yet.

