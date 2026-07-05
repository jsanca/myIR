Task 3: Add CanonicalProductKey for product URL identity normalization

Important:
Only implement product URL key normalization in this task. Do not refactor extraction models, do not change card classification, do not change sitemap logic, and do not add quality reporting yet.

Goal:
Create a stable canonical key for grouping product cards/details that refer to the same product, even when URLs differ by trailing slash, fragments, or irrelevant query parameters.

Context:
The same product can appear in multiple categories or listing pages. That is normal and should not be treated as an extraction error. We need a stable product identity key so future reports can group product cards and details by canonical product URL.

Examples that should map to the same logical key:

- https://syjleathers.com/producto/sole-wallet/
- https://syjleathers.com/producto/sole-wallet
- https://syjleathers.com/producto/sole-wallet/?foo=bar
- https://syjleathers.com/producto/sole-wallet/#categories

Expected canonical key:

- /producto/sole-wallet

Scope:
1. Add a small immutable value object:
    - CanonicalProductKey

2. Provide a factory method:
    - CanonicalProductKey.fromUrl(URI url)

3. Keep it product-focused.
    - This is not the global crawler URI canonicalizer.
    - Do not change current crawling canonicalization rules.
    - Do not change UrlCanonicalizer unless absolutely necessary.

4. Normalization rules:
    - require non-null URI
    - remove fragments
    - ignore query parameters by default for product identity
    - normalize trailing slash differences
    - preserve meaningful path
    - lowercase scheme/host only if stored
    - for same-domain product identity, prefer the normalized path as the key

5. Do not do:
    - DNS lookup
    - host alias detection
    - site-wide canonicalization refactor
    - sorting query parameters for crawler canonicalization
    - cross-domain equivalence assumptions

6. Suggested API:

   public record CanonicalProductKey(String value) {
   public CanonicalProductKey {
   Objects.requireNonNull(value, "value must not be null");
   if (value.isBlank()) {
   throw new IllegalArgumentException("value must not be blank");
   }
   }

       public static CanonicalProductKey fromUrl(URI url) {
           ...
       }
   }

7. Placement:
    - Prefer product package if it is part of the product extraction public model.
    - Prefer internal/product if it is only used internally for now.
    - Choose based on current package style, but avoid leaking unnecessary implementation details.

Tests:
Add tests covering:

1. Trailing slash normalization:
    - https://syjleathers.com/producto/sole-wallet/
    - https://syjleathers.com/producto/sole-wallet
      both produce:
    - /producto/sole-wallet

2. Fragment removal:
    - https://syjleathers.com/producto/sole-wallet/#categories
      produces:
    - /producto/sole-wallet

3. Query removal:
    - https://syjleathers.com/producto/sole-wallet/?foo=bar
      produces:
    - /producto/sole-wallet

4. Different product slugs remain different:
    - /producto/sole-wallet
    - /producto/another-wallet

5. Root/blank path handling:
    - define expected conservative behavior and test it

6. Null URL:
    - throws NullPointerException or follows current project style

Acceptance criteria:
- CanonicalProductKey can group product URLs that differ only by trailing slash, fragment, or irrelevant query parameters.
- It does not affect crawler URL canonicalization.
- It does not change extraction behavior yet unless there is an obvious safe place to expose the key.
- All tests pass.

Run:
mvn clean test

Report back:
- Files changed
- Package chosen and why
- Normalization rules implemented
- Tests added
- Any edge cases intentionally left for later
- Final test summary