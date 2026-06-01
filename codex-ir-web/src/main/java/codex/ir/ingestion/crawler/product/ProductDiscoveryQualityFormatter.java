package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.crawler.internal.product.ExtractionQualitySummary;
import codex.ir.ingestion.crawler.internal.product.ExtractionSummarizer;
import codex.ir.ingestion.crawler.internal.product.ExtractionWarnings;
import codex.ir.ingestion.crawler.internal.product.PageExtractionResult;
import codex.ir.ingestion.crawler.internal.product.ProductCardExtract;
import codex.ir.ingestion.crawler.internal.product.ProductDetailExtract;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Formats an extraction quality summary from a {@link ProductDiscoveryReport}.
 *
 * <p>Bridges the public discovery API to the internal extraction quality
 * models for runner/report output.</p>
 */
public final class ProductDiscoveryQualityFormatter {

    private ProductDiscoveryQualityFormatter() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Formats a human-readable extraction quality summary string.
     *
     * @param report the product discovery report, must not be null
     * @return a formatted summary string
     */
    public static String formatSummary(final ProductDiscoveryReport report) {
        Objects.requireNonNull(report, "report must not be null");
        final ExtractionQualitySummary summary = computeSummary(report);
        return format(summary);
    }

    /**
     * Computes an {@link ExtractionQualitySummary} from a {@link ProductDiscoveryReport}.
     *
     * @param report the product discovery report, must not be null
     * @return the extraction quality summary
     */
    public static ExtractionQualitySummary computeSummary(final ProductDiscoveryReport report) {
        Objects.requireNonNull(report, "report must not be null");
        final List<PageExtractionResult> pageResults = toPageExtractionResults(report);
        return ExtractionSummarizer.summarize(pageResults);
    }

    private static List<PageExtractionResult> toPageExtractionResults(final ProductDiscoveryReport report) {
        final List<PageExtractionResult> results = new ArrayList<>();
        for (final ProductDiscoveryResult r : report.pageResults()) {
            results.add(toPageExtractionResult(r));
        }
        return results;
    }

    static PageExtractionResult toPageExtractionResult(final ProductDiscoveryResult result) {
        ProductDetailExtract detail = null;
        if (result.productDetail().isPresent()) {
            final ProductDetailExtract base = ProductDetailExtract.fromProductDetail(result.productDetail().get());
            detail = base.withWarnings(ExtractionWarnings.forProductDetail(base));
        }

        final List<ProductCardExtract> cards = new ArrayList<>();
        for (final ProductCard card : result.productCards()) {
            final ProductCardExtract base = ProductCardExtract.fromProductCard(card);
            cards.add(base.withWarnings(ExtractionWarnings.forProductCard(base)));
        }

        return new PageExtractionResult(
                result.pageUri(),
                result.pageClassification(),
                Optional.ofNullable(detail),
                cards,
                List.of()
        );
    }

    private static String format(final ExtractionQualitySummary s) {
        final String sep = "=".repeat(60);
        final StringBuilder sb = new StringBuilder();
        sb.append(sep).append("\n");
        sb.append("  Quality Summary").append("\n");
        sb.append(sep).append("\n");
        sb.append(String.format("  %-25s %d%n", "Pages processed", s.pagesProcessed()));
        sb.append(String.format("  %-25s %d%n", "Product detail pages", s.productDetailPages()));
        sb.append(String.format("  %-25s %d%n", "Category pages", s.categoryPages()));
        sb.append(String.format("  %-25s %d%n", "Product cards found", s.productCardsFound()));
        sb.append(String.format("  %-25s %d%n", "Category cards found", s.categoryCardsFound()));
        sb.append(String.format("  %-25s %d%n", "Navigation ignored", s.navigationCardsIgnored()));
        sb.append(String.format("  %-25s %d%n", "Unique product URLs", s.uniqueProductUrls()));
        sb.append(String.format("  %-25s %d%n", "Products with detail", s.productsWithDetail()));
        sb.append(String.format("  %-25s %d%n", "Products only in cards", s.productsOnlyFromCards()));
        sb.append(String.format("  %-25s %d%n", "Price warnings", s.priceParseWarnings()));
        sb.append(String.format("  %-25s %d%n", "Missing image warnings", s.missingImageWarnings()));
        sb.append(String.format("  %-25s %d%n", "Missing SKU warnings", s.missingSkuWarnings()));
        sb.append(String.format("  %-25s %d%n", "Missing desc warnings", s.missingDescriptionWarnings()));
        sb.append(sep);
        return sb.toString();
    }
}
