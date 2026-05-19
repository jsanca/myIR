Please apply a small cleanup pass to the product extraction helpers.

Scope:
1. Add Objects.requireNonNull validations:
    - ProductPriceParser.parse(priceElement)
    - ProductImageExtractor.extract(doc, baseUri)
    - JsonLdProductExtractor.extract(doc, baseUri)

2. Remove unused imports from JsonLdProductExtractor.

3. Update JsonLdProductExtractor.textValue(...) so blank strings return null.

4. Add tests if needed:
    - blank JSON-LD name should not satisfy missing CSS name
    - helper null validation if consistent with project style

Constraints:
- No behavior change except blank JSON-LD fields being treated as missing.
- No ProductCardExtractor yet.
- No new dependencies.
- Run mvn test.