Please refactor ProductDetailExtractors to remove the current god-extractor structure and replace manual JSON-LD parsing with Jackson.

Context:
ProductDetailExtractors currently handles too many responsibilities in one inner class:
- WooCommerce CSS extraction
- price parsing
- image extraction
- srcset parsing
- JSON-LD detection
- manual JSON-LD parsing with regex/bracket matching
- CSS + JSON-LD fallback merging

The current behavior is covered by tests and should be preserved, but the implementation is becoming too large and fragile.

Goal:
Split ProductDetailExtractors into smaller internal components and use Jackson for JSON-LD parsing instead of manual regex/string parsing.

Scope:

1. Add Jackson dependency to codex-ir-web only
    - Add jackson-databind to codex-ir-web/pom.xml.
    - Do not add Jackson to codex-ir-core.
    - Update codex-ir-web/module-info.java as needed.
    - Expected JPMS module is likely:
      requires com.fasterxml.jackson.databind;
    - Add any additional Jackson requires only if compilation requires them.

2. Extract price parsing
   Create an internal helper, for example:
   codex.ir.ingestion.crawler.internal.product.ProductPriceParser

   Responsibilities:
    - parse WooCommerce price Element
    - preserve current behavior:
        - strip non-numeric characters except dot/comma
        - comma treated as thousands separator
        - period treated as decimal separator
        - currency symbol extracted from .woocommerce-Price-currencySymbol
    - return Optional<ProductPrice>
    - keep current limitations documented

3. Extract image extraction
   Create an internal helper, for example:
   codex.ir.ingestion.crawler.internal.product.ProductImageExtractor

   Responsibilities:
    - extract product gallery images from WooCommerce selectors
    - resolve image URL priority:
      data-large_image → data-src → largest srcset candidate → src
    - preserve display order
    - resolve relative URLs against page URI
    - return List<ProductImage>

4. Extract JSON-LD Product extraction using Jackson
   Create an internal helper, for example:
   codex.ir.ingestion.crawler.internal.product.JsonLdProductExtractor

   Responsibilities:
    - scan script[type="application/ld+json"]
    - parse JSON using Jackson ObjectMapper
    - support:
        - direct Product object
        - @graph containing Product nodes
        - @type as string
        - @type as array containing Product
        - image as string
        - image as array
        - offers.price
    - ignore malformed JSON-LD gracefully
    - do not throw for unknown JSON structures
    - return a small internal data record, for example JsonLdProductData:
        - String name
        - String sku
        - String description
        - ProductPrice regularPrice
        - List<ProductImage> images

5. Preserve currency behavior
    - JSON-LD offers.priceCurrency usually provides ISO code such as USD, CRC, EUR.
    - Do NOT map priceCurrency into ProductPrice.currencySymbol.
    - JSON-LD fallback prices should continue to create ProductPrice without currencySymbol for now.
    - Do not expand ProductPrice in this task unless absolutely necessary.

6. Keep ProductDetailExtractors as a factory/orchestrator
   ProductDetailExtractors.woocommerceDefault() should remain the public factory.

   The inner WooCommerce extractor should mainly:
    - validate WebPage
    - parse HTML with Jsoup
    - extract CSS fields
    - ask ProductPriceParser, ProductImageExtractor, JsonLdProductExtractor for help
    - merge CSS + JSON-LD fallback values
    - return ProductDetail

7. Preserve behavior
   Existing behavior must remain:
    - CSS values win over JSON-LD.
    - JSON-LD only fills missing fields.
    - salePrice only comes from <ins>.
    - <del> is regular/original price only.
    - non-product or missing-name page returns Optional.empty().
    - malformed JSON-LD is ignored gracefully.
    - null WebPage throws NullPointerException.

8. Tests
    - Keep all existing ProductDetailExtractors tests passing.
    - Add tests if Jackson support improves any cases:
        - JSON-LD with escaped characters in strings.
        - JSON-LD @graph Product node.
        - JSON-LD @type array containing Product.
        - malformed JSON-LD ignored.
    - Add focused tests for helper behavior if useful, but do not over-test private implementation details.

9. Package/API boundaries
    - Product models and extractor interface remain in public product package.
    - New helper classes should be internal and not exported.
    - Do not expose Jackson types in public APIs.
    - Do not change codex-ir-core.

Constraints:
- No ProductCardExtractor yet.
- No category extraction yet.
- No variations/inventory/reviews.
- No Playwright.
- No LLM.
- No dotCMS integration.
- Keep comments in English.
- Preserve JPMS cleanliness.
- Run mvn test from root.

Expected output:
- List changed files.
- Explain new helper responsibilities.
- Explain Jackson dependency and module-info changes.
- Explain CSS vs JSON-LD precedence.
- Confirm manual regex/bracket JSON-LD parsing was removed.
- Report mvn test results.