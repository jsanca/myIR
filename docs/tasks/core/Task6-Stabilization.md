Please implement the next stabilization refactor for myIR based on the current review.

Scope:

1. Add Searchers.lexical(...)
    - Add a lexical factory method to Searchers.java.
    - It should construct and return the public search abstraction without exposing SimpleSearcher to callers.
    - Keep SimpleSearcher hidden as an implementation detail as much as the current package structure allows.
    - Update tests that instantiate SimpleSearcher directly to use Searchers.lexical(...).

2. Make Corpus statistics deterministic by default
    - Change Corpora.inMemory() so the default statistics refresh mode is EAGER.
    - Keep DEBOUNCED mode available through an explicit factory or explicit constructor/factory argument.
    - If appropriate, add a clearly named convenience factory such as Corpora.inMemoryDebounced().
    - Do not remove existing APIs unless absolutely necessary.

3. Update tests for deterministic statistics
    - Review RankersTest and DocumentWeighterTest.
    - Ensure tests that depend on immediate statistics use EAGER behavior explicitly or rely on the new deterministic default.
    - Add or adjust tests to prove Corpora.inMemory() returns up-to-date statistics immediately after add/replace.

4. Add Searchers.lexical factory test
    - Add a focused test proving lexical search can be created through Searchers.lexical(...).
    - Avoid broad integration rewrites.

Constraints:
- Do not implement per-field indexing.
- Do not implement field weighting yet.
- Do not change DocumentPreprocessor aggregation strategy.
- Do not add new dependencies.
- Keep code comments in English.
- Keep the refactor incremental and minimal.
- Preserve the academic/didactic nature of myIR.

Expected output:
- Briefly explain changed files.
- Run the full test suite.
- Report any failing tests and the cause.