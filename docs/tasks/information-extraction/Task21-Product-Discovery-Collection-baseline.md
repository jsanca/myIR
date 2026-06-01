Implement Product Discovery Collection baseline.

Goals:

1. Review existing product discovery API:
    - ProductDiscoverer
    - ProductDiscoverers
    - ProductDiscoveryResult
    - ProductDetail
    - ProductCard
    - WebPage

2. Introduce a small collection/orchestration API:
    - Prefer ProductDiscoveryCollector or ProductDiscoveryCollectors.
    - Keep it thin.
    - It should not fetch pages.
    - It should not crawl.
    - It should not export.
    - It should only run discovery over already available WebPage instances.

3. Possible API:
    - ProductDiscoveryCollector.collect(List<WebPage> pages)
    - Return either:
      a) List<ProductDiscoveryResult>, if we want minimalism
      b) ProductDiscoveryReport, if useful

4. If ProductDiscoveryReport is introduced, include:
    - List<ProductDiscoveryResult> pageResults
    - List<ProductDetail> productDetails
    - List<ProductCard> productCards
    - defensive copies

5. Behavior:
    - Run ProductDiscoverer.discover(page) for each page.
    - Preserve page-level results for diagnostics.
    - Aggregate discovered ProductDetail values.
    - Aggregate discovered ProductCard values.
    - Do not deduplicate across pages yet unless trivial and clearly safe.
    - Do not introduce new heuristics.

6. Factory:
    - ProductDiscoveryCollectors.jsoupDefault()
    - Compose ProductDiscoverers.jsoupDefault()

7. Tests:
    - Collects product details from product pages.
    - Collects product cards from category/home pages.
    - Preserves page-level discovery results.
    - Handles empty input.
    - ProductDiscoveryReport defensively copies lists.
    - Factory returns a working collector.

8. Run:
   mvn clean test

9. Report:
    - Files changed
    - Tests added
    - Test summary