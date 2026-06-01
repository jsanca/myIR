package codex.ir.ingestion.crawler.internal.product;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Classifies extracted cards as PRODUCT, CATEGORY, NAVIGATION, or UNKNOWN
 * based on URL path patterns and label text.
 *
 * <p>URL-path rules take precedence over label rules; PRODUCT and CATEGORY
 * are determined by path segments, while NAVIGATION is determined by
 * fragment-only hrefs or known navigation labels.</p>
 */
public final class CardClassifier {

    private static final List<String> NAVIGATION_LABELS = List.of(
            "volver", "categorías", "categorias", "ver todos"
    );

    private CardClassifier() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Classifies a card based on its URL path, display label, and raw href attribute.
     *
     * @param url     the resolved product card URL, must not be null
     * @param label   the display label (card name), may be null
     * @param rawHref the raw {@code href} attribute before resolution, may be null
     * @return the card type classification
     */
    public static ExtractedCardType classify(final URI url, final String label, final String rawHref) {
        Objects.requireNonNull(url, "url must not be null");

        if (rawHref != null && rawHref.startsWith("#")) {
            return ExtractedCardType.NAVIGATION;
        }

        if (isNavigationLabel(label)) {
            return ExtractedCardType.NAVIGATION;
        }

        final String path = url.getRawPath();
        if (path != null) {
            final String lower = path.toLowerCase();

            if (lower.contains("/producto/") || lower.contains("/product/")) {
                return ExtractedCardType.PRODUCT;
            }

            if (lower.contains("/category-producto/")
                    || lower.contains("/product-category/")
                    || lower.contains("/categoria-producto/")
                    || lower.contains("/product_cat/")) {
                return ExtractedCardType.CATEGORY;
            }
        }

        return ExtractedCardType.UNKNOWN;
    }

    private static boolean isNavigationLabel(final String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        final String lower = label.strip().toLowerCase();
        for (final String navLabel : NAVIGATION_LABELS) {
            if (lower.equals(navLabel) || lower.contains(navLabel)) {
                return true;
            }
        }
        return false;
    }
}
