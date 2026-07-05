Please tighten ProductDetailExtractors before adding JSON-LD extraction.

Issues found:
1. extractSalePrice currently falls back to parsing <del> price when <ins> is missing. In WooCommerce, <del> usually represents the original/regular price, not the sale price. Sale price should only come from <ins> or another explicit sale-price signal.
2. extractImages has a redundant fallback: the fallback selector repeats part of the original selector.
3. resolveImageUrl does not yet support srcset, although the earlier design mentioned it.
4. extract(WebPage page) should validate page is not null.

Scope:
- Change extractSalePrice so it does not return <del> as sale price.
- Keep extractRegularPrice using <del> as regular/original price.
- Simplify image selector fallback or add a real fallback if appropriate.
- Add srcset support if small:
  data-large_image -> data-src -> largest srcset candidate -> src
- Add Objects.requireNonNull(page, "page must not be null") in extract.
- Add/update tests:
    - product with <del> only should not produce salePrice
    - product with <del> and <ins> should produce regularPrice from <del> and salePrice from <ins>
    - srcset image chooses largest candidate if no data-large_image/data-src
    - null page behavior if project convention expects NullPointerException

Constraints:
- Do not add JSON-LD extraction yet.
- Do not add variations.
- Do not add category extraction.
- Do not change crawler/classifier behavior.
- No codex-ir-core changes.
- Keep comments in English.
- Run mvn test from root.