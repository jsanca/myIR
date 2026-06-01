package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCardExtractorsJsoupDefaultTest {

    private static final Instant NOW = Instant.now();
    private final ProductCardExtractor extractor = ProductCardExtractors.jsoupDefault();

    @Test
    void shouldExtractCardsFromSimpleProductGrid() {
        final String html = """
                <html><body>
                <div class="product-grid">
                    <div class="product-card">
                        <a href="https://example.com/product/alpha/"><h2>Alpha</h2></a>
                        <span class="price">$10.00</span>
                    </div>
                    <div class="product-card">
                        <a href="https://example.com/product/beta/"><h2>Beta</h2></a>
                        <span class="price">$20.00</span>
                    </div>
                </div>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(2, cards.size());
    }

    @Test
    void shouldResolveRelativeProductUrls() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product-item">
                        <a href="/product/shoes/"><h2>Shoes</h2></a>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals(URI.create("https://example.com/product/shoes/"), cards.get(0).url());
    }

    @Test
    void shouldExtractTitleFromAnchorText() {
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="/product/hat/">Summer Hat</a>
                </div>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals("Summer Hat", cards.get(0).name());
    }

    @Test
    void shouldPreferHeadingOverAnchorOwnText() {
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="/product/jacket/">
                        <h2>Leather Jacket</h2>
                    </a>
                </div>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals("Leather Jacket", cards.get(0).name());
    }

    @Test
    void shouldFallBackToImgAltWhenNoHeadingOrAnchorText() {
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="/product/bag/">
                        <img src="bag.jpg" alt="Canvas Bag" />
                    </a>
                </div>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals("Canvas Bag", cards.get(0).name());
    }

    @Test
    void shouldExtractImageFromImgSrc() {
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="/product/mug/">
                        <img src="https://example.com/images/mug.jpg" alt="Mug" />
                        <h2>Coffee Mug</h2>
                    </a>
                </div>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).thumbnail().isPresent());
        assertEquals("https://example.com/images/mug.jpg",
                cards.get(0).thumbnail().get().url().toString());
    }

    @Test
    void shouldExtractImageFromDataSrc() {
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="/product/lamp/">
                        <img data-src="https://example.com/images/lamp.jpg" src="placeholder.gif" alt="Lamp" />
                        <h2>Table Lamp</h2>
                    </a>
                </div>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).thumbnail().isPresent());
        assertTrue(cards.get(0).thumbnail().get().url().toString().contains("lamp.jpg"));
    }

    @Test
    void shouldExtractPriceFromPriceElement() {
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="/product/watch/"><h2>Watch</h2></a>
                    <span class="price">$99.99</span>
                </div>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).regularPrice().isPresent());
        assertEquals(0, cards.get(0).regularPrice().get().amount().compareTo(
                new java.math.BigDecimal("99.99")));
    }

    @Test
    void shouldDeduplicateCardsByUrl() {
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="https://example.com/product/chair/"><h2>Chair</h2></a>
                </div>
                <div class="product-card">
                    <a href="https://example.com/product/chair/"><h2>Chair</h2></a>
                </div>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoProductCardsFound() {
        final String html = """
                <html><body>
                <article>
                    <h1>Blog Post Title</h1>
                    <p>Some blog content here.</p>
                </article>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/blog/post/", html));

        assertTrue(cards.isEmpty());
    }

    @Test
    void publicFactoryShouldReturnWorkingExtractor() {
        final ProductCardExtractor factory = ProductCardExtractors.jsoupDefault();

        assertNotNull(factory);
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="/product/pen/"><h2>Pen</h2></a>
                </div>
                </body></html>""";
        final List<ProductCard> cards = factory.extract(page("https://example.com/shop/", html));
        assertFalse(cards.isEmpty());
        assertEquals("Pen", cards.get(0).name());
    }

    @Test
    void shouldDecodeDoubleEncodedEntityInGenericCardName() {
        final String html = """
                <html><body>
                <div class="product-card">
                    <a href="https://example.com/product/bolsos/"><h2>Bolso &amp;amp; Carteras</h2></a>
                    <span class="price">$20.00</span>
                </div>
                </body></html>""";
        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));
        assertEquals(1, cards.size());
        assertEquals("Bolso & Carteras", cards.get(0).name());
    }

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "", "", 200, "text/html", NOW, Map.of());
    }
}
