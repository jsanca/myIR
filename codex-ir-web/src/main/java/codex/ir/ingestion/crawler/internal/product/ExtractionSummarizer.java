package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.classifier.UrlType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Computes {@link ExtractionQualitySummary} from a list of {@link PageExtractionResult}.
 */
public final class ExtractionSummarizer {

    private ExtractionSummarizer() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    public static ExtractionQualitySummary summarize(final List<PageExtractionResult> results) {
        Objects.requireNonNull(results, "results must not be null");

        int pagesProcessed = results.size();
        int productDetailPages = 0;
        int categoryPages = 0;
        int productCardsFound = 0;
        int categoryCardsFound = 0;
        int navigationCardsIgnored = 0;
        int priceParseWarnings = 0;
        int missingImageWarnings = 0;
        int missingSkuWarnings = 0;
        int missingDescriptionWarnings = 0;

        final Set<String> detailKeys = new HashSet<>();
        final Set<String> cardKeys = new HashSet<>();
        final Set<String> uniqueUrls = new HashSet<>();

        for (final PageExtractionResult result : results) {
            final UrlType pageType = result.pageClassification().type();

            if (result.productDetail().isPresent()) {
                productDetailPages++;
                final ProductDetailExtract detail = result.productDetail().get();
                detailKeys.add(detail.canonicalKey().value());
                uniqueUrls.add(detail.canonicalKey().value());
                priceParseWarnings += count(detail.warnings(), ExtractionWarning.PRICE_LOOKS_SUSPICIOUS);
                missingImageWarnings += count(detail.warnings(), ExtractionWarning.MISSING_IMAGE);
                missingSkuWarnings += count(detail.warnings(), ExtractionWarning.MISSING_SKU);
                missingDescriptionWarnings += count(detail.warnings(), ExtractionWarning.MISSING_DESCRIPTION);
            }

            if (pageType == UrlType.CATEGORY) {
                categoryPages++;
            }

            for (final ProductCardExtract card : result.productCards()) {
                productCardsFound++;
                cardKeys.add(card.canonicalKey().value());
                uniqueUrls.add(card.canonicalKey().value());
                priceParseWarnings += count(card.warnings(), ExtractionWarning.PRICE_LOOKS_SUSPICIOUS);
                missingImageWarnings += count(card.warnings(), ExtractionWarning.MISSING_IMAGE);
            }

            for (final CategoryExtract cat : result.categories()) {
                if (cat.cardType() == ExtractedCardType.NAVIGATION) {
                    navigationCardsIgnored++;
                } else {
                    categoryCardsFound++;
                }
            }
        }

        final int productsWithDetail = detailKeys.size();
        final int productsOnlyFromCards = countOnlyInCards(detailKeys, cardKeys);

        return new ExtractionQualitySummary(
                pagesProcessed,
                productDetailPages,
                categoryPages,
                productCardsFound,
                categoryCardsFound,
                navigationCardsIgnored,
                uniqueUrls.size(),
                productsWithDetail,
                productsOnlyFromCards,
                priceParseWarnings,
                missingImageWarnings,
                missingSkuWarnings,
                missingDescriptionWarnings
        );
    }

    private static int count(final List<ExtractionWarning> warnings, final ExtractionWarning target) {
        int count = 0;
        for (final ExtractionWarning w : warnings) {
            if (w == target) {
                count++;
            }
        }
        return count;
    }

    private static int countOnlyInCards(final Set<String> detailKeys, final Set<String> cardKeys) {
        final Set<String> onlyCards = new HashSet<>(cardKeys);
        onlyCards.removeAll(detailKeys);
        return onlyCards.size();
    }
}
