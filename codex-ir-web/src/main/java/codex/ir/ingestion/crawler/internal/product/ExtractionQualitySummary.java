package codex.ir.ingestion.crawler.internal.product;

import java.util.Objects;

/**
 * Summary of extraction quality computed from {@link PageExtractionResult} data.
 */
public record ExtractionQualitySummary(
        int pagesProcessed,
        int productDetailPages,
        int categoryPages,
        int productCardsFound,
        int categoryCardsFound,
        int navigationCardsIgnored,
        int uniqueProductUrls,
        int productsWithDetail,
        int productsOnlyFromCards,
        int priceParseWarnings,
        int missingImageWarnings,
        int missingSkuWarnings,
        int missingDescriptionWarnings
) {
    public ExtractionQualitySummary {
        if (pagesProcessed < 0) {
            throw new IllegalArgumentException("pagesProcessed must not be negative");
        }
        if (productDetailPages < 0) {
            throw new IllegalArgumentException("productDetailPages must not be negative");
        }
        if (categoryPages < 0) {
            throw new IllegalArgumentException("categoryPages must not be negative");
        }
        if (productCardsFound < 0) {
            throw new IllegalArgumentException("productCardsFound must not be negative");
        }
        if (categoryCardsFound < 0) {
            throw new IllegalArgumentException("categoryCardsFound must not be negative");
        }
        if (navigationCardsIgnored < 0) {
            throw new IllegalArgumentException("navigationCardsIgnored must not be negative");
        }
        if (uniqueProductUrls < 0) {
            throw new IllegalArgumentException("uniqueProductUrls must not be negative");
        }
        if (productsWithDetail < 0) {
            throw new IllegalArgumentException("productsWithDetail must not be negative");
        }
        if (productsOnlyFromCards < 0) {
            throw new IllegalArgumentException("productsOnlyFromCards must not be negative");
        }
        if (priceParseWarnings < 0) {
            throw new IllegalArgumentException("priceParseWarnings must not be negative");
        }
        if (missingImageWarnings < 0) {
            throw new IllegalArgumentException("missingImageWarnings must not be negative");
        }
        if (missingSkuWarnings < 0) {
            throw new IllegalArgumentException("missingSkuWarnings must not be negative");
        }
        if (missingDescriptionWarnings < 0) {
            throw new IllegalArgumentException("missingDescriptionWarnings must not be negative");
        }
    }

    public static ExtractionQualitySummary empty() {
        return new ExtractionQualitySummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
