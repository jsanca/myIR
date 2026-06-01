package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.product.ProductPrice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Computes {@link ExtractionWarning} lists from extraction results.
 *
 * <p>Warnings are diagnostic only and do not affect extraction behavior.</p>
 */
public final class ExtractionWarnings {

    private static final BigDecimal SUSPICIOUS_PRICE_THRESHOLD = new BigDecimal("999999");

    private ExtractionWarnings() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    public static List<ExtractionWarning> forProductDetail(final ProductDetailExtract extract) {
        final List<ExtractionWarning> warnings = new ArrayList<>();

        if (extract.sku().isEmpty()) {
            warnings.add(ExtractionWarning.MISSING_SKU);
        }
        if (extract.images().isEmpty()) {
            warnings.add(ExtractionWarning.MISSING_IMAGE);
        }
        if (extract.shortDescription().isEmpty()) {
            warnings.add(ExtractionWarning.MISSING_DESCRIPTION);
        }
        if (hasSuspiciousPrice(extract.regularPrice(), extract.salePrice())) {
            warnings.add(ExtractionWarning.PRICE_LOOKS_SUSPICIOUS);
        }

        return List.copyOf(warnings);
    }

    public static List<ExtractionWarning> forProductCard(final ProductCardExtract extract) {
        final List<ExtractionWarning> warnings = new ArrayList<>();

        if (extract.thumbnail().isEmpty()) {
            warnings.add(ExtractionWarning.MISSING_IMAGE);
        }
        if (hasSuspiciousPrice(extract.regularPrice(), extract.salePrice())) {
            warnings.add(ExtractionWarning.PRICE_LOOKS_SUSPICIOUS);
        }
        if (extract.cardType() == ExtractedCardType.UNKNOWN) {
            warnings.add(ExtractionWarning.UNKNOWN_CARD_TYPE);
        }

        return List.copyOf(warnings);
    }

    public static List<ExtractionWarning> forCategory(final CategoryExtract extract) {
        final List<ExtractionWarning> warnings = new ArrayList<>();

        if (extract.cardType() == ExtractedCardType.NAVIGATION) {
            warnings.add(ExtractionWarning.CARD_CLASSIFIED_AS_NAVIGATION);
        }
        if (extract.cardType() == ExtractedCardType.UNKNOWN) {
            warnings.add(ExtractionWarning.UNKNOWN_CARD_TYPE);
        }

        return List.copyOf(warnings);
    }

    private static boolean hasSuspiciousPrice(
            final Optional<ProductPrice> regularPrice,
            final Optional<ProductPrice> salePrice) {

        if (regularPrice.isPresent() && isSuspicious(regularPrice.get().amount())) {
            return true;
        }
        if (salePrice.isPresent() && isSuspicious(salePrice.get().amount())) {
            return true;
        }
        if (regularPrice.isPresent() && salePrice.isPresent()) {
            if (salePrice.get().amount().compareTo(regularPrice.get().amount()) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSuspicious(final BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(SUSPICIOUS_PRICE_THRESHOLD) >= 0;
    }
}
