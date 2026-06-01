package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDiscoveryCollectorsTest {

    private static final Instant NOW = Instant.now();
    private final ProductDiscoveryCollector collector = ProductDiscoveryCollectors.jsoupDefault();

    @Test
    void shouldCollectProductDetailsFromProductPages() {
        final WebPage productPage = page(
                "https://example.com/product/headphones/",
                """
                <html>
                <head>
                    <script type="application/ld+json">
                    {"@context":"https://schema.org","@type":"Product",
                     "name":"Wireless Headphones","sku":"WH-100",
                     "offers":{"@type":"Offer","price":"79.99","priceCurrency":"USD"}}
                    </script>
                </head>
                <body><h1 class="product_title">Wireless Headphones</h1><button>Add to cart</button></body>
                </html>""");

        final ProductDiscoveryReport report = collector.collect(List.of(productPage));

        assertEquals(1, report.productDetails().size());
        assertEquals("Wireless Headphones", report.productDetails().get(0).name());
        assertTrue(report.productCards().isEmpty());
    }

    @Test
    void shouldCollectProductCardsFromCategoryPages() {
        final WebPage categoryPage = page(
                "https://example.com/category/all/",
                """
                <html><body>
                <div class="product-card"><a href="/product/alpha/"><h2>Alpha</h2></a><span class="price">$10</span></div>
                <div class="product-card"><a href="/product/beta/"><h2>Beta</h2></a><span class="price">$20</span></div>
                <div class="product-card"><a href="/product/gamma/"><h2>Gamma</h2></a><span class="price">$30</span></div>
                </body></html>""");

        final ProductDiscoveryReport report = collector.collect(List.of(categoryPage));

        assertFalse(report.productCards().isEmpty());
        assertTrue(report.productDetails().isEmpty());
    }

    @Test
    void shouldCollectCardsFromHomePageAlongWithProductDetails() {
        final WebPage homePage = page(
                "https://example.com/",
                """
                <html><body>
                <div class="product-card"><a href="/product/featured/"><h2>Featured Item</h2></a></div>
                <div class="product-card"><a href="/product/new/"><h2>New Arrival</h2></a></div>
                </body></html>""");
        final WebPage productPage = page(
                "https://example.com/product/headphones/",
                """
                <html>
                <head>
                    <script type="application/ld+json">
                    {"@context":"https://schema.org","@type":"Product","name":"Headphones",
                     "offers":{"@type":"Offer","price":"59.99","priceCurrency":"USD"}}
                    </script>
                </head>
                <body><h1>Headphones</h1><button>Add to cart</button></body>
                </html>""");

        final ProductDiscoveryReport report = collector.collect(List.of(homePage, productPage));

        assertEquals(1, report.productDetails().size());
        assertFalse(report.productCards().isEmpty());
    }

    @Test
    void shouldPreservePageLevelDiscoveryResults() {
        final WebPage page1 = page("https://example.com/product/a/",
                """
                <html>
                <head><script type="application/ld+json">
                {"@context":"https://schema.org","@type":"Product","name":"Product A",
                 "offers":{"@type":"Offer","price":"10.00","priceCurrency":"USD"}}
                </script></head>
                <body><h1>Product A</h1><button>Add to cart</button></body>
                </html>""");
        final WebPage page2 = page("https://example.com/about/",
                "<html><body><h1>About Us</h1></body></html>");

        final ProductDiscoveryReport report = collector.collect(List.of(page1, page2));

        assertEquals(2, report.pageResults().size(),
                "Page-level results should be preserved for all pages");
        assertEquals(URI.create("https://example.com/product/a/"),
                report.pageResults().get(0).pageUri());
        assertEquals(URI.create("https://example.com/about/"),
                report.pageResults().get(1).pageUri());
    }

    @Test
    void shouldReturnEmptyReportForEmptyInput() {
        final ProductDiscoveryReport report = collector.collect(List.of());

        assertNotNull(report);
        assertTrue(report.pageResults().isEmpty());
        assertTrue(report.productDetails().isEmpty());
        assertTrue(report.productCards().isEmpty());
    }

    @Test
    void productDiscoveryReportShouldDefensivelyCopyLists() {
        final List<ProductDiscoveryResult> mutableResults = new ArrayList<>();
        final List<ProductDetail> mutableDetails = new ArrayList<>();
        final List<ProductCard> mutableCards = new ArrayList<>();

        final ProductDiscoveryReport report = new ProductDiscoveryReport(
                mutableResults, mutableDetails, mutableCards);

        mutableResults.add(null);
        mutableDetails.add(null);
        mutableCards.add(null);

        assertTrue(report.pageResults().isEmpty(), "pageResults should be defensively copied");
        assertTrue(report.productDetails().isEmpty(), "productDetails should be defensively copied");
        assertTrue(report.productCards().isEmpty(), "productCards should be defensively copied");
    }

    @Test
    void shouldThrowOnNullPageList() {
        assertThrows(NullPointerException.class, () -> collector.collect(null));
    }

    @Test
    void publicFactoryShouldReturnWorkingCollector() {
        final ProductDiscoveryCollector c = ProductDiscoveryCollectors.jsoupDefault();
        assertNotNull(c);

        final ProductDiscoveryReport report = c.collect(List.of(
                page("https://example.com/about/", "<html><body><p>About</p></body></html>")));
        assertNotNull(report);
        assertEquals(1, report.pageResults().size());
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition) {
        assertFalse(condition, "Expected false but was true");
    }

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "", "", 200, "text/html", NOW, Map.of());
    }
}
