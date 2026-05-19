Please implement the first generic URL classification milestone in codex-ir-web.

Context:
Sitemap traversal is now implemented and stable. It discovers URLs from robots.txt and XML sitemaps, then delegates fetched pages through SiteTraversalStrategy with expandLinks=false.

Before implementing WooCommerce/product extraction, we need a generic URL classification layer.

Goal:
Add a small deterministic URL classifier that classifies discovered URLs into broad page types without fetching page HTML first.

Scope:

1. Add UrlType enum
   Suggested values:
    - HOMEPAGE
    - PRODUCT
    - CATEGORY
    - PAGE
    - BLOG_POST
    - SEARCH
    - CART
    - CHECKOUT
    - ACCOUNT
    - ADMIN
    - FEED
    - ASSET
    - IGNORED
    - UNKNOWN

   Keep the enum practical. If this list feels too large, start smaller:
   HOMEPAGE, PRODUCT, CATEGORY, PAGE, IGNORED, UNKNOWN.

2. Add ClassifiedUrl record
   Suggested:
    - URI uri
    - UrlType type
    - String reason or classificationSource if useful

3. Add UrlClassifier interface
   Suggested:
   ClassifiedUrl classify(URI uri);

4. Add default implementation/factory
   Suggested factory:
   UrlClassifiers.wordpressWooCommerceDefault()
   or
   UrlClassifiers.defaultWeb()

   For now, use deterministic URL patterns only.

5. Classification rules
   Cover common WordPress/WooCommerce patterns:

   Product-like:
    - /product/
    - /producto/
    - /produit/
    - /shop/
    - /tienda/

   Category-like:
    - /product-category/
    - /categoria-producto/
    - product_cat query param

   Ignored:
    - /cart/
    - /checkout/
    - /my-account/
    - /wp-admin/
    - /wp-login.php
    - /wp-json/
    - /feed/
    - /search/
    - /order-tracking/
    - query params like add-to-cart, filter_*, orderby, min_price, max_price

   Homepage:
    - root path
    - empty path
    - /

   Asset:
    - common static asset extensions if encountered:
      .jpg, .jpeg, .png, .gif, .webp, .svg, .css, .js, .pdf

6. Keep it generic
    - Do not hardcode a specific client site.
    - Do not implement product extraction.
    - Do not fetch HTML yet.
    - Do not add Playwright or LLM logic.
    - Do not integrate with dotCMS.

7. Tests
   Add focused tests with local URI examples:
    - homepage classification
    - product URL classification
    - Spanish product URL classification
    - category URL classification
    - ignored cart/checkout/account/admin URLs
    - ignored query parameters
    - asset URL classification
    - unknown URL fallback
    - null handling if applicable

8. Package placement
   Put this in a clean package under codex-ir-web, for example:
   codex.ir.ingestion.crawler.classifier
   or
   codex.ir.ingestion.web.classifier

   Keep JPMS boundaries clean.
   Update module-info.java only if this package must be exported. Prefer not exporting it yet unless needed.

Constraints:
- No WooCommerce extraction yet.
- No JSON-LD extraction yet.
- No ProductDetail/ProductCard yet.
- No changes to codex-ir-core.
- No crawler rewrite.
- Preserve existing sitemap traversal behavior.
- Keep comments in English.

Expected output:
- List changed files.
- Explain classification rules.
- Explain what remains URL-only and what will require HTML page classification later.
- Run mvn test from root and report results.