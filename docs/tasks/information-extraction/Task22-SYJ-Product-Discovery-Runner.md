Task 22: Add a simple SYJ product discovery runner

Goal:
Create a small app/dev runner that fetches selected SYJ pages, runs the existing product discovery pipeline, and prints an inspectable report.

Context:
We already have:
- WebPage
- fetchers
- PageClassifier
- ProductDiscoverer
- ProductDiscoveryCollector
- ProductDiscoveryReport
- ProductDetail/ProductCard extraction

Requirements:

1. Put the runner in codex-ir-app, not in core.
2. Do not refactor modules in this task.
3. Do not add dotCMS import/export yet.
4. Do not add a full crawler/BFS traversal yet.
5. Keep this as a practical validation tool.

Behavior:
- Accept one or more seed URLs, either hardcoded temporarily or passed via command-line args.
- Fetch each URL using the existing web fetcher.
- Build WebPage instances.
- Run ProductDiscoveryCollectors.jsoupDefault().
- Print a readable report:
    - total pages analyzed
    - total product details found
    - total product cards found
    - per-page classification
    - for each ProductDetail:
        - URL
        - name
        - SKU if present
        - brand if present
        - price if present
        - availability if present
        - short description if present
        - image count
    - for each ProductCard:
        - URL
        - name/title
        - price if present
        - image URL if present

Implementation constraints:
- Keep the runner simple.
- Avoid introducing new abstractions unless clearly needed.
- Do not change extractor behavior.
- Do not add site-specific SYJ heuristics yet.
- If a page cannot be fetched, print a clear message and continue.

Tests:
- Add tests for report formatting if a formatter class is introduced.
- Otherwise, keep the runner minimal and rely on existing collector/extractor tests.

Run:
mvn clean test

Report:
- files changed
- how to run the runner
- sample output shape
- test summary