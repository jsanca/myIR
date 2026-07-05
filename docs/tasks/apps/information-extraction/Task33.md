Task 1: Fix Costa Rican colón price parsing for SYJ product extraction

Goal:
Improve the product price parser/normalizer so myIR correctly parses Costa Rican colón prices from SYJ product cards/details and avoids accidentally mixing SKU/product-name numbers into prices.

Context:
The current SYJ extraction report shows valid prices such as:
- ₡63,590
- ₡145,490
- ₡5,995

But it also shows suspicious/bad parsed values that look like SKU/name numbers got mixed with prices, for example:
- 910052990
- 71981690
- 100127490
- 5.0058
- 5.00539

This task must focus only on price parsing. Do not refactor product models, discovery flow, sitemap runner, card classification, or reporting in this task.

Scope:
1. Review the existing price parsing code:
    - ProductPriceParser
    - ProductPrice
    - ProductCardExtractors
    - ProductDetailExtractors
    - any related tests

2. Implement or improve a dedicated ProductPriceParser / PriceNormalizer.

3. Correctly support Costa Rican colón formats:
    - ₡63.590      -> 63590
    - ₡63,590      -> 63590
    - ₡5.995       -> 5995
    - ₡5,995       -> 5995
    - ₡5.005,80    -> 5005.80
    - ₡5,005.80    -> 5005.80

4. Avoid parsing product codes, SKU fragments, or name numbers as prices:
    - BOT-719
    - ZH-9100
    - 100-annete
    - F-17-08

5. Be conservative:
    - Prefer parsing text that contains a currency symbol such as ₡.
    - Prefer parsing text from known price elements.
    - If uncertain, return Optional.empty() instead of producing a suspicious price.
    - Do not concatenate unrelated numbers from different parts of a card/detail.

6. Preserve current public API where possible.
    - Do not introduce large architecture changes.
    - Do not change ProductDiscoveryCollector.
    - Do not change ProductDiscoverer.
    - Do not add warnings/quality summary yet.

Required tests:
Add or update tests for real SYJ-like cases:

Positive cases:
- "BOT-719" + price text "₡81.690"       -> 81690
- "ZH-9100 Casual Sneaker" + "₡52.990"  -> 52990
- "Crema hidratante" + "₡5.005,80"      -> 5005.80
- "GRASA" + "₡5.005,50"                 -> 5005.50
- "₡63,590"                             -> 63590
- "₡145,490"                            -> 145490
- "₡5,995"                              -> 5995

Negative cases:
- "BOT-719" alone       -> Optional.empty()
- "ZH-9100" alone       -> Optional.empty()
- "100-annete" alone    -> Optional.empty()
- "F-17-08" alone       -> Optional.empty()
- plain product name with no currency/price -> Optional.empty()

Acceptance criteria:
- Valid colón prices are parsed correctly.
- SKU/name fragments are not parsed as prices.
- Existing product extraction behavior remains compatible.
- No model refactor is introduced in this task.
- All tests pass.

Run:
mvn clean test

Report back:
- Files changed
- Parsing rules implemented
- Tests added/updated
- Any remaining suspicious price cases
- Final test summary