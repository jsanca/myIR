Task — Review field-aware indexing implementation

The field-aware indexing change is implemented and tests pass.

Before adding new features, please review the implementation and tests.

Goals:
1. Review the new resolveContent() logic in DocumentPreprocessor.
2. Confirm it handles:
    - no fields
    - null fields map if possible
    - empty fields
    - blank field values
    - mixed blank and non-blank field values
    - rawContent fallback
3. Confirm the new tests cover the intended behavior.
4. Add only small missing tests if you find an important uncovered edge case.
5. Do not implement field weighting.
6. Do not implement field-specific queries.
7. Do not change rankers/searchers/vectorizers unless a bug is found.
8. Run the full test suite.

After that, summarize write the report on docs/reports:
- Whether the implementation is sufficient as an intermediate migration step.
- Any tests added.
- Any remaining TODOs.
- Full test result.