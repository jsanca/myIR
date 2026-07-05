Please refactor SitemapSiteTraversalStrategy to delegate discovered sitemap page URIs through an injected seed traversal delegate, instead of directly constructing or calling SiteTraversalStrategy/WebPageSourceStrategies.

Context:
SitemapSiteTraversalStrategy currently discovers page URIs from sitemaps, then directly creates or depends on the concrete traversal implementation. We want it to remain focused only on sitemap discovery. It should not know what traversal implementation processes the discovered page URIs.

Goal:
Introduce a small internal delegate abstraction that receives discovered seed URIs and emits WebPage instances. WebPageSourceStrategies should wire that delegate to SiteTraversalStrategy with expandLinks=false.

Scope:

1. Add a small internal functional interface
   Suggested name:
    - SeededWebPageTraversal
      or
    - PageSeedTraversal
      or
    - PageSeedProcessor

   Suggested shape:
   void traverse(Set<URI> seedUris, Consumer<WebPage> consumer);

2. Update SitemapSiteTraversalStrategy
    - Remove direct dependency on SiteTraversalStrategy.
    - Remove direct construction of traversal implementations.
    - Receive the seed traversal delegate in the constructor.
    - After collecting pageUris from sitemaps, call the delegate:
      seededTraversal.traverse(pageUris, consumer);
    - Keep robots.txt discovery and sitemap parsing unchanged.

3. Update WebPageSourceStrategies
    - When creating sitemapTraversal(...), build a delegate that internally creates SiteTraversalStrategy with expandLinks=false.
    - siteTraversal(...) factories must preserve existing behavior with expandLinks=true.
    - The factory remains the composition root for concrete strategy construction.

4. Visibility
    - Keep the new delegate interface package-private if possible.
    - Do not export new packages from module-info.java unless necessary.
    - Keep SiteTraversalStrategy construction hidden behind factory/composition code.

5. Tests
    - Update existing tests if needed.
    - Add a focused test if useful proving SitemapSiteTraversalStrategy passes discovered page URIs to the delegate.
    - Confirm sitemap traversal still does not expand links.
    - Confirm all previous sitemap tests still pass.

Constraints:
- No WooCommerce extraction.
- No product extraction.
- No crawler package reorganization yet.
- No codex-ir-core changes.
- No JPMS export changes unless necessary.
- Preserve existing behavior.
- Keep comments in English.

Expected output:
- List changed files.
- Show the new delegate interface.
- Explain how WebPageSourceStrategies wires the delegate to SiteTraversalStrategy(expandLinks=false).
- Confirm SitemapSiteTraversalStrategy no longer depends on SiteTraversalStrategy.
- Run mvn test from root and report results.