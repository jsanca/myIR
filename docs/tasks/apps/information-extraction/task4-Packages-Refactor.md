Please refactor codex-ir-web packages to follow an API vs internal package structure for crawler-related code.

Context:
codex-ir-web is now a JPMS module. We want module exports to clearly expose only public API packages while keeping traversal and sitemap implementations internal.

Current issue:
Some implementation classes are public or live in packages that may look like API:
- SiteTraversalStrategy
- SitemapSiteTraversalStrategy
- SitemapParser
- RobotsParser
- SitemapEntry
- SitemapEntries
- SeededWebPageTraversal

We want callers to use factories and API abstractions, especially:
- WebPageSourceStrategies
- WebPageSourceStrategy
- UrlClassifier / UrlFilter APIs

Goal:
Separate public API from internal implementation packages and refine module-info.java exports.

Scope:

1. Define crawler public API packages
   Keep as public/exported API:
    - codex.ir.ingestion
    - codex.ir.ingestion.crawler
    - codex.ir.ingestion.crawler.classifier
    - codex.ir.canonicalizer

   The classifier package should remain API because callers need to build custom classification/filtering rules.

2. Move implementation classes to internal packages
   Move traversal implementation classes to something like:
    - codex.ir.ingestion.crawler.internal.traversal

   Move sitemap implementation classes to something like:
    - codex.ir.ingestion.crawler.internal.sitemap

   Suggested moves:
    - SiteTraversalStrategy → internal.traversal
    - SeededWebPageTraversal → internal.traversal or internal.sitemap, depending on usage
    - SitemapSiteTraversalStrategy → internal.sitemap
    - SitemapParser → internal.sitemap
    - RobotsParser → internal.sitemap
    - SitemapEntry → internal.sitemap
    - SitemapEntries → internal.sitemap

3. Preserve public factories
   WebPageSourceStrategies should remain the public composition root.
   Existing public factory methods should keep working:
    - siteTraversal(...)
    - sitemapTraversal(...)

   Callers should not instantiate implementation classes directly.

4. Refine JPMS exports
   Update codex-ir-web/module-info.java:
    - Export only API packages.
    - Do not export internal traversal or internal sitemap packages.

5. Fix UrlFilters edge cases
   In UrlFilters:
    - includeTypes() with no arguments should return rejectAll()
    - excludeTypes() with no arguments should return acceptAll()
    - Add tests for both cases.
    - Keep null validation behavior clear and tested.

6. Keep behavior unchanged
    - No crawling behavior changes.
    - No sitemap logic changes.
    - No URL classification/filtering behavior changes except the empty include/exclude edge cases.
    - No product extraction.
    - No PageClassifier yet.
    - No codex-ir-core changes unless absolutely necessary.

7. Update tests
    - Update package imports after moves.
    - Ensure tests still validate sitemap parser, robots parser, traversal, UrlFilters, and sitemap traversal.
    - Add tests for empty includeTypes/excludeTypes behavior.

Expected output:
- List moved classes/packages.
- Show updated module-info.java exports.
- Confirm which packages are public API and which are internal.
- Explain UrlFilters empty include/exclude behavior.
- Run mvn test from root and report results.