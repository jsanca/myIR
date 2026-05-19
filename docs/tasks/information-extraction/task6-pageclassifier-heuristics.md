Please tighten PageClassifiers homepage and category classification heuristics.

Issues:
1. isHomePage currently treats any single-segment path as HOMEPAGE. That can misclassify /contact, /about, /shop, etc.
2. Category detection can classify homepages with WooCommerce product loops as CATEGORY.
3. refineType receives wpDetected and wcDetected but does not use them.
4. urlClassifier should be null-checked.

Scope:
- Make homepage detection strict:
    - URL type HOMEPAGE
    - root path /
    - body.home
    - do not classify arbitrary single-segment paths as homepage
- Change precedence to:
  PRODUCT > HOMEPAGE > CATEGORY > original URL type
- Either remove unused wpDetected/wcDetected parameters from refineType or use wcDetected to guard weaker WooCommerce category signals.
- Add tests:
    - /contact with generic HTML is not HOMEPAGE
    - /about with generic HTML is not HOMEPAGE
    - root path / is HOMEPAGE
    - body.home is HOMEPAGE
    - homepage with WooCommerce product loop remains HOMEPAGE, not CATEGORY
    - category page with product loop remains CATEGORY
- Add null validation for urlClassifier.

Constraints:
- Do not implement product extraction.
- Do not add JSON-LD parsing beyond current simple detection.
- Do not change UrlClassifier/UrlFilter behavior unless required.
- Keep comments in English.
- Run mvn test from root.