package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCardExtractorsTest {

    private static final Instant NOW = Instant.now();
    private final ProductCardExtractor extractor = ProductCardExtractors.woocommerceDefault();

    @Test
    void shouldExtractMultipleCardsFromListingPage() {
        final String html = """
                <html><body>
                <ul class="products columns-3">
                    <li class="product">
                        <a href="https://example.com/product/item-1/" class="woocommerce-LoopProduct-link">
                            <img src="item1.jpg" alt="Item 1" />
                            <h2 class="woocommerce-loop-product__title">Item One</h2>
                        </a>
                        <span class="price"><span class="woocommerce-Price-amount">$10.00</span></span>
                    </li>
                    <li class="product">
                        <a href="https://example.com/product/item-2/" class="woocommerce-LoopProduct-link">
                            <img src="item2.jpg" alt="Item 2" />
                            <h2 class="woocommerce-loop-product__title">Item Two</h2>
                        </a>
                        <span class="price"><span class="woocommerce-Price-amount">$20.00</span></span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(2, cards.size());
    }

    @Test
    void shouldExtractProductUrls() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="https://example.com/product/shoes/" class="woocommerce-LoopProduct-link">
                            <h2 class="woocommerce-loop-product__title">Shoes</h2>
                        </a>
                        <span class="price">$50.00</span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals(URI.create("https://example.com/product/shoes/"), cards.get(0).url());
    }

    @Test
    void shouldExtractProductNames() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product/red-shoes/">
                            <h2 class="woocommerce-loop-product__title">Red Running Shoes</h2>
                        </a>
                        <span class="price">$89.99</span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals("Red Running Shoes", cards.get(0).name());
    }

    @Test
    void shouldExtractRegularPrice() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product/jeans/"><h2>Jeans</h2></a>
                        <span class="price">
                            <span class="woocommerce-Price-amount">$49.99</span>
                        </span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).regularPrice().isPresent());
    }

    @Test
    void shouldExtractSalePriceFromIns() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product/sale-item/"><h2>Sale Item</h2></a>
                        <span class="price">
                            <del><span class="woocommerce-Price-amount">$100.00</span></del>
                            <ins><span class="woocommerce-Price-amount">$75.00</span></ins>
                        </span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).salePrice().isPresent(),
                "Expected sale price from <ins> element");
        assertTrue(cards.get(0).regularPrice().isPresent(),
                "Expected regular price from <del> element");
    }

    @Test
    void shouldNotHaveSalePriceWhenOnlyNormalPrice() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product/normal/"><h2>Normal Item</h2></a>
                        <span class="price">
                            <span class="woocommerce-Price-amount">$30.00</span>
                        </span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).salePrice().isEmpty(),
                "Expected no sale price when <ins> is absent");
    }

    @Test
    void shouldExtractThumbnailFromDataSrc() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product/thumb/" class="woocommerce-LoopProduct-link">
                            <img data-src="https://example.com/images/thumb.jpg" alt="Thumb" />
                            <h2 class="woocommerce-loop-product__title">Thumb Product</h2>
                        </a>
                        <span class="price">$15.00</span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).thumbnail().isPresent());
        assertTrue(cards.get(0).thumbnail().get().url().toString().contains("thumb.jpg"));
    }

    @Test
    void shouldExtractThumbnailFromSrcset() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product/thumb/" class="woocommerce-LoopProduct-link">
                            <img src="small.jpg"
                                 srcset="medium.jpg 300w, large.jpg 1024w"
                                 alt="Thumb" />
                            <h2 class="woocommerce-loop-product__title">Srcset Product</h2>
                        </a>
                        <span class="price">$20.00</span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).thumbnail().isPresent());
        assertTrue(cards.get(0).thumbnail().get().url().toString().contains("large.jpg"),
                "Expected largest srcset image (1024w) to be selected");
    }

    @Test
    void shouldSkipCardsWithoutUrl() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <h2>No Link Product</h2>
                        <span class="price">$10.00</span>
                    </li>
                    <li class="product">
                        <a href="/product/has-link/"><h2>Has Link</h2></a>
                        <span class="price">$20.00</span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals("Has Link", cards.get(0).name());
    }

    @Test
    void shouldReturnEmptyForNonListingHtml() {
        final String html = """
                <html><body>
                <h1>About Us</h1>
                <p>This is not a listing page.</p>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/about/", html));

        assertTrue(cards.isEmpty());
    }

    @Test
    void shouldThrowNpeForNullPage() {
        assertThrows(NullPointerException.class, () -> extractor.extract(null));
    }

    @Test
    void shouldReturnEmptyForNullHtml() {
        final WebPage page = new WebPage(
                URI.create("https://example.com/shop/"),
                null, "", "", 200, "text/html", NOW, Map.of());
        assertTrue(extractor.extract(page).isEmpty());
    }

    @Test
    void shouldReturnEmptyForBlankHtml() {
        final WebPage page = new WebPage(
                URI.create("https://example.com/shop/"),
                "   ", "", "", 200, "text/html", NOW, Map.of());
        assertTrue(extractor.extract(page).isEmpty());
    }

    @Test
    void shouldDecodeDoubleEncodedEntityInCardName() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product/carteras/" class="woocommerce-LoopProduct-link">
                            <h2 class="woocommerce-loop-product__title">Bolso &amp;amp; Carteras</h2>
                        </a>
                        <span class="price"><span class="woocommerce-Price-amount">$20.00</span></span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals("Bolso & Carteras", cards.get(0).name());
    }

    @Test
    void shouldDecodeEntityInCardThumbnailAlt() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product/shoes/" class="woocommerce-LoopProduct-link">
                            <img src="shoe.jpg" alt="Zapatos &amp;amp; Botas" />
                            <h2 class="woocommerce-loop-product__title">Shoes</h2>
                        </a>
                        <span class="price"><span class="woocommerce-Price-amount">$30.00</span></span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertTrue(cards.get(0).thumbnail().isPresent());
        assertEquals("Zapatos & Botas", cards.get(0).thumbnail().get().altText());
    }

    @Test
    void shouldSkipNavigationCardWithHashHref() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="#categories"><h2>Categorías</h2></a>
                    </li>
                    <li class="product">
                        <a href="/product/real-item/"><h2>Real Product</h2></a>
                        <span class="price"><span class="woocommerce-Price-amount">$10.00</span></span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals("Real Product", cards.get(0).name());
    }

    @Test
    void shouldSkipNavigationCardWithVolverLabel() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/shop/"><h2>Volver</h2></a>
                    </li>
                    <li class="product">
                        <a href="/product/real-item/"><h2>Real Product</h2></a>
                        <span class="price"><span class="woocommerce-Price-amount">$10.00</span></span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals("Real Product", cards.get(0).name());
    }

    @Test
    void shouldSkipCategoryCard() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product-category/bags/"><h2>Bags</h2></a>
                    </li>
                    <li class="product">
                        <a href="/product/real-item/"><h2>Real Product</h2></a>
                        <span class="price"><span class="woocommerce-Price-amount">$10.00</span></span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(1, cards.size());
        assertEquals("Real Product", cards.get(0).name());
    }

    @Test
    void shouldStillExtractProductCardsInMixedList() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product-category/bags/"><h2>Bags</h2></a>
                    </li>
                    <li class="product">
                        <a href="/product/alpha/"><h2>Alpha</h2></a>
                        <span class="price"><span class="woocommerce-Price-amount">$10.00</span></span>
                    </li>
                    <li class="product">
                        <a href="/product/beta/"><h2>Beta</h2></a>
                        <span class="price"><span class="woocommerce-Price-amount">$20.00</span></span>
                    </li>
                    <li class="product">
                        <a href="/shop/"><h2>Volver</h2></a>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(2, cards.size());
        assertEquals("Alpha", cards.get(0).name());
        assertEquals("Beta", cards.get(1).name());
    }

    @Test
    void shouldDeduplicateProductsStill() {
        final String html = """
                <html><body>
                <ul class="products">
                    <li class="product">
                        <a href="/product/unique/"><h2>Unique</h2></a>
                        <span class="price"><span class="woocommerce-Price-amount">$10.00</span></span>
                    </li>
                    <li class="product">
                        <a href="/product/unique/?ref=cat"><h2>Unique</h2></a>
                        <span class="price"><span class="woocommerce-Price-amount">$10.00</span></span>
                    </li>
                </ul>
                </body></html>""";

        final List<ProductCard> cards = extractor.extract(page("https://example.com/shop/", html));

        assertEquals(2, cards.size(), "Same product with different query params are still separate cards");
    }

    private static WebPage page(final String url, final String html) {
        return new WebPage(URI.create(url), html, "", "", 200, "text/html", NOW, Map.of());
    }
}
