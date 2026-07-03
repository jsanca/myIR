Task 6: Add extraction quality warnings and summary

Important:
Only add extraction quality warnings and summary in this task.
Do not change sitemap logic.
Do not move modules.
Do not add dotCMS export logic.
Do not refactor public APIs unless strictly necessary.
Do not add new extraction heuristics unless needed for warning calculation.

Goal:
Make the SYJ product discovery/extraction report measurable by adding warnings and summary counts over the new extraction result models.

Context:
We now have internal extraction models:
- ProductDetailExtract
- ProductCardExtract
- CategoryExtract
- PageExtractionResult

We need a way to measure extraction quality and understand problems such as suspicious prices, missing images, missing SKU, navigation cards, and products only discovered from cards.

Scope:
1. Add an extraction warning enum, for example:

   ExtractionWarning {
   PRICE_LOOKS_SUSPICIOUS,
   MISSING_SKU,
   MISSING_IMAGE,
   MISSING_DESCRIPTION,
   CARD_CLASSIFIED_AS_NAVIGATION,
   UNKNOWN_CARD_TYPE
   }

2. Add warnings to extraction result models where appropriate:
    - ProductDetailExtract
    - ProductCardExtract
    - CategoryExtract
    - PageExtractionResult if useful

3. Preserve immutability:
    - warning lists should be List.copyOf(...)
    - no null Optional or null List components
    - records should keep compact constructors

4. Add a summary record, for example:
    - ExtractionQualitySummary
      or:
    - ProductDiscoverySummary

5. Summary should include:
    - pagesProcessed
    - productDetailPages
    - categoryPages
    - productCardsFound
    - categoryCardsFound
    - navigationCardsIgnored
    - uniqueProductUrls
    - productsWithDetail
    - productsOnlyFromCards
    - priceParseWarnings
    - missingImageWarnings
    - missingSkuWarnings
    - missingDescriptionWarnings

6. Summary calculation:
    - Prefer a small internal summarizer/helper.
    - It should calculate summary from PageExtractionResult list if possible.
    - Use CanonicalProductKey to count uniqueProductUrls.
    - productsWithDetail should count unique canonical keys with detail.
    - productsOnlyFromCards should count unique canonical keys found in cards but not details.

7. Warning rules:
   ProductDetailExtract:
    - missing SKU -> MISSING_SKU
    - no images -> MISSING_IMAGE
    - no shortDescription -> MISSING_DESCRIPTION
    - suspicious price, if detectable -> PRICE_LOOKS_SUSPICIOUS

   ProductCardExtract:
    - no thumbnail -> MISSING_IMAGE
    - suspicious price, if detectable -> PRICE_LOOKS_SUSPICIOUS
    - UNKNOWN card type -> UNKNOWN_CARD_TYPE

   CategoryExtract:
    - NAVIGATION card type -> CARD_CLASSIFIED_AS_NAVIGATION
    - UNKNOWN card type -> UNKNOWN_CARD_TYPE

8. Keep behavior conservative:
    - Do not fail extraction because of warnings.
    - Warnings are diagnostic only.
    - Existing product extraction and discovery tests must remain green.

9. Runner/report output:
   If the current DiscoveryRunner can easily print the summary, add a final section like:

   --- Quality Summary ---
   Pages processed       : 67
   Product detail pages  : 25
   Category pages        : 42
   Product cards found   : 608
   Unique product URLs   : 312
   Products with detail  : 25
   Products only in cards: 287
   Price warnings        : 0
   Missing image warnings: 0

   If wiring into the runner is too invasive, keep summary calculation tested internally and report that runner wiring is left for the next task.

Tests:
Add tests for:
- ExtractionWarning enum usage
- ProductDetailExtract warning list is immutable
- ProductCardExtract warning list is immutable
- CategoryExtract warning list is immutable
- summary counts pagesProcessed
- summary counts productDetailPages
- summary counts categoryPages
- summary counts productCardsFound
- summary counts categoryCardsFound
- summary counts navigationCardsIgnored
- summary counts uniqueProductUrls
- summary counts productsWithDetail
- summary counts productsOnlyFromCards
- summary counts missing image warnings
- summary counts missing SKU warnings
- summary handles empty input

Acceptance criteria:
- Warnings can be attached to extraction result models.
- Summary can be computed from extraction/page results.
- Unique product counting uses CanonicalProductKey.
- Existing public APIs stay stable.
- No sitemap/export/module changes are introduced.
- All tests pass.

Run:
mvn clean test

Report back:
- Files changed
- Records/enums/helpers added
- Warning rules implemented
- Summary fields implemented
- Whether DiscoveryRunner output was updated
- Tests added/updated
- Final test summary