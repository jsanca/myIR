package codex.ir.ingestion.crawler.classifier;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageClassifiersTest {

    private static final Instant NOW = Instant.now();
    private final PageClassifier classifier =
            PageClassifiers.wordpressWooCommerceDefault(UrlClassifiers.wordpressWooCommerceDefaultWeb());

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "", "", 200, "text/html", NOW, Map.of());
    }

    @Test
    void shouldClassifyProductPageByBodyClass() {
        final String html = """
                <html><body class="single-product">
                <h1 class="product_title">Red Shoes</h1>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/shop/red-shoes/", html));

        assertEquals(UrlType.PRODUCT, result.type());
        assertTrue(result.wooCommerceDetected());
    }

    @Test
    void shouldClassifyProductPageByProductTitle() {
        final String html = """
                <html><body>
                <h1 class="product_title">Blue Jeans</h1>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/page/about/", html));

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void shouldClassifyProductPageByAddToCartButton() {
        final String html = """
                <html><body>
                <button class="single_add_to_cart_button">Add to cart</button>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/page/about/", html));

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void shouldClassifyProductPageByJsonLd() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {"@type": "Product", "name": "Test Product"}
                </script>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/page/about/", html));

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void shouldClassifyCategoryPageByProductLoop() {
        final String html = """
                <html><body class="woocommerce">
                <ul class="products columns-3">
                    <li class="product"><h2 class="woocommerce-loop-product__title">Item 1</h2></li>
                    <li class="product"><h2 class="woocommerce-loop-product__title">Item 2</h2></li>
                </ul>
                <nav class="woocommerce-pagination"></nav>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/page/about/", html));

        assertEquals(UrlType.CATEGORY, result.type());
    }

    @Test
    void shouldClassifyCategoryPageByTaxProductCat() {
        final String html = """
                <html><body class="tax-product_cat">
                <ul class="products"><li class="product">Item</li></ul>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/page/about/", html));

        assertEquals(UrlType.CATEGORY, result.type());
    }

    @Test
    void shouldDetectWordPressByGeneratorMeta() {
        final String html = """
                <html><head>
                <meta name="generator" content="WordPress 6.5"/>
                </head><body></body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/page/about/", html));

        assertTrue(result.wordpressDetected());
    }

    @Test
    void shouldDetectWordPressByApiLink() {
        final String html = """
                <html><head>
                <link rel="https://api.w.org/" href="https://example.com/wp-json/"/>
                </head><body></body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/page/about/", html));

        assertTrue(result.wordpressDetected());
    }

    @Test
    void shouldDetectWordPressByWpContentAsset() {
        final String html = """
                <html><head>
                <link rel="stylesheet" href="https://example.com/wp-content/themes/mytheme/style.css"/>
                </head><body></body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/page/about/", html));

        assertTrue(result.wordpressDetected());
    }

    @Test
    void shouldDetectWooCommerceByBodyClass() {
        final String html = """
                <html><body class="woocommerce">
                <p>Content</p>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/page/about/", html));

        assertTrue(result.wooCommerceDetected());
    }

    @Test
    void shouldDetectWooCommerceByPriceElement() {
        final String html = """
                <html><body>
                <span class="woocommerce-Price-amount"><bdi>$19.99</bdi></span>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/page/about/", html));

        assertTrue(result.wooCommerceDetected());
    }

    @Test
    void shouldKeepIgnoredUrlTypeEvenWithProductHtml() {
        final String html = """
                <html><body class="single-product">
                <h1 class="product_title">Product</h1>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/wp-admin/", html));

        assertEquals(UrlType.ADMIN, result.type());
        assertFalse(result.wordpressDetected());
        assertFalse(result.wooCommerceDetected());
    }

    @Test
    void shouldReturnUnknownForPlainPage() {
        final String html = """
                <html><body>
                <p>Just a plain page with no CMS signals.</p>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/page/about/", html));

        assertEquals(UrlType.UNKNOWN, result.type());
        assertFalse(result.wordpressDetected());
        assertFalse(result.wooCommerceDetected());
    }

    @Test
    void shouldClassifyHomePageByUrl() {
        final String html = """
                <html><body><p>Welcome</p></body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/", html));

        assertEquals(UrlType.HOMEPAGE, result.type());
    }

    @Test
    void shouldClassifyHomePageByBodyClass() {
        final String html = """
                <html><body class="home">
                <p>Welcome to our site</p>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/about/", html));

        assertEquals(UrlType.HOMEPAGE, result.type());
    }

    @Test
    void shouldNotClassifyContactAsHomePage() {
        final String html = """
                <html><body>
                <p>Contact us at info@example.com</p>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/contact/", html));

        assertEquals(UrlType.UNKNOWN, result.type(),
                "Arbitrary single-segment paths must not be classified as HOMEPAGE");
    }

    @Test
    void shouldNotClassifyAboutAsHomePage() {
        final String html = """
                <html><body>
                <p>About our company</p>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/about/", html));

        assertEquals(UrlType.UNKNOWN, result.type(),
                "Arbitrary single-segment paths must not be classified as HOMEPAGE");
    }

    @Test
    void shouldNotClassifyShopAsHomePage() {
        final String html = """
                <html><body>
                <p>Our shop</p>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/shop/", html));

        assertEquals(UrlType.PRODUCT, result.type(),
                "Shop URL path is already classified as PRODUCT by URL classifier");
    }

    @Test
    void homePageWithWooCommerceProductLoopShouldRemainHomePage() {
        final String html = """
                <html><body class="home">
                <ul class="products columns-3">
                    <li class="product"><h2 class="woocommerce-loop-product__title">Item 1</h2></li>
                </ul>
                <nav class="woocommerce-pagination"></nav>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/", html));

        assertEquals(UrlType.HOMEPAGE, result.type(),
                "Homepage with WooCommerce product loops must not be classified as CATEGORY");
    }

    @Test
    void categoryPageWithProductLoopShouldRemainCategory() {
        final String html = """
                <html><body class="archive woocommerce tax-product_cat">
                <ul class="products columns-3">
                    <li class="product"><h2 class="woocommerce-loop-product__title">Item 1</h2></li>
                </ul>
                <nav class="woocommerce-pagination"></nav>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/page/about/", html));

        assertEquals(UrlType.CATEGORY, result.type(),
                "Category body class must take precedence and keep CATEGORY type");
    }

    @Test
    void shouldHandleBlankHtml() {
        final PageClassification result = classifier.classify(
                page("https://example.com/product/shoes/", ""));

        assertEquals(UrlType.PRODUCT, result.type(),
                "URL classification should be used when HTML is blank");
    }

    @Test
    void shouldHandleNullHtml() {
        final PageClassification result = classifier.classify(
                new WebPage(URI.create("https://example.com/product/shoes/"),
                        null, "", "", 200, "text/html", NOW, Map.of()));

        assertEquals(UrlType.PRODUCT, result.type(),
                "URL classification should be used when HTML is null");
    }

    @Test
    void shouldKeepCartUrlAsCart() {
        final String html = """
                <html><body class="woocommerce">
                <h1 class="product_title">Cart</h1>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/cart/", html));

        assertEquals(UrlType.CART, result.type());
    }

    @Test
    void shouldKeepProductUrlAsProduct() {
        final String html = """
                <html><body>
                <h1>Normal product page without WooCommerce CSS classes</h1>
                </body></html>""";

        final PageClassification result = classifier.classify(
                page("https://example.com/product/shoes/", html));

        assertEquals(UrlType.PRODUCT, result.type(),
                "URL-based product classification should be kept when HTML has no contradicting signals");
    }
}
