package codex.ir.ingestion.crawler.classifier;

import java.net.URI;

/**
 * A URL together with its URL-pattern-based classification.
 *
 * @param uri the original URI that was classified
 * @param type the broad page type inferred from the URL pattern
 */
public record ClassifiedUrl(URI uri, UrlType type) {
}
