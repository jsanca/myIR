Task 2: Add controlled HTML entity decoding for extracted product/category text

Goal:
Normalize extracted text values so HTML entities and double-encoded HTML entities do not appear in the product discovery report.

Context:
In the SYJ extraction report we saw values like:
- Menú S&amp;amp;J
- Corporativo &amp;gt; Ejecutivo

These should be normalized to:
- Menú S&J
- Corporativo > Ejecutivo

Scope:
This task must focus only on text normalization/HTML entity decoding.
Do not refactor extraction models.
Do not change ProductDiscoverer.
Do not change ProductDiscoveryCollector.
Do not change sitemap runner.
Do not change card classification.
Do not add quality warnings yet.

Requirements:
1. Add an internal text normalization helper, for example:
    - HtmlTextDecoder
    - ExtractedTextNormalizer
    - ProductTextNormalizer

2. Decode HTML entities in extracted text fields:
    - product names
    - product short descriptions
    - category/card names if currently extracted
    - image alt text

3. Support double-encoded values when they appear:
    - S&amp;amp;J -> S&J
    - &amp;gt; -> >
    - Corporativo &amp;gt; Ejecutivo -> Corporativo > Ejecutivo

4. Trim surrounding whitespace after decoding.

5. Keep normalization controlled:
    - Do not remove meaningful punctuation.
    - Do not lowercase names.
    - Do not alter accents.
    - Do not introduce slug normalization here.
    - Only decode entities and trim text.

6. Apply the helper only at extraction boundaries, where text enters ProductDetail, ProductCard, ProductImage alt text, or related product/category text.

Tests:
Add tests for the normalizer:
- "Menú S&amp;amp;J" -> "Menú S&J"
- "Corporativo &amp;gt; Ejecutivo" -> "Corporativo > Ejecutivo"
- "Bolso &amp; Carteras" -> "Bolso & Carteras"
- "S&J" -> "S&J"
- "Texto normal" -> "Texto normal"
- blank/null handling according to current project style

Add/update extraction tests where practical:
- product name is decoded
- product short description is decoded
- product image alt text is decoded
- product card name is decoded

Acceptance criteria:
- Extracted product/card text no longer contains visible double-encoded entities like &amp;amp; or &amp;gt;.
- Existing extraction behavior remains compatible.
- No model refactor is introduced.
- All tests pass.

Run:
mvn clean test

Report back:
- Files changed
- Helper added
- Fields where decoding is applied
- Tests added/updated
- Final test summary