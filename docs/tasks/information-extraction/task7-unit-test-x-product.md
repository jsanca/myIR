Please implement the first ProductDetailExtractor milestone using local WooCommerce HTML fixtures.

Context:
We now have:
- UrlClassifier
- UrlFilter
- PageClassifier
- Sitemap traversal
- clean API/internal package separation in codex-ir-web

The next step is deterministic product detail extraction from fetched WebPage HTML. You can use the @product.html as a html source.

Goal:
Given a WebPage that represents a WooCommerce product detail page, extract a minimal ProductDetail model using deterministic HTML signals.

Scope:

1. Add product extraction model
   Suggested package:
   codex.ir.ingestion.crawler.product
   or
   codex.ir.ingestion.crawler.extractor.product

   Suggested records:
    - ProductDetail
    - ProductPrice
    - ProductImage

   Minimal fields:
   ProductDetail:
    - URI url
    - String name
    - Optional<String> sku
    - Optional<ProductPrice> regularPrice
    - Optional<ProductPrice> salePrice
    - Optional<String> shortDescription
    - List<ProductImage> images

   ProductPrice:
    - BigDecimal amount
    - Optional<String> currencyCode
    - String rawText if useful

   ProductImage:
    - URI url
    - Optional<String> altText
    - int displayOrder

2. Add extractor interface
   Suggested:
   @FunctionalInterface
   public interface ProductDetailExtractor {
   Optional<ProductDetail> extract(WebPage page);
   }

3. Add factory
   Suggested:
   ProductDetailExtractors.woocommerceDefault()

4. Extraction strategy
   Use deterministic extraction only.

   Extract name from:
    - h1.product_title
    - .product_title
    - og:title fallback if useful

   Extract SKU from:
    - .sku
    - .product_meta .sku

   Extract price from:
    - .price ins .woocommerce-Price-amount for sale price
    - .price del .woocommerce-Price-amount for regular/original price
    - .price .woocommerce-Price-amount fallback

   Extract currency from:
    - .woocommerce-Price-currencySymbol
    - raw price symbol if available

   Extract short description from:
    - .woocommerce-product-details__short-description

   Extract images from:
    - .woocommerce-product-gallery__image img
    - figure.woocommerce-product-gallery__wrapper img
    - prefer data-large_image, then data-src, then src
    - resolve relative URLs against the WebPage URL

5. Price parsing
   Add a small internal PriceParser if needed.
   Keep it simple:
    - strip currency symbols
    - support comma thousands and decimal dot initially
    - preserve raw price text if parsing is ambiguous
    - do not overbuild international currency parsing yet

6. Tests
   Use local fixture files under:
   src/test/resources/fixtures/woocommerce/

   Add at least:
    - product-detail-basic.html
    - test should extract name
    - test should extract SKU if present
    - test should extract regular price
    - test should extract sale price if present
    - test should extract short description
    - test should extract image URLs
    - test should return Optional.empty() or incomplete result for non-product HTML, depending on chosen contract

7. Important
   The fixture may be based on a real WooCommerce product page, but:
    - do not hardcode client-specific paths in production code
    - do not hardcode product names in selectors
    - keep production logic generic for WooCommerce

8. Do not implement yet:
    - product variations
    - inventory
    - reviews
    - full JSON-LD mapping
    - category extraction
    - ProductProjection to myIR Document
    - dotCMS integration
    - Playwright
    - LLM

Constraints:
- No codex-ir-core changes.
- Keep JPMS boundaries clean.
- Export product package only if it is intended as public API.
- Keep comments in English.
- Run mvn test from root.

Expected output:
- List changed files.
- Explain ProductDetailExtractor contract.
- Explain supported selectors.
- Explain price parsing limitations.
- Report tests and failures.