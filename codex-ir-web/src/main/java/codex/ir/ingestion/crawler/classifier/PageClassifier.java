package codex.ir.ingestion.crawler.classifier;

import codex.ir.ingestion.WebPage;

/**
 * Classifies a fetched {@link WebPage} by inspecting both its URL
 * (via the injected {@link UrlClassifier}) and its HTML content.
 *
 * <p>Unlike {@link UrlClassifier} which is URL-only, a
 * {@code PageClassifier} may override or refine the URL-based type
 * when HTML signals provide stronger evidence (e.g. a page at a
 * generic URL path may be detected as a product page via WooCommerce
 * CSS classes or JSON-LD).</p>
 */
@FunctionalInterface
public interface PageClassifier {

    /**
     * Classifies the given fetched page.
     *
     * @param page the fetched web page with HTML content; must not be null
     * @return classification result including refined type and detection flags
     */
    PageClassification classify(WebPage page);
}
