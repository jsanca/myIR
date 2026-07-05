Implement the first Product Detail Extraction baseline.

Goals:

1. Review the existing product-related domain records:
    - ProductDetail
    - ProductPrice
    - ProductImage
    - ProductCard
    - ClassifiedUrl
    - PageClassification / UrlType
    - PageMetadata

2. Introduce or refine a ProductDetailExtractor public API:
    - Keep the public API minimal.
    - Prefer a method such as:
      Optional<ProductDetail> extract(URI uri, String html, PageMetadata metadata)
    - If an existing WebPage abstraction exists, follow the current project style instead.

3. Add a public factory:
    - ProductDetailExtractors.jsoupDefault()
    - Keep the Jsoup implementation internal.

4. Extraction priority:
    - First try JSON-LD schema.org/Product.
    - Then try OpenGraph/meta tags.
    - Then try common HTML selectors.
    - Finally use PageMetadata fallback for title/description/canonical URL where appropriate.

5. JSON-LD Product support:
   Extract when present:
    - name
    - description
    - sku
    - brand
    - image / images
    - offers.price
    - offers.priceCurrency
    - offers.availability
    - url

6. OpenGraph/meta fallback:
   Extract when present:
    - og:title
    - og:description
    - og:image
    - og:url
    - product:price:amount
    - product:price:currency
    - product:availability

7. HTML fallback:
   Use simple readable selectors for:
    - product title
    - price-like elements
    - main image candidates
    - availability text
    - SKU-like text if obvious

8. Avoid over-engineering:
    - Do not introduce a full rule engine.
    - Do not add site-specific SYJ logic yet.
    - Do not use ML/embeddings.
    - Keep extraction contributors internal only if they simplify the implementation.

9. Add tests:
    - Extracts ProductDetail from JSON-LD Product.
    - Extracts ProductDetail from JSON-LD Product with offers as an object.
    - Extracts ProductDetail from JSON-LD Product with offers as an array.
    - Extracts image when JSON-LD image is a string.
    - Extracts images when JSON-LD image is an array.
    - Extracts price and currency from OpenGraph product meta tags.
    - Falls back to PageMetadata title/description when product-specific fields are missing.
    - Returns Optional.empty() when no product signals are present.
    - Public factory returns a working extractor.

10. Run mvn clean test and report results.