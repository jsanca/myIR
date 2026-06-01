Implement the first Page Classification baseline.

Goals:

1. Review the current web/package structure and existing classes related to:
    - URL filtering
    - page metadata extraction
    - page classification
    - product-related domain records

2. Introduce or refine the PageClassification model:
    - Keep it as a small public API domain type if it is meant to be consumed by users of the module.
    - Prefer an enum or record-based model only if the current design suggests it.
    - Include at least:
        - PRODUCT_DETAIL
        - PRODUCT_LISTING
        - CATEGORY_PAGE
        - HOME_PAGE
        - ARTICLE
        - UNKNOWN

3. Introduce or refine PageClassifier:
    - Public API should be minimal.
    - Prefer something like:
      PageClassification classify(URI uri, String html, PageMetadata metadata)
    - If a WebPage or fetched document abstraction already exists, use the existing project style instead of introducing a new one.

4. Implement a Jsoup-based classifier internally:
    - Keep implementation classes internal.
    - Expose construction through a factory, for example:
      PageClassifiers.jsoupDefault()
    - Preserve the existing public API style used by PageMetadataExtractors.

5. Classification heuristics:
    - PRODUCT_DETAIL signals:
        - JSON-LD schema.org Product
        - meta property og:type product
        - presence of price-like metadata or selectors
        - add-to-cart / buy button text
        - URL patterns like /product/, /products/, /p/, /item/
    - PRODUCT_LISTING signals:
        - multiple repeated product cards
        - multiple links/images/prices in a grid/list
        - URL patterns like /collections/, /category/, /shop/
    - CATEGORY_PAGE signals:
        - category-like URL patterns
        - heading/category metadata but weak product-card evidence
    - HOME_PAGE signals:
        - root path or very short path
    - ARTICLE signals:
        - article schema
        - og:type article
        - blog/news URL patterns
    - UNKNOWN fallback.

6. Avoid over-engineering:
    - Use simple weighted scoring internally.
    - Do not introduce ML, embeddings, or a rules engine yet.
    - Keep heuristics readable and testable.

7. Add tests:
    - Classifies a JSON-LD Product page as PRODUCT_DETAIL.
    - Classifies a product page with price and add-to-cart button as PRODUCT_DETAIL.
    - Classifies a page with repeated product cards as PRODUCT_LISTING.
    - Classifies a root URL as HOME_PAGE.
    - Classifies an article/blog page as ARTICLE.
    - Falls back to UNKNOWN when signals are weak.
    - Ensure public factories return working classifiers.

8. Run mvn clean test and report results.