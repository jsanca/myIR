Implement the first Product Card Extraction baseline.

Goals:

1. Review the existing product-related domain records:
    - ProductCard
    - ProductPrice
    - ProductImage
    - ProductDetail
    - PageClassification
    - UrlType

2. Introduce or refine a ProductCardExtractor public API:
    - Keep the public API minimal.
    - Prefer something like:
      List<ProductCard> extract(URI pageUri, String html)
    - If PageMetadata is useful for fallback, include it only if consistent with existing project style.

3. Add a public factory:
    - ProductCardExtractors.jsoupDefault()
    - Keep the Jsoup implementation internal.

4. Extraction strategy:
    - Detect repeated product-card-like containers.
    - Extract:
        - product title
        - product URL
        - image candidate
        - price candidate if visible
    - Resolve relative product URLs against the page URI.
    - Avoid duplicates by canonical/resolved URL when possible.

5. Heuristics:
    - Prefer anchors whose href looks product-like.
    - Prefer containers/classes containing words like:
        - product
        - card
        - item
        - grid
        - collection
        - woocommerce
    - Look for title text in:
        - anchor text
        - h2/h3/h4
        - img alt
    - Look for image in:
        - img src
        - img data-src
        - img srcset first usable candidate
    - Look for price-like text in:
        - .price
        - [class*=price]
        - visible text with currency/decimal pattern

6. Avoid over-engineering:
    - No site-specific SYJ logic yet.
    - No ML.
    - No crawling orchestration changes yet.
    - Keep the extractor readable and testable.

7. Add tests:
    - Extracts product cards from a simple product grid.
    - Resolves relative product URLs.
    - Extracts title from anchor text.
    - Extracts title from heading when anchor text is weak.
    - Extracts title from image alt as fallback.
    - Extracts image from img src.
    - Extracts image from data-src.
    - Extracts price from .price.
    - Deduplicates repeated product URLs.
    - Returns an empty list when no product cards are found.
    - Public factory returns a working extractor.

8. Run mvn clean test and report results.