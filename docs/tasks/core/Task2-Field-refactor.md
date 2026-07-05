Yes, implement the smallest safe plan.

Please modify DocumentPreprocessor so it builds the text to tokenize as follows:

1. If document.fields() is non-empty, aggregate all non-null, non-blank field values using whitespace.
2. If document.fields() is empty, fall back to document.rawContent().
3. If fields exist but all values are null/blank, also fall back to document.rawContent().
4. Generate normalizedContent from that selected/aggregated content.

Rules:
- Do not implement field-specific indexes.
- Do not implement field weighting.
- Do not change query syntax.
- Do not change rankers/searchers/vectorizers unless tests prove it is necessary.
- Preserve backward compatibility for body-only/rawContent documents.
- Keep the implementation small and cohesive.

Tests to add:
1. Body-only document still indexes and searches correctly.
2. Document with title/body fields indexes and searches correctly.
3. A term that appears only in the title field is searchable.
4. A document with empty/blank fields falls back to rawContent.

After implementation:
- Run the full test suite.
- Summarize files changed.
- Summarize compatibility behavior.
- Summarize any TODOs for future field weighting or field-specific search.