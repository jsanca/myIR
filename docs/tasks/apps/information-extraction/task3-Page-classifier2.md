Please implement URL filtering as a composable stage after URL classification and before sitemap seed traversal.

Context:
We now have URL-only classification:
- UrlType
- ClassifiedUrl
- UrlClassifier
- UrlClassifiers

SitemapSiteTraversalStrategy discovers page URIs from sitemaps and delegates selected seed URIs to SeededWebPageTraversal.

We want to separate two responsibilities:
- UrlClassifier: determines what a URI appears to be.
- UrlFilter: decides whether a classified URI should be fetched/processed.

Goal:
Add a composable URL filtering layer using a simple Composite Pattern, so users can inspect sitemap classification results and then provide custom business-specific rules.

Scope:

1. Add UrlFilter interface
   Suggested:
   @FunctionalInterface
   public interface UrlFilter {
   boolean accepts(ClassifiedUrl classifiedUrl);
   }

2. Add UrlFilters factory/composite helpers
   Suggested methods:
    - acceptAll()
    - rejectAll()
    - includeTypes(UrlType...)
    - excludeTypes(UrlType...)
    - pathStartsWith(String prefix)
    - pathMatches(Pattern pattern)
    - hasQueryParam(String name)
    - hasQueryParamPrefix(String prefix)
    - not(UrlFilter filter)
    - allOf(UrlFilter... filters)
    - anyOf(UrlFilter... filters)

   Keep the first version small if needed. Prefer clean, tested behavior over a large API.

3. Integrate with SitemapSiteTraversalStrategy
    - SitemapSiteTraversalStrategy should receive:
        - UrlClassifier
        - UrlFilter
    - After collecting page URIs from sitemaps:
        - classify each URI
        - apply UrlFilter
        - pass only accepted URIs to SeededWebPageTraversal
    - Preserve URI order and deduplication.

4. Update WebPageSourceStrategies
    - Existing sitemapTraversal(config, rootUri) should keep working with default behavior.
    - Add overload(s) allowing callers to pass:
        - UrlClassifier
        - UrlFilter
    - Default behavior should be reasonable and backward compatible.

5. Default behavior
   Suggested:
    - use UrlClassifiers.defaultWeb()
    - use a default filter that excludes clearly ignored/unsafe types:
      ADMIN, CART, CHECKOUT, ACCOUNT, FEED, ASSET, SEARCH, IGNORED
    - allow PRODUCT, CATEGORY, PAGE, BLOG_POST, HOMEPAGE, UNKNOWN for now
      Or keep acceptAll() if preserving existing sitemap traversal behavior is preferred.

   Please choose the safer/backward-compatible option and explain it.

6. Tests
   Add tests for:
    - includeTypes accepts only selected types.
    - excludeTypes rejects selected types.
    - allOf composition.
    - anyOf composition.
    - not composition.
    - path prefix filtering.
    - query param filtering.
    - sitemap traversal passes only accepted classified URLs to SeededWebPageTraversal.
    - existing sitemapTraversal overload still works.

7. Keep this URL-only
    - Do not implement PageClassifier yet.
    - Do not implement PageFilter yet.
    - Do not implement product extraction.
    - Do not add WooCommerce extractor.
    - Do not add Playwright or LLM.

Constraints:
- No codex-ir-core changes unless absolutely necessary.
- Keep JPMS boundaries clean.
- No module-info exports unless required.
- Preserve all existing tests.
- Keep comments in English.

Expected output:
- List changed files.
- Explain UrlClassifier vs UrlFilter.
- Explain composite filter helpers.
- Explain sitemap traversal filtering behavior.
- Run mvn test from root and report results.