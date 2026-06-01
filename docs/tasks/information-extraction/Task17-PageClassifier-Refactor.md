Refactor PageClassifiers without changing behavior.

Goals:

1. Keep the public API unchanged:
    - PageClassifiers.jsoupDefault()
    - PageClassifiers.wordpressWooCommerceDefault(UrlClassifier)

2. Move private implementation classes out of PageClassifiers:
    - JsoupGenericClassifier → internal JsoupGenericPageClassifier
    - WordPressWooCommerceClassifier → internal WordPressWooCommercePageClassifier

3. Keep implementation classes non-exported by the module.

4. Extract duplicated HTML/JSON-LD signal helpers only if it reduces duplication:
    - JSON-LD type detection
    - OpenGraph type detection
    - selectFirst helper

5. Remove unused parameters:
    - wpDetected in refineType if it is not needed.

6. Do not change classification behavior.
    - Existing tests should pass unchanged.
    - Add no new feature heuristics in this task.

7. Run mvn clean test and report results.