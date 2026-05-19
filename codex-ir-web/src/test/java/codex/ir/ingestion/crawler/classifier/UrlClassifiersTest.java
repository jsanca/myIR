package codex.ir.ingestion.crawler.classifier;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlClassifiersTest {

    private final UrlClassifier classifier = UrlClassifiers.wordpressWooCommerceDefaultWeb();

    @Test
    void shouldClassifyHomepageByRootPath() {
        assertEquals(UrlType.HOMEPAGE, classifier.classify(URI.create("https://example.com/")).type());
    }

    @Test
    void shouldClassifyHomepageByEmptyPath() {
        assertEquals(UrlType.HOMEPAGE, classifier.classify(URI.create("https://example.com")).type());
    }

    @Test
    void shouldClassifyEnglishProductUrl() {
        assertEquals(UrlType.PRODUCT,
                classifier.classify(URI.create("https://example.com/product/red-shoes/")).type());
    }

    @Test
    void shouldClassifySpanishProductUrl() {
        assertEquals(UrlType.PRODUCT,
                classifier.classify(URI.create("https://example.com/producto/zapatos-rojos/")).type());
    }

    @Test
    void shouldClassifyFrenchProductUrl() {
        assertEquals(UrlType.PRODUCT,
                classifier.classify(URI.create("https://example.com/produit/chaussures-rouges/")).type());
    }

    @Test
    void shouldClassifyShopProductUrl() {
        assertEquals(UrlType.PRODUCT,
                classifier.classify(URI.create("https://example.com/shop/blue-jeans/")).type());
    }

    @Test
    void shouldClassifyStoreProductUrl() {
        assertEquals(UrlType.PRODUCT,
                classifier.classify(URI.create("https://example.com/store/blue-jeans/")).type());
    }

    @Test
    void shouldClassifyTiendaProductUrl() {
        assertEquals(UrlType.PRODUCT,
                classifier.classify(URI.create("https://example.com/tienda/camisetas/")).type());
    }

    @Test
    void shouldClassifyEnglishCategoryUrl() {
        assertEquals(UrlType.CATEGORY,
                classifier.classify(URI.create("https://example.com/product-category/shoes/")).type());
    }

    @Test
    void shouldClassifySpanishCategoryUrl() {
        assertEquals(UrlType.CATEGORY,
                classifier.classify(URI.create("https://example.com/categoria-producto/calzado/")).type());
    }

    @Test
    void shouldClassifyCartUrl() {
        assertEquals(UrlType.CART,
                classifier.classify(URI.create("https://example.com/cart/")).type());
    }

    @Test
    void shouldClassifyCartUrlWithoutTrailingSlash() {
        assertEquals(UrlType.CART,
                classifier.classify(URI.create("https://example.com/cart")).type());
    }

    @Test
    void shouldClassifyCheckoutUrl() {
        assertEquals(UrlType.CHECKOUT,
                classifier.classify(URI.create("https://example.com/checkout/")).type());
    }

    @Test
    void shouldClassifyMyAccountUrl() {
        assertEquals(UrlType.ACCOUNT,
                classifier.classify(URI.create("https://example.com/my-account/")).type());
    }

    @Test
    void shouldClassifyAccountUrl() {
        assertEquals(UrlType.ACCOUNT,
                classifier.classify(URI.create("https://example.com/account/")).type());
    }

    @Test
    void shouldClassifyWpAdminUrl() {
        assertEquals(UrlType.ADMIN,
                classifier.classify(URI.create("https://example.com/wp-admin/")).type());
    }

    @Test
    void shouldClassifyWpLoginUrl() {
        assertEquals(UrlType.ADMIN,
                classifier.classify(URI.create("https://example.com/wp-login.php")).type());
    }

    @Test
    void shouldClassifyWpJsonUrl() {
        assertEquals(UrlType.IGNORED,
                classifier.classify(URI.create("https://example.com/wp-json/wp/v2/posts")).type());
    }

    @Test
    void shouldClassifyFeedUrl() {
        assertEquals(UrlType.FEED,
                classifier.classify(URI.create("https://example.com/feed/")).type());
    }

    @Test
    void shouldClassifySearchUrl() {
        assertEquals(UrlType.SEARCH,
                classifier.classify(URI.create("https://example.com/search/test")).type());
    }

    @Test
    void shouldClassifyOrderTrackingUrl() {
        assertEquals(UrlType.IGNORED,
                classifier.classify(URI.create("https://example.com/order-tracking/")).type());
    }

    @Test
    void shouldClassifyAddToCartQueryParam() {
        assertEquals(UrlType.IGNORED,
                classifier.classify(URI.create("https://example.com/product/shoes/?add-to-cart=123")).type());
    }

    @Test
    void shouldClassifyFilterQueryParam() {
        assertEquals(UrlType.IGNORED,
                classifier.classify(URI.create("https://example.com/product-category/shoes/?filter_color=red")).type());
    }

    @Test
    void shouldClassifyOrderByQueryParam() {
        assertEquals(UrlType.IGNORED,
                classifier.classify(URI.create("https://example.com/shop/?orderby=price")).type());
    }

    @Test
    void shouldClassifyMinPriceQueryParam() {
        assertEquals(UrlType.IGNORED,
                classifier.classify(URI.create("https://example.com/shop/?min_price=10")).type());
    }

    @Test
    void shouldClassifyUrmQueryParams() {
        assertEquals(UrlType.IGNORED,
                classifier.classify(URI.create("https://example.com/product/shoes/?utm_source=google")).type());
    }

    @Test
    void shouldClassifyJpgAsset() {
        assertEquals(UrlType.ASSET,
                classifier.classify(URI.create("https://example.com/wp-content/uploads/photo.jpg")).type());
    }

    @Test
    void shouldClassifyPngAsset() {
        assertEquals(UrlType.ASSET,
                classifier.classify(URI.create("https://example.com/images/logo.png")).type());
    }

    @Test
    void shouldClassifyCssAsset() {
        assertEquals(UrlType.ASSET,
                classifier.classify(URI.create("https://example.com/wp-content/themes/style.css")).type());
    }

    @Test
    void shouldClassifyJsAsset() {
        assertEquals(UrlType.ASSET,
                classifier.classify(URI.create("https://example.com/js/main.js")).type());
    }

    @Test
    void shouldClassifyPdfAsset() {
        assertEquals(UrlType.ASSET,
                classifier.classify(URI.create("https://example.com/docs/catalog.pdf")).type());
    }

    @Test
    void shouldClassifyBlogPostByYearMonthPattern() {
        assertEquals(UrlType.BLOG_POST,
                classifier.classify(URI.create("https://example.com/2024/01/hello-world/")).type());
    }

    @Test
    void shouldClassifyBlogPostByBlogYearPattern() {
        assertEquals(UrlType.BLOG_POST,
                classifier.classify(URI.create("https://example.com/blog/2024/01/hello-world/")).type());
    }

    @Test
    void shouldClassifyUnknownUrl() {
        assertEquals(UrlType.UNKNOWN,
                classifier.classify(URI.create("https://example.com/custom-landing-page/")).type());
    }

    @Test
    void shouldRejectNullUri() {
        assertThrows(NullPointerException.class, () -> classifier.classify(null));
    }
}
