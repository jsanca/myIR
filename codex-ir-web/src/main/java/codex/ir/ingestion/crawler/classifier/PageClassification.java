package codex.ir.ingestion.crawler.classifier;

import java.net.URI;

/**
 * Classification result after inspecting both the URL pattern and
 * the fetched page HTML.
 *
 * @param uri the page URI
 * @param type the refined page type after HTML inspection
 * @param urlClassification the original URL-only classification
 * @param wordpressDetected whether WordPress signals were found in the HTML
 * @param wooCommerceDetected whether WooCommerce signals were found in the HTML
 */
public record PageClassification(
        URI uri,
        UrlType type,
        ClassifiedUrl urlClassification,
        boolean wordpressDetected,
        boolean wooCommerceDetected
) {
}
