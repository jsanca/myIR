package codex.ir.ingestion.crawler.classifier;

import java.net.URI;

/**
 * Classifies a URI into a broad {@link UrlType} based on its URL pattern.
 *
 * <p>This interface is intentionally URL-only — it does not require
 * fetching the page or inspecting HTML content. Implementations may
 * use path patterns, query parameters, file extensions, or host-based
 * rules to determine {@link UrlType}.</p>
 *
 * <p>HTML-based classification (e.g. detecting WooCommerce via body
 * classes or JSON-LD) is a separate concern that may be layered on
 * top when the page has been fetched.</p>
 */
@FunctionalInterface
public interface UrlClassifier {

    /**
     * Classifies the given URI.
     *
     * @param uri the URI to classify; must not be null
     * @return classified URL with type
     */
    ClassifiedUrl classify(URI uri);
}
