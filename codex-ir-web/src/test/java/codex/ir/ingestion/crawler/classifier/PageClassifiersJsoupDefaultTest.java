package codex.ir.ingestion.crawler.classifier;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PageClassifiersJsoupDefaultTest {

    private static final Instant NOW = Instant.now();
    private final PageClassifier classifier = PageClassifiers.jsoupDefault();

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "", "", 200, "text/html", NOW, Map.of());
    }

    @Test
    void jsoupDefaultShouldReturnNonNullClassifier() {
        assertNotNull(classifier);
    }

    @Test
    void shouldClassifyJsonLdProductPageAsProduct() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"Product","name":"Blue Widget","price":"19.99"}
                </script>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/item/blue-widget", html));

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void shouldClassifyOgTypeProductPageAsProduct() {
        final String html = """
                <html><head>
                <meta property="og:type" content="product"/>
                </head><body><p>A product page</p></body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/items/blue-widget", html));

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void shouldClassifyPriceAndAddToCartButtonPageAsProduct() {
        final String html = """
                <html><body>
                <span class="price">$29.99</span>
                <button>Add to Cart</button>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/items/blue-widget", html));

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void shouldClassifyBuyNowButtonWithPriceAsProduct() {
        final String html = """
                <html><body>
                <span class="product-price">$49.00</span>
                <a class="btn">Buy Now</a>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/items/gadget", html));

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void shouldClassifyPageWithMultipleProductCardsAsCategory() {
        final String html = """
                <html><body>
                <div class="product-card"><p>Item 1</p></div>
                <div class="product-card"><p>Item 2</p></div>
                <div class="product-card"><p>Item 3</p></div>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/items/", html));

        assertEquals(UrlType.CATEGORY, result.type());
    }

    @Test
    void shouldClassifyPageWithProductItemsAsCategory() {
        final String html = """
                <html><body>
                <div class="product-item">Shoes</div>
                <div class="product-item">Boots</div>
                <div class="product-item">Sandals</div>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/store/footwear", html));

        assertEquals(UrlType.CATEGORY, result.type());
    }

    @Test
    void shouldClassifyRootUrlAsHomePage() {
        final String html = """
                <html><body><p>Welcome to our site</p></body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/", html));

        assertEquals(UrlType.HOMEPAGE, result.type());
    }

    @Test
    void shouldClassifyArticleByOgTypeAsArticle() {
        final String html = """
                <html><head>
                <meta property="og:type" content="article"/>
                </head><body>
                <article><p>Long form article content here.</p></article>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/blog/my-post", html));

        assertEquals(UrlType.ARTICLE, result.type());
    }

    @Test
    void shouldClassifyJsonLdArticlePageAsArticle() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"Article","headline":"News Story"}
                </script>
                <article><p>Article body</p></article>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/news/story", html));

        assertEquals(UrlType.ARTICLE, result.type());
    }

    @Test
    void shouldClassifyBlogPostingJsonLdAsArticle() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"BlogPosting","headline":"My Post"}
                </script>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/posts/my-post", html));

        assertEquals(UrlType.ARTICLE, result.type());
    }

    @Test
    void shouldFallBackToUnknownWhenSignalsAreWeak() {
        final String html = """
                <html><body>
                <p>Contact us at info@example.com</p>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/contact/", html));

        assertEquals(UrlType.UNKNOWN, result.type());
    }

    @Test
    void shouldFallBackToUnknownForPlainPage() {
        final String html = """
                <html><body>
                <p>Just a simple page with no classification signals.</p>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/about/", html));

        assertEquals(UrlType.UNKNOWN, result.type());
    }

    @Test
    void shouldPassThroughAdminUrl() {
        final String html = """
                <html><body>
                <script type="application/ld+json">{"@type":"Product"}</script>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/wp-admin/", html));

        assertEquals(UrlType.ADMIN, result.type());
    }

    @Test
    void shouldPassThroughCartUrl() {
        final String html = "<html><body><p>Cart</p></body></html>";

        final PageClassification result = classifier.classify(page("https://example.com/cart/", html));

        assertEquals(UrlType.CART, result.type());
    }

    @Test
    void shouldFallBackToUrlTypeForProductUrlWithNoHtmlSignals() {
        final String html = """
                <html><body><p>A page about shoes</p></body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/product/shoes/", html));

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void shouldNotSetWordPressOrWooCommerceFlags() {
        final String html = """
                <html><head>
                <meta name="generator" content="WordPress 6.5"/>
                </head><body class="woocommerce">
                <script type="application/ld+json">{"@type":"Product","name":"Widget"}</script>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/items/widget", html));

        assertFalse(result.wordpressDetected());
        assertFalse(result.wooCommerceDetected());
    }

    @Test
    void shouldHandleNullHtml() {
        final WebPage page = new WebPage(URI.create("https://example.com/product/shoes/"),
                null, "", "", 200, "text/html", NOW, Map.of());

        final PageClassification result = classifier.classify(page);

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void shouldHandleBlankHtml() {
        final PageClassification result = classifier.classify(
                page("https://example.com/product/shoes/", ""));

        assertEquals(UrlType.PRODUCT, result.type());
    }

    @Test
    void productSignalsShouldOutweighCategorySignalsWhenBoth() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"Product","name":"Widget"}
                </script>
                <div class="product-card">Item 1</div>
                <div class="product-card">Item 2</div>
                <div class="product-card">Item 3</div>
                </body></html>""";

        final PageClassification result = classifier.classify(page("https://example.com/items/widget", html));

        assertEquals(UrlType.PRODUCT, result.type(),
                "JSON-LD Product (score 3) should outweigh three product cards (score 3) when URL adds +2 to product");
    }
}
