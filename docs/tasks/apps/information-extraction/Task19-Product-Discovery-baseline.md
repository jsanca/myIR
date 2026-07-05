Implement Product Discovery baseline.

Goals:

1. Review existing components:
    - WebPage
    - PageClassifier
    - PageClassifiers
    - PageClassification
    - UrlType
    - ProductDetailExtractor / ProductDetailExtractors
    - ProductCardExtractor / ProductCardExtractors
    - ProductDetail
    - ProductCard

2. Introduce a small public product discovery API:
    - Prefer a ProductDiscovery or ProductDiscoverer interface.
    - Prefer a result record such as ProductDiscoveryResult.
    - Keep the API minimal and immutable.

3. ProductDiscoveryResult should include:
    - URI pageUri
    - PageClassification pageClassification
    - Optional<ProductDetail> productDetail
    - List<ProductCard> productCards

4. Add a public factory:
    - ProductDiscoverers.jsoupDefault()
    - Internally compose:
        - PageClassifiers.jsoupDefault()
        - ProductDetailExtractors.jsoupDefault()
        - ProductCardExtractors.jsoupDefault()

5. Discovery behavior:
    - For PRODUCT pages:
        - extract ProductDetail
        - productCards should be empty
    - For CATEGORY pages:
        - extract ProductCard list
        - productDetail should be empty
    - For HOMEPAGE pages:
        - attempt ProductCard extraction
        - productDetail should be empty
    - For ARTICLE, BLOG_POST, ignored/pass-through types, and UNKNOWN:
        - return empty detail and empty cards

6. Keep this as orchestration only:
    - Do not add new extraction heuristics.
    - Do not add crawling.
    - Do not fetch additional pages.
    - Do not add dotCMS export logic.

7. Add tests:
    - PRODUCT page returns ProductDetail.
    - CATEGORY/listing page returns product cards.
    - HOMEPAGE with product cards returns product cards.
    - ARTICLE page returns no product data.
    - UNKNOWN page returns no product data.
    - Empty/malformed HTML does not throw and returns empty result.
    - ProductDiscoveryResult defensively copies card list.
    - Public factory returns a working discoverer.

8. Run mvn clean test and report results.