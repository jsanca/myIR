package codex.ir.ingestion.crawler.classifier;

import codex.ir.ingestion.crawler.internal.classifier.JsoupGenericPageClassifier;
import codex.ir.ingestion.crawler.internal.classifier.WordPressWooCommercePageClassifier;

import java.util.Objects;

/**
 * Factory for {@link PageClassifier} implementations.
 */
public final class PageClassifiers {

    private PageClassifiers() {
    }

    /**
     * Returns a general-purpose Jsoup-based classifier using weighted HTML signal
     * scoring. Detects product detail pages, product listings, articles, and home
     * pages without requiring WooCommerce or WordPress signals.
     *
     * <p>When no HTML signal reaches the scoring threshold the URL-based type
     * is preserved. Falls back to {@link UrlType#UNKNOWN} when neither URL nor
     * HTML signals are conclusive.</p>
     *
     * @return a generic HTML signal classifier
     */
    public static PageClassifier jsoupDefault() {
        return new JsoupGenericPageClassifier(UrlClassifiers.wordpressWooCommerceDefaultWeb());
    }

    /**
     * Returns a classifier that detects WordPress and WooCommerce signals
     * in HTML content, refining the URL-based classification.
     *
     * <p>URL types that are clearly not content pages ({@code ADMIN},
     * {@code CART}, etc.) are returned as-is without HTML inspection.
     * For other types, HTML signals may refine the classification.</p>
     *
     * @param urlClassifier the URL classifier used as the first stage
     * @return a WordPress/WooCommerce-aware page classifier
     */
    public static PageClassifier wordpressWooCommerceDefault(final UrlClassifier urlClassifier) {
        return new WordPressWooCommercePageClassifier(
                Objects.requireNonNull(urlClassifier, "urlClassifier must not be null"));
    }
}
