package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;

/**
 * Orchestrates page classification and product extraction for a single fetched page.
 */
@FunctionalInterface
public interface ProductDiscoverer {

    /**
     * Classifies the page and extracts whichever product data is appropriate
     * for its type.
     *
     * @param page the fetched web page
     * @return the discovery result — never {@code null}
     */
    ProductDiscoveryResult discover(WebPage page);
}
