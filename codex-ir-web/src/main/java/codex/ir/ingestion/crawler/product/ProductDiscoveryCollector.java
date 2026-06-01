package codex.ir.ingestion.crawler.product;

import codex.ir.ingestion.WebPage;

import java.util.List;

/**
 * Runs product discovery over a batch of already-fetched pages and
 * aggregates the results into a {@link ProductDiscoveryReport}.
 *
 * <p>Implementations must not fetch additional pages or perform crawling.</p>
 */
@FunctionalInterface
public interface ProductDiscoveryCollector {

    /**
     * Discovers product data across all provided pages.
     *
     * @param pages the fetched pages to inspect; must not be {@code null}
     * @return an aggregated report — never {@code null}
     */
    ProductDiscoveryReport collect(List<WebPage> pages);
}
