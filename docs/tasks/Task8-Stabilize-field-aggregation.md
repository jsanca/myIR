Stabilize and document the current field aggregation contract in myIR.

Context:
myIR now supports structured document fields, but the current indexing model is still whole-document indexing. Fields are used as structured content sources, then DocumentPreprocessor aggregates them into a single normalizedContent string. After preprocessing, field provenance is intentionally lost. The current system does NOT yet support per-field indexing, field-aware ranking, or field weighting.

Goal:
Make this current behavior explicit in code documentation, tests, and/or a small ADR so future refactors do not accidentally assume that myIR already has true field-aware indexing.

Scope:

1. Document the current behavior in DocumentPreprocessor
    - Add or improve JavaDoc/comments explaining that:
        - If fields are present and contain non-blank values, they take precedence over rawContent.
        - Field values are aggregated into one whole-document content stream.
        - Field names are not preserved past preprocessing.
        - The resulting normalizedContent is the source used by lexical indexing, vector indexing, and ranking.

2. Document the behavior in the Document model if appropriate
    - Add or improve JavaDoc/comments around fields, rawContent, and normalizedContent.
    - Clarify that fields currently represent structured input content, not indexed field dimensions.
    - Avoid over-documenting implementation details in the domain model if DocumentPreprocessor is the better place.

3. Add a short ADR
    - Create a small ADR file if the project already has a docs/adr or similar structure.
    - If no ADR structure exists, create a minimal docs/adr directory and add the ADR there.
    - Suggested title:
      ADR: Document Fields Are Aggregated Into Whole-Document Content
    - The ADR should explain:
        - Current decision
        - Context
        - Consequences
        - What is intentionally not supported yet
        - Future extension path toward field-aware indexing/field weighting

4. Verify existing tests
    - Review the recently added field aggregation tests.
    - Do not duplicate tests unnecessarily.
    - If a tiny test rename or assertion message improves clarity, that is fine.
    - Do not add broad new test suites for this task.

5. Keep behavior unchanged
    - This task should not change runtime behavior.
    - No production logic changes unless they are strictly documentation-only or naming/comment improvements.

Constraints:
- Do not implement per-field indexing.
- Do not implement field weighting.
- Do not change DocumentPreprocessor aggregation behavior.
- Do not change ranking logic.
- Do not change index structures.
- Do not add dependencies.
- Keep comments and documentation in English.
- Keep the task small and focused.

Expected output:
- List changed files.
- Briefly summarize the documented contract.
- Confirm that runtime behavior did not change.
- Run the full test suite and report the result.