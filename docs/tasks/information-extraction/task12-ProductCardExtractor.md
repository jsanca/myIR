Please implement the first ProductCardExtractor milestone for WooCommerce listing/category pages.

Context:
We now have a stable ProductDetailExtractor with:
- CSS extraction
- JSON-LD fallback via Jackson
- ProductPriceParser
- ProductImageExtractor
- clean internal helpers
- 273 tests passing

The next step is to extract product summaries/cards from WooCommerce category/listing pages.

Goal:
Given a WebPage representing a WooCommerce listing/category/archive page, extract a list of ProductCard records deterministically.

Scope:

1. Add ProductCard record
   Suggested public package:
   codex.ir.ingestion.crawler.product

   Suggested fields:
    - URI url
    - String name
    - Optional<ProductPrice> regularPrice
    - Optional<ProductPrice> salePrice
    - Optional<ProductImage> thumbnail

   Add compact constructor validation:
    - url must not be null
    - name must not be null or blank
    - Optional fields must not be null
    - normalize name by trimming

2. Add ProductCardExtractor interface
   Suggested:
   @FunctionalInterface
   public interface ProductCardExtractor {
   List<ProductCard> extract(WebPage page);
   }

3. Add ProductCardExtractors factory
   Suggested:
   ProductCardExtractors.woocommerceDefault()

4. Use existing helpers where possible
    - Reuse ProductPriceParser for card prices.
    - Reuse image URL/srcset logic if possible.
    - If ProductImageExtractor is too detail-page-specific, extract reusable image resolution logic into a smaller internal helper rather than duplicating.

5. WooCommerce card selectors
   Product card containers:
    - li.product
    - .product.type-product
    - .products .product

   Product URL:
    - a.woocommerce-LoopProduct-link
    - a.woocommerce-loop-product__link
    - first anchor inside card that has href

   Name:
    - .woocommerce-loop-product__title
    - h2
    - h3
    - product link text fallback

   Regular price:
    - .price del .woocommerce-Price-amount
    - fallback: .price .woocommerce-Price-amount

   Sale price:
    - .price ins .woocommerce-Price-amount only

   Thumbnail:
    - img[data-src]
    - largest srcset
    - img[src]
    - resolve relative URLs against WebPage URL
    - alt text if present
    - displayOrder should reflect card position or image position, whichever is simpler and documented

6. Behavior
    - Return List.of() for null/blank HTML.
    - Throw NullPointerException for null WebPage if consistent with ProductDetailExtractor.
    - Skip cards without a usable URL.
    - Skip cards without a usable name.
    - Preserve card order.
    - Return empty list for non-listing HTML.

7. Tests
   Use local HTML fixtures or inline fixtures.

   Add tests for:
    - extract multiple cards from a WooCommerce listing page
    - extract product URLs
    - extract product names
    - extract regular price
    - extract sale price from <ins>
    - regular price from <del> when sale exists
    - no sale price when only normal price exists
    - extract thumbnail from data-src
    - extract thumbnail from srcset when data-src is missing
    - skip incomplete cards
    - return empty list for non-listing HTML
    - null WebPage throws NPE

8. Keep scope limited
   Do not implement:
    - pagination crawling
    - multi-page category traversal
    - ProductDetail fetching from ProductCard URLs
    - ProductProjection
    - myIR indexing
    - dotCMS integration
    - Playwright
    - LLM

Constraints:
- No codex-ir-core changes.
- Keep product public API clean.
- Keep helper classes internal.
- Do not expose Jackson types in public APIs.
- Preserve all existing tests.
- Keep comments in English.
- Run mvn test from root.

Expected output:
- List changed files.
- Explain ProductCardExtractor contract.
- Explain selectors and limitations.
- Explain reused helpers.
  - Report test counts and failures.