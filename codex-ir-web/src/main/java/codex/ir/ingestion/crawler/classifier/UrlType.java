package codex.ir.ingestion.crawler.classifier;

/**
 * Broad page types used for URL-level classification.
 *
 * <p>Classification is based on URL patterns only — no HTML fetching.
 * A more detailed classification may be layered on top later
 * (e.g. HTML-based detection of WooCommerce signals).</p>
 */
public enum UrlType {

    /** Site root or empty path. */
    HOMEPAGE,

    /** Product detail page (e.g. /product/, /producto/, /shop/). */
    PRODUCT,

    /** Product category or listing page (e.g. /product-category/). */
    CATEGORY,

    /** Generic content page (e.g. /about/, /contact/, /blog/). */
    PAGE,

    /** Blog post (e.g. /blog/2024/01/15/some-post). */
    BLOG_POST,

    /** Search results page. */
    SEARCH,

    /** Shopping cart page. */
    CART,

    /** Checkout page. */
    CHECKOUT,

    /** Customer account area. */
    ACCOUNT,

    /** Admin dashboard or login. */
    ADMIN,

    /** RSS/Atom feed. */
    FEED,

    /** Static asset (image, CSS, JS, PDF, etc.). */
    ASSET,

    /** Explicitly ignored URL (transactional, filtered, etc.). */
    IGNORED,

    /** Cannot be classified from URL pattern alone. */
    UNKNOWN
}
