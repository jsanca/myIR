Please implement the first HTML-aware PageClassifier milestone in codex-ir-web.

Context:
codex-ir-web now has a clean API/internal package structure.

Current public API includes:
- codex.ir.ingestion
- codex.ir.ingestion.crawler
- codex.ir.ingestion.crawler.classifier
- codex.ir.canonicalizer

Current internal packages include:
- crawler internal traversal
- crawler internal sitemap
- web utilities

We also have URL-only classification and filtering:
- UrlType
- ClassifiedUrl
- UrlClassifier
- UrlClassifiers
- UrlFilter
- UrlFilters

The next stage is to classify fetched WebPage HTML using deterministic signals. This complements URL classification; it does not replace it.

Goal:
Add a deterministic HTML-aware PageClassifier that classifies fetched WebPage instances using WordPress/WooCommerce and generic HTML signals.

Scope:

1. Add PageClassification record
   Suggested fields:
    - URI uri
    - UrlType type
    - ClassifiedUrl urlClassification
    - boolean wordpressDetected
    - boolean wooCommerceDetected
    - String reason

   Keep it simple. If reason feels too verbose, use a compact field such as source or signal.

2. Add PageClassifier interface
   Suggested:
   PageClassification classify(WebPage page);

3. Add PageClassifiers factory
   Suggested:
    - PageClassifiers.defaultWeb(UrlClassifier urlClassifier)
    - PageClassifiers.wordpressWooCommerceDefault(UrlClassifier urlClassifier)

   If only one factory is implemented now, prefer:
   PageClassifiers.wordpressWooCommerceDefault(...)

4. Classification strategy
   Use URL classification first, then refine with HTML.

   Strong ignored URL types should remain ignored:
    - ADMIN
    - CART
    - CHECKOUT
    - ACCOUNT
    - FEED
    - ASSET
    - SEARCH
    - IGNORED

   For PAGE, UNKNOWN, CATEGORY, PRODUCT, or HOMEPAGE, inspect HTML signals.

5. HTML signals to support

   WordPress detection:
    - meta[name=generator][content*=WordPress]
    - link[rel="https://api.w.org/"]
    - links/scripts containing /wp-content/
    - links/scripts containing /wp-includes/

   WooCommerce detection:
    - body.woocommerce
    - body.woocommerce-page
    - link/script href/src containing woocommerce
    - .woocommerce-breadcrumb
    - .woocommerce-Price-amount

   Product page signals:
    - body.single-product
    - .product_title
    - h1.product_title
    - .single_add_to_cart_button
    - form.cart
    - JSON-LD containing @type Product

   Category/listing page signals:
    - body.tax-product_cat
    - body.post-type-archive-product
    - body.archive.woocommerce
    - .products
    - li.product
    - .woocommerce-loop-product__title
    - .woocommerce-pagination

   Homepage:
    - root URL path
    - body.home

6. JSON-LD detection
   Full JSON-LD extraction is not required yet.
   For this milestone, only detect whether any script[type="application/ld+json"] appears to contain:
    - "@type": "Product"
    - "@type": ["Product", ...]
    - or a @graph node with Product

   Keep this simple and deterministic. Do not build a full JSON-LD mapper yet.

7. Package placement
   Put PageClassifier-related API in the existing classifier package:
   codex.ir.ingestion.crawler.classifier

   This package is public API, because future users may want custom PageClassifier implementations.

   Any helper implementation classes can be package-private inside the same package, or internal if appropriate.

8. Tests
   Add tests using local HTML strings/fixtures only. No network.

   Cover:
    - product page by body.single-product
    - product page by .product_title
    - product page by JSON-LD Product
    - category page by product loop
    - category page by body.tax-product_cat
    - WordPress detection by generator meta
    - WordPress detection by wp-content asset
    - WooCommerce detection by body.woocommerce
    - WooCommerce detection by price element
    - ignored URL type remains ignored even if HTML has product-looking content
    - unknown page remains UNKNOWN or PAGE according to chosen contract
    - homepage by URL/body.home if supported

9. Preserve current behavior
    - Do not change UrlClassifier behavior.
    - Do not change UrlFilter behavior.
    - Do not change sitemap traversal behavior.
    - Do not add product extraction yet.
    - Do not add JSON-LD extraction beyond simple Product detection.
    - Do not add PriceParser.
    - Do not add ImageUrlNormalizer.
    - Do not add Playwright.
    - Do not add LLM/LangChain4j.
    - Do not change codex-ir-core.

10. JPMS
- classifier package is already public API.
- Update module-info.java only if required.
- Do not export internal packages.

Expected output:
- List changed files.
- Explain PageClassifier vs UrlClassifier.
- Explain supported HTML signals.
- Explain classification precedence.
- Explain JSON-LD Product detection limitations.
- Run mvn test from root and report counts/failures.