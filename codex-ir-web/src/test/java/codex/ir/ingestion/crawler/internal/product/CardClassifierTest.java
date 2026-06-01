package codex.ir.ingestion.crawler.internal.product;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardClassifierTest {

    @Test
    void productoPathShouldBeProduct() {
        assertEquals(ExtractedCardType.PRODUCT,
                classify("/producto/sole-wallet"));
    }

    @Test
    void productPathShouldBeProduct() {
        assertEquals(ExtractedCardType.PRODUCT,
                classify("/product/sole-wallet"));
    }

    @Test
    void categoryProductoPathShouldBeCategory() {
        assertEquals(ExtractedCardType.CATEGORY,
                classify("/category-producto/bolsos"));
    }

    @Test
    void productCategoryPathShouldBeCategory() {
        assertEquals(ExtractedCardType.CATEGORY,
                classify("/product-category/bags"));
    }

    @Test
    void categoriaProductoPathShouldBeCategory() {
        assertEquals(ExtractedCardType.CATEGORY,
                classify("/categoria-producto/accesorios"));
    }

    @Test
    void productCatPathShouldBeCategory() {
        assertEquals(ExtractedCardType.CATEGORY,
                classify("/product_cat/shoes"));
    }

    @Test
    void hashCategoriesHrefShouldBeNavigation() {
        assertEquals(ExtractedCardType.NAVIGATION,
                CardClassifier.classify(
                        URI.create("https://example.com/shop/#categories"),
                        "Some Label", "#categories"));
    }

    @Test
    void hashAnyHrefShouldBeNavigation() {
        assertEquals(ExtractedCardType.NAVIGATION,
                CardClassifier.classify(
                        URI.create("https://example.com/shop/#anything"),
                        "Some Label", "#anything"));
    }

    @Test
    void volverLabelShouldBeNavigation() {
        assertEquals(ExtractedCardType.NAVIGATION,
                classifyWithLabel("/shop/something", "Volver"));
    }

    @Test
    void categoriasLabelShouldBeNavigation() {
        assertEquals(ExtractedCardType.NAVIGATION,
                classifyWithLabel("/shop/something", "Categorías"));
    }

    @Test
    void categoriasLabelWithoutAccentShouldBeNavigation() {
        assertEquals(ExtractedCardType.NAVIGATION,
                classifyWithLabel("/shop/something", "Categorias"));
    }

    @Test
    void verTodosLabelShouldBeNavigation() {
        assertEquals(ExtractedCardType.NAVIGATION,
                classifyWithLabel("/shop/something", "Ver todos"));
    }

    @Test
    void unknownExternalLinkShouldBeUnknown() {
        assertEquals(ExtractedCardType.UNKNOWN,
                classify("/info/about"));
    }

    @Test
    void unknownPageShouldBeUnknown() {
        assertEquals(ExtractedCardType.UNKNOWN,
                classify("/tienda"));
    }

    @Test
    void pathCaseShouldBeIgnoredForProduct() {
        assertEquals(ExtractedCardType.PRODUCT,
                classify("/Producto/Sole-Wallet"));
    }

    @Test
    void pathCaseShouldBeIgnoredForCategory() {
        assertEquals(ExtractedCardType.CATEGORY,
                classify("/Category-Producto/Bolsos"));
    }

    @Test
    void rawHrefStartingWithHashOverridesPath() {
        assertEquals(ExtractedCardType.NAVIGATION,
                CardClassifier.classify(
                        URI.create("https://example.com/producto/something"),
                        "Product Name", "#section"));
    }

    @Test
    void navigationLabelOverridesUnknownPath() {
        assertEquals(ExtractedCardType.NAVIGATION,
                classifyWithLabel("/shop/page", "Categorías"));
    }

    private static ExtractedCardType classify(final String path) {
        return CardClassifier.classify(URI.create("https://example.com" + path), null, null);
    }

    private static ExtractedCardType classifyWithLabel(final String path, final String label) {
        return CardClassifier.classify(URI.create("https://example.com" + path), label, null);
    }
}
