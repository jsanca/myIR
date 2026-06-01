package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDetailExtractorsJsoupDefaultTest {

    private static final Instant NOW = Instant.now();
    private final ProductDetailExtractor extractor = ProductDetailExtractors.jsoupDefault();

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "", "", 200, "text/html", NOW, Map.of());
    }

    @Test
    void jsoupDefaultShouldReturnNonNullExtractor() {
        assertNotNull(extractor);
    }

    @Test
    void shouldExtractProductDetailFromJsonLd() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Product",
                  "name": "Blue Widget",
                  "sku": "BW-001",
                  "description": "A quality blue widget",
                  "brand": {"@type": "Brand", "name": "WidgetCo"},
                  "offers": {
                    "@type": "Offer",
                    "price": "29.99",
                    "priceCurrency": "USD",
                    "availability": "https://schema.org/InStock"
                  }
                }
                </script>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/product/blue-widget", html));

        assertTrue(result.isPresent());
        final ProductDetail detail = result.get();
        assertEquals("Blue Widget", detail.name());
        assertTrue(detail.sku().isPresent());
        assertEquals("BW-001", detail.sku().get());
        assertTrue(detail.shortDescription().isPresent());
        assertEquals("A quality blue widget", detail.shortDescription().get());
        assertTrue(detail.brand().isPresent());
        assertEquals("WidgetCo", detail.brand().get());
        assertTrue(detail.regularPrice().isPresent());
        assertEquals(0, new java.math.BigDecimal("29.99").compareTo(detail.regularPrice().get().amount()));
        assertTrue(detail.regularPrice().get().currencySymbol().isPresent());
        assertEquals("USD", detail.regularPrice().get().currencySymbol().get());
        assertTrue(detail.availability().isPresent());
        assertEquals("InStock", detail.availability().get());
    }

    @Test
    void shouldExtractFromJsonLdWithOffersAsObject() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {
                  "@type": "Product",
                  "name": "Red Shoes",
                  "offers": {"@type": "Offer", "price": "49.00", "priceCurrency": "EUR"}
                }
                </script>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/items/red-shoes", html));

        assertTrue(result.isPresent());
        assertEquals("Red Shoes", result.get().name());
        assertTrue(result.get().regularPrice().isPresent());
        assertEquals(0, new java.math.BigDecimal("49.00").compareTo(result.get().regularPrice().get().amount()));
        assertTrue(result.get().regularPrice().get().currencySymbol().isPresent());
        assertEquals("EUR", result.get().regularPrice().get().currencySymbol().get());
    }

    @Test
    void shouldExtractFromJsonLdWithOffersAsArray() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {
                  "@type": "Product",
                  "name": "Green Hat",
                  "offers": [
                    {"@type": "Offer", "price": "15.00", "priceCurrency": "USD"},
                    {"@type": "Offer", "price": "18.00", "priceCurrency": "USD"}
                  ]
                }
                </script>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/items/green-hat", html));

        assertTrue(result.isPresent());
        assertEquals("Green Hat", result.get().name());
        assertTrue(result.get().regularPrice().isPresent(),
                "Expected price from first offer in array");
        assertEquals(0, new java.math.BigDecimal("15.00").compareTo(result.get().regularPrice().get().amount()));
    }

    @Test
    void shouldExtractImageWhenJsonLdImageIsString() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {
                  "@type": "Product",
                  "name": "Purple Mug",
                  "image": "https://example.com/images/purple-mug.jpg"
                }
                </script>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/product/purple-mug", html));

        assertTrue(result.isPresent());
        assertFalse(result.get().images().isEmpty());
        assertTrue(result.get().images().get(0).url().toString().contains("purple-mug.jpg"));
    }

    @Test
    void shouldExtractImagesWhenJsonLdImageIsArray() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {
                  "@type": "Product",
                  "name": "Yellow Lamp",
                  "image": [
                    "https://example.com/img/lamp-1.jpg",
                    "https://example.com/img/lamp-2.jpg",
                    "https://example.com/img/lamp-3.jpg"
                  ]
                }
                </script>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/product/yellow-lamp", html));

        assertTrue(result.isPresent());
        assertEquals(3, result.get().images().size());
        assertTrue(result.get().images().get(0).url().toString().contains("lamp-1.jpg"));
        assertTrue(result.get().images().get(2).url().toString().contains("lamp-3.jpg"));
    }

    @Test
    void shouldExtractPriceAndCurrencyFromOpenGraphProductMeta() {
        final String html = """
                <html><head>
                <meta property="og:title" content="Silver Watch"/>
                <meta property="og:description" content="Elegant silver watch"/>
                <meta property="og:image" content="https://example.com/img/watch.jpg"/>
                <meta property="product:price:amount" content="199.00"/>
                <meta property="product:price:currency" content="GBP"/>
                <meta property="product:availability" content="in stock"/>
                </head><body></body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/items/silver-watch", html));

        assertTrue(result.isPresent());
        final ProductDetail detail = result.get();
        assertEquals("Silver Watch", detail.name());
        assertTrue(detail.shortDescription().isPresent());
        assertEquals("Elegant silver watch", detail.shortDescription().get());
        assertTrue(detail.regularPrice().isPresent());
        assertEquals(0, new java.math.BigDecimal("199.00").compareTo(detail.regularPrice().get().amount()));
        assertTrue(detail.regularPrice().get().currencySymbol().isPresent());
        assertEquals("GBP", detail.regularPrice().get().currencySymbol().get());
        assertTrue(detail.availability().isPresent());
        assertEquals("in stock", detail.availability().get());
    }

    @Test
    void shouldExtractOgImageWhenNoJsonLdImages() {
        final String html = """
                <html><head>
                <meta property="og:title" content="Wooden Chair"/>
                <meta property="og:image" content="https://example.com/img/chair.jpg"/>
                </head><body></body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/items/wooden-chair", html));

        assertTrue(result.isPresent());
        assertFalse(result.get().images().isEmpty());
        assertTrue(result.get().images().get(0).url().toString().contains("chair.jpg"));
    }

    @Test
    void shouldFallBackToDocumentTitleAndMetaDescriptionWhenProductFieldsMissing() {
        final String html = """
                <html>
                <head>
                <title>Leather Wallet</title>
                <meta name="description" content="Quality genuine leather wallet"/>
                </head>
                <body><p>Our premium leather wallet.</p></body>
                </html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/items/leather-wallet", html));

        assertTrue(result.isPresent(), "Expected a result using document title as name fallback");
        assertEquals("Leather Wallet", result.get().name());
        assertTrue(result.get().shortDescription().isPresent());
        assertEquals("Quality genuine leather wallet", result.get().shortDescription().get());
    }

    @Test
    void shouldFallBackToH1WhenNoJsonLdOrOgTitle() {
        final String html = """
                <html><body>
                <h1>Canvas Backpack</h1>
                <span class="price">$45.00</span>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/items/backpack", html));

        assertTrue(result.isPresent());
        assertEquals("Canvas Backpack", result.get().name());
    }

    @Test
    void shouldReturnEmptyWhenNoProductSignalsPresent() {
        final String html = """
                <html><body>
                <p>Contact us at info@example.com</p>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/contact/", html));

        assertTrue(result.isEmpty(),
                "Expected empty when no name can be resolved from any source");
    }

    @Test
    void shouldReturnEmptyForNullHtml() {
        final WebPage page = new WebPage(URI.create("https://example.com/product/shoes/"),
                null, "", "", 200, "text/html", NOW, Map.of());

        assertTrue(extractor.extract(page).isEmpty());
    }

    @Test
    void shouldReturnEmptyForBlankHtml() {
        final WebPage page = new WebPage(URI.create("https://example.com/product/shoes/"),
                "   ", "", "", 200, "text/html", NOW, Map.of());

        assertTrue(extractor.extract(page).isEmpty());
    }

    @Test
    void jsonLdShouldTakePriorityOverOgTitle() {
        final String html = """
                <html><head>
                <meta property="og:title" content="OG Title"/>
                </head><body>
                <script type="application/ld+json">
                {"@type": "Product", "name": "JSON-LD Title"}
                </script>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/product/item", html));

        assertTrue(result.isPresent());
        assertEquals("JSON-LD Title", result.get().name(),
                "JSON-LD name must win over og:title");
    }

    @Test
    void shouldExtractBrandFromJsonLdBrandObject() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {
                  "@type": "Product",
                  "name": "Sport Shoe",
                  "brand": {"@type": "Brand", "name": "RunFast"}
                }
                </script>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/product/sport-shoe", html));

        assertTrue(result.isPresent());
        assertTrue(result.get().brand().isPresent());
        assertEquals("RunFast", result.get().brand().get());
    }

    @Test
    void shouldExtractFromJsonLdWithinGraph() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@graph": [
                    {"@type": "WebSite", "name": "My Shop"},
                    {
                      "@type": "Product",
                      "name": "Graph Product",
                      "sku": "GP-007",
                      "offers": {"@type": "Offer", "price": "99.00", "priceCurrency": "USD",
                                 "availability": "https://schema.org/InStock"}
                    }
                  ]
                }
                </script>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(page("https://example.com/product/graph-product", html));

        assertTrue(result.isPresent());
        assertEquals("Graph Product", result.get().name());
        assertTrue(result.get().sku().isPresent());
        assertEquals("GP-007", result.get().sku().get());
        assertTrue(result.get().availability().isPresent());
        assertEquals("InStock", result.get().availability().get());
    }

    @Test
    void shouldResolvePageUrlWhenNoJsonLdOrOgUrl() {
        final String html = """
                <html><body>
                <script type="application/ld+json">
                {"@type": "Product", "name": "Test Item"}
                </script>
                </body></html>""";

        final String pageUrl = "https://example.com/product/test-item";
        final Optional<ProductDetail> result = extractor.extract(page(pageUrl, html));

        assertTrue(result.isPresent());
        assertEquals(URI.create(pageUrl), result.get().url());
    }

    @Test
    void shouldDecodeDoubleEncodedEntityInOgTitle() {
        final String html = """
                <html><body>
                <h1>Fallback</h1>
                <meta property="og:title" content="Menú S&amp;amp;J" />
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(
                page("https://example.com/product/test/", html));

        assertTrue(result.isPresent());
        assertEquals("Menú S&J", result.get().name());
    }

    @Test
    void shouldDecodeDoubleEncodedEntityInBrand() {
        final String html = """
                <html><body>
                <h1>Product Name</h1>
                <span itemprop="brand">Marca &amp;amp; Co</span>
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(
                page("https://example.com/product/test/", html));

        assertTrue(result.isPresent());
        assertTrue(result.get().brand().isPresent());
        assertEquals("Marca & Co", result.get().brand().get());
    }

    @Test
    void shouldDecodeDoubleEncodedInMetaDescription() {
        final String html = """
                <html><body>
                <h1>Product Name</h1>
                <meta name="description" content="Corporativo &amp;gt; Personal" />
                </body></html>""";

        final Optional<ProductDetail> result = extractor.extract(
                page("https://example.com/product/test/", html));

        assertTrue(result.isPresent());
        assertTrue(result.get().shortDescription().isPresent());
        assertEquals("Corporativo > Personal", result.get().shortDescription().get());
    }
}
