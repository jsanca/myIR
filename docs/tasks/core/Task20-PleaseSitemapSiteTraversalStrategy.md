Please implement the first generic SitemapSiteTraversalStrategy milestone in codex-ir-web.

Context:
myIR is now a real Maven + JPMS modular project:

- codex-ir-core
    - module: codex.ir.core
    - pure IR engine
    - no jsoup/playwright dependency

- codex-ir-web
    - module: codex.ir.web
    - crawling, ingestion, canonicalization, web fetchers
    - depends on codex.ir.core

- codex-ir-app
    - module: codex.ir.app
    - contains codex.Main
    - depends on core + web

The build is green:
- myIR SUCCESS
- codex-ir-core SUCCESS
- codex-ir-web SUCCESS
- codex-ir-app SUCCESS

Goal:
Add a generic sitemap-based traversal strategy to codex-ir-web, reusing the existing web/crawler infrastructure.

This is not a WooCommerce extractor task yet.
This is not a product extraction task yet.
This is generic sitemap discovery for any website.

Scope:

1. Inspect existing web traversal APIs
   Review and reuse the current abstractions:
    - SiteTraversalStrategy
    - WebPageSourceStrategies
    - WebCrawlingConfig
    - WebPageFetcher / JsoupWebPageFetcher
    - WebHttpFetcher / WebHttpFetchers
    - UriCanonicalizers
    - VisitedUriRegistry
    - any existing sitemap stub or placeholder

   Do not create a parallel crawler if the current abstractions can support this.

2. Add sitemap parsing support
   Add a small parser capable of handling:

    - <urlset>
      - extract <url><loc>...</loc></url>

    - <sitemapindex>
      - extract <sitemap><loc>...</loc></sitemap>
      - recursively fetch/parse nested sitemaps in a controlled way

   Optional if easy:
    - <lastmod>

   Keep the parser deterministic and testable.
   Use local XML fixtures/strings in tests, not live network calls.

3. Add robots.txt sitemap discovery
   Implement simple robots.txt sitemap discovery:

    - fetch /robots.txt from the base URL
    - parse Sitemap: directives
    - support multiple Sitemap: lines
    - be tolerant of case/spacing where reasonable
    - if robots.txt is missing, invalid, or has no sitemap, continue gracefully

4. Add known sitemap path fallback
   If robots.txt does not provide sitemap URLs, try common sitemap paths:

    - /wp-sitemap.xml
    - /sitemap_index.xml
    - /sitemap.xml
    - /product-sitemap.xml
    - /wp-sitemap-posts-product-1.xml

   These are discovery candidates only.
   Do not make the strategy WooCommerce-specific.

5. Implement SitemapSiteTraversalStrategy
   Add it under a suitable package, for example:

   codex.ir.ingestion.crawler.sitemap

   It should:
    - fit the existing SiteTraversalStrategy abstraction
    - use existing fetchers where appropriate
    - canonicalize URLs using existing canonicalization utilities
    - deduplicate discovered URLs
    - respect same-host policy if WebCrawlingConfig already supports it
    - avoid query-param explosion
    - avoid fetching every discovered URL unless the current SiteTraversalStrategy contract requires WebPage objects

6. Add/complete factory method
   Add a factory method in the existing factory class, likely WebPageSourceStrategies.

   Suggested names:
    - sitemap(...)
    - siteMap(...)
    - sitemapTraversal(...)

   Follow the current project naming style.

7. Add focused tests
   Add tests in codex-ir-web.

   Cover at least:
    - parsing a simple urlset sitemap
    - parsing a sitemapindex
    - parsing nested sitemapindex -> urlset
    - robots.txt with one Sitemap directive
    - robots.txt with multiple Sitemap directives
    - robots.txt missing sitemap directives
    - duplicate URL deduplication
    - malformed/blank <loc> ignored
    - same-host filtering if supported by current config
    - graceful behavior when sitemap cannot be fetched, if testable

8. JPMS considerations
    - Keep new packages inside codex-ir-web.
    - Update codex-ir-web/module-info.java only if the new public API requires exports.
    - Do not export internal sitemap helper packages unless necessary.
    - Do not create split packages.

9. Keep this milestone small
   Do not implement:
    - WooCommerce detection
    - ProductDetailExtractor
    - ProductCardExtractor
    - JSON-LD extraction
    - PriceParser
    - ImageUrlNormalizer
    - Playwright fallback
    - LangChain4j / LLM inference
    - persistence
    - dotCMS integration
    - myIR indexing integration for products

Constraints:
- No changes to codex-ir-core unless absolutely necessary.
- No behavior changes to existing BFS traversal.
- No crawler rewrite.
- No new dependencies unless strictly necessary.
- Keep comments and documentation in English.
- Preserve the current modular structure.
- Preserve all existing tests.

Expected output:
- List changed files.
- Explain how robots.txt sitemap discovery works.
- Explain how sitemapindex/urlset parsing works.
- Explain how SitemapSiteTraversalStrategy integrates with existing traversal abstractions.
- Explain what is intentionally not implemented yet.
- Show any module-info.java changes.
- Run mvn test from the root.
- Report test counts and failures by module.