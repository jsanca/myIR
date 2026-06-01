package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.crawler.classifier.ClassifiedUrl;
import codex.ir.ingestion.crawler.classifier.PageClassification;
import codex.ir.ingestion.crawler.classifier.UrlType;
import codex.ir.ingestion.crawler.internal.product.ExtractionQualitySummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDiscoveryQualityFormatterTest {

    private static final URI PRODUCT_URI = URI.create("https://example.com/product/alpha/");
    private static final URI CATEGORY_URI = URI.create("https://example.com/shop/");
    private static final URI CARD_URI = URI.create("https://example.com/product/beta/");

    @Test
    void formatSummaryShouldRejectNullReport() {
        assertThrows(NullPointerException.class, () ->
                ProductDiscoveryQualityFormatter.formatSummary(null));
    }

    @Test
    void formatSummaryShouldIncludePagesProcessed() {
        final ProductDiscoveryReport report = reportWithOneProductDetail();

        final String output = ProductDiscoveryQualityFormatter.formatSummary(report);

        assertTrue(output.contains("Pages processed"));
        assertTrue(output.contains("1"));
    }

    @Test
    void formatSummaryShouldIncludeProductDetailPages() {
        final ProductDiscoveryReport report = reportWithOneProductDetail();

        final String output = ProductDiscoveryQualityFormatter.formatSummary(report);

        assertTrue(output.contains("Product detail pages"));
    }

    @Test
    void formatSummaryShouldIncludeProductCardsFound() {
        final ProductDiscoveryReport report = reportWithProductCards();

        final String output = ProductDiscoveryQualityFormatter.formatSummary(report);

        assertTrue(output.contains("Product cards found"));
    }

    @Test
    void formatSummaryShouldIncludeUniqueProductUrls() {
        final ProductDiscoveryReport report = reportWithProductCards();

        final String output = ProductDiscoveryQualityFormatter.formatSummary(report);

        assertTrue(output.contains("Unique product URLs"));
    }

    @Test
    void formatSummaryShouldIncludeWarningCounts() {
        final ProductDiscoveryReport report = reportWithOneProductDetail();

        final String output = ProductDiscoveryQualityFormatter.formatSummary(report);

        assertTrue(output.contains("Price warnings"));
        assertTrue(output.contains("Missing image warnings"));
        assertTrue(output.contains("Missing SKU warnings"));
        assertTrue(output.contains("Missing desc warnings"));
    }

    @Test
    void computeSummaryShouldCountUniqueProductUrls() {
        final ProductCard card1 = new ProductCard(
                URI.create("https://example.com/product/alpha"),
                "Alpha", Optional.empty(), Optional.empty(), Optional.empty());
        final ProductCard card2 = new ProductCard(
                URI.create("https://example.com/product/alpha?ref=top"),
                "Alpha Again", Optional.empty(), Optional.empty(), Optional.empty());

        final ProductDiscoveryResult result = new ProductDiscoveryResult(
                CATEGORY_URI,
                categoryClassification(CATEGORY_URI),
                Optional.empty(),
                List.of(card1, card2));

        final ProductDiscoveryReport report = new ProductDiscoveryReport(
                List.of(result), List.of(), List.of(card1, card2));

        final ExtractionQualitySummary summary =
                ProductDiscoveryQualityFormatter.computeSummary(report);

        assertEquals(1, summary.uniqueProductUrls());
    }

    @Test
    void computeSummaryShouldCountProductsWithDetail() {
        final ProductDiscoveryReport report = reportWithOneProductDetail();

        final ExtractionQualitySummary summary =
                ProductDiscoveryQualityFormatter.computeSummary(report);

        assertEquals(1, summary.productsWithDetail());
    }

    @Test
    void computeSummaryShouldCountProductsOnlyFromCards() {
        final ProductDiscoveryReport report = reportWithProductCards();

        final ExtractionQualitySummary summary =
                ProductDiscoveryQualityFormatter.computeSummary(report);

        assertEquals(1, summary.productsOnlyFromCards());
        assertEquals(0, summary.productsWithDetail());
    }

    @Test
    void computeSummaryShouldNotDoubleCountProductInDetailAndCards() {
        final ProductDetail detail = new ProductDetail(
                PRODUCT_URI, "Alpha", Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), Optional.empty(), Optional.empty());
        final ProductCard card = new ProductCard(
                PRODUCT_URI, "Alpha", Optional.empty(), Optional.empty(), Optional.empty());

        final ProductDiscoveryResult result = new ProductDiscoveryResult(
                PRODUCT_URI,
                productClassification(PRODUCT_URI),
                Optional.of(detail),
                List.of(card));

        final ProductDiscoveryReport report = new ProductDiscoveryReport(
                List.of(result), List.of(detail), List.of(card));

        final ExtractionQualitySummary summary =
                ProductDiscoveryQualityFormatter.computeSummary(report);

        assertEquals(1, summary.productsWithDetail());
        assertEquals(0, summary.productsOnlyFromCards());
    }

    @Test
    void computeSummaryShouldCountCategoryPages() {
        final ProductDetail detail = new ProductDetail(
                PRODUCT_URI, "Alpha", Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), Optional.empty(), Optional.empty());

        final ProductDiscoveryResult productResult = new ProductDiscoveryResult(
                PRODUCT_URI,
                productClassification(PRODUCT_URI),
                Optional.of(detail),
                List.of());

        final ProductCard card = new ProductCard(
                CARD_URI, "Card", Optional.empty(), Optional.empty(), Optional.empty());
        final ProductDiscoveryResult categoryResult = new ProductDiscoveryResult(
                CATEGORY_URI,
                categoryClassification(CATEGORY_URI),
                Optional.empty(),
                List.of(card));

        final ProductDiscoveryReport report = new ProductDiscoveryReport(
                List.of(productResult, categoryResult),
                List.of(detail),
                List.of(card));

        final ExtractionQualitySummary summary =
                ProductDiscoveryQualityFormatter.computeSummary(report);

        assertEquals(1, summary.productDetailPages());
        assertEquals(1, summary.categoryPages());
        assertEquals(2, summary.pagesProcessed());
    }

    @Test
    void computeSummaryShouldCountMissingSkuWarning() {
        final ProductDetail detail = new ProductDetail(
                PRODUCT_URI, "No SKU", Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), Optional.empty(), Optional.empty());

        final ProductDiscoveryResult result = new ProductDiscoveryResult(
                PRODUCT_URI,
                productClassification(PRODUCT_URI),
                Optional.of(detail),
                List.of());

        final ProductDiscoveryReport report = new ProductDiscoveryReport(
                List.of(result), List.of(detail), List.of());

        final ExtractionQualitySummary summary =
                ProductDiscoveryQualityFormatter.computeSummary(report);

        assertEquals(1, summary.missingSkuWarnings());
    }

    @Test
    void computeSummaryShouldCountMissingImageWarningForCard() {
        final ProductCard card = new ProductCard(
                CARD_URI, "Card", Optional.empty(), Optional.empty(), Optional.empty());

        final ProductDiscoveryResult result = new ProductDiscoveryResult(
                CATEGORY_URI,
                categoryClassification(CATEGORY_URI),
                Optional.empty(),
                List.of(card));

        final ProductDiscoveryReport report = new ProductDiscoveryReport(
                List.of(result), List.of(), List.of(card));

        final ExtractionQualitySummary summary =
                ProductDiscoveryQualityFormatter.computeSummary(report);

        assertEquals(1, summary.missingImageWarnings());
    }

    @Test
    void computeSummaryShouldHandleEmptyReport() {
        final ProductDiscoveryReport report = new ProductDiscoveryReport(
                List.of(), List.of(), List.of());

        final ExtractionQualitySummary summary =
                ProductDiscoveryQualityFormatter.computeSummary(report);

        assertEquals(0, summary.pagesProcessed());
        assertEquals(0, summary.productCardsFound());
        assertEquals(0, summary.uniqueProductUrls());
    }

    @Test
    void formatSummaryShouldReturnMultilineString() {
        final ProductDiscoveryReport report = reportWithOneProductDetail();

        final String output = ProductDiscoveryQualityFormatter.formatSummary(report);

        assertTrue(output.contains("\n"));
        assertTrue(output.contains("Quality Summary"));
    }

    private static ProductDiscoveryReport reportWithOneProductDetail() {
        final ProductDetail detail = new ProductDetail(
                PRODUCT_URI, "Alpha", Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(), Optional.empty(), Optional.empty());

        final ProductDiscoveryResult result = new ProductDiscoveryResult(
                PRODUCT_URI,
                productClassification(PRODUCT_URI),
                Optional.of(detail),
                List.of());

        return new ProductDiscoveryReport(List.of(result), List.of(detail), List.of());
    }

    private static ProductDiscoveryReport reportWithProductCards() {
        final ProductCard card = new ProductCard(
                CARD_URI, "Beta", Optional.of(new ProductPrice(new BigDecimal("19.99"))),
                Optional.empty(), Optional.empty());

        final ProductDiscoveryResult result = new ProductDiscoveryResult(
                CATEGORY_URI,
                categoryClassification(CATEGORY_URI),
                Optional.empty(),
                List.of(card));

        return new ProductDiscoveryReport(
                List.of(result), List.of(), List.of(card));
    }

    private static PageClassification productClassification(final URI uri) {
        return new PageClassification(uri, UrlType.PRODUCT,
                new ClassifiedUrl(uri, UrlType.PRODUCT), false, false);
    }

    private static PageClassification categoryClassification(final URI uri) {
        return new PageClassification(uri, UrlType.CATEGORY,
                new ClassifiedUrl(uri, UrlType.CATEGORY), false, false);
    }
}
