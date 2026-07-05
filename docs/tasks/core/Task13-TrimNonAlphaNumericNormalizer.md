Please address the TrimNonAlphaNumericNormalizer uppercase edge case in myIR.

Context:
Deep previously identified that TrimNonAlphaNumericNormalizer appears to use a lowercase-only regex such as [^a-z0-9]. This means that if the normalizer is used standalone, without a lowercase normalizer before it, uppercase alphabetic characters may be removed unexpectedly.

Current concern:
A normalizer named TrimNonAlphaNumericNormalizer should probably preserve alphanumeric characters regardless of case. Lowercasing should remain the responsibility of a separate LowercaseNormalizer or normalizer pipeline step.

Goal:
Add focused tests for TrimNonAlphaNumericNormalizer standalone behavior and make the smallest safe fix if the current behavior is clearly wrong.

Scope:

1. Add focused tests
   Add or update NormalizersTest to cover TrimNonAlphaNumericNormalizer when used directly, without a lowercase normalizer before it.

   Cover at least:
    - "HELLO" should preserve alphabetic characters
    - "HELLO!!!" should trim/remove punctuation but preserve "HELLO"
    - "Java-25" should preserve letters and digits according to the current intended trimming behavior
    - "ABC123" should preserve both uppercase letters and digits
    - Mixed-case input such as "MyIR!!!" should preserve letters and digits
    - Punctuation-only input such as "!!!" should produce the current expected empty/null behavior, depending on existing contract

2. Decide whether behavior should change
    - If uppercase letters are currently removed, treat that as a bug unless existing tests clearly document otherwise.
    - Update the regex or implementation so uppercase letters are considered alphabetic/alphanumeric.
    - Prefer a minimal implementation change.
    - Do not change the broader normalizer pipeline behavior.

3. Preserve responsibility boundaries
    - TrimNonAlphaNumericNormalizer should remove or trim non-alphanumeric characters.
    - It should not lowercase.
    - It should not remove stop words.
    - It should not tokenize.
    - Lowercasing remains a separate normalizer responsibility.

4. Verify existing pipelines
    - Ensure existing English, Spanish, and minimal normalizer pipeline tests still pass.
    - The existing pipeline order may still lowercase before trimming; that is fine.
    - This task only ensures the standalone normalizer is safe and unsurprising.

Constraints:
- Do not change tokenizer behavior.
- Do not change DocumentPreprocessor behavior.
- Do not change indexing, ranking, search, or corpus logic.
- Do not add logging yet.
- Do not introduce the future IndexWriter/read-write-index infrastructure yet.
- Do not add dependencies.
- Keep comments and test names in English.
- Keep the task small and focused.

Expected output:
- List changed files.
- Explain the previous uppercase behavior.
- Explain whether the behavior changed.
- Summarize the TrimNonAlphaNumericNormalizer standalone contract.
- Run the full test suite and report the result.