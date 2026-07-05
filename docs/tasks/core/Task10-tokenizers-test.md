Please add dedicated unit tests for Tokenizers.whitespace().

Context:
The tokenizer is currently exercised indirectly through indexing/search tests, but it does not have focused unit test coverage. We want to document its exact responsibility before future analysis-pipeline work.

Goal:
Add focused tests that verify the behavior of the whitespace tokenizer without changing production behavior unless a real bug is found.

Scope:
1. Add a TokenizersTest or WhitespaceTokenizerTest.
2. Cover:
    - null input
    - empty input
    - blank input
    - multiple spaces
    - tabs and newlines
    - leading/trailing whitespace
    - punctuation preservation
    - case preservation
3. Make the tests clarify that:
    - Tokenizer splits input into tokens.
    - Tokenizer does not lowercase.
    - Tokenizer does not remove punctuation.
    - Tokenizer does not remove stop words.
    - Those responsibilities belong to Normalizer.

Constraints:
- Do not change tokenizer behavior unless a test exposes a clear bug.
- Do not modify Normalizers.
- Do not add dependencies.
- Keep comments and test names in English.
- Keep the task small and focused.

Expected output:
- List changed files.
- Summarize the tokenizer contract documented by tests.
- Run the full test suite and report the result.