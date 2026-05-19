Please clean up the ProductPrice and ProductImage model semantics before we build more extraction logic on top of them.

Scope:

1. ProductPrice
    - Rename currencyCode to currencySymbol if the extractor currently stores symbols such as ₡, $, or €.
    - Keep Optional<String>.
    - Add compact constructor validation:
        - amount must not be null
        - currencySymbol Optional must not be null
        - trim blank currency symbols to Optional.empty()
    - Update ProductDetailExtractors and tests accordingly.

2. ProductImage
    - Add compact constructor validation:
        - url must not be null
        - displayOrder must not be negative
    - Decide whether altText should remain String or become Optional<String>.
    - Preferred simple option: keep String altText, normalize null to empty string and trim it.
    - Update tests accordingly.

3. Preserve behavior
    - Do not change extraction selectors.
    - Do not add JSON-LD extraction yet.
    - Do not add category extraction yet.
    - Do not change crawler/classifier behavior.
    - No codex-ir-core changes.

Expected output:
- List changed files.
- Explain ProductPrice currencySymbol vs currencyCode decision.
- Explain ProductImage altText normalization.
- Run mvn test from root.