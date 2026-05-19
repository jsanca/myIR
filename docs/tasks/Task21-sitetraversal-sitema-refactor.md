Please refactor sitemap traversal to reuse the existing SiteTraversalStrategy machinery instead of duplicating page fetching.

Goal:
SitemapSiteTraversalStrategy should discover page URIs from sitemaps, then delegate fetching/emission to the existing traversal infrastructure with link expansion disabled.

Scope:
1. Add an internal expandLinks option to SiteTraversalStrategy.
    - Existing siteTraversal(...) factory methods must preserve current behavior with expandLinks=true.
    - sitemapTraversal(...) should use expandLinks=false.

2. When expandLinks=false:
    - fetched pages are emitted
    - discovered links from WebPageFetcher are ignored
    - no links are enqueued
    - maxPages, delay, canonicalization, domain rules, disallowed paths, and VisitedUriRegistry still apply

3. Refactor SitemapSiteTraversalStrategy:
    - keep robots.txt discovery
    - keep sitemap parsing
    - collect canonical page URIs
    - remove duplicated fetchAndEmitPages logic
    - delegate page URI processing to SiteTraversalStrategy or a small seed/list traversal helper
    - remove its own emittedPages state
    - ensure visitedUriRegistry is actually used through the delegated traversal

4. Keep existing public factories:
    - WebPageSourceStrategies.siteTraversal(...) should behave the same as today
    - WebPageSourceStrategies.sitemapTraversal(...) should behave as sitemap-only, no link expansion

5. Add/update tests:
    - normal siteTraversal still expands links
    - sitemapTraversal does not expand links from fetched pages
    - duplicate sitemap URLs are fetched once
    - readInto called twice should not retain stale emittedPages state
    - VisitedUriRegistry participates in sitemap traversal dedupe

Constraints:
- No WooCommerce extraction.
- No product extraction.
- No JSON-LD extraction.
- No crawler package reorganization yet.
- No JPMS export changes unless necessary.
- No codex-ir-core changes unless absolutely necessary.
- Keep comments in English.
- Preserve all existing tests.

Expected output:
- Explain how expandLinks works.
- Explain how sitemapTraversal now delegates to traversal infrastructure.
- List changed files.
- Confirm sitemapTraversal does not perform BFS expansion.
- Run mvn test from root.