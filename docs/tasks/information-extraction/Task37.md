Task 5: Introduce explicit extraction result models

Important:
Only introduce extraction result models in this task. Do not add quality summary/warnings yet, do not change sitemap logic, do not move modules, and do not add dotCMS export logic.

Goal:
Separate full product detail extraction, product card extraction, category extraction, and page-level extraction results so the SYJ report does not force every discovered item into the same “product” shape.

Context:
Current extraction can produce:
- ProductDetail from product pages
- ProductCard from category/listing/home pages
- filtered category/navigation cards via ExtractedCardType

But product cards are not the same as full product details. A card may have only name, URL, price, and image. A product detail may have SKU, brand, description, availability, and multiple images. Category cards should also be represented separately.

Scope:
1. Add extraction-facing records:
    - ProductDetailExtract
    - ProductCardExtract
    - CategoryExtract
    - PageExtractionResult

2. Keep existing public APIs stable where possible:
    - ProductDetail
    - ProductCard
    - ProductDiscoverer
    - ProductDiscoveryResult
    - ProductDiscoveryCollector
    - ProductDiscoveryReport

3. Do not remove or break existing ProductDetail/ProductCard behavior.
   This task may adapt internally or add conversion methods if useful, but should avoid a disruptive migration.

Suggested model:

ProductDetailExtract:
- CanonicalProductKey canonicalKey
- URI url
- String name
- Optional<String> sku
- Optional<String> brand
- Optional<ProductPrice> regularPrice
- Optional<ProductPrice> salePrice
- Optional<String> availability
- Optional<String> shortDescription
- List<ProductImage> images

ProductCardExtract:
- CanonicalProductKey canonicalKey
- URI url
- String name
- Optional<ProductPrice> regularPrice
- Optional<ProductPrice> salePrice
- Optional<ProductImage> thumbnail
- ExtractedCardType cardType

CategoryExtract:
- URI url
- String name
- ExtractedCardType cardType

PageExtractionResult:
- URI pageUri
- PageClassification pageClassification
- Optional<ProductDetailExtract> productDetail
- List<ProductCardExtract> productCards
- List<CategoryExtract> categories

4. Defensive immutability:
- Use Java records.
- Add compact constructors.
- Validate required fields with Objects.requireNonNull.
- Defensively copy lists with List.copyOf.
- Keep Optional components non-null.

5. Placement:
- Since this is still not fully public API, choose package placement carefully.
- If these are intended to be public extraction results soon, place them in the product package.
- If they are still transitional/internal, place them under internal/product.
- Report the choice and why.

6. Integration:
- Prefer a minimal integration.
- Do not rewrite the entire discovery flow unless necessary.
- It is acceptable for this task to introduce the records and add mapper/helper methods from existing ProductDetail/ProductCard.
- Avoid large behavior changes.

Tests:
Add tests for:
- ProductDetailExtract rejects null required fields
- ProductDetailExtract defensively copies images
- ProductCardExtract rejects null required fields
- CategoryExtract rejects null required fields
- PageExtractionResult defensively copies productCards/categories
- Optional fields must not be null
- CanonicalProductKey is included for product detail/card extracts
- CategoryExtract can represent CATEGORY cards separately from ProductCardExtract

Acceptance criteria:
- New extraction result models exist and are tested.
- Product cards, product details, and categories are semantically separated.
- Existing ProductDiscovery/ProductCard/ProductDetail behavior remains green.
- No quality summary/warnings are introduced yet.
- No dotCMS export logic is introduced.
- All tests pass.

Run:
mvn clean test

Report back:
- Files changed
- Package placement decision
- Records introduced
- Tests added/updated
- Whether existing discovery flow was changed or left untouched
- Final test summary