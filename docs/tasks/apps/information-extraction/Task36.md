Task 4: Classify extracted cards as PRODUCT, CATEGORY, NAVIGATION, or UNKNOWN

Important:
Only implement extracted card classification in this task. Do not refactor extraction models yet, do not add quality summaries/warnings, do not change sitemap logic, and do not move modules.

Goal:
Prevent category/navigation cards from being treated as product cards in the SYJ product discovery report.

Context:
The current product card extraction can pick up cards/links that are not real products. In the SYJ report, some category/navigation cards appear together with actual products, such as:

- Categorías
- Volver
- Bolsos
- Accesorios
- Ver todos
- #categories

These should not be counted as product cards.

Scope:
1. Add an extracted card classifier.
2. Keep existing ProductCard API stable if possible.
3. Do not introduce ProductCardExtract/ProductDetailExtract models yet.
4. Do not add quality summary or warnings yet.
5. Do not change ProductDiscoverer or ProductDiscoveryCollector unless required to filter navigation cards safely.

Requirements:
1. Add enum:

   ExtractedCardType {
   PRODUCT,
   CATEGORY,
   NAVIGATION,
   UNKNOWN
   }

2. Add an internal classifier, for example:
    - ExtractedCardClassifier
    - ProductCardClassifier
    - CardTypeClassifier

3. Initial classification rules:

   PRODUCT:
    - URL path contains /producto/
    - URL path contains /product/

   CATEGORY:
    - URL path contains /category-producto/
    - URL path contains /product-category/
    - URL path contains /categoria-producto/
    - URL path contains /product_cat/ if applicable

   NAVIGATION:
    - href is #categories
    - href starts with #
    - label/text equals or contains:
        - Volver
        - Categorías
        - Ver todos
    - obvious non-product navigation links

   UNKNOWN:
    - anything that cannot be confidently classified

4. Product card extraction behavior:
    - PRODUCT cards may be returned as ProductCard.
    - NAVIGATION cards must not be returned as ProductCard.
    - CATEGORY cards should not be returned as ProductCard for now.
    - UNKNOWN cards should be handled conservatively:
        - either skip them
        - or include only if existing behavior depends on broad extraction
    - Prefer conservative behavior for SYJ validation.

5. Keep CanonicalProductKey available for later grouping, but do not wire it deeply into report models yet unless there is a small obvious use.

6. Do not add site-specific hardcoded SYJ class names yet unless absolutely necessary.
    - Prefer URL/text-based rules.

Tests:
Add tests for the classifier:
- /producto/sole-wallet -> PRODUCT
- /product/sole-wallet -> PRODUCT
- /category-producto/bolsos -> CATEGORY
- /product-category/bags -> CATEGORY
- #categories -> NAVIGATION
- #anything -> NAVIGATION
- "Volver" -> NAVIGATION
- "Categorías" -> NAVIGATION
- "Ver todos" -> NAVIGATION
- unknown external/info link -> UNKNOWN

Add/update ProductCardExtractor tests:
- navigation card is not returned as ProductCard
- category card is not returned as ProductCard
- product card is still returned
- product cards continue to deduplicate by URL
- existing valid product card tests remain green

Acceptance criteria:
- Category/navigation cards no longer appear in productCards.
- Real product cards are still extracted.
- Existing public APIs remain stable where possible.
- No extraction model refactor is introduced yet.
- All tests pass.

Run:
mvn clean test

Report back:
- Files changed
- Enum/classifier added
- Classification rules implemented
- ProductCardExtractor behavior changes
- Tests added/updated
- Any ambiguous UNKNOWN behavior left for later
- Final test summary