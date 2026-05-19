Please add basic JSON-LD Product fallback extraction to ProductDetailExtractors.

Context:
ProductDetailExtractor v1 currently extracts product details from WooCommerce CSS selectors.
It supports:
- name
- SKU
- regular price
- sale price
- currency symbol
- short description
- images including srcset
- Optional.empty() for non-product/missing-name pages

Goal:
Add simple deterministic JSON-LD Product fallback extraction for missing fields, without building a full JSON-LD framework yet.

Scope:

1. Detect JSON-LD Product nodes
    - Look at script[type="application/ld+json"].
    - Support a direct object with "@type": "Product".
    - Support "@graph" arrays containing Product nodes if feasible.
    - Support "@type" as either a string or an array.
    - Keep implementation small and deterministic.

2. Extract fallback fields from JSON-LD Product
   Use JSON-LD only when CSS extraction is missing or empty.

   Fields:
    - name
    - sku
    - description as shortDescription fallback
    - image or image[] as ProductImage fallback
    - offers.price as regularPrice fallback
    - offers.priceCurrency as currencyCode or currency-like metadata if model supports it

3. Currency handling
   Current ProductPrice stores currencySymbol, not ISO currencyCode.
   Since JSON-LD usually provides ISO code such as "USD" or "CRC", do not force it into currencySymbol incorrectly.
   Options:
    - leave currencySymbol empty for JSON-LD prices for now
    - or add a separate Optional<String> currencyCode only if the design remains small
      Prefer not to expand ProductPrice unless necessary.

4. Keep CSS behavior unchanged
    - Existing CSS extraction should keep passing.
    - JSON-LD should fill gaps, not override already extracted CSS values.
    - Do not change sale-price behavior yet.

5. Tests
   Add local HTML fixtures or inline HTML tests for:
    - direct JSON-LD Product with name and sku
    - JSON-LD Product inside @graph
    - @type as array containing Product
    - JSON-LD image as string
    - JSON-LD image as array
    - JSON-LD offers.price fallback
    - CSS values win over JSON-LD when both exist
    - malformed JSON-LD is ignored gracefully

6. Constraints
    - Do not add a full JSON-LD abstraction yet.
    - Do not implement variations.
    - Do not implement inventory.
    - Do not implement category extraction yet.
    - Do not add Playwright or LLM.
    - No codex-ir-core changes unless absolutely necessary.
    - Keep comments in English.
    - Run mvn test from root.

Expected output:
- List changed files.
- Explain JSON-LD support and limitations.
- Explain CSS-vs-JSON-LD precedence.
- Explain currency handling decision.
- Report test counts and failures.