package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDetailExtractorsTest {

    private static final Instant NOW = Instant.now();
    private static String productHtml;
    private static String nonProductHtml;

    private final ProductDetailExtractor extractor = ProductDetailExtractors.woocommerceDefault();

    @BeforeAll
    static void loadFixtures() throws IOException, URISyntaxException {
        productHtml = Files.readString(Path.of(
                "src/test/resources/fixtures/woocommerce/product.html"));
        nonProductHtml = """
                <html><body>
                <h1>About Us</h1>
                <p>This is not a product page.</p>
                </body></html>""";
    }

    @Test
    void shouldExtractProductName() {
        final WebPage page = page("https://syjleathers.com/producto/bm-02-colores/", productHtml);

        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertEquals("BM-02-Colores", result.get().name());
    }

    @Test
    void shouldExtractSku() {
        final WebPage page = page("https://syjleathers.com/producto/bm-02-colores/", productHtml);

        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertTrue(result.get().sku().isPresent());
        assertEquals("BM-02-Colores", result.get().sku().get());
    }

    @Test
    void shouldExtractRegularPrice() {
        final WebPage page = page("https://syjleathers.com/producto/bm-02-colores/", productHtml);

        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        final Optional<ProductPrice> price = result.get().regularPrice();
        assertTrue(price.isPresent(), "Expected regular price to be extracted");
        assertNotNull(price.get().amount());
    }

    @Test
    void shouldExtractShortDescription() {
        final WebPage page = page("https://syjleathers.com/producto/bm-02-colores/", productHtml);

        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        final Optional<String> desc = result.get().shortDescription();
        assertTrue(desc.isPresent(), "Expected short description to be extracted");
        assertFalse(desc.get().isBlank());
    }

    @Test
    void shouldExtractImages() {
        final WebPage page = page("https://syjleathers.com/producto/bm-02-colores/", productHtml);

        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertFalse(result.get().images().isEmpty(),
                "Expected at least one product image to be extracted");
    }

    @Test
    void shouldReturnEmptyForNonProductHtml() {
        final WebPage page = page("https://example.com/about/", nonProductHtml);

        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isEmpty(),
                "Expected empty result for non-product HTML");
    }

    @Test
    void shouldReturnEmptyForNullHtml() {
        final WebPage page = new WebPage(
                URI.create("https://example.com/product/shoes/"),
                null, "", "", 200, "text/html", NOW, Map.of());

        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isEmpty(),
                "Expected empty result when rawHtml is null");
    }

    @Test
    void shouldReturnEmptyForBlankHtml() {
        final WebPage page = new WebPage(
                URI.create("https://example.com/product/shoes/"),
                "   ", "", "", 200, "text/html", NOW, Map.of());

        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isEmpty(),
                "Expected empty result when rawHtml is blank");
    }

    @Test
    void shouldThrowNpeForNullPage() {
        assertThrows(NullPointerException.class, () -> extractor.extract(null),
                "Expected NullPointerException for null page");
    }

    @Test
    void shouldNotReturnSalePriceWhenOnlyDelPresent() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <p class="price">
                    <del><span class="woocommerce-Price-amount"><bdi>$50.00</bdi></span></del>
                    <span class="woocommerce-Price-amount"><bdi>$40.00</bdi></span>
                </p>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertTrue(result.get().regularPrice().isPresent(),
                "Expected regular price from non-del element");
        assertTrue(result.get().salePrice().isEmpty(),
                "Expected no sale price when <ins> is absent");
    }

    @Test
    void shouldExtractRegularAndSalePriceWhenBothPresent() {
        final String html = """
                <html><body>
                <h1 class="product_title">On Sale Product</h1>
                <p class="price">
                    <del><span class="woocommerce-Price-amount"><bdi>$50.00</bdi></span></del>
                    <ins><span class="woocommerce-Price-amount"><bdi>$30.00</bdi></span></ins>
                </p>
                </body></html>""";

        final WebPage page = page("https://example.com/product/on-sale/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertTrue(result.get().regularPrice().isPresent(),
                "Expected regular price from <del>");
        assertTrue(result.get().salePrice().isPresent(),
                "Expected sale price from <ins>");
    }

    @Test
    void shouldExtractImageFromSrcsetWhenNoLargeImage() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <figure class="woocommerce-product-gallery__wrapper">
                    <img src="small.jpg"
                         srcset="medium.jpg 300w, large.jpg 1024w, small.jpg 150w"
                         alt="Product photo" />
                </figure>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertFalse(result.get().images().isEmpty());
        final ProductImage image = result.get().images().get(0);
        assertTrue(image.url().toString().contains("large.jpg"),
                "Expected largest srcset image (1024w) to be selected, got: " + image.url());
    }

    @Test
    void shouldExtractNameFromJsonLdWhenCssMissing() {
        final String html = """
                <html><body>
                <h1>Some Other Heading</h1>
                <script type="application/ld+json">
                {"@type": "Product", "name": "JSON-LD Product Name"}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertEquals("JSON-LD Product Name", result.get().name());
    }

    @Test
    void shouldExtractSkuFromJsonLdWhenCssMissing() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <script type="application/ld+json">
                {"@type": "Product", "name": "Test Product", "sku": "SKU-12345"}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertTrue(result.get().sku().isPresent());
        assertEquals("SKU-12345", result.get().sku().get());
    }

    @Test
    void shouldExtractDescriptionFromJsonLdAsShortDescriptionFallback() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <script type="application/ld+json">
                {"@type": "Product", "name": "Test Product", "description": "JSON-LD description text"}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertTrue(result.get().shortDescription().isPresent());
        assertEquals("JSON-LD description text", result.get().shortDescription().get());
    }

    @Test
    void shouldExtractPriceFromJsonLdOffersFallback() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <script type="application/ld+json">
                {"@type": "Product", "name": "Test Product",
                 "offers": {"@type": "Offer", "price": "49.99", "priceCurrency": "USD"}}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertTrue(result.get().regularPrice().isPresent(),
                "Expected regular price from JSON-LD offers.price");
    }

    @Test
    void shouldExtractImageFromJsonLdAsString() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <script type="application/ld+json">
                {"@type": "Product", "name": "Test Product",
                 "image": "https://example.com/images/product.jpg"}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertFalse(result.get().images().isEmpty());
        assertTrue(result.get().images().get(0).url().toString().contains("product.jpg"));
    }

    @Test
    void shouldExtractImagesFromJsonLdArray() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <script type="application/ld+json">
                {"@type": "Product", "name": "Test Product",
                 "image": ["https://example.com/img1.jpg", "https://example.com/img2.jpg"]}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().images().size(),
                "Expected 2 images from JSON-LD array");
    }

    @Test
    void shouldExtractJsonLdProductFromGraph() {
        final String html = """
                <html><body>
                <h1 class="product_title">Graph Product</h1>
                <script type="application/ld+json">
                {"@context": "https://schema.org",
                 "@graph": [
                    {"@type": "WebSite", "name": "My Shop"},
                    {"@type": "Product", "name": "Graph Product", "sku": "GP-001",
                     "offers": {"@type": "Offer", "price": "29.99"}}
                 ]}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertTrue(result.get().sku().isPresent());
        assertEquals("GP-001", result.get().sku().get());
    }

    @Test
    void shouldHandleJsonLdTypeAsArray() {
        final String html = """
                <html><body>
                <h1 class="product_title">Array Type Product</h1>
                <script type="application/ld+json">
                {"@context": "https://schema.org",
                 "@type": ["Product", "Item"],
                 "name": "Array Type Product"}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
    }

    @Test
    void cssValuesShouldWinOverJsonLd() {
        final String html = """
                <html><body>
                <h1 class="product_title">CSS Product Name</h1>
                <span class="sku">CSS-SKU</span>
                <script type="application/ld+json">
                {"@type": "Product", "name": "JSON-LD Name", "sku": "JSON-SKU"}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertEquals("CSS Product Name", result.get().name(),
                "CSS name should win over JSON-LD name");
        assertTrue(result.get().sku().isPresent());
        assertEquals("CSS-SKU", result.get().sku().get(),
                "CSS SKU should win over JSON-LD SKU");
    }

    @Test
    void shouldHandleMalformedJsonLdGracefully() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <script type="application/ld+json">
                {not valid json at all}
                </script>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertEquals("Test Product", result.get().name(),
                "CSS extraction should still work when JSON-LD is malformed");
    }

    @Test
    void shouldDecodeDoubleEncodedAmpersandInProductName() {
        final String html = """
                <html><body>
                <h1 class="product_title">Menú S&amp;amp;J</h1>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertEquals("Menú S&J", result.get().name());
    }

    @Test
    void shouldDecodeDoubleEncodedGreaterThanInDescription() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <div class="woocommerce-product-details__short-description">
                    Corporativo &amp;gt; Ejecutivo
                </div>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertTrue(result.get().shortDescription().isPresent());
        assertEquals("Corporativo > Ejecutivo", result.get().shortDescription().get());
    }

    @Test
    void shouldDecodeDoubleEncodedAmpersandInShortDescription() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <div class="woocommerce-product-details__short-description">
                    Bolsos &amp;amp; Carteras de lujo
                </div>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertTrue(result.get().shortDescription().isPresent());
        assertEquals("Bolsos & Carteras de lujo", result.get().shortDescription().get());
    }

    @Test
    void shouldDecodeEntityInImageAltText() {
        final String html = """
                <html><body>
                <h1 class="product_title">Test Product</h1>
                <figure class="woocommerce-product-gallery__wrapper">
                    <img src="shoe.jpg" alt="Zapatos &amp;amp; Botas" />
                </figure>
                </body></html>""";

        final WebPage page = page("https://example.com/product/test/", html);
        final Optional<ProductDetail> result = extractor.extract(page);

        assertTrue(result.isPresent());
        assertFalse(result.get().images().isEmpty());
        assertEquals("Zapatos & Botas", result.get().images().get(0).altText());
    }

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "", "", 200, "text/html", NOW, Map.of());
    }
}
