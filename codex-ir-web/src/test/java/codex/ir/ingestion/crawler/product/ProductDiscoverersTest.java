package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import codex.ir.ingestion.crawler.classifier.UrlType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDiscoverersTest {

    private static final Instant NOW = Instant.now();
    private final ProductDiscoverer discoverer = ProductDiscoverers.jsoupDefault();

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "", "", 200, "text/html", NOW, Map.of());
    }

    @Test
    void productPageShouldReturnProductDetail() {
        final String html = """
                <html>
                <head>
                    <script type="application/ld+json">
                    {"@context":"https://schema.org","@type":"Product",
                     "name":"Wireless Headphones","sku":"WH-100",
                     "offers":{"@type":"Offer","price":"79.99","priceCurrency":"USD"}}
                    </script>
                </head>
                <body>
                    <h1 class="product_title">Wireless Headphones</h1>
                    <button>Add to cart</button>
                </body>
                </html>""";

        final ProductDiscoveryResult result = discoverer.discover(
                page("https://example.com/product/wireless-headphones/", html));

        assertEquals(UrlType.PRODUCT, result.pageClassification().type());
        assertTrue(result.productDetail().isPresent(), "Expected product detail for PRODUCT page");
        assertEquals("Wireless Headphones", result.productDetail().get().name());
        assertTrue(result.productCards().isEmpty(), "Product page should have no cards");
    }

    @Test
    void categoryPageShouldReturnProductCards() {
        final String html = """
                <html><body>
                <h1>Shop All</h1>
                <div class="product-card">
                    <a href="/product/alpha/"><h2>Alpha</h2></a>
                    <span class="price">$10.00</span>
                </div>
                <div class="product-card">
                    <a href="/product/beta/"><h2>Beta</h2></a>
                    <span class="price">$20.00</span>
                </div>
                <div class="product-card">
                    <a href="/product/gamma/"><h2>Gamma</h2></a>
                    <span class="price">$30.00</span>
                </div>
                </body></html>""";

        final ProductDiscoveryResult result = discoverer.discover(
                page("https://example.com/category/all/", html));

        assertEquals(UrlType.CATEGORY, result.pageClassification().type());
        assertFalse(result.productCards().isEmpty(), "Expected product cards for CATEGORY page");
        assertTrue(result.productDetail().isEmpty(), "Category page should have no product detail");
    }

    @Test
    void homePageWithProductCardsShouldReturnCards() {
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="/product/featured-a/"><h2>Featured A</h2></a>
                </div>
                <div class="product-card">
                    <a href="/product/featured-b/"><h2>Featured B</h2></a>
                </div>
                </body></html>""";

        final ProductDiscoveryResult result = discoverer.discover(
                page("https://example.com/", html));

        assertEquals(UrlType.HOMEPAGE, result.pageClassification().type());
        assertFalse(result.productCards().isEmpty(), "Expected product cards on home page");
        assertTrue(result.productDetail().isEmpty(), "Home page should have no product detail");
    }

    @Test
    void articlePageShouldReturnNoProductData() {
        final String html = """
                <html>
                <head>
                    <script type="application/ld+json">
                    {"@context":"https://schema.org","@type":"Article","headline":"Best Headphones 2025"}
                    </script>
                    <meta property="og:type" content="article" />
                </head>
                <body>
                    <article><h1>Best Headphones 2025</h1><p>Content here.</p></article>
                    <time datetime="2025-01-15">January 15, 2025</time>
                </body>
                </html>""";

        final ProductDiscoveryResult result = discoverer.discover(
                page("https://example.com/blog/best-headphones/", html));

        assertEquals(UrlType.ARTICLE, result.pageClassification().type());
        assertTrue(result.productDetail().isEmpty(), "Article page should have no product detail");
        assertTrue(result.productCards().isEmpty(), "Article page should have no product cards");
    }

    @Test
    void unknownPageShouldReturnNoProductData() {
        final String html = """
                <html><body>
                <h1>About Us</h1>
                <p>We are a company that makes things.</p>
                </body></html>""";

        final ProductDiscoveryResult result = discoverer.discover(
                page("https://example.com/about/", html));

        assertTrue(result.productDetail().isEmpty(), "Unknown page should have no product detail");
        assertTrue(result.productCards().isEmpty(), "Unknown page should have no product cards");
    }

    @Test
    void emptyHtmlShouldNotThrowAndShouldReturnEmptyResult() {
        final ProductDiscoveryResult result = discoverer.discover(
                page("https://example.com/empty/", ""));

        assertNotNull(result);
        assertTrue(result.productDetail().isEmpty());
        assertTrue(result.productCards().isEmpty());
    }

    @Test
    void nullHtmlShouldNotThrowAndShouldReturnEmptyResult() {
        final WebPage pageWithNullHtml = new WebPage(
                URI.create("https://example.com/null/"),
                null, "", "", 200, "text/html", NOW, Map.of());

        final ProductDiscoveryResult result = discoverer.discover(pageWithNullHtml);

        assertNotNull(result);
        assertTrue(result.productDetail().isEmpty());
        assertTrue(result.productCards().isEmpty());
    }

    @Test
    void productDiscoveryResultShouldDefensivelyCopyCardList() {
        final ProductCard card = new ProductCard(
                URI.create("https://example.com/product/x/"), "X",
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        final List<ProductCard> mutable = new java.util.ArrayList<>(List.of(card));
        final ProductDiscoveryResult result = new ProductDiscoveryResult(
                URI.create("https://example.com/"),
                new codex.ir.ingestion.crawler.classifier.PageClassification(
                        URI.create("https://example.com/"),
                        UrlType.HOMEPAGE,
                        new codex.ir.ingestion.crawler.classifier.ClassifiedUrl(
                                URI.create("https://example.com/"), UrlType.HOMEPAGE),
                        false, false),
                java.util.Optional.empty(),
                mutable);

        mutable.clear();

        assertEquals(1, result.productCards().size(), "Card list should be defensively copied");
    }

    @Test
    void publicFactoryShouldReturnWorkingDiscoverer() {
        final ProductDiscoverer d = ProductDiscoverers.jsoupDefault();
        assertNotNull(d);

        final ProductDiscoveryResult result = d.discover(
                page("https://example.com/about/", "<html><body><h1>About</h1></body></html>"));
        assertNotNull(result);
        assertNotNull(result.pageClassification());
        assertEquals(URI.create("https://example.com/about/"), result.pageUri());
    }
}
