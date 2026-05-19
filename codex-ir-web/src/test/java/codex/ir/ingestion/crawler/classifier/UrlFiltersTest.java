package codex.ir.ingestion.crawler.classifier;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlFiltersTest {

    private static final ClassifiedUrl PRODUCT_URL =
            new ClassifiedUrl(URI.create("https://example.com/product/shoes/"), UrlType.PRODUCT);
    private static final ClassifiedUrl CATEGORY_URL =
            new ClassifiedUrl(URI.create("https://example.com/product-category/shoes/"), UrlType.CATEGORY);
    private static final ClassifiedUrl ADMIN_URL =
            new ClassifiedUrl(URI.create("https://example.com/wp-admin/"), UrlType.ADMIN);

    @Test
    void acceptAllShouldAcceptEverything() {
        assertTrue(UrlFilters.acceptAll().accepts(PRODUCT_URL));
        assertTrue(UrlFilters.acceptAll().accepts(ADMIN_URL));
    }

    @Test
    void rejectAllShouldRejectEverything() {
        assertFalse(UrlFilters.rejectAll().accepts(PRODUCT_URL));
        assertFalse(UrlFilters.rejectAll().accepts(ADMIN_URL));
    }

    @Test
    void includeTypesShouldAcceptOnlySpecifiedTypes() {
        final UrlFilter filter = UrlFilters.includeTypes(UrlType.PRODUCT);

        assertTrue(filter.accepts(PRODUCT_URL));
        assertFalse(filter.accepts(CATEGORY_URL));
        assertFalse(filter.accepts(ADMIN_URL));
    }

    @Test
    void includeTypesWithNoArgumentsShouldReturnRejectAll() {
        final UrlFilter filter = UrlFilters.includeTypes();

        assertFalse(filter.accepts(PRODUCT_URL));
        assertFalse(filter.accepts(CATEGORY_URL));
    }

    @Test
    void excludeTypesShouldRejectSpecifiedTypes() {
        final UrlFilter filter = UrlFilters.excludeTypes(UrlType.ADMIN);

        assertTrue(filter.accepts(PRODUCT_URL));
        assertTrue(filter.accepts(CATEGORY_URL));
        assertFalse(filter.accepts(ADMIN_URL));
    }

    @Test
    void excludeTypesWithNoArgumentsShouldReturnAcceptAll() {
        final UrlFilter filter = UrlFilters.excludeTypes();

        assertTrue(filter.accepts(PRODUCT_URL));
        assertTrue(filter.accepts(ADMIN_URL));
    }

    @Test
    void pathStartsWithShouldMatchPrefix() {
        final UrlFilter filter = UrlFilters.pathStartsWith("/product/");

        assertTrue(filter.accepts(PRODUCT_URL));
        assertFalse(filter.accepts(CATEGORY_URL));
    }

    @Test
    void pathMatchesShouldMatchRegex() {
        final UrlFilter filter = UrlFilters.pathMatches(Pattern.compile("/(product|producto)/"));

        assertTrue(filter.accepts(PRODUCT_URL));
        assertFalse(filter.accepts(CATEGORY_URL));
    }

    @Test
    void allOfShouldRequireAllFilters() {
        final UrlFilter filter = UrlFilters.allOf(
                UrlFilters.includeTypes(UrlType.PRODUCT),
                UrlFilters.pathStartsWith("/product/")
        );

        assertTrue(filter.accepts(PRODUCT_URL));
        assertFalse(filter.accepts(new ClassifiedUrl(
                URI.create("https://example.com/shop/item/"), UrlType.PRODUCT)));
    }

    @Test
    void anyOfShouldAcceptIfAnyFilterMatches() {
        final UrlFilter filter = UrlFilters.anyOf(
                UrlFilters.includeTypes(UrlType.PRODUCT),
                UrlFilters.pathStartsWith("/product-category/")
        );

        assertTrue(filter.accepts(PRODUCT_URL));
        assertTrue(filter.accepts(CATEGORY_URL));
        assertFalse(filter.accepts(ADMIN_URL));
    }

    @Test
    void notShouldNegateFilter() {
        final UrlFilter filter = UrlFilters.not(UrlFilters.includeTypes(UrlType.ADMIN));

        assertTrue(filter.accepts(PRODUCT_URL));
        assertFalse(filter.accepts(ADMIN_URL));
    }
}
